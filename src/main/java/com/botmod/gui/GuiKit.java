package com.botmod.gui;

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

/** Помощники для меню на основе сундука. */
public final class GuiKit {
	private GuiKit() {
	}

	public static void open(ServerPlayer player, Component title, int rows, SimpleContainer container,
							boolean[] locked, ChestGuiMenu.ButtonHandler handler, Runnable onClose) {
		player.openMenu(new MenuProvider() {
			@Override
			public Component getDisplayName() {
				return title;
			}

			@Override
			public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player p) {
				return new ChestGuiMenu(containerId, inventory, container, rows, locked, handler, onClose);
			}
		});
	}

	public static ItemStack glass() {
		ItemStack stack = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
		return stack;
	}

	public static ItemStack named(Item item, String name, String... lore) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		if (lore.length > 0) {
			List<Component> lines = new ArrayList<>();
			for (String line : lore) {
				lines.add(Component.literal(line));
			}
			stack.set(DataComponents.LORE, new ItemLore(lines));
		}
		return stack;
	}

	/** Заполняет пустые слоты стеклом. */
	public static void fillGlass(SimpleContainer container, boolean[] locked) {
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (container.getItem(i).isEmpty()) {
				container.setItem(i, glass());
				locked[i] = true;
			}
		}
	}

	public static boolean[] allLocked(int size) {
		boolean[] locked = new boolean[size];
		Arrays.fill(locked, true);
		return locked;
	}
}
