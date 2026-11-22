package com.botmod.client.render;

import com.botmod.BotMod;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;

/**
 * Слой модели бота. Отдельный слой (а не {@code ModelLayers.PLAYER}) нужен,
 * чтобы бот мог получить собственную текстуру и не зависеть от внутренностей
 * ванильной модели игрока.
 */
public final class ModModelLayers {
	public static final ModelLayerLocation TRAINING_BOT =
			new ModelLayerLocation(BotMod.id("training_bot"), "main");

	private ModModelLayers() {
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(TRAINING_BOT, TrainingBotModel::createBodyLayer);
	}
}
