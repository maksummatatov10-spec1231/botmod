package com.kitmod.gui;

import com.kitmod.KitMod;
import com.kitmod.data.KitData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Все экраны редактора китов.
 *
 * <p>Каждый экран — ванильный сундук (6/4/3/1 рядов), поэтому не нужны ни кастомные
 * экраны, ни миксины: клиент рисует обычный {@code ContainerScreen}, а сервер
 * решает, что делать с кликом.</p>
 */
public final class KitGuis {
	// --- редактор -----------------------------------------------------
	public static final int SLOT_OFFHAND = 0;
	public static final int SLOT_HELMET = 2;
	public static final int SLOT_CHESTPLATE = 3;
	public static final int SLOT_LEGGINGS = 4;
	public static final int SLOT_BOOTS = 5;
	public static final int INV_START = 9;
	public static final int EDITOR_ROWS = 6;

	// --- меню настроек -----------------------------------------------
	public static final int CFG_BACK = 0;
	public static final int CFG_NAME = 1;
	public static final int CFG_ICON = 2;
	public static final int CFG_CONTENTS = 3;
	public static final int CFG_OFFHAND = 9;
	public static final int CFG_ARMOR = 10;
	public static final int CFG_SAVE = 11;
	public static final int CFG_DELETE = 12;

	private KitGuis() {
	}

	// ==================================================================
	// Редактор содержимого
	// ==================================================================

	public static void openEditor(ServerPlayer player, String kitId) {
		KitData kit = KitMod.storage().get(kitId);
		if (kit == null) {
			player.sendSystemMessage(Component.literal("Кит «" + kitId + "» не найден."));
			return;
		}

		SimpleContainer container = new SimpleContainer(EDITOR_ROWS * 9);
		boolean[] locked = new boolean[EDITOR_ROWS * 9];
		Arrays.fill(locked, true);

		locked[SLOT_OFFHAND] = false;
		locked[SLOT_HELMET] = false;
		locked[SLOT_CHESTPLATE] = false;
		locked[SLOT_LEGGINGS] = false;
		locked[SLOT_BOOTS] = false;
		for (int i = 0; i < KitData.INVENTORY_SIZE; i++) {
			locked[INV_START + i] = false;
		}

		container.setItem(SLOT_OFFHAND, kit.getOffhand().copy());
		for (int i = 0; i < KitData.ARMOR_SIZE; i++) {
			container.setItem(SLOT_HELMET + i, kit.getArmor()[i].copy());
		}
		for (int i = 0; i < KitData.INVENTORY_SIZE; i++) {
			container.setItem(INV_START + i, kit.getInventory()[i].copy());
		}
		for (int i = 0; i < locked.length; i++) {
			if (locked[i]) {
				container.setItem(i, glass());
			}
		}

		open(player, Component.literal("Кит: " + kit.getDisplayName()), EDITOR_ROWS, container, locked, null,
				() -> saveEditor(kit, container, player));
	}

	private static void saveEditor(KitData kit, SimpleContainer container, ServerPlayer player) {
		kit.setOffhand(container.getItem(SLOT_OFFHAND).copy());
		for (int i = 0; i < KitData.ARMOR_SIZE; i++) {
			kit.getArmor()[i] = container.getItem(SLOT_HELMET + i).copy();
		}
		for (int i = 0; i < KitData.INVENTORY_SIZE; i++) {
			kit.getInventory()[i] = container.getItem(INV_START + i).copy();
		}
		KitMod.storage().save(kit, player.level().getServer());
	}

	// ==================================================================
	// Список китов
	// ==================================================================

	public static void openList(ServerPlayer player) {
		List<KitData> kits = new ArrayList<>(KitMod.storage().all());

		SimpleContainer container = new SimpleContainer(EDITOR_ROWS * 9);
		boolean[] locked = new boolean[EDITOR_ROWS * 9];
		Arrays.fill(locked, true);

		final List<String> ids = new ArrayList<>();
		int slot = 0;
		for (KitData kit : kits) {
			if (slot >= 45) {
				break;
			}
			container.setItem(slot, kitIcon(kit));
			ids.add(kit.getId());
			slot++;
		}
		container.setItem(49, named(Items.DYE.lime(), "§a＋ Создать кит",
				"§7Нажмите и напишите название в чат"));
		for (int i = 0; i < locked.length; i++) {
			if (container.getItem(i).isEmpty()) {
				container.setItem(i, glass());
			}
		}

		open(player, Component.literal("Киты (" + kits.size() + ")"), EDITOR_ROWS, container, locked,
				(clicked, p) -> {
					if (clicked == 49) {
						KitMod.chat().requestCreate(p);
					} else if (clicked >= 0 && clicked < ids.size()) {
						openConfig(p, ids.get(clicked));
					}
				}, null);
	}

	private static ItemStack kitIcon(KitData kit) {
		ItemStack stack = kit.getIcon().copy();
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("§e" + kit.getDisplayName()));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
				Component.literal("§7Нажмите, чтобы открыть настройки"))));
		return stack;
	}

	// ==================================================================
	// Меню настроек кита
	// ==================================================================

	public static void openConfig(ServerPlayer player, String kitId) {
		KitData kit = KitMod.storage().get(kitId);
		if (kit == null) {
			player.sendSystemMessage(Component.literal("Кит «" + kitId + "» не найден."));
			return;
		}

		SimpleContainer container = new SimpleContainer(27);
		boolean[] locked = new boolean[27];
		Arrays.fill(locked, true);

		container.setItem(CFG_BACK, named(Items.ARROW, "§a← Назад", "§7К списку китов"));
		container.setItem(CFG_NAME, named(Items.NAME_TAG, "§bНазвание",
				"§7Сейчас: §f" + kit.getDisplayName(),
				"§7Нажмите, затем напишите название в чат"));
		container.setItem(CFG_ICON, named(Items.ITEM_FRAME, "§dИконка",
				"§7Сейчас: §f" + itemName(kit.getIcon()),
				"§7Нажмите, чтобы выбрать предмет"));
		container.setItem(CFG_CONTENTS, named(Items.CHEST, "§6Содержимое",
				"§7Открыть редактор инвентаря"));
		container.setItem(CFG_OFFHAND, named(Items.SHIELD, "§eЛевая рука",
				"§7Настроить предмет в левой руке"));
		container.setItem(CFG_ARMOR, named(Items.IRON_CHESTPLATE, "§eБроня",
				"§7Настроить 4 слота брони"));
		container.setItem(CFG_SAVE, named(Items.DYE.lime(), "§aСохранить",
				"§7Записать кит на диск"));
		container.setItem(CFG_DELETE, named(Items.BARRIER, "§cУдалить",
				"§7Удалить кит безвозвратно"));

		for (int i = 0; i < locked.length; i++) {
			if (container.getItem(i).isEmpty()) {
				container.setItem(i, glass());
			}
		}

		open(player, Component.literal("Настройки: " + kit.getDisplayName()), 3, container, locked,
				(clicked, p) -> handleConfigClick(clicked, p, kitId), null);
	}

	private static void handleConfigClick(int slot, ServerPlayer player, String kitId) {
		KitData kit = KitMod.storage().get(kitId);
		if (kit == null) {
			return;
		}

		if (slot == CFG_BACK) {
			openList(player);
		} else if (slot == CFG_NAME) {
			KitMod.chat().requestRename(player, kitId);
		} else if (slot == CFG_ICON) {
			openIconPicker(player, kitId);
		} else if (slot == CFG_CONTENTS) {
			openEditor(player, kitId);
		} else if (slot == CFG_OFFHAND) {
			openOffhand(player, kitId);
		} else if (slot == CFG_ARMOR) {
			openArmor(player, kitId);
		} else if (slot == CFG_SAVE) {
			KitMod.storage().save(kit, player.level().getServer());
			player.sendSystemMessage(Component.literal("§aКит «" + kit.getDisplayName() + "» сохранён."));
			openConfig(player, kitId);
		} else if (slot == CFG_DELETE) {
			KitMod.storage().delete(kitId);
			player.sendSystemMessage(Component.literal("§cКит «" + kitId + "» удалён."));
			openList(player);
		}
	}

	// ==================================================================
	// Левая рука / броня
	// ==================================================================

	public static void openOffhand(ServerPlayer player, String kitId) {
		KitData kit = KitMod.storage().get(kitId);
		if (kit == null) {
			return;
		}

		SimpleContainer container = new SimpleContainer(9);
		boolean[] locked = new boolean[9];
		Arrays.fill(locked, true);
		locked[4] = false;

		container.setItem(4, kit.getOffhand().copy());
		for (int i = 0; i < 9; i++) {
			if (locked[i]) {
				container.setItem(i, glass());
			}
		}

		open(player, Component.literal("Левая рука: " + kit.getDisplayName()), 1, container, locked, null,
				() -> {
					kit.setOffhand(container.getItem(4).copy());
					KitMod.storage().save(kit, player.level().getServer());
					openConfig(player, kitId);
				});
	}

	public static void openArmor(ServerPlayer player, String kitId) {
		KitData kit = KitMod.storage().get(kitId);
		if (kit == null) {
			return;
		}

		SimpleContainer container = new SimpleContainer(36);
		boolean[] locked = new boolean[36];
		Arrays.fill(locked, true);
		int[] slots = {4, 13, 22, 31};

		for (int i = 0; i < 4; i++) {
			locked[slots[i]] = false;
			container.setItem(slots[i], kit.getArmor()[i].copy());
		}
		for (int i = 0; i < locked.length; i++) {
			if (locked[i]) {
				container.setItem(i, glass());
			}
		}

		open(player, Component.literal("Броня: " + kit.getDisplayName()), 4, container, locked, null,
				() -> {
					for (int i = 0; i < 4; i++) {
						kit.getArmor()[i] = container.getItem(slots[i]).copy();
					}
					KitMod.storage().save(kit, player.level().getServer());
					openConfig(player, kitId);
				});
	}

	// ==================================================================
	// Выбор иконки
	// ==================================================================

	public static void openIconPicker(ServerPlayer player, String kitId) {
		KitData kit = KitMod.storage().get(kitId);
		if (kit == null) {
			return;
		}

		Inventory inventory = player.getInventory();
		SimpleContainer container = new SimpleContainer(54);
		boolean[] locked = new boolean[54];
		Arrays.fill(locked, true);

		for (int i = 0; i < KitData.INVENTORY_SIZE; i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty()) {
				container.setItem(i, stack.copy());
			}
		}
		for (int i = 0; i < locked.length; i++) {
			if (container.getItem(i).isEmpty()) {
				container.setItem(i, glass());
			}
		}

		open(player, Component.literal("Иконка для «" + kit.getDisplayName() + "»"), 6, container, locked,
				(clicked, p) -> {
					if (clicked < 0 || clicked >= locked.length || locked[clicked]) {
						return;
					}
					ItemStack chosen = container.getItem(clicked);
					if (chosen.isEmpty()) {
						return;
					}
					kit.setIcon(chosen);
					KitMod.storage().save(kit, p.level().getServer());
					p.sendSystemMessage(Component.literal("§aИконка: " + itemName(chosen)));
					openConfig(p, kitId);
				}, null);
	}

	// ==================================================================
	// Вспомогательное
	// ==================================================================

	private static void open(ServerPlayer player, Component title, int rows, SimpleContainer container,
							 boolean[] locked, KitGuiMenu.ButtonHandler handler, Runnable onClose) {
		player.openMenu(new MenuProvider() {
			@Override
			public Component getDisplayName() {
				return title;
			}

			@Override
			public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player p) {
				return new KitGuiMenu(containerId, inventory, container, rows, locked, handler, onClose);
			}
		});
	}

	private static ItemStack glass() {
		ItemStack stack = new ItemStack(Items.GLASS_PANE);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
		return stack;
	}

	private static ItemStack named(Item item, String name, String... lore) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));

		List<Component> lines = new ArrayList<>();
		for (String line : lore) {
			lines.add(Component.literal(line));
		}
		if (!lines.isEmpty()) {
			stack.set(DataComponents.LORE, new ItemLore(lines));
		}
		return stack;
	}

	private static String itemName(ItemStack stack) {
		if (stack.isEmpty()) {
			return "пусто";
		}
		return stack.getHoverName().getString();
	}
}
