package com.botmod.lab;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Неизменяемый снимок состояния для лаборатории.
 *
 * @param tps          сглаженный TPS (0..20)
 * @param tickMs       среднее время тика, мс
 * @param players      количество игроков онлайн
 * @param dimension    id измерения
 * @param bots         состояние ботов
 * @param events       последние события
 * @param uptimeSec    аптайм сервера, секунды
 */
public record Snapshot(double tps, double tickMs, int players, String dimension,
					   List<BotState> bots, List<LabEvent> events, double uptimeSec) {

	public static Snapshot empty() {
		return new Snapshot(20.0, 0.0, 0, "minecraft:overworld", List.of(), List.of(), 0.0);
	}

	public static Snapshot capture(MinecraftServer server, double tps, double tickMs,
								   Iterable<LabEvent> events, long startMillis) {
		List<BotState> bots = new ArrayList<>();
		int players = 0;
		String dimension = "minecraft:overworld";

		List<ServerPlayer> online = server.getPlayerList().getPlayers();
		players = online.size();

		for (ServerPlayer player : online) {
			dimension = player.level().dimension().identifier().toString();
			AABB box = player.getBoundingBox().inflate(96.0D);

			for (com.botmod.entity.TrainingBotEntity bot : player.level().getEntitiesOfClass(
					com.botmod.entity.TrainingBotEntity.class, box, entity -> entity.isAlive())) {
				bots.add(new BotState(
						bot.getId(),
						bot.getName().getString(),
						bot.getMode().name().toLowerCase(java.util.Locale.ROOT),
						bot.getKit().name().toLowerCase(java.util.Locale.ROOT),
						bot.getHealth(),
						bot.getMaxHealth(),
						bot.getX(), bot.getY(), bot.getZ(),
						bot.distanceTo(player),
						bot.getDuelTarget() == null ? null : bot.getDuelTarget().getName().getString(),
						bot.getDuelTarget() != null,
						bot.getAggression(),
						bot.getLandedHits(),
						bot.getTakenHits()
				));
			}
		}

		return new Snapshot(tps, tickMs, players, dimension, List.copyOf(bots),
				List.copyOf(BotLabBridge.events()),
				(System.currentTimeMillis() - startMillis) / 1000.0);
	}

	public record BotState(int id, String name, String mode, String kit, float health, float maxHealth,
						   double x, double y, double z, double distance, String target, boolean attacking,
						   float aggression, int landedHits, int takenHits) {
	}
}
