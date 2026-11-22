package com.botmod.lab;

import com.botmod.BotMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP-телеметрия для лаборатории. Слушает только 127.0.0.1:17777.
 *
 * <p>Эндпоинты:</p>
 * <ul>
 *   <li>{@code GET  /api/state}   — снимок состояния (TPS, боты, события);</li>
 *   <li>{@code GET  /api/ping}    — проверка, что мод жив;</li>
 *   <li>{@code POST /api/command} — действие: spawn / mode / kit / duel / stop / remove.</li>
 * </ul>
 *
 * <p>Потоки HTTP никогда не трогают сущности: читают только {@link BotLabBridge#snapshot()},
 * а действия ставятся в очередь и выполняются на серверном тике.</p>
 */
public final class BotLabServer {
	public static final int PORT = 17777;

	private static final Gson GSON = new GsonBuilder().create();
	private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() { }.getType();

	private static HttpServer httpServer;
	private static volatile boolean started;

	private BotLabServer() {
	}

	public static void start() {
		if (started) {
			return;
		}

		try {
			httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
			httpServer.createContext("/api/state", BotLabServer::handleState);
			httpServer.createContext("/api/ping", BotLabServer::handlePing);
			httpServer.createContext("/api/command", BotLabServer::handleCommand);
			httpServer.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(2, runnable -> {
				Thread thread = new Thread(runnable, "BotLab-HTTP");
				thread.setDaemon(true);
				return thread;
			}));
			httpServer.start();
			started = true;
			// Не-daemon поток диспетчера JDK HttpServer не даёт JVM завершиться:
			// при остановке сервера гасим HTTP-сервер явно.
			ServerLifecycleEvents.SERVER_STOPPING.register(server -> stop());
			BotMod.LOGGER.info("BotLab telemetry on http://127.0.0.1:{}", PORT);
		} catch (IOException | RuntimeException e) {
			BotMod.LOGGER.warn("BotLab telemetry could not start on port {}", PORT, e);
		}
	}

	public static void stop() {
		if (httpServer != null) {
			httpServer.stop(0);
			started = false;
		}
	}

	// ------------------------------------------------------------------
	// Хендлеры
	// ------------------------------------------------------------------

	private static void handleState(HttpExchange exchange) throws IOException {
		Snapshot snapshot = BotLabBridge.snapshot();

		Map<String, Object> game = new LinkedHashMap<>();
		game.put("mcVersion", "26.3");
		game.put("modVersion", BotMod.configVersion());
		game.put("tps", round(snapshot.tps()));
		game.put("tickMs", round(snapshot.tickMs()));
		game.put("players", snapshot.players());
		game.put("dimension", snapshot.dimension());
		game.put("uptimeSec", round(snapshot.uptimeSec()));

		java.util.List<Map<String, Object>> bots = new java.util.ArrayList<>();
		for (Snapshot.BotState bot : snapshot.bots()) {
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("id", bot.id());
			entry.put("name", bot.name());
			entry.put("mode", bot.mode());
			entry.put("kit", bot.kit());
			entry.put("health", round(bot.health()));
			entry.put("maxHealth", round(bot.maxHealth()));
			entry.put("x", round(bot.x()));
			entry.put("y", round(bot.y()));
			entry.put("z", round(bot.z()));
			entry.put("distance", round(bot.distance()));
			entry.put("target", bot.target());
			entry.put("attacking", bot.attacking());
			entry.put("aggression", round(bot.aggression()));
			entry.put("landedHits", bot.landedHits());
			entry.put("takenHits", bot.takenHits());
			bots.add(entry);
		}

		java.util.List<Map<String, Object>> events = new java.util.ArrayList<>();
		for (LabEvent event : snapshot.events()) {
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("t", event.time());
			entry.put("type", event.type());
			entry.put("message", event.message());
			events.add(entry);
		}

		Map<String, Object> root = new LinkedHashMap<>();
		root.put("source", "mod");
		root.put("serverTime", System.currentTimeMillis() / 1000.0);
		root.put("game", game);
		root.put("bots", bots);
		root.put("events", events);

		send(exchange, 200, GSON.toJson(root));
	}

	private static void handlePing(HttpExchange exchange) throws IOException {
		send(exchange, 200, "{\"ok\":true,\"mod\":\"botmod\",\"version\":\"" + BotMod.configVersion() + "\"}");
	}

	private static void handleCommand(HttpExchange exchange) throws IOException {
		String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
		Map<String, Object> payload;

		try {
			payload = GSON.fromJson(body, MAP_TYPE);
		} catch (RuntimeException e) {
			send(exchange, 400, "{\"ok\":false,\"error\":\"bad json\"}");
			return;
		}

		if (payload == null) {
			payload = Map.of();
		}

		String action = String.valueOf(payload.getOrDefault("action", "")).toLowerCase(java.util.Locale.ROOT);
		String mode = payload.get("mode") == null ? null : String.valueOf(payload.get("mode"));
		String kit = payload.get("kit") == null ? null : String.valueOf(payload.get("kit"));

		BotLabBridge.enqueue(() -> LabActions.handle(action, mode, kit));

		send(exchange, 200, "{\"ok\":true,\"action\":\"" + action + "\"}");
	}

	private static void send(HttpExchange exchange, int code, String json) throws IOException {
		byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
		exchange.sendResponseHeaders(code, bytes.length);

		try (OutputStream output = exchange.getResponseBody()) {
			output.write(bytes);
		}
	}

	private static double round(double value) {
		return Math.round(value * 100.0) / 100.0;
	}
}
