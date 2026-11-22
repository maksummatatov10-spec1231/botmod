package com.botmod.entity;

import net.minecraft.network.chat.Component;

/**
 * Режимы поведения бота. Именно режимы — то, что отличает тренировочный бот
 * от «ещё одного моба»: каждый режим меняет дистанцию боя, скорость, темп
 * атаки и отношение к щиту.
 */
public enum BotMode {
	/** Стоит на месте, не атакует, отвечает на удар только если его ударили. */
	IDLE("botmod.mode.idle", false, 0.30F, 20, false),

	/** Классический спарринг: держит дистанцию, бьёт в темпе, реагирует на игрока. */
	SPARRING("botmod.mode.spar", true, 0.30F, 12, false),

	/** Агрессивный: бежит на игрока, часто прыгает (криты), бьёт быстро. */
	RUSH("botmod.mode.rush", true, 0.34F, 10, false),

	/** Защитник: поднимает щит в ближнем бою и наказывает за ошибку. */
	DEFENDER("botmod.mode.defender", true, 0.27F, 14, true);

	private final String translationKey;
	private final boolean fights;
	private final float movementSpeed;
	private final int attackIntervalTicks;
	private final boolean usesShield;

	BotMode(String translationKey, boolean fights, float movementSpeed, int attackIntervalTicks, boolean usesShield) {
		this.translationKey = translationKey;
		this.fights = fights;
		this.movementSpeed = movementSpeed;
		this.attackIntervalTicks = attackIntervalTicks;
		this.usesShield = usesShield;
	}

	public String translationKey() {
		return translationKey;
	}

	public boolean fights() {
		return fights;
	}

	public float movementSpeed() {
		return movementSpeed;
	}

	public int attackIntervalTicks() {
		return attackIntervalTicks;
	}

	public boolean usesShield() {
		return usesShield;
	}

	public Component displayName() {
		return Component.translatable(translationKey);
	}

	public int id() {
		return ordinal();
	}

	/** Имена режимов в нижнем регистре — для автодополнения команд. */
	public static java.util.List<String> names() {
		java.util.List<String> names = new java.util.ArrayList<>();
		for (BotMode mode : values()) {
			names.add(mode.name().toLowerCase(java.util.Locale.ROOT));
		}
		return names;
	}

	public static BotMode byId(int id) {
		BotMode[] values = values();
		if (id < 0 || id >= values.length) {
			return IDLE;
		}
		return values[id];
	}

	/** Следующий режим по кругу — используется при клике ПКМ по боту. */
	public BotMode next() {
		BotMode[] values = values();
		return values[(ordinal() + 1) % values.length];
	}
}
