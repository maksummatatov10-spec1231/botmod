package com.botmod.client.render;

import com.botmod.BotMod;
import com.botmod.entity.TrainingBotEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * Рендерер бота. {@link HumanoidMobRenderer} автоматически подключает слои
 * брони и предмета в руке — поэтому надетый на бота kit виден игроку.
 */
public class TrainingBotRenderer
		extends HumanoidMobRenderer<TrainingBotEntity, LivingEntityRenderState, TrainingBotModel> {

	private static final Identifier TEXTURE = BotMod.id("textures/entity/training_bot.png");

	public TrainingBotRenderer(EntityRendererProvider.Context context) {
		super(context, new TrainingBotModel(context.bakeLayer(ModModelLayers.TRAINING_BOT)), 0.35F);
	}

	@Override
	public LivingEntityRenderState createRenderState() {
		return new LivingEntityRenderState();
	}

	@Override
	public Identifier getTextureLocation(LivingEntityRenderState state) {
		return TEXTURE;
	}
}
