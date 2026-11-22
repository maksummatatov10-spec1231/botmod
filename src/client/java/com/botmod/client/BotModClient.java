package com.botmod.client;

import com.botmod.client.hud.BotHud;
import com.botmod.client.keymap.BotModKeyMappings;
import com.botmod.client.render.ModModelLayers;
import com.botmod.client.render.TrainingBotRenderer;
import com.botmod.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.entity.EntityRenderers;

/**
 * Клиентская часть мода: рендерер бота, HUD и клавиши.
 *
 * <p>Важно для 26.3: клиентский source set отделён ({@code loom.splitEnvironmentSourceSets()}),
 * поэтому весь рендер живёт в {@code src/client}, а не в {@code src/main}.</p>
 */
public class BotModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ModModelLayers.register();
		EntityRenderers.register(ModEntities.TRAINING_BOT, TrainingBotRenderer::new);

		BotHud.register();
		BotModKeyMappings.register();
	}
}
