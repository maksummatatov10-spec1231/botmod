package com.botmod.arena;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Стиль арены: палитра блоков, из которой собирается пол, бордюр и колонны.
 *
 * <p>Каждая арена имеет одинаковые размеры (300×180), но своё настроение.</p>
 */
public enum ArenaStyle {
	END("end", "Энд", 0,
			Blocks.END_STONE, Blocks.PURPUR_BLOCK, Blocks.END_STONE_BRICKS,
			Blocks.OBSIDIAN, Blocks.PURPUR_PILLAR),
	DESERT("desert", "Пустыня", 1000,
			Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.CHISELED_SANDSTONE,
			Blocks.RED_SAND, Blocks.SANDSTONE),
	NETHER("nether", "Незер", 2000,
			Blocks.NETHERRACK, Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS,
			Blocks.BASALT, Blocks.MAGMA_BLOCK),
	ICE("ice", "Лёд", 3000,
			Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.SNOW_BLOCK,
			Blocks.ICE, Blocks.QUARTZ_BLOCK),
	JUNGLE("jungle", "Джунгли", 4000,
			Blocks.GRASS_BLOCK, Blocks.MOSS_BLOCK, Blocks.MUD_BRICKS,
			Blocks.JUNGLE_LOG, Blocks.OAK_LEAVES),
	OCEAN("ocean", "Океан", 5000,
			Blocks.PRISMARINE, Blocks.DARK_PRISMARINE, Blocks.PRISMARINE_BRICKS,
			Blocks.SEA_LANTERN, Blocks.WET_SPONGE);

	/** Длина арены (по X). */
	public static final int LENGTH = 300;
	/** Ширина арены (по Z). */
	public static final int WIDTH = 180;
	/** Высота стен. */
	public static final int WALL_HEIGHT = 24;
	/** Y пола (верхний слой). */
	public static final int FLOOR_Y = 63;
	/** Толщина слоя барьера. */
	public static final int BARRIER_LAYERS = 2;

	private final String id;
	private final String displayName;
	private final int offsetZ;
	private final Block floorTop;
	private final Block floorBase;
	private final Block accent;
	private final Block border;
	private final Block pillar;

	ArenaStyle(String id, String displayName, int offsetZ, Block floorTop, Block floorBase,
			   Block accent, Block border, Block pillar) {
		this.id = id;
		this.displayName = displayName;
		this.offsetZ = offsetZ;
		this.floorTop = floorTop;
		this.floorBase = floorBase;
		this.accent = accent;
		this.border = border;
		this.pillar = pillar;
	}

	public String id() {
		return this.id;
	}

	public String displayName() {
		return this.displayName;
	}

	public int offsetZ() {
		return this.offsetZ;
	}

	public BlockState floorTop() {
		return this.floorTop.defaultBlockState();
	}

	public BlockState floorBase() {
		return this.floorBase.defaultBlockState();
	}

	public BlockState accent() {
		return this.accent.defaultBlockState();
	}

	public BlockState border() {
		return this.border.defaultBlockState();
	}

	public BlockState pillar() {
		return this.pillar.defaultBlockState();
	}

	/** Идентификаторы арен — для автодополнения команд. */
	public static java.util.List<String> ids() {
		java.util.List<String> ids = new java.util.ArrayList<>();
		for (ArenaStyle style : values()) {
			ids.add(style.id);
		}
		return ids;
	}

	public static ArenaStyle byId(String id) {
		for (ArenaStyle style : values()) {
			if (style.id.equals(id) || style.name().equalsIgnoreCase(id)) {
				return style;
			}
		}
		return null;
	}
}
