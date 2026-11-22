package com.botmod.lab;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Мост между игрой и лабораторией.
 *
 * <p>Всё, что читает HTTP-сервер, — это неизменяемый снимок {@link Snapshot},
 * который пересобирается на серверном тике. HTTP-поток никогда не трогает
 * сущности напрямую: действия складываются в очередь и выполняются на
 * следующем тике.</p>
 */
public final class BotLabBridge {
	private static final int MAX_EVENTS = 300;
	private static final Deque<LabEvent> EVENTS = new ArrayDeque<>();
	private static final List<Runnable> ACTIONS = new ArrayList<>();

	private static volatile Snapshot snapshot = Snapshot.empty();

	private static long tickStartNanos;
	private static double tickMsSmoothed = 0.0;
	private static double tpsSmoothed = 20.0;
	private static long serverStartMillis = System.currentTimeMillis();

	private BotLabBridge() {
	}

	public static void init() {
		ServerTickEvents.START_SERVER_TICK.register(server -> tickStartNanos = System.nanoTime());

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			LabActions.setServer(server);
			long nanos = System.nanoTime() - tickStartNanos;
			double tickMs = nanos / 1_000_000.0;

			tickMsSmoothed = tickMsSmoothed * 0.9 + tickMs * 0.1;
			double rawTps = 1000.0 / Math.max(0.001, tickMs);
			tpsSmoothed = tpsSmoothed * 0.9 + Math.min(20.0, rawTps) * 0.1;

			runPendingActions();
			snapshot = Snapshot.capture(server, tpsSmoothed, tickMsSmoothed, EVENTS, serverStartMillis);
		});
	}

	public static Snapshot snapshot() {
		return snapshot;
	}

	public static void enqueue(Runnable action) {
		synchronized (ACTIONS) {
			ACTIONS.add(action);
		}
	}

	private static void runPendingActions() {
		List<Runnable> pending;
		synchronized (ACTIONS) {
			if (ACTIONS.isEmpty()) {
				return;
			}
			pending = new ArrayList<>(ACTIONS);
			ACTIONS.clear();
		}

		for (Runnable action : pending) {
			try {
				action.run();
			} catch (RuntimeException e) {
				com.botmod.BotMod.LOGGER.warn("BotLab action failed", e);
			}
		}
	}

	/** Событие для журнала лаборатории. Никогда не бросает исключений. */
	public static void event(String type, String message) {
		synchronized (EVENTS) {
			EVENTS.addLast(new LabEvent(System.currentTimeMillis() / 1000.0, type, message));
			while (EVENTS.size() > MAX_EVENTS) {
				EVENTS.removeFirst();
			}
		}
	}

	public static List<LabEvent> events() {
		synchronized (EVENTS) {
			return new ArrayList<>(EVENTS);
		}
	}
}
