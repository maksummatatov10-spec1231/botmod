package com.botmod.client.hud;

import com.botmod.BotMod;
import com.botmod.config.BotModConfig;
import com.botmod.entity.TrainingBotEntity;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;

/**
 * HUD ближайшего бота: имя, режим, полоска здоровья.
 *
 * <p>Рисуется перед чатом, чтобы не перекрывать хотбар. Если ботов рядом нет —
 * ничего не рисует.</p>
 */
public final class BotHud {
	private static final int PANEL_WIDTH = 132;
	private static final int PANEL_HEIGHT = 34;
	private static final int MARGIN = 8;

	private BotHud() {
	}

	public static void register() {
		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				BotMod.id("bot_hud"),
				BotHud::render
		);
	}

	private static void render(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
		if (!BotModConfig.get().hudEnabled) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			return;
		}

		TrainingBotEntity bot = findNearest(client, 16.0D);
		if (bot == null) {
			return;
		}

		int screenX = client.getWindow().getGuiScaledWidth();
		int screenY = client.getWindow().getGuiScaledHeight();
		int x = MARGIN;
		int y = screenY - PANEL_HEIGHT - MARGIN - 22;

		// Панель
		graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0x90000000);
		graphics.outline(x, y, PANEL_WIDTH, PANEL_HEIGHT, 0x50FFFFFF);

		// Имя и режим
		String title = bot.getName().getString();
		String mode = bot.getMode().displayName().getString();

		graphics.text(client.font, title, x + 4, y + 4, 0xFFFFFFFF, true);
		graphics.text(client.font, mode, x + 4, y + 14, 0xFF9AD0FF, true);

		// Полоска здоровья
		int barX = x + 4;
		int barY = y + PANEL_HEIGHT - 9;
		int barWidth = PANEL_WIDTH - 8;
		int barHeight = 5;

		graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF3A3A3A);

		float healthRatio = bot.getHealth() / bot.getMaxHealth();
		int filled = Math.round(barWidth * Math.max(0.0F, Math.min(1.0F, healthRatio)));
		int healthColor = healthRatio > 0.5F ? 0xFF4CAF50 : healthRatio > 0.25F ? 0xFFFFC107 : 0xFFF44336;
		graphics.fill(barX, barY, barX + filled, barY + barHeight, healthColor);

		graphics.text(client.font,
				Component.translatable("botmod.hud.health",
						(int) Math.ceil(bot.getHealth()), (int) bot.getMaxHealth()).getString(),
				barX, barY - 9, 0xFFDDDDDD, true);
	}

	private static TrainingBotEntity findNearest(Minecraft client, double radius) {
		AABB box = client.player.getBoundingBox().inflate(radius);

		TrainingBotEntity nearest = null;
		double best = Double.MAX_VALUE;

		for (TrainingBotEntity bot : client.level.getEntitiesOfClass(TrainingBotEntity.class, box, entity -> entity.isAlive())) {
			double distance = client.player.distanceToSqr(bot);
			if (distance < best) {
				best = distance;
				nearest = bot;
			}
		}

		return nearest;
	}
}
