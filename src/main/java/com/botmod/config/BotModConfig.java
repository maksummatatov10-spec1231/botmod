package com.botmod.config;

import com.botmod.BotMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Простая JSON-конфигурация без внешних зависимостей.
 *
 * <p>Файл: {@code config/botmod.json}. Gson уже входит в поставку Minecraft,
 * поэтому тянуть YACL/Cloth Config ради пяти полей незачем. Когда настроек
 * станет больше — подключим YACL (3.9.7+26.3) для экрана настроек.</p>
 */
public final class BotModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("botmod.json");

	/** Используется только для лога, в файл не пишется. */
	public transient String modVersion = "0.1.0";

	/** Показывать HUD с состоянием ближайшего бота. */
	public boolean hudEnabled = true;

	/** Бот восстанавливает здоровье, когда никто с ним не дерётся. */
	public boolean botHealsWhenIdle = true;

	/** Множитель урона бота (1.0 = ванильный баланс). */
	public float damageMultiplier = 1.0F;

	/** Максимальное количество ботов на игрока (0 = без лимита). */
	public int maxBotsPerPlayer = 4;

	/** Бот получает отдачу (нужно для тренировки комбо). */
	public boolean botTakesKnockback = true;

	// --- Лаборатория -------------------------------------------------

	/** Отправлять готовые кадры игры в BotLab (POST /api/frame). */
	public boolean labFramesEnabled = true;

	/** Адрес BotLab. */
	public String labUrl = "http://127.0.0.1:8099";

	/** Кадров в секунду (целое, 1..10). */
	public int labFramesPerSecond = 1;

	/** Даунскейл кадра (1 = без уменьшения, 2 = в два раза меньше). */
	public int labFrameDownscale = 2;

	private static BotModConfig instance = new BotModConfig();

	private BotModConfig() {
	}

	public static BotModConfig get() {
		return instance;
	}

	public static void load() {
		if (Files.exists(PATH)) {
			try {
				BotModConfig loaded = GSON.fromJson(Files.readString(PATH), BotModConfig.class);
				if (loaded != null) {
					instance = loaded;
				}
			} catch (IOException | RuntimeException e) {
				BotMod.LOGGER.warn("Could not read {}, using defaults", PATH, e);
			}
		}
		save();
	}

	public static void save() {
		try {
			Files.createDirectories(PATH.getParent());
			Files.writeString(PATH, GSON.toJson(instance));
		} catch (IOException e) {
			BotMod.LOGGER.error("Could not save {}", PATH, e);
		}
	}
}
