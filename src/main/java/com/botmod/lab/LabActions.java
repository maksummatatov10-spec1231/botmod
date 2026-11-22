package com.botmod.lab;

import com.botmod.entity.BotKit;
import com.botmod.entity.BotMode;
import com.botmod.entity.ModEntities;
import com.botmod.entity.TrainingBotEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/**
 * Действия, которые лаборатория может запустить в игре.
 * Выполняются только на серверном тике (через {@link BotLabBridge#enqueue}).
 */
public final class LabActions {
	private static volatile MinecraftServer server;

	private LabActions() {
	}

	public static void setServer(MinecraftServer minecraftServer) {
		server = minecraftServer;
	}

	public static void handle(String action, String modeName, String kitName) {
		MinecraftServer minecraftServer = server;
		if (minecraftServer == null) {
			BotLabBridge.event("error", "server is not ready");
			return;
		}

		ServerPlayer player = firstPlayer(minecraftServer);
		if (player == null) {
			BotLabBridge.event("error", "no players online");
			return;
		}

		switch (action) {
			case "spawn" -> spawn(player, modeName, kitName);
			case "mode" -> applyMode(player, modeName);
			case "kit" -> applyKit(player, kitName);
			case "duel" -> duel(player);
			case "stop" -> stop(player);
			case "remove" -> remove(player);
			default -> BotLabBridge.event("error", "unknown action: " + action);
		}
	}

	// ------------------------------------------------------------------

	private static void spawn(ServerPlayer player, String modeName, String kitName) {
		ServerLevel level = (ServerLevel) player.level();
		BotMode mode = parseMode(modeName, BotMode.SPARRING);
		BotKit kit = parseKit(kitName, BotKit.IRON_SWORD);

		float yaw = player.getYRot();
		double offsetX = -Mth.sin(yaw * Mth.DEG_TO_RAD) * 2.5D;
		double offsetZ = Mth.cos(yaw * Mth.DEG_TO_RAD) * 2.5D;
		Vec3 position = player.position();

		TrainingBotEntity bot = ModEntities.TRAINING_BOT.create(level, EntitySpawnReason.COMMAND);
		if (bot == null) {
			BotLabBridge.event("error", "could not create bot entity");
			return;
		}

		bot.setPos(position.x + offsetX, position.y, position.z + offsetZ);
		bot.setYRot(yaw + 180.0F);
		bot.setMode(mode);
		bot.setKit(kit);
		bot.setOwnerId(player.getUUID());

		level.addFreshEntity(bot);
		BotLabBridge.event("spawn", bot.getName().getString() + " · " + mode.name().toLowerCase(Locale.ROOT)
				+ " · " + kit.name().toLowerCase(Locale.ROOT));
	}

	private static void applyMode(ServerPlayer player, String modeName) {
		BotMode mode = parseMode(modeName, null);
		if (mode == null) {
			BotLabBridge.event("error", "unknown mode: " + modeName);
			return;
		}

		TrainingBotEntity bot = nearest(player, 64.0D);
		if (bot == null) {
			return;
		}

		bot.setMode(mode);
		BotLabBridge.event("mode", bot.getName().getString() + " → " + mode.name().toLowerCase(Locale.ROOT));
	}

	private static void applyKit(ServerPlayer player, String kitName) {
		BotKit kit = parseKit(kitName, null);
		if (kit == null) {
			BotLabBridge.event("error", "unknown kit: " + kitName);
			return;
		}

		TrainingBotEntity bot = nearest(player, 64.0D);
		if (bot == null) {
			return;
		}

		bot.setKit(kit);
		BotLabBridge.event("kit", bot.getName().getString() + " → " + kit.name().toLowerCase(Locale.ROOT));
	}

	private static void duel(ServerPlayer player) {
		TrainingBotEntity bot = nearest(player, 64.0D);
		if (bot == null) {
			return;
		}

		if (!bot.getMode().fights()) {
			bot.setMode(BotMode.SPARRING);
		}

		bot.setDuelTarget(player);
		BotLabBridge.event("duel", bot.getName().getString() + " начинает бой");
	}

	private static void stop(ServerPlayer player) {
		TrainingBotEntity bot = nearest(player, 64.0D);
		if (bot == null) {
			return;
		}

		bot.setDuelTarget(null);
		BotLabBridge.event("stop", bot.getName().getString() + " прекращает бой");
	}

	private static void remove(ServerPlayer player) {
		TrainingBotEntity bot = nearest(player, 64.0D);
		if (bot == null) {
			return;
		}

		String name = bot.getName().getString();
		bot.discard();
		BotLabBridge.event("remove", name + " удалён");
	}

	// ------------------------------------------------------------------

	private static ServerPlayer firstPlayer(MinecraftServer minecraftServer) {
		List<ServerPlayer> players = minecraftServer.getPlayerList().getPlayers();
		return players.isEmpty() ? null : players.get(0);
	}

	private static TrainingBotEntity nearest(ServerPlayer player, double radius) {
		AABB box = player.getBoundingBox().inflate(radius);
		TrainingBotEntity nearest = null;
		double best = Double.MAX_VALUE;

		for (TrainingBotEntity bot : player.level().getEntitiesOfClass(
				TrainingBotEntity.class, box, entity -> entity.isAlive())) {
			double distance = bot.distanceToSqr(player);
			if (distance < best) {
				best = distance;
				nearest = bot;
			}
		}

		if (nearest == null) {
			BotLabBridge.event("error", "нет ботов в радиусе " + (int) radius + " блоков");
		}
		return nearest;
	}

	private static BotMode parseMode(String name, BotMode fallback) {
		if (name == null) {
			return fallback;
		}
		for (BotMode mode : BotMode.values()) {
			if (mode.name().equalsIgnoreCase(name)) {
				return mode;
			}
		}
		return fallback;
	}

	private static BotKit parseKit(String name, BotKit fallback) {
		if (name == null) {
			return fallback;
		}
		for (BotKit kit : BotKit.values()) {
			if (kit.name().equalsIgnoreCase(name)) {
				return kit;
			}
		}
		return fallback;
	}
}
