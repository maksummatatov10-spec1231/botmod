package com.botmod.client.lab;

import com.botmod.config.BotModConfig;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Отправляет готовые кадры игры в BotLab.
 *
 * <p>Кадр снимается на клиентском тике (каждые {@code 20 / fps} тиков),
 * кодируется в PNG во временный файл и уходит на {@code POST /api/frame}
 * из фонового потока — рендер не блокируется.</p>
 *
 * <p>Снимать можно только с главного таргета рендера и только из потока
 * рендера, поэтому вся работа с {@link NativeImage} идёт в колбэке
 * {@link Screenshot#takeScreenshot}.</p>
 */
public final class BotLabFrameSender {
	private static final AtomicInteger SEQUENCE = new AtomicInteger();
	private static final BlockingQueue<Path> PENDING = new ArrayBlockingQueue<>(4);
	private static final Path TEMP_DIR = createTempDir();

	private static ExecutorService uploader;
	private static HttpClient http;
	private static int tickCounter;
	private static int cooldownTicks;
	private static boolean warned;

	private BotLabFrameSender() {
	}

	public static void init() {
		if (uploader == null) {
			uploader = Executors.newSingleThreadExecutor(runnable -> {
				Thread thread = new Thread(runnable, "BotLab-FrameSender");
				thread.setDaemon(true);
				return thread;
			});
			http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
			uploader.execute(BotLabFrameSender::uploadLoop);
		}
	}

	/** Вызывается из {@code ClientTickEvents.END_CLIENT_TICK}. */
	public static void tick(Minecraft client) {
		if (!BotModConfig.get().labFramesEnabled) {
			return;
		}

		if (client.level == null || client.player == null || client.gameRenderer.mainRenderTarget() == null) {
			return;
		}

		int fps = Math.max(1, Math.min(10, BotModConfig.get().labFramesPerSecond));
		int interval = Math.max(1, 20 / fps);

		if (cooldownTicks > 0) {
			cooldownTicks--;
			return;
		}

		tickCounter++;
		if (tickCounter < interval) {
			return;
		}
		tickCounter = 0;

		// Не копим очередь: если лаборатория не успевает — пропускаем кадр.
		if (PENDING.remainingCapacity() == 0) {
			return;
		}

		int downscale = Math.max(1, Math.min(4, BotModConfig.get().labFrameDownscale));
		cooldownTicks = 2;

		Screenshot.takeScreenshot(client.gameRenderer.mainRenderTarget(), downscale, image -> encode(image));
	}

	private static void encode(NativeImage image) {
		try (image) {
			Path file = TEMP_DIR.resolve("frame_" + System.currentTimeMillis() + "_" + SEQUENCE.incrementAndGet() + ".png");
			image.writeToFile(file);
			PENDING.offer(file);
		} catch (IOException | RuntimeException e) {
			if (!warned) {
				warned = true;
				com.botmod.BotMod.LOGGER.warn("BotLab frame encode failed", e);
			}
		}
	}

	private static void uploadLoop() {
		while (true) {
			Path file;
			try {
				file = PENDING.take();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}

			try {
				byte[] bytes = Files.readAllBytes(file);
				String url = BotModConfig.get().labUrl.replaceAll("/+$", "") + "/api/frame";

				HttpRequest request = HttpRequest.newBuilder(URI.create(url))
						.timeout(Duration.ofSeconds(2))
						.header("Content-Type", "image/png")
						.POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
						.build();

				HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() / 100 != 2 && !warned) {
					warned = true;
					com.botmod.BotMod.LOGGER.warn("BotLab frame rejected: HTTP {}", response.statusCode());
				}
			} catch (IOException | InterruptedException | RuntimeException e) {
				if (!warned) {
					warned = true;
					com.botmod.BotMod.LOGGER.info("BotLab недоступна ({}): {}", e.getMessage(), e.getClass().getSimpleName());
				}
			} finally {
				try {
					Files.deleteIfExists(file);
				} catch (IOException ignored) {
					// временный файл, не страшно
				}
			}
		}
	}

	private static Path createTempDir() {
		try {
			return Files.createTempDirectory("botmod-frames");
		} catch (IOException e) {
			return Path.of(System.getProperty("java.io.tmpdir", "."));
		}
	}
}
