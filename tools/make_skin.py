#!/usr/bin/env python3
"""Генератор скина тренировочного бота (64x64, стандартная раскладка игрока).

Запуск: python3 tools/make_skin.py
Результат: docs/skins/generated_alternative.png

ВНИМАНИЕ: основной скин бота — docs/skins/training_bot_source.png (прислал пользователь).
Этот скрипт генерирует только альтернативный вариант и НЕ трогает игровой скин.

Рисует узнаваемого «спарринг-бота»: тёмные волосы, кожа, синяя футболка,
тёмные штаны, кроссовки. Без внешних зависимостей (только zlib/struct).
"""
import struct
import zlib
from pathlib import Path

W = H = 64
SKIN = (232, 190, 150)
SKIN_DARK = (206, 164, 126)
HAIR = (58, 42, 32)
HAIR_LIGHT = (86, 64, 48)
SHIRT = (46, 96, 168)
SHIRT_DARK = (32, 70, 128)
PANTS = (40, 52, 74)
PANTS_DARK = (28, 38, 56)
SHOE = (30, 30, 34)
EYE = (28, 28, 34)
MOUTH = (150, 96, 84)

px = [[(0, 0, 0, 0) for _ in range(W)] for _ in range(H)]


def fill(x0, y0, w, h, color):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            if 0 <= x < W and 0 <= y < H:
                px[y][x] = (*color, 255)


def shade(x0, y0, w, h, color, factor=0.88):
    fill(x0, y0, w, h, tuple(int(c * factor) for c in color))


# --- голова (0,0)-(32,32) ---
fill(0, 0, 32, 32, SKIN)                 # база
fill(8, 0, 8, 8, HAIR)                   # макушка
fill(0, 8, 8, 8, HAIR)                   # правая сторона головы
fill(16, 8, 8, 8, HAIR_LIGHT)            # левая сторона
fill(24, 8, 8, 8, HAIR)                  # затылок
fill(8, 8, 8, 8, SKIN)                   # лицо
fill(8, 8, 8, 2, HAIR)                   # чёлка

# глаза и рот (лицо 8x8 в (8,8))
fill(10, 12, 1, 1, EYE)
fill(13, 12, 1, 1, EYE)
fill(11, 14, 2, 1, MOUTH)
shade(8, 8, 8, 1, SKIN, 0.95)            # блик лба

# --- тело (16,16)-(40,32) ---
fill(20, 16, 8, 4, SHIRT_DARK)           # верх плеч
fill(16, 20, 24, 12, SHIRT)              # торс
fill(32, 20, 8, 12, SHIRT_DARK)          # спина
fill(20, 20, 8, 12, SHIRT)
shade(20, 20, 8, 3, SHIRT, 1.12)         # блик на груди
fill(23, 24, 2, 2, (220, 226, 236))      # эмблема на груди

# --- правая рука (40,16)-(56,32) ---
fill(44, 16, 4, 4, SKIN)                 # плечо сверху
fill(40, 20, 4, 12, SKIN_DARK)
fill(44, 20, 4, 12, SKIN)
fill(48, 20, 4, 12, SKIN_DARK)
fill(52, 20, 4, 12, SKIN)
fill(44, 26, 4, 2, SHIRT_DARK)           # короткий рукав

# --- левая рука (32,48)-(48,64) ---
fill(36, 48, 4, 4, SKIN)
fill(32, 52, 4, 12, SKIN_DARK)
fill(36, 52, 4, 12, SKIN)
fill(40, 52, 4, 12, SKIN_DARK)
fill(44, 52, 4, 12, SKIN)
fill(36, 58, 4, 2, SHIRT_DARK)

# --- правая нога (0,16)-(16,32) ---
fill(4, 16, 4, 4, PANTS_DARK)
fill(0, 20, 4, 12, PANTS_DARK)
fill(4, 20, 4, 12, PANTS)
fill(8, 20, 4, 12, PANTS_DARK)
fill(12, 20, 4, 12, PANTS)
fill(4, 29, 4, 3, SHOE)                  # кроссовок

# --- левая нога (16,48)-(32,64) ---
fill(20, 48, 4, 4, PANTS_DARK)
fill(16, 52, 4, 12, PANTS_DARK)
fill(20, 52, 4, 12, PANTS)
fill(24, 52, 4, 12, PANTS_DARK)
fill(28, 52, 4, 12, PANTS)
fill(20, 61, 4, 3, SHOE)


def png_bytes() -> bytes:
    raw = b"".join(
        b"\x00" + b"".join(struct.pack("BBBB", *px[y][x]) for x in range(W))
        for y in range(H)
    )

    def chunk(tag: bytes, data: bytes) -> bytes:
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    header = struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header)
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def main() -> None:
    target = Path(__file__).resolve().parents[1] / \
        "docs/skins/generated_alternative.png"
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(png_bytes())
    print(f"skin written: {target} ({target.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
