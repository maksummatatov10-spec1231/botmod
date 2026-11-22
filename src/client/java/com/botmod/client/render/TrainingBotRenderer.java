package com.botmod.client.render;

import com.botmod.BotMod;
import com.botmod.entity.TrainingBotEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/**
 * Рендерер бота. {@link HumanoidMobRenderer} автоматически подключает слои
 * брони и предмета в руке — поэтому надетый на бота kit виден игроку.
 */
public class TrainingBotRenderer
		extends HumanoidMobRenderer<TrainingBotEntity, HumanoidRenderState, TrainingBotModel> {

	private static final Identifier TEXTURE = BotMod.id("textures/entity/training_bot.png");

	public TrainingBotRenderer(EntityRendererProvider.Context context) {
		super(context, new TrainingBotModel(context.bakeLayer(ModModelLayers.TRAINING_BOT)), 0.35F);
	}

	@Override
	public HumanoidRenderState createRenderState() {
		return new HumanoidRenderState();
	}

	@Override
	public Identifier getTextureLocation(HumanoidRenderState state) {
		return TEXTURE;
	}
}
