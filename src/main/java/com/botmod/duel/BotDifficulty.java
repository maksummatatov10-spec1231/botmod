package com.botmod.duel;

/**
 * Уровень сложности бота.
 *
 * @param speed множитель скорости движения
 * @param damage множитель урона
 * @param reactTicks тики реакции на пропущенный удар (меньше = быстрее)
 * @param aggression базовая агрессия
 */
public enum BotDifficulty {
	EASY("Лёгкий", 0.85D, 0.80D, 20, 0.85D),
	NORMAL("Обычный", 1.00D, 1.00D, 14, 1.00D),
	HARD("Сложный", 1.12D, 1.15D, 9, 1.20D),
	EXPERT("Эксперт", 1.22D, 1.35D, 5, 1.40D);

	private final String displayName;
	private final double speed;
	private final double damage;
	private final int reactTicks;
	private final double aggression;

	BotDifficulty(String displayName, double speed, double damage, int reactTicks, double aggression) {
		this.displayName = displayName;
		this.speed = speed;
		this.damage = damage;
		this.reactTicks = reactTicks;
		this.aggression = aggression;
	}

	public String displayName() {
		return this.displayName;
	}

	public double speed() {
		return this.speed;
	}

	public double damage() {
		return this.damage;
	}

	public int reactTicks() {
		return this.reactTicks;
	}

	public double aggression() {
		return this.aggression;
	}

	public BotDifficulty next() {
		BotDifficulty[] values = values();
		return values[(this.ordinal() + 1) % values.length];
	}
}
