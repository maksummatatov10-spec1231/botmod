package com.kitmod.data;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Arrays;

/**
 * Один кит: название, иконка, 36 слотов инвентаря, 4 слота брони и левая рука.
 *
 * <p>Хранится в {@code config/kitmod/kits/<id>.json} — файл можно скопировать
 * и передать другому человеку или другому моду (например, PvP-боту).</p>
 */
public final class KitData {
	public static final int INVENTORY_SIZE = 36;
	public static final int ARMOR_SIZE = 4;

	private String id;
	private String displayName;
	private ItemStack icon = new ItemStack(Items.CHEST);
	private final ItemStack[] inventory = new ItemStack[INVENTORY_SIZE];
	private final ItemStack[] armor = new ItemStack[ARMOR_SIZE];
	private ItemStack offhand = ItemStack.EMPTY;

	public KitData(String id, String displayName) {
		this.id = id;
		this.displayName = displayName == null || displayName.isBlank() ? id : displayName;
		Arrays.fill(this.inventory, ItemStack.EMPTY);
		Arrays.fill(this.armor, ItemStack.EMPTY);
	}

	public String getId() {
		return this.id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getDisplayName() {
		return this.displayName == null || this.displayName.isBlank() ? this.id : this.displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public ItemStack getIcon() {
		return this.icon == null || this.icon.isEmpty() ? new ItemStack(Items.CHEST) : this.icon;
	}

	public void setIcon(ItemStack icon) {
		this.icon = icon == null || icon.isEmpty() ? new ItemStack(Items.CHEST) : icon.copy();
	}

	public ItemStack[] getInventory() {
		return this.inventory;
	}

	public ItemStack[] getArmor() {
		return this.armor;
	}

	public ItemStack getOffhand() {
		return this.offhand == null ? ItemStack.EMPTY : this.offhand;
	}

	public void setOffhand(ItemStack offhand) {
		this.offhand = offhand == null ? ItemStack.EMPTY : offhand.copy();
	}
}
