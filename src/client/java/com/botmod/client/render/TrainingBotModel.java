package com.botmod.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/**
 * Модель бота — стандартный гуманоид (пропорции игрока), поэтому текстура
 * формата 64x64 (обычный скин) ложится без правок UV.
 *
 * <p>Анимации ходьбы, поворота головы и взмаха руки уже реализованы в
 * {@link HumanoidModel#setupAnim(HumanoidRenderState)} — переопределять
 * их не нужно, пока не понадобится своя боевой анимации.</p>
 */
public class TrainingBotModel extends HumanoidModel<HumanoidRenderState> {
	public TrainingBotModel(ModelPart root) {
		super(root);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(0.0F), 0.0F);
		return LayerDefinition.create(mesh, 64, 64);
	}
}
