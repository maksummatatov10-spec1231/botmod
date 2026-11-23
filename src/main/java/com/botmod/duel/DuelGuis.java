package com.botmod.duel;

import com.botmod.arena.ArenaStyle;
import com.botmod.entity.BotKit;
import com.botmod.gui.GuiKit;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Arrays;
import java.util.Locale;

/**
 * Меню дуэли: выбор арены, кита и поведения бота.
 *
 * <p>Всё в одном сундуке 6 рядов:</p>
 * <pre>
 *   ряд 1: [Энд][Пустыня][Незер][Лёд][Джунгли][Океан]
 *   ряд 2: [Кит][Сложность]
 *   ряд 3: [Спринт][Зелья][Криты][Щит][W-tap][Прыжки]
 *   ряд 6: [Начать дуэль]
 * </pre>
 */
public final class DuelGuis {
	private static final int ROWS = 6;
	private static final int SIZE = ROWS * 9;

	public static final int SLOT_ARENA_START = 0;
	public static final int SLOT_KIT = 9;
	public static final int SLOT_DIFFICULTY = 10;
	public static final int SLOT_SPRINT = 18;
	public static final int SLOT_POTIONS = 19;
	public static final int SLOT_CRITS = 20;
	public static final int SLOT_SHIELD = 21;
	public static final int SLOT_WTAP = 22;
	public static final int SLOT_JUMPS = 23;
	public static final int SLOT_INFO = 27;
	public static final int SLOT_START = 49;

	private DuelGuis() {
	}

	public static void open(ServerPlayer player, DuelSettings settings) {
		SimpleContainer container = new SimpleContainer(SIZE);
		boolean[] locked = GuiKit.allLocked(SIZE);

		ArenaStyle[] styles = ArenaStyle.values();
		for (int i = 0; i < styles.length && i < 9; i++) {
			ArenaStyle style = styles[i];
			boolean selected = settings.arena().equals(style.id());
			container.setItem(SLOT_ARENA_START + i, GuiKit.named(arenaIcon(style),
					(selected ? "§a▶ " : "§7") + style.displayName(),
					selected ? "§7Выбрана" : "§7Нажмите, чтобы выбрать"));
			locked[SLOT_ARENA_START + i] = false;
		}

		BotKit kit = parseKit(settings.kit());
		container.setItem(SLOT_KIT, GuiKit.named(kitIcon(kit), "§eКит: §f" + kitName(kit),
				"§7Нажмите, чтобы сменить"));
		locked[SLOT_KIT] = false;

		BotDifficulty difficulty = settings.difficulty();
		container.setItem(SLOT_DIFFICULTY, GuiKit.named(Items.NETHER_STAR,
				"§dСложность: §f" + difficulty.displayName(),
				"§7Скорость ×" + String.format(Locale.ROOT, "%.2f", difficulty.speed()),
				"§7Урон ×" + String.format(Locale.ROOT, "%.2f", difficulty.damage()),
				"§7Нажмите, чтобы сменить"));
		locked[SLOT_DIFFICULTY] = false;

		container.setItem(SLOT_SPRINT, toggle(Items.SUGAR, "Спринт", settings.sprint(),
				"Бот бегает спринтом на дальней дистанции"));
		container.setItem(SLOT_POTIONS, toggle(Items.SPLASH_POTION, "Зелья", settings.potions(),
				"Бот пьёт зелье лечения при низком HP"));
		container.setItem(SLOT_CRITS, toggle(Items.GOLDEN_SWORD, "Криты", settings.crits(),
				"Крит-прыжки в бою"));
		container.setItem(SLOT_SHIELD, toggle(Items.SHIELD, "Щит", settings.shield(),
				"Бот поднимает щит в ближнем бою"));
		container.setItem(SLOT_WTAP, toggle(Items.FEATHER, "W-tap", settings.wTap(),
				"Короткий отход после попадания"));
		container.setItem(SLOT_JUMPS, toggle(Items.RABBIT_FOOT, "Прыжки", settings.jumps(),
				"Общие прыжки при стрейфе"));
		for (int slot : new int[]{SLOT_SPRINT, SLOT_POTIONS, SLOT_CRITS, SLOT_SHIELD, SLOT_WTAP, SLOT_JUMPS}) {
			locked[slot] = false;
		}

		container.setItem(SLOT_INFO, GuiKit.named(Items.BOOK, "§bКак играть",
				"§71. Выберите арену и кит",
				"§72. Настройте поведение бота",
				"§73. Нажмите «Начать дуэль»",
				"§7Бот появится на другом конце арены"));
		locked[SLOT_INFO] = false;

		container.setItem(SLOT_START, GuiKit.named(Items.NETHERITE_SWORD, "§a▶ Начать дуэль",
				"§7Перенестись на арену и начать бой"));
		locked[SLOT_START] = false;

		GuiKit.fillGlass(container, locked);

		GuiKit.open(player, Component.literal("Дуэль с ботом"), ROWS, container, locked,
				(clicked, p) -> handleClick(clicked, p, settings), null);
	}

	private static void handleClick(int slot, ServerPlayer player, DuelSettings settings) {
		if (slot >= SLOT_ARENA_START && slot < SLOT_ARENA_START + ArenaStyle.values().length) {
			ArenaStyle style = ArenaStyle.values()[slot - SLOT_ARENA_START];
			settings.setArena(style.id());
			open(player, settings);
			return;
		}

		if (slot == SLOT_KIT) {
			BotKit[] kits = BotKit.values();
			int index = parseKit(settings.kit()).ordinal();
			settings.setKit(kits[(index + 1) % kits.length].name().toLowerCase(Locale.ROOT));
			open(player, settings);
		} else if (slot == SLOT_DIFFICULTY) {
			settings.setDifficulty(settings.difficulty().next());
			open(player, settings);
		} else if (slot == SLOT_SPRINT) {
			settings.setSprint(!settings.sprint());
			open(player, settings);
		} else if (slot == SLOT_POTIONS) {
			settings.setPotions(!settings.potions());
			open(player, settings);
		} else if (slot == SLOT_CRITS) {
			settings.setCrits(!settings.crits());
			open(player, settings);
		} else if (slot == SLOT_SHIELD) {
			settings.setShield(!settings.shield());
			open(player, settings);
		} else if (slot == SLOT_WTAP) {
			settings.setWTap(!settings.wTap());
			open(player, settings);
		} else if (slot == SLOT_JUMPS) {
			settings.setJumps(!settings.jumps());
			open(player, settings);
		} else if (slot == SLOT_START) {
			player.closeContainer();
			DuelManager.start(player, settings);
		}
	}

	private static ItemStack toggle(Item item, String label, boolean enabled, String description) {
		return GuiKit.named(item,
				(enabled ? "§a✔ " : "§c✘ ") + label + ": " + (enabled ? "ВКЛ" : "ВЫКЛ"),
				"§7" + description,
				"§7Нажмите, чтобы переключить");
	}

	private static Item arenaIcon(ArenaStyle style) {
		return switch (style) {
			case END -> Items.END_STONE;
			case DESERT -> Items.SANDSTONE;
			case NETHER -> Items.NETHERRACK;
			case ICE -> Items.PACKED_ICE;
			case JUNGLE -> Items.GRASS_BLOCK;
			case OCEAN -> Items.PRISMARINE;
		};
	}

	private static Item kitIcon(BotKit kit) {
		return switch (kit) {
			case UNARMED -> Items.STICK;
			case IRON_SWORD -> Items.IRON_SWORD;
			case STONE_AXE -> Items.STONE_AXE;
			case DIAMOND_SWORD -> Items.DIAMOND_SWORD;
		};
	}

	private static String kitName(BotKit kit) {
		return switch (kit) {
			case UNARMED -> "Без оружия";
			case IRON_SWORD -> "Железный меч";
			case STONE_AXE -> "Каменный топор";
			case DIAMOND_SWORD -> "Алмазный меч";
		};
	}

	private static BotKit parseKit(String id) {
		try {
			return BotKit.valueOf(id.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return BotKit.IRON_SWORD;
		}
	}
}
