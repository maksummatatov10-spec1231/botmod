package com.botmod.duel;

import com.botmod.arena.ArenaManager;
import com.botmod.arena.ArenaStyle;
import com.botmod.entity.BotKit;
import com.botmod.entity.BotMode;
import com.botmod.entity.ModEntities;
import com.botmod.entity.TrainingBotEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Жизненный цикл дуэли: перенос на арену, спавн бота, возврат после боя.
 *
 * <p>На время дуэли в измерении арены запрещено ломать и ставить блоки.</p>
 */
public final class DuelManager {
	private record Active(ServerLevel originalLevel, Vec3 originalPos, float originalYaw,
						   float originalPitch, TrainingBotEntity bot, ArenaStyle style) {
	}

	private static final Map<UUID, Active> ACTIVE = new HashMap<>();

	private DuelManager() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(DuelManager::tick);

		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
				!isArena(level));

		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (isArena(level)) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
	}

	private static boolean isArena(net.minecraft.world.level.Level level) {
		return level != null && ArenaManager.ARENA_LEVEL.equals(level.dimension());
	}

	public static void start(ServerPlayer player, DuelSettings settings) {
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return;
		}

		stop(player, false);

		ArenaStyle style = ArenaStyle.byId(settings.arena());
		if (style == null) {
			style = ArenaStyle.END;
		}

		ArenaManager.ensureBuilt(server, style);
		ServerLevel level = ArenaManager.level(server);
		if (level == null) {
			player.sendSystemMessage(Component.literal("§cИзмерение арены не загружено."));
			return;
		}

		ServerLevel originalLevel = (ServerLevel) player.level();
		Vec3 originalPos = player.position();
		float originalYaw = player.getYRot();
		float originalPitch = player.getXRot();

		ArenaManager.forceChunks(level, style, true);

		BlockPos playerSpawn = ArenaManager.playerSpawn(style);
		boolean teleported = player.teleportTo(level, playerSpawn.getX() + 0.5D, playerSpawn.getY(),
				playerSpawn.getZ() + 0.5D, Set.of(), -90.0F, 0.0F, true);
		com.botmod.BotMod.LOGGER.info("DuelManager: teleported={} playerLevel={} arenaLevel={}",
				teleported, player.level().dimension().identifier(), level.dimension().identifier());

		BlockPos botSpawn = ArenaManager.botSpawn(style);
		TrainingBotEntity bot = ModEntities.TRAINING_BOT.create(level, EntitySpawnReason.COMMAND);
		if (bot == null) {
			player.sendSystemMessage(Component.literal("§cНе удалось заспавнить бота."));
			return;
		}

		bot.setPos(botSpawn.getX() + 0.5D, botSpawn.getY(), botSpawn.getZ() + 0.5D);
		bot.setYRot(90.0F);
		bot.setXRot(0.0F);
		bot.setYBodyRot(90.0F);
		bot.setMode(BotMode.SPARRING);
		bot.setKit(parseKit(settings.kit()));
		bot.applyDuelSettings(settings);
		bot.setOwnerId(player.getUUID());
		boolean added = level.addFreshEntity(bot);
		bot.setDuelTarget(player);
		com.botmod.BotMod.LOGGER.info("DuelManager: botAdded={} botAlive={} botHealth={} botPos={}",
				added, bot.isAlive(), bot.getHealth(), bot.position());

		ACTIVE.put(player.getUUID(), new Active(originalLevel, originalPos, originalYaw, originalPitch, bot, style));
		player.sendSystemMessage(Component.literal(
				"§aДуэль на арене «" + style.displayName() + "». Противник: "
						+ settings.difficulty().displayName() + ". Бой!"));
	}

	/** Закрывает дуэль и возвращает игрока на прежнее место. */
	public static void stop(ServerPlayer player, boolean silent) {
		Active active = ACTIVE.remove(player.getUUID());
		if (active == null) {
			return;
		}

		if (active.bot() != null && !active.bot().isRemoved()) {
			active.bot().discard();
		}
		if (active.style() != null) {
			ServerLevel arenaLevel = ArenaManager.level(player.level().getServer());
			if (arenaLevel != null) {
				ArenaManager.forceChunks(arenaLevel, active.style(), false);
			}
		}

		player.teleportTo(active.originalLevel(), active.originalPos().x, active.originalPos().y,
				active.originalPos().z, Set.of(), active.originalYaw(), active.originalPitch(), true);

		if (!silent) {
			player.sendSystemMessage(Component.literal("§eДуэль окончена. Вы вернулись на прежнее место."));
		}
	}

	private static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) {
			return;
		}

		Iterator<Map.Entry<UUID, Active>> iterator = ACTIVE.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<UUID, Active> entry = iterator.next();
			Active active = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());

			boolean playerGone = player == null || player.isRemoved() || !player.isAlive();
			boolean botGone = active.bot() == null || active.bot().isRemoved() || !active.bot().isAlive();
			boolean playerLeft = player != null && !isArena(player.level());

			if (playerGone || botGone || playerLeft) {
				com.botmod.BotMod.LOGGER.info("DuelManager: end duel playerGone={} botGone={} playerLeft={} playerLevel={}"
								+ " botRemoved={} botHealth={} botY={} botOnGround={} blockBelow={}",
						playerGone, botGone, playerLeft,
						player == null ? "null" : player.level().dimension().identifier(),
						active.bot() == null ? "null" : active.bot().isRemoved(),
						active.bot() == null ? -1.0F : active.bot().getHealth(),
						active.bot() == null ? -1.0D : active.bot().getY(),
						active.bot() != null && active.bot().onGround(),
						active.bot() == null ? "?" : String.valueOf(active.bot().level().getBlockState(
								active.bot().blockPosition().below()).getBlock()));
				if (active.bot() != null) {
					com.botmod.BotMod.LOGGER.info("DuelManager: bot block at feet={} below2={}",
							active.bot().level().getBlockState(active.bot().blockPosition()).getBlock(),
							active.bot().level().getBlockState(active.bot().blockPosition().below(2)).getBlock());
				}
				iterator.remove();
				if (active.bot() != null && !active.bot().isRemoved()) {
					active.bot().discard();
				}
				if (active.style() != null) {
					ServerLevel arenaLevel = ArenaManager.level(server);
					if (arenaLevel != null) {
						ArenaManager.forceChunks(arenaLevel, active.style(), false);
					}
				}
				if (player != null && player.isAlive() && !playerLeft) {
					player.teleportTo(active.originalLevel(), active.originalPos().x,
							active.originalPos().y, active.originalPos().z, Set.of(),
							active.originalYaw(), active.originalPitch(), true);
					player.sendSystemMessage(Component.literal(
							botGone ? "§aПобеда! Бот повержен." : "§eДуэль окончена."));
				}
			}
		}
	}

	public static boolean isInDuel(ServerPlayer player) {
		return ACTIVE.containsKey(player.getUUID());
	}

	private static BotKit parseKit(String id) {
		try {
			return BotKit.valueOf(id.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return BotKit.IRON_SWORD;
		}
	}
}
