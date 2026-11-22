package com.botmod.entity;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Снаряжение бота. Кит определяет оружие, броню, урон и темп атаки —
 * то есть фактически «класс» противника, против которого тренируется игрок.
 */
public enum BotKit {
	/** Без оружия и брони. Для разминки и отработки комбо. */
	UNARMED(ItemStack.EMPTY, ItemStack.EMPTY,
			ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
			1.0F, 15),

	/** Меч + железная броня. Базовый спарринг-набор. */
	IRON_SWORD(new ItemStack(Items.IRON_SWORD), ItemStack.EMPTY,
			new ItemStack(Items.IRON_HELMET), new ItemStack(Items.IRON_CHESTPLATE),
			new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.IRON_BOOTS),
			6.0F, 12),

	/** Топор, без брони. Медленный, но больной удар — учит уклоняться. */
	STONE_AXE(new ItemStack(Items.STONE_AXE), ItemStack.EMPTY,
			ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
			7.0F, 16),

	/** Алмазный меч + щит + алмазная броня. «Хардкорный» набор. */
	DIAMOND_SWORD(new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.SHIELD),
			new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
			new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS),
			7.0F, 10);

	private final ItemStack weapon;
	private final ItemStack offhand;
	private final ItemStack helmet;
	private final ItemStack chestplate;
	private final ItemStack leggings;
	private final ItemStack boots;
	private final float attackDamage;
	private final int attackIntervalTicks;

	BotKit(ItemStack weapon, ItemStack offhand, ItemStack helmet, ItemStack chestplate,
		   ItemStack leggings, ItemStack boots, float attackDamage, int attackIntervalTicks) {
		this.weapon = weapon;
		this.offhand = offhand;
		this.helmet = helmet;
		this.chestplate = chestplate;
		this.leggings = leggings;
		this.boots = boots;
		this.attackDamage = attackDamage;
		this.attackIntervalTicks = attackIntervalTicks;
	}

	public ItemStack weapon() {
		return weapon.copy();
	}

	public ItemStack offhand() {
		return offhand.copy();
	}

	public float attackDamage() {
		return attackDamage;
	}

	public int attackIntervalTicks() {
		return attackIntervalTicks;
	}

	/** Надевает набор на бота (копии ItemStack — один enum используется многими ботами). */
	public void applyTo(TrainingBotEntity bot) {
		bot.setItemSlot(EquipmentSlot.MAINHAND, weapon.copy());
		bot.setItemSlot(EquipmentSlot.OFFHAND, offhand.copy());
		bot.setItemSlot(EquipmentSlot.HEAD, helmet.copy());
		bot.setItemSlot(EquipmentSlot.CHEST, chestplate.copy());
		bot.setItemSlot(EquipmentSlot.LEGS, leggings.copy());
		bot.setItemSlot(EquipmentSlot.FEET, boots.copy());
	}

	public static BotKit byId(int id) {
		BotKit[] values = values();
		if (id < 0 || id >= values.length) {
			return IRON_SWORD;
		}
		return values[id];
	}

	public int id() {
		return ordinal();
	}

	/** Имена наборов в нижнем регистре — для автодополнения команд. */
	public static java.util.List<String> names() {
		java.util.List<String> names = new java.util.ArrayList<>();
		for (BotKit kit : values()) {
			names.add(kit.name().toLowerCase(java.util.Locale.ROOT));
		}
		return names;
	}
}
