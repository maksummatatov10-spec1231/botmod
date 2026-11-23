package com.botmod.duel;

/**
 * Настройки дуэли: поведение бота и выбранная арена.
 *
 * <p>Каждый параметр переключается кликом по иконке в меню {@code /duel}.</p>
 */
public class DuelSettings {
	private boolean sprint = true;
	private boolean potions = true;
	private boolean crits = true;
	private boolean shield = true;
	private boolean wTap = true;
	private boolean jumps = true;
	private BotDifficulty difficulty = BotDifficulty.NORMAL;
	private String kit = "diamond_sword";
	private String arena = "end";

	public boolean sprint() {
		return this.sprint;
	}

	public void setSprint(boolean sprint) {
		this.sprint = sprint;
	}

	public boolean potions() {
		return this.potions;
	}

	public void setPotions(boolean potions) {
		this.potions = potions;
	}

	public boolean crits() {
		return this.crits;
	}

	public void setCrits(boolean crits) {
		this.crits = crits;
	}

	public boolean shield() {
		return this.shield;
	}

	public void setShield(boolean shield) {
		this.shield = shield;
	}

	public boolean wTap() {
		return this.wTap;
	}

	public void setWTap(boolean wTap) {
		this.wTap = wTap;
	}

	public boolean jumps() {
		return this.jumps;
	}

	public void setJumps(boolean jumps) {
		this.jumps = jumps;
	}

	public BotDifficulty difficulty() {
		return this.difficulty;
	}

	public void setDifficulty(BotDifficulty difficulty) {
		this.difficulty = difficulty;
	}

	public String kit() {
		return this.kit;
	}

	public void setKit(String kit) {
		this.kit = kit;
	}

	public String arena() {
		return this.arena;
	}

	public void setArena(String arena) {
		this.arena = arena;
	}

	public DuelSettings copy() {
		DuelSettings copy = new DuelSettings();
		copy.sprint = this.sprint;
		copy.potions = this.potions;
		copy.crits = this.crits;
		copy.shield = this.shield;
		copy.wTap = this.wTap;
		copy.jumps = this.jumps;
		copy.difficulty = this.difficulty;
		copy.kit = this.kit;
		copy.arena = this.arena;
		return copy;
	}
}
