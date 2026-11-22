/* BotLab dashboard: опрос телеметрии, графики, кадры, журнал, команды. */

const $ = (id) => document.getElementById(id);

const state = {
	source: "…",
	fps: [],
	tps: [],
	tickMs: [],
	lastFrame: null,
	events: [],
	eventCount: 0,
};

const MAX_POINTS = 90;

/* ----------------------------- графики ----------------------------- */

function drawChart(canvas, values, color, options = {}) {
	const ctx = canvas.getContext("2d");
	const w = canvas.width;
	const h = canvas.height;
	ctx.clearRect(0, 0, w, h);

	const max = options.max || Math.max(1, ...values);
	const min = options.min || 0;

	// сетка
	ctx.strokeStyle = "#1b2129";
	ctx.lineWidth = 1;
	for (let i = 1; i <= 3; i++) {
		const y = (h / 4) * i;
		ctx.beginPath();
		ctx.moveTo(0, y);
		ctx.lineTo(w, y);
		ctx.stroke();
	}

	if (values.length < 2) return;

	const step = w / (MAX_POINTS - 1);
	const points = values.slice(-MAX_POINTS);

	// заливка
	ctx.beginPath();
	ctx.moveTo(0, h);
	points.forEach((value, index) => {
		const y = h - ((value - min) / (max - min || 1)) * (h - 8) - 4;
		if (index === 0) ctx.lineTo(0, y);
		else ctx.lineTo(index * step, y);
	});
	ctx.lineTo(points.length * step, h);
	ctx.closePath();
	const gradient = ctx.createLinearGradient(0, 0, 0, h);
	gradient.addColorStop(0, color + "33");
	gradient.addColorStop(1, color + "00");
	ctx.fillStyle = gradient;
	ctx.fill();

	// линия
	ctx.beginPath();
	points.forEach((value, index) => {
		const y = h - ((value - min) / (max - min || 1)) * (h - 8) - 4;
		if (index === 0) ctx.moveTo(0, y);
		else ctx.lineTo(index * step, y);
	});
	ctx.strokeStyle = color;
	ctx.lineWidth = 2;
	ctx.stroke();

	// текущее значение
	const last = points[points.length - 1];
	const lastY = h - ((last - min) / (max - min || 1)) * (h - 8) - 4;
	ctx.fillStyle = color;
	ctx.beginPath();
	ctx.arc((points.length - 1) * step, lastY, 3, 0, Math.PI * 2);
	ctx.fill();
}

function renderCharts() {
	drawChart($("chartFps"), state.fps, "#4ea8ff", { max: 120 });
	drawChart($("chartTps"), state.tps, "#4bd37b", { max: 22 });
}

/* ----------------------------- боты ----------------------------- */

const MODE_CLASS = { idle: "mode-idle", spar: "mode-spar", rush: "mode-rush", defender: "mode-defender" };

function botCard(bot) {
	const ratio = Math.max(0, Math.min(1, (bot.health || 0) / (bot.maxHealth || 20)));
	const color = ratio > 0.5 ? "#4bd37b" : ratio > 0.25 ? "#ffc857" : "#ff6b6b";
	const mode = bot.mode || "idle";

	return `
	<div class="bot">
		<div class="avatar"></div>
		<div class="meta">
			<div class="name">${escapeHtml(bot.name || "Bot")}</div>
			<div class="tags">
				<span class="tag ${MODE_CLASS[mode] || ""}">${mode}</span>
				<span class="tag">${bot.kit || "—"}</span>
				<span class="tag">x ${fmt(bot.x)} y ${fmt(bot.y)} z ${fmt(bot.z)}</span>
				<span class="tag">${bot.attacking ? "в бою" : "ждёт"}</span>
			</div>
		</div>
		<div class="hp">
			<div class="bar"><i style="width:${(ratio * 100).toFixed(0)}%;background:${color}"></i></div>
			<span>${Math.ceil(bot.health || 0)} / ${Math.ceil(bot.maxHealth || 20)}</span>
		</div>
	</div>`;
}

function renderBots(bots) {
	const container = $("bots");
	if (!bots || !bots.length) {
		container.innerHTML = `<div class="empty">ботов нет — заспавнь через команду ниже</div>`;
		return;
	}
	container.innerHTML = bots.map(botCard).join("");
}

/* ----------------------------- журнал ----------------------------- */

function renderEvents(events) {
	const container = $("events");
	if (!events || !events.length) {
		container.innerHTML = `<div class="empty">событий пока нет</div>`;
		return;
	}

	const rows = events.slice(-80).reverse().map((event) => {
		const kind = event.type || "info";
		const cls = /damage|kill|hit/.test(kind) ? "bad" : /heal|spawn|duel|scenario/.test(kind) ? "good" : "";
		const time = new Date((event.t || 0) * 1000).toLocaleTimeString("ru-RU");
		return `<div class="event ${cls}">
			<span class="t">${time}</span>
			<span class="k">${escapeHtml(kind)}</span>
			<span class="m">${escapeHtml(event.message || "")}</span>
		</div>`;
	});

	container.innerHTML = rows.join("");
}

/* ----------------------------- кадры ----------------------------- */

function refreshFrame() {
	if (!state.lastFrame) return;
	const image = $("frame");
	image.src = `/frames/${state.lastFrame}?t=${Date.now()}`;
	image.onload = () => $("frameOverlay").classList.add("hidden");
	image.onerror = () => $("frameOverlay").classList.remove("hidden");
}

async function refreshStrip() {
	try {
		const response = await fetch("/api/frames");
		const data = await response.json();
		const strip = $("frameStrip");
		strip.innerHTML = (data.frames || []).slice(-10).reverse().map((frame) =>
			`<img src="/frames/${frame.name}" alt="${frame.name}" title="${frame.name}"/>`
		).join("");
	} catch (error) {
		/* да, молча */
	}
}

/* ----------------------------- состояние ----------------------------- */

async function poll() {
	try {
		const response = await fetch("/api/state");
		const data = await response.json();

		state.source = data.source || "mock";
		const perf = data.perf || {};
		const game = data.game || {};

		push(state.fps, perf.fps || 0);
		push(state.tps, game.tps || 0);
		push(state.tickMs, game.tickMs || 0);

		$("fpsPill").textContent = `FPS ${Math.round(perf.fps || 0)}`;
		$("tpsPill").textContent = `TPS ${(game.tps || 0).toFixed(1)} · ${(game.tickMs || 0).toFixed(1)} мс`;

		const sourcePill = $("sourcePill");
		sourcePill.textContent = state.source === "mod" ? "● мод подключён" : "○ симуляция";
		sourcePill.className = `pill ${state.source === "mod" ? "ok" : "warn"}`;

		const recPill = $("recPill");
		recPill.textContent = perf.recording ? "● REC" : "○ запись выкл";
		recPill.className = `pill ${perf.recording ? "live" : ""}`;
		$("btnRecord").textContent = perf.recording ? "■ Стоп" : "● Запись";

		$("statFrames").textContent = perf.frames || 0;
		$("statFrameMs").textContent = (perf.frameMs || 0).toFixed(1);
		$("statTickMs").textContent = (game.tickMs || 0).toFixed(1);
		$("statBots").textContent = (data.bots || []).length;
		$("worldInfo").textContent = `${game.mcVersion || "?"} · ${game.dimension || ""} · игроков: ${game.players || 0}`;
		$("frameHint").textContent = perf.lastFrame ? `последний: ${perf.lastFrame}` : "кадров нет";

		state.lastFrame = perf.lastFrame;
		state.events = data.events || [];
		$("eventCount").textContent = `${state.events.length} событий`;

		renderCharts();
		renderBots(data.bots);
		renderEvents(state.events);

		if (data.scenario && data.scenario.result) {
			$("cmdOut").textContent = `сценарий: ${data.scenario.scenario} → ${data.scenario.result}\n` +
				(data.scenario.reportFile ? `отчёт: ${data.scenario.reportFile}` : "");
		}
	} catch (error) {
		const sourcePill = $("sourcePill");
		sourcePill.textContent = "нет связи с лабораторией";
		sourcePill.className = "pill bad";
	}
}

function push(array, value) {
	array.push(Number(value) || 0);
	if (array.length > MAX_POINTS) array.shift();
}

/* ----------------------------- действия ----------------------------- */

async function sendCommand() {
	const payload = { action: $("cmdAction").value };
	const mode = $("cmdMode").value;
	const kit = $("cmdKit").value;
	if (mode) payload.mode = mode;
	if (kit) payload.kit = kit;

	$("cmdOut").textContent = `→ ${JSON.stringify(payload)}`;
	try {
		const response = await fetch("/api/command", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify(payload),
		});
		const data = await response.json();
		$("cmdOut").textContent = `→ ${JSON.stringify(payload)}\n← ${JSON.stringify(data)}`;
	} catch (error) {
		$("cmdOut").textContent = `→ ${JSON.stringify(payload)}\n← ошибка: ${error}`;
	}
}

async function toggleRecording() {
	const on = $("btnRecord").textContent.includes("Запись");
	await fetch("/api/recording", {
		method: "POST",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify({ on }),
	});
}

async function captureNow() {
	const response = await fetch("/api/capture", { method: "POST" });
	const data = await response.json();
	$("cmdOut").textContent = `кадр ${data.name} · ${data.method} · ${data.ms} мс`;
}

async function runScenario() {
	const name = $("scenario").value;
	const response = await fetch("/api/scenario", {
		method: "POST",
		headers: { "Content-Type": "application/json" },
		body: JSON.stringify({ name }),
	});
	const data = await response.json();
	$("cmdOut").textContent = `сценарий «${name}» → ${JSON.stringify(data)}`;
}

/* ------------------------ стадия проекта ------------------------ */

async function refreshPlan() {
	try {
		const response = await fetch("/api/plan", { cache: "no-store" });
		if (!response.ok) return;
		const plan = await response.json();

		$("planCurrent").textContent = plan.current || "—";
		$("planUpdated").textContent = plan.updated
			? "обновлено " + new Date(plan.updated * 1000).toLocaleTimeString("ru-RU")
			: "—";

		const holder = $("planStages");
		holder.innerHTML = "";
		for (const stage of plan.stages || []) {
			const chip = document.createElement("div");
			const status = String(stage.status || "");
			chip.className = "stage " + (status.includes("✅") ? "done"
				: (status.includes("🔄") ? "active" : "next"));
			chip.innerHTML = `<span class="num">${stage.num}</span><span>${escapeHtml(stage.name)}</span>`;
			chip.title = `${stage.status} · ${stage.result}`;
			holder.appendChild(chip);
		}
	} catch (error) {
		/* панель стадии не критична */
	}
}

async function refreshScreenshots() {
	try {
		const response = await fetch("/api/screenshots", { cache: "no-store" });
		if (!response.ok) return;
		const payload = await response.json();
		const shots = payload.screenshots || [];

		$("shotCount").textContent = String(shots.length);
		const strip = $("shotStrip");
		strip.innerHTML = "";

		for (const shot of shots.slice(0, 12)) {
			const link = document.createElement("a");
			link.href = "/screenshots/" + encodeURIComponent(shot.name);
			link.target = "_blank";
			const img = document.createElement("img");
			img.src = link.href + "?t=" + shot.mtime;
			img.alt = shot.name;
			img.title = shot.name;
			link.appendChild(img);
			strip.appendChild(link);
		}

		if (!shots.length) {
			const empty = document.createElement("div");
			empty.className = "empty";
			empty.textContent = "скриншотов из CI пока нет — job ещё не прошёл";
			strip.appendChild(empty);
		}
	} catch (error) {
		/* галерея не критична */
	}
}

/* ----------------------------- утилиты ----------------------------- */

function escapeHtml(value) {
	return String(value).replace(/[&<>"']/g, (char) => (
		{ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[char]
	));
}

function fmt(value) {
	const number = Number(value);
	return Number.isFinite(number) ? number.toFixed(1) : "—";
}

/* ----------------------------- запуск ----------------------------- */

$("btnSend").addEventListener("click", sendCommand);
$("btnRecord").addEventListener("click", toggleRecording);
$("btnCapture").addEventListener("click", captureNow);
$("btnScenario").addEventListener("click", runScenario);

poll();
refreshStrip();
refreshPlan();
refreshScreenshots();
setInterval(poll, 500);
setInterval(refreshFrame, 1000);
setInterval(refreshStrip, 2500);
setInterval(refreshPlan, 15000);
setInterval(refreshScreenshots, 15000);
