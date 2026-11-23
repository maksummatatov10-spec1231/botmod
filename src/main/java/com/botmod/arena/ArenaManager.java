package com.botmod.arena;

import com.botmod.BotMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.Set;

/**
 * Арены дуэлей в отдельном измерении {@code botmod:arena}.
 *
 * <p>Размеры у всех одинаковые: 300 (длина, X) × 180 (ширина, Z).
 * Игрок и бот появляются на противоположных концах по длине.</p>
 *
 * <ul>
 *   <li>пол — несколько слоёв блоков стиля;</li>
 *   <li>по периметру и сверху — два слоя барьера;</li>
 *   <li>внутри — декоративные колонны по углам.</li>
 * </ul>
 */
public final class ArenaManager {
	public static final ResourceKey<Level> ARENA_LEVEL =
			ResourceKey.create(Registries.DIMENSION, BotMod.id("arena"));

	/** Игрок появляется на этом расстоянии от центра (по длине). */
	public static final int SPAWN_OFFSET = 140;

	private static final Set<ArenaStyle> BUILT = EnumSet.noneOf(ArenaStyle.class);

	private ArenaManager() {
	}

	public static ServerLevel level(MinecraftServer server) {
		return server == null ? null : server.getLevel(ARENA_LEVEL);
	}

	public static BlockPos playerSpawn(ArenaStyle style) {
		return new BlockPos(-SPAWN_OFFSET, ArenaStyle.FLOOR_Y + 1, style.offsetZ());
	}

	public static BlockPos botSpawn(ArenaStyle style) {
		return new BlockPos(SPAWN_OFFSET, ArenaStyle.FLOOR_Y + 1, style.offsetZ());
	}

	/** Строит арену, если она ещё не построена (проверка по маркерному блоку). */
	public static void ensureBuilt(MinecraftServer server, ArenaStyle style) {
		ServerLevel level = level(server);
		if (level == null) {
			BotMod.LOGGER.warn("Arena dimension {} is not loaded", ARENA_LEVEL.identifier());
			return;
		}

		if (BUILT.contains(style) || isBuilt(level, style)) {
			BUILT.add(style);
			return;
		}

		long started = System.currentTimeMillis();
		build(level, style);
		BUILT.add(style);
		BotMod.LOGGER.info("Arena {} built in {} ms", style.id(),
				System.currentTimeMillis() - started);
	}

	private static boolean isBuilt(ServerLevel level, ArenaStyle style) {
		BlockPos marker = new BlockPos(-SPAWN_OFFSET, ArenaStyle.FLOOR_Y, style.offsetZ());
		return level.getBlockState(marker).is(style.accent().getBlock());
	}

	// ------------------------------------------------------------------
	// Постройка
	// ------------------------------------------------------------------

	private static void build(ServerLevel level, ArenaStyle style) {
		int halfL = ArenaStyle.LENGTH / 2;
		int halfW = ArenaStyle.WIDTH / 2;
		int cx = 0;
		int cz = style.offsetZ();
		int floorY = ArenaStyle.FLOOR_Y;
		int wallTop = floorY + ArenaStyle.WALL_HEIGHT;
		BlockState base = style.floorBase();
		BlockState top = style.floorTop();
		BlockState accent = style.accent();
		BlockState border = style.border();
		BlockState pillar = style.pillar();
		BlockState barrier = Blocks.BARRIER.defaultBlockState();

		// --- пол: 3 слоя основания + верхний слой ---
		fill(level, cx - halfL, floorY - 3, cz - halfW, cx + halfL, floorY - 1, cz + halfW, base);
		fill(level, cx - halfL, floorY, cz - halfW, cx + halfL, floorY, cz + halfW, top);

		// --- бордюр по периметру пола ---
		for (int x = cx - halfL; x <= cx + halfL; x++) {
			level.setBlock(new BlockPos(x, floorY, cz - halfW), border, 2);
			level.setBlock(new BlockPos(x, floorY, cz + halfW), border, 2);
		}
		for (int z = cz - halfW; z <= cz + halfW; z++) {
			level.setBlock(new BlockPos(cx - halfL, floorY, z), border, 2);
			level.setBlock(new BlockPos(cx + halfL, floorY, z), border, 2);
		}

		// --- площадки спавна ---
		fill(level, cx - SPAWN_OFFSET - 3, floorY, cz - 3,
				cx - SPAWN_OFFSET + 3, floorY, cz + 3, accent);
		fill(level, cx + SPAWN_OFFSET - 3, floorY, cz - 3,
				cx + SPAWN_OFFSET + 3, floorY, cz + 3, accent);

		// --- колонны по углам (внутри арены) ---
		int inset = 8;
		int[][] corners = {
				{cx - halfL + inset, cz - halfW + inset},
				{cx - halfL + inset, cz + halfW - inset},
				{cx + halfL - inset, cz - halfW + inset},
				{cx + halfL - inset, cz + halfW - inset}
		};
		for (int[] corner : corners) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					for (int y = floorY + 1; y <= wallTop; y++) {
						level.setBlock(new BlockPos(corner[0] + dx, y, corner[1] + dz), pillar, 2);
					}
				}
			}
		}

		// --- стены: 2 слоя барьера по периметру ---
		for (int layer = 1; layer <= ArenaStyle.BARRIER_LAYERS; layer++) {
			int xEdge = cx + halfL + layer - 1;
			int xEdgeNeg = cx - halfL - layer + 1;
			int zEdge = cz + halfW + layer - 1;
			int zEdgeNeg = cz - halfW - layer + 1;

			for (int y = floorY + 1; y <= wallTop; y++) {
				for (int x = cx - halfL - 1; x <= cx + halfL + 1; x++) {
					level.setBlock(new BlockPos(x, y, zEdgeNeg), barrier, 2);
					level.setBlock(new BlockPos(x, y, zEdge), barrier, 2);
				}
				for (int z = cz - halfW - 1; z <= cz + halfW + 1; z++) {
					level.setBlock(new BlockPos(xEdgeNeg, y, z), barrier, 2);
					level.setBlock(new BlockPos(xEdge, y, z), barrier, 2);
				}
			}
		}

		// --- потолок: 2 слоя барьера ---
		fill(level, cx - halfL - 2, wallTop + 1, cz - halfW - 2,
				cx + halfL + 2, wallTop + 1, cz + halfW + 2, barrier);
		fill(level, cx - halfL - 2, wallTop + 2, cz - halfW - 2,
				cx + halfL + 2, wallTop + 2, cz + halfW + 2, barrier);
	}

	private static void fill(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1,
							 BlockState state) {
		int minX = Math.min(x0, x1);
		int maxX = Math.max(x0, x1);
		int minY = Math.min(y0, y1);
		int maxY = Math.max(y0, y1);
		int minZ = Math.min(z0, z1);
		int maxZ = Math.max(z0, z1);

		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int y = minY; y <= maxY; y++) {
					level.setBlock(pos.set(x, y, z), state, 2);
				}
			}
		}
	}
}
