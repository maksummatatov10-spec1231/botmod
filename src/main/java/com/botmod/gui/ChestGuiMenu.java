package com.botmod.gui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Меню на основе ванильного сундука: клиент рисует обычный экран сундука,
 * сервер решает, что делать с кликом.
 *
 * <p>{@code handler} — если задан, клик по слоту сундука вызывает действие
 * вместо перемещения предмета. {@code locked} — слоты-разделители (стекло).</p>
 */
public class ChestGuiMenu extends AbstractContainerMenu {
	@FunctionalInterface
	public interface ButtonHandler {
		void click(int slot, ServerPlayer player);
	}

	private final Container container;
	private final int rows;
	private final boolean[] locked;
	private final ButtonHandler handler;
	private final Runnable onClose;

	public ChestGuiMenu(int containerId, Inventory inventory, Container container, int rows,
						boolean[] locked, ButtonHandler handler, Runnable onClose) {
		super(menuType(rows), containerId);
		this.container = container;
		this.rows = rows;
		this.locked = locked;
		this.handler = handler;
		this.onClose = onClose;

		for (int i = 0; i < rows * 9; i++) {
			final boolean isLocked = i < locked.length && locked[i];
			this.addSlot(new Slot(container, i, 8 + (i % 9) * 18, 18 + (i / 9) * 18) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return !isLocked;
				}

				@Override
				public boolean mayPickup(Player player) {
					return !isLocked;
				}
			});
		}

		addStandardInventorySlots(inventory, 8, 18 + rows * 18 + 13);
	}

	private static MenuType<?> menuType(int rows) {
		return switch (rows) {
			case 1 -> MenuType.GENERIC_9x1;
			case 2 -> MenuType.GENERIC_9x2;
			case 3 -> MenuType.GENERIC_9x3;
			case 4 -> MenuType.GENERIC_9x4;
			case 5 -> MenuType.GENERIC_9x5;
			default -> MenuType.GENERIC_9x6;
		};
	}

	public Container getContainer() {
		return this.container;
	}

	@Override
	public boolean stillValid(Player player) {
		return true;
	}

	@Override
	public void clicked(int slotIndex, int buttonNum, ContainerInput containerInput, Player player) {
		int containerSize = this.rows * 9;

		if (this.handler != null && slotIndex >= 0 && slotIndex < containerSize) {
			if (player instanceof ServerPlayer serverPlayer) {
				this.handler.click(slotIndex, serverPlayer);
			}
			return;
		}

		if (slotIndex >= 0 && slotIndex < containerSize
				&& this.locked.length > slotIndex && this.locked[slotIndex]) {
			return;
		}

		super.clicked(slotIndex, buttonNum, containerInput, player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		if (this.handler != null) {
			return ItemStack.EMPTY;
		}
		if (index < 0 || index >= this.slots.size()) {
			return ItemStack.EMPTY;
		}

		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}

		ItemStack original = slot.getItem();
		ItemStack copy = original.copy();
		int containerSize = this.rows * 9;

		boolean moved;
		if (index < containerSize) {
			moved = moveItemStackTo(original, containerSize, this.slots.size(), true);
		} else {
			moved = moveItemStackTo(original, 0, containerSize, false);
		}

		if (!moved) {
			return ItemStack.EMPTY;
		}
		if (original.isEmpty()) {
			slot.set(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return copy;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		if (this.onClose != null && !player.level().isClientSide()) {
			this.onClose.run();
		}
	}
}
