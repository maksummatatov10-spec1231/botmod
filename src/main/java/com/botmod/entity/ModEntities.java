package com.botmod.entity;

import com.botmod.BotMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

/**
 * Регистрация сущностей мода.
 *
 * <p>26.3: константы ванильных типов переехали в {@code EntityTypes}, а сам
 * {@code EntityType} остался фабрикой. Регистрация идёт через
 * {@code BuiltInRegistries.ENTITY_TYPE} и {@code ResourceKey}.</p>
 */
public final class ModEntities {
	public static final ResourceKey<EntityType<?>> TRAINING_BOT_KEY =
			ResourceKey.create(Registries.ENTITY_TYPE, BotMod.id("training_bot"));

	public static final EntityType<TrainingBotEntity> TRAINING_BOT = register(
			TRAINING_BOT_KEY,
			EntityType.Builder.<TrainingBotEntity>of(TrainingBotEntity::new, MobCategory.MISC)
					.sized(0.6F, 1.95F)
					.eyeHeight(1.62F)
					.clientTrackingRange(32)
					.updateInterval(3),
			TrainingBotEntity.createAttributes()
	);

	private ModEntities() {
	}

	private static <T extends Entity> EntityType<T> register(ResourceKey<EntityType<?>> key, EntityType.Builder<T> builder) {
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static <T extends LivingEntity> EntityType<T> register(ResourceKey<EntityType<?>> key,
																	EntityType.Builder<T> builder,
																	AttributeSupplier.Builder attributes) {
		EntityType<T> type = register(key, builder);
		FabricDefaultAttributeRegistry.register(type, attributes);
		return type;
	}

	public static void initialize() {
		// Регистрация происходит при загрузке класса, метод нужен для явного порядка инициализации.
	}
}
