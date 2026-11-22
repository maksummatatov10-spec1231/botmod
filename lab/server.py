#!/usr/bin/env python3
"""
BotLab — локальная лаборатория для тестирования мода BotMod.

Что умеет:
  * забирает телеметрию из Minecraft-мода (http://127.0.0.1:17777/api/state);
  * если мод недоступен — работает в режиме симуляции, чтобы дашборд
    можно было проверять без запущенной игры;
  * складывает кадры (скриншоты) в lab/frames и отдаёт их в дашборд;
  * принимает команды и сценарии тестирования и пересылает их моду.

Запуск:
    python3 lab/server.py --port 8080 --mc http://127.0.0.1:17777 --fps 1

Без флагов: порт 8080, автопоиск мода на 127.0.0.1:17777, 1 кадр/сек.
"""

from __future__ import annotations

import argparse
import json
import math
import os
import random
import struct
import threading
import time
import urllib.error
import urllib.request
import zlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

LAB_DIR = Path(__file__).resolve().parent
ROOT = LAB_DIR.parent  # корень репозитория
FRAMES_DIR = LAB_DIR / "frames"
REPORTS_DIR = LAB_DIR / "reports"

STATE_LOCK = threading.Lock()
FRAME_LOCK = threading.Lock()

CONFIG = {
    "mc_url": "http://127.0.0.1:17777",
    "capture_fps": 1.0,
    "max_frames": 240,
}

RUNTIME = {
    "recording": False,
    "capture_thread": None,
    "frame_count": 0,
    "last_frame": None,
    "last_frame_time": 0.0,
    "fps": 0.0,
    "frame_ms": 0.0,
    "fps_history": [],
    "scenario": None,
    "source": "mock",
}

EVENTS = []          # ring buffer
MAX_EVENTS = 400

MOCK = {
    "t0": time.time(),
    "bots": [],
    "next_id": 1,
    "players": 1,
}


# --------------------------------------------------------------------------
# PNG: синтетический кадр (на случай, когда экрана нет — песочница, CI, headless)
# --------------------------------------------------------------------------

def _png_bytes(width: int, height: int, painter) -> bytes:
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        for x in range(width):
            r, g, b = painter(x, y)
            raw += bytes((r, g, b))

    def chunk(tag: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    out = b"\x89PNG\r\n\x1a\n"
    out += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
    out += chunk(b"IDAT", zlib.compress(bytes(raw), 6))
    out += chunk(b"IEND", b"")
    return out


def synthetic_frame(t: float, state: dict) -> bytes:
    """Рисуем «вид из игры»: небо, земля, силуэты ботов и полосу состояния."""
    w, h = 320, 180

    def paint(x: int, y: int):
        horizon = 96
        if y < horizon:
            k = y / horizon
            return (int(96 - 40 * k), int(150 - 50 * k), int(210 - 40 * k))
        # земля
        k = (y - horizon) / (h - horizon)
        return (int(70 - 20 * k), int(110 - 25 * k), int(62 - 18 * k))

    bots = state.get("bots", [])
    px = bytearray()

    def put(x: int, y: int, rgb):
        if 0 <= x < w and 0 <= y < h:
            idx = (y * w + x) * 3
            px[idx:idx + 3] = bytes(rgb)

    # заполняем фон
    for y in range(h):
        for x in range(w):
            px += bytes(paint(x, y))

    # «солнце»
    for cy in range(18, 38):
        for cx in range(250, 278):
            if (cx - 264) ** 2 + (cy - 28) ** 2 < 220:
                put(cx, cy, (255, 236, 170))

    # боты: прямоугольники + полоска HP
    for i, bot in enumerate(bots[:4]):
        bx = 40 + i * 72 + int(8 * math.sin(t * 0.7 + i))
        by = 70 + int(2 * math.sin(t * 1.3 + i))
        body = (198, 150, 114) if bot.get("kit") != "diamond_sword" else (120, 200, 255)
        for yy in range(by, by + 34):
            for xx in range(bx, bx + 18):
                put(xx, yy, body if yy < by + 22 else (46, 52, 64))
        # голова
        for yy in range(by - 16, by - 2):
            for xx in range(bx + 2, bx + 16):
                put(xx, yy, (60, 64, 74))
        # HP bar
        ratio = bot.get("health", 20) / max(1.0, bot.get("maxHealth", 20))
        for xx in range(bx, bx + 18):
            put(xx, by - 20, (60, 200, 90) if xx - bx < 18 * ratio else (200, 60, 60))

    # нижняя полоса «HUD»
    for yy in range(h - 18, h):
        for xx in range(w):
            put(xx, yy, (12, 14, 18))

    # индикатор записи
    if RUNTIME["recording"]:
        phase = int(t * 2) % 2
        for yy in range(6, 16):
            for xx in range(6, 16):
                put(xx, yy, (230, 60, 60) if phase else (120, 30, 30))

    return _png_bytes(w, h, lambda x, y: tuple(px[(y * w + x) * 3:(y * w + x) * 3 + 3]))


# --------------------------------------------------------------------------
# Захват кадра: сначала реальный экран, потом синтетика
# --------------------------------------------------------------------------

def capture_frame(state: dict) -> dict:
    started = time.time()
    data = None
    method = "synthetic"

    try:
        import mss  # type: ignore

        with mss.mss() as sct:
            shot = sct.grab(sct.monitors[1])
            data = _png_bytes(shot.width, shot.height,
                              lambda x, y: tuple(shot.pixel(x, y))[:3])
            method = "mss"
    except Exception:
        try:
            from PIL import ImageGrab  # type: ignore

            img = ImageGrab.grab()
            img = img.convert("RGB")
            data = _png_bytes(img.width, img.height, lambda x, y: img.getpixel((x, y)))
            method = "pil"
        except Exception:
            data = synthetic_frame(time.time(), state)

    elapsed_ms = (time.time() - started) * 1000.0
    name = time.strftime("frame_%Y%m%d_%H%M%S") + f"_{int(time.time() * 1000) % 1000:03d}.png"

    with FRAME_LOCK:
        FRAMES_DIR.mkdir(parents=True, exist_ok=True)
        (FRAMES_DIR / name).write_bytes(data)
        RUNTIME["last_frame"] = name
        RUNTIME["frame_count"] += 1
        RUNTIME["frame_ms"] = elapsed_ms
        # чистим старые кадры
        files = sorted(FRAMES_DIR.glob("*.png"))
        for old in files[:-CONFIG["max_frames"]]:
            old.unlink(missing_ok=True)

    return {"name": name, "method": method, "ms": round(elapsed_ms, 2)}


def store_frame(data: bytes, method: str) -> dict:
    """Сохраняет готовый PNG (пришёл от мода) и обновляет счётчики."""
    started = time.time()
    name = time.strftime("frame_%Y%m%d_%H%M%S") + f"_{int(time.time() * 1000) % 1000:03d}.png"

    with FRAME_LOCK:
        FRAMES_DIR.mkdir(parents=True, exist_ok=True)
        (FRAMES_DIR / name).write_bytes(data)
        RUNTIME["last_frame"] = name
        RUNTIME["frame_count"] += 1
        RUNTIME["frame_ms"] = (time.time() - started) * 1000.0
        RUNTIME["source"] = "mod"

        now = time.time()
        RUNTIME["fps_history"].append(1.0 / max(1e-6, now - RUNTIME.get("last_frame_time", now - 1.0)))
        RUNTIME["fps_history"] = RUNTIME["fps_history"][-120:]
        RUNTIME["fps"] = sum(RUNTIME["fps_history"]) / len(RUNTIME["fps_history"])
        RUNTIME["last_frame_time"] = now

        files = sorted(FRAMES_DIR.glob("*.png"))
        for old in files[:-CONFIG["max_frames"]]:
            old.unlink(missing_ok=True)

    return {"name": name, "method": method, "ms": round(RUNTIME["frame_ms"], 2)}


def capture_loop(state_provider):
    interval = 1.0 / max(0.1, CONFIG["capture_fps"])
    last = time.time()
    while RUNTIME["recording"]:
        now = time.time()
        state = state_provider()
        capture_frame(state)
        RUNTIME["fps_history"].append(1.0 / max(1e-6, now - last))
        RUNTIME["fps_history"] = RUNTIME["fps_history"][-120:]
        RUNTIME["fps"] = sum(RUNTIME["fps_history"]) / len(RUNTIME["fps_history"])
        last = now
        time.sleep(max(0.05, interval - (time.time() - now)))


def start_capture(state_provider):
    if RUNTIME["capture_thread"] and RUNTIME["capture_thread"].is_alive():
        RUNTIME["recording"] = True
        return
    RUNTIME["recording"] = True
    RUNTIME["capture_thread"] = threading.Thread(
        target=capture_loop, args=(state_provider,), daemon=True)
    RUNTIME["capture_thread"].start()


def stop_capture():
    RUNTIME["recording"] = False


# --------------------------------------------------------------------------
# Мок-состояние: живой мир без Minecraft
# --------------------------------------------------------------------------

def mock_state() -> dict:
    now = time.time()
    t = now - MOCK["t0"]

    # боты появляются и дерутся
    if len(MOCK["bots"]) < 2 and random.random() < 0.02:
        MOCK["bots"].append({
            "id": MOCK["next_id"],
            "name": f"Bot-{MOCK['next_id']}",
            "mode": random.choice(["spar", "rush", "defender"]),
            "kit": random.choice(["iron_sword", "stone_axe", "diamond_sword"]),
            "health": 20.0,
            "maxHealth": 20.0,
            "x": 12.4, "y": 65.0, "z": -3.2,
            "distance": 4.5,
            "target": "Player",
            "attacking": random.random() < 0.6,
        })
        MOCK["next_id"] += 1

    for bot in MOCK["bots"]:
        if bot["attacking"] and random.random() < 0.25:
            dmg = round(random.uniform(0.5, 6.0), 1)
            bot["health"] = max(0.0, bot["health"] - dmg)
            record_event("damage", f"{bot['name']} получил {dmg} урона")
        if bot["health"] <= 1.0 and random.random() < 0.2:
            bot["health"] = bot["maxHealth"]
            record_event("heal", f"{bot['name']} восстановил HP")

    tps = 20.0 - abs(math.sin(t * 0.4)) * 1.6
    tick_ms = 2.0 + abs(math.sin(t * 0.9)) * 3.5

    return {
        "source": "mock",
        "serverTime": now,
        "game": {
            "mcVersion": "26.3",
            "modVersion": "0.1.0",
            "tps": round(tps, 2),
            "tickMs": round(tick_ms, 2),
            "players": MOCK["players"],
            "dimension": "minecraft:overworld",
        },
        "bots": MOCK["bots"],
        "events": list(EVENTS[-60:]),
    }


def record_event(kind: str, message: str):
    with STATE_LOCK:
        EVENTS.append({"t": round(time.time(), 3), "type": kind, "message": message})
        if len(EVENTS) > MAX_EVENTS:
            del EVENTS[: len(EVENTS) - MAX_EVENTS]


def fetch_mod_state() -> dict | None:
    url = CONFIG["mc_url"].rstrip("/") + "/api/state"
    try:
        with urllib.request.urlopen(url, timeout=0.8) as response:
            return json.loads(response.read().decode("utf-8"))
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, OSError):
        return None


def project_plan() -> dict:
    """Текущая стадия и таблица этапов из docs/03-plan-and-status.md."""
    plan = {"current": "—", "stages": [], "updated": None}
    path = ROOT / "docs" / "03-plan-and-status.md"
    if not path.exists():
        return plan

    plan["updated"] = path.stat().st_mtime
    text = path.read_text(encoding="utf-8")

    for line in text.splitlines():
        if line.startswith("## Текущий этап"):
            plan["current"] = (line.split(":", 1)[1]
                                  .replace("**", "").replace("*", "").replace("`", "").strip())
            break

    for line in text.splitlines():
        line = line.strip()
        if not (line.startswith("|") and line.count("|") >= 5):
            continue
        cells = [c.strip() for c in line.strip("|").split("|")]
        if len(cells) < 4 or not cells[0].isdigit():
            continue
        plan["stages"].append({
            "num": int(cells[0]),
            "name": cells[1],
            "status": cells[2],
            "result": cells[3],
        })
    return plan


def real_screenshots() -> list:
    """Скриншоты настоящего клиента, снятые в CI (docs/screenshots)."""
    folder = ROOT / "docs" / "screenshots"
    if not folder.exists():
        return []
    return sorted(
        ({"name": item.name, "size": item.stat().st_size, "mtime": item.stat().st_mtime}
         for item in folder.glob("*.png")),
        key=lambda entry: entry["mtime"],
        reverse=True,
    )


def current_state() -> dict:
    state = fetch_mod_state()
    if state is None:
        RUNTIME["source"] = "mock"
        return mock_state()
    RUNTIME["source"] = "mod"
    return state


def forward_command(payload: dict) -> dict:
    url = CONFIG["mc_url"].rstrip("/") + "/api/command"
    body = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        url, data=body, headers={"Content-Type": "application/json"}, method="POST")
    try:
        with urllib.request.urlopen(request, timeout=2.0) as response:
            return json.loads(response.read().decode("utf-8"))
    except (urllib.error.URLError, TimeoutError, json.JSONDecodeError, OSError):
        record_event("command", f"(mock) {payload.get('action')} {payload.get('mode', '')}")
        return {"ok": True, "mode": "mock", "action": payload.get("action")}


# --------------------------------------------------------------------------
# Сценарии
# --------------------------------------------------------------------------

def run_scenario(name: str) -> dict:
    steps = {
        "smoke": [
            ("spawn", {"mode": "spar", "kit": "iron_sword"}),
            ("wait", {"seconds": 2}),
            ("duel", {}),
            ("wait", {"seconds": 8}),
            ("stop", {}),
            ("report", {}),
        ],
        "combo": [
            ("spawn", {"mode": "rush", "kit": "diamond_sword"}),
            ("wait", {"seconds": 1}),
            ("duel", {}),
            ("wait", {"seconds": 15}),
            ("stop", {}),
            ("report", {}),
        ],
        "defense": [
            ("spawn", {"mode": "defender", "kit": "diamond_sword"}),
            ("wait", {"seconds": 1}),
            ("duel", {}),
            ("wait", {"seconds": 12}),
            ("stop", {}),
            ("report", {}),
        ],
    }

    scenario = steps.get(name)
    if scenario is None:
        return {"ok": False, "error": f"unknown scenario: {name}"}

    report = {
        "scenario": name,
        "started": time.strftime("%Y-%m-%d %H:%M:%S"),
        "steps": [],
        "result": "running",
    }
    RUNTIME["scenario"] = report

    def worker():
        started = time.time()
        for action, params in scenario:
            if action == "wait":
                time.sleep(float(params.get("seconds", 1)))
                report["steps"].append({"action": action, "params": params, "ok": True})
                continue

            if action == "report":
                state = current_state()
                report["result"] = "ok"
                report["duration"] = round(time.time() - started, 2)
                report["snapshot"] = state
                report["steps"].append({"action": "report", "ok": True})
                REPORTS_DIR.mkdir(parents=True, exist_ok=True)
                path = REPORTS_DIR / f"{name}_{int(time.time())}.json"
                path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
                report["reportFile"] = str(path)
                record_event("scenario", f"сценарий «{name}» завершён, отчёт {path.name}")
                continue

            result = forward_command({"action": action, **params})
            report["steps"].append({"action": action, "params": params, "result": result})
            record_event("scenario", f"{action} {params}")

        if report["result"] == "running":
            report["result"] = "ok"

    threading.Thread(target=worker, daemon=True).start()
    return {"ok": True, "scenario": name}


# --------------------------------------------------------------------------
# HTTP
# --------------------------------------------------------------------------

class Handler(BaseHTTPRequestHandler):
    server_version = "BotLab/0.1"

    def log_message(self, fmt, *args):  # тише в консоли
        if os.environ.get("BOTLAB_VERBOSE"):
            super().log_message(fmt, *args)

    def _send(self, code: int, body: bytes, content_type: str = "application/json"):
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def _json(self, obj, code: int = 200):
        self._send(code, json.dumps(obj, ensure_ascii=False).encode("utf-8"))

    def do_GET(self):
        path = self.path.split("?")[0]

        if path == "/":
            self._file(LAB_DIR / "index.html", "text/html; charset=utf-8")
        elif path == "/app.js":
            self._file(LAB_DIR / "app.js", "application/javascript; charset=utf-8")
        elif path == "/style.css":
            self._file(LAB_DIR / "style.css", "text/css; charset=utf-8")
        elif path == "/api/state":
            state = current_state()
            state["perf"] = {
                "fps": round(RUNTIME["fps"], 2),
                "frameMs": round(RUNTIME["frame_ms"], 2),
                "frames": RUNTIME["frame_count"],
                "recording": RUNTIME["recording"],
                "lastFrame": RUNTIME["last_frame"],
            }
            state["scenario"] = RUNTIME["scenario"]
            self._json(state)
        elif path == "/api/frames":
            frames = []
            if FRAMES_DIR.exists():
                for item in sorted(FRAMES_DIR.glob("*.png")):
                    frames.append({"name": item.name, "size": item.stat().st_size,
                                   "mtime": item.stat().st_mtime})
            self._json({"frames": frames[-60:], "total": len(frames)})
        elif path.startswith("/frames/"):
            name = Path(path[len("/frames/"):]).name
            target = FRAMES_DIR / name
            if target.exists() and target.parent == FRAMES_DIR:
                self._file(target, "image/png")
            else:
                self._json({"error": "frame not found"}, 404)
        elif path == "/api/plan":
            self._json(project_plan())
        elif path == "/api/screenshots":
            self._json({"screenshots": real_screenshots()})
        elif path.startswith("/screenshots/"):
            name = Path(path[len("/screenshots/"):]).name
            target = ROOT / "docs" / "screenshots" / name
            if target.exists():
                self._file(target, "image/png")
            else:
                self._json({"error": "screenshot not found"}, 404)
        elif path == "/api/health":
            self._json({"ok": True, "source": RUNTIME["source"],
                        "mcUrl": CONFIG["mc_url"], "frames": RUNTIME["frame_count"]})
        else:
            self._json({"error": "not found"}, 404)

    def _file(self, path: Path, content_type: str):
        if not path.exists():
            self._json({"error": "not found"}, 404)
            return
        self._send(200, path.read_bytes(), content_type)

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0") or 0)
        raw = self.rfile.read(length) if length else b"{}"
        path = self.path.split("?")[0]

        # Кадр от мода: тело — готовый PNG (image/png), без JSON.
        if path == "/api/frame":
            if not raw.startswith(b"\x89PNG"):
                self._json({"ok": False, "error": "not a png"}, 400)
                return
            result = store_frame(raw, "mod")
            record_event("frame", f"кадр от мода {result['name']}")
            self._json({"ok": True, **result})
            return

        try:
            payload = json.loads(raw.decode("utf-8") or "{}")
        except json.JSONDecodeError:
            self._json({"error": "bad json"}, 400)
            return

        if path == "/api/command":
            self._json(forward_command(payload))
        elif path == "/api/capture":
            result = capture_frame(current_state())
            record_event("capture", f"кадр {result['name']} ({result['method']})")
            self._json({"ok": True, **result})
        elif path == "/api/recording":
            if bool(payload.get("on", True)):
                start_capture(current_state)
                record_event("record", "запись кадров включена")
            else:
                stop_capture()
                record_event("record", "запись кадров выключена")
            self._json({"ok": True, "recording": RUNTIME["recording"]})
        elif path == "/api/scenario":
            self._json(run_scenario(str(payload.get("name", "smoke"))))
        else:
            self._json({"error": "not found"}, 404)


def main():
    parser = argparse.ArgumentParser(description="BotLab — тестовая лаборатория BotMod")
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument("--mc", default="http://127.0.0.1:17777",
                        help="URL телеметрии мода")
    parser.add_argument("--fps", type=float, default=1.0,
                        help="кадров в секунду при записи")
    parser.add_argument("--record", action="store_true",
                        help="сразу начать писать кадры")
    args = parser.parse_args()

    CONFIG["mc_url"] = args.mc
    CONFIG["capture_fps"] = args.fps

    FRAMES_DIR.mkdir(parents=True, exist_ok=True)
    REPORTS_DIR.mkdir(parents=True, exist_ok=True)

    if args.record:
        start_capture(current_state)

    server = ThreadingHTTPServer(("0.0.0.0", args.port), Handler)
    print(f"BotLab: http://0.0.0.0:{args.port}  (mc={CONFIG['mc_url']}, fps={CONFIG['capture_fps']})")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        stop_capture()
        print("\nBotLab stopped")


if __name__ == "__main__":
    main()
