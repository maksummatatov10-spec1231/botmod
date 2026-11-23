package com.botmod.command;

import com.botmod.config.BotModConfig;
import com.botmod.duel.DuelGuis;
import com.botmod.duel.DuelManager;
import com.botmod.duel.DuelSettings;
import com.botmod.entity.BotKit;
import com.botmod.entity.BotMode;
import com.botmod.entity.ModEntities;
import com.botmod.entity.TrainingBotEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Команды мода: {@code /bot spawn|mode|kit|duel|stop|remove|list}.
 *
 * <p>Специально без проверки прав: мод тренировочный, команды нужны в одиночке
 * и на тренировочном сервере. При необходимости оберните вызов в
 * {@code source.permissions().hasPermission(...)}.</p>
 */
public final class BotModCommands {
	// В 26.3 у SharedSuggestionProvider перегрузки изменились, поэтому
	// провайдер собирается вручную — это стабильно во всех версиях.
	private static final SuggestionProvider<CommandSourceStack> MODE_SUGGESTIONS =
			(context, builder) -> suggestNames(builder, BotMode.names());
	private static final SuggestionProvider<CommandSourceStack> KIT_SUGGESTIONS =
			(context, builder) -> suggestNames(builder, BotKit.names());
	private static final SuggestionProvider<CommandSourceStack> ARENA_SUGGESTIONS =
			(context, builder) -> suggestNames(builder, com.botmod.arena.ArenaStyle.ids());
	/** Первым аргументом /bot spawn можно ввести режим или кит. */
	private static final SuggestionProvider<CommandSourceStack> MODE_OR_KIT_SUGGESTIONS =
			(context, builder) -> {
				List<String> names = new java.util.ArrayList<>(BotMode.names());
				names.addAll(BotKit.names());
				return suggestNames(builder, names);
			};

	private BotModCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register(BotModCommands::registerCommands);
	}

	// 26.3: (dispatcher, CommandBuildContext, Commands.CommandSelection)
	private static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher,
										 net.minecraft.commands.CommandBuildContext buildContext,
										 net.minecraft.commands.Commands.CommandSelection selection) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("bot");

		root.then(Commands.literal("spawn")
				.executes(context -> spawn(context, null, null))
				.then(Commands.argument("first", StringArgumentType.word())
						.suggests(MODE_OR_KIT_SUGGESTIONS)
						.executes(context -> {
							String first = StringArgumentType.getString(context, "first");
							// Введено имя кита — спавним бота с этим китом (режим по умолчанию).
							if (parseKit(first, null) != null) {
								return spawn(context, null, first);
							}
							return spawn(context, first, null);
						})
						.then(Commands.argument("kit", StringArgumentType.word())
								.suggests(KIT_SUGGESTIONS)
								.executes(context -> spawn(context,
										StringArgumentType.getString(context, "first"),
										StringArgumentType.getString(context, "kit"))))));

		root.then(Commands.literal("mode")
				.then(Commands.argument("mode", StringArgumentType.word())
						.suggests(MODE_SUGGESTIONS)
						.executes(context -> changeMode(context, StringArgumentType.getString(context, "mode")))));

		root.then(Commands.literal("kit")
				.then(Commands.argument("kit", StringArgumentType.word())
						.suggests(KIT_SUGGESTIONS)
						.executes(context -> changeKit(context, StringArgumentType.getString(context, "kit")))));

		root.then(Commands.literal("lab")
				.executes(context -> labInfo(context))
				.then(Commands.literal("on").executes(context -> labToggle(context, true)))
				.then(Commands.literal("off").executes(context -> labToggle(context, false)))
				.then(Commands.argument("url", StringArgumentType.greedyString())
						.executes(context -> labUrl(context, StringArgumentType.getString(context, "url")))));

		root.then(Commands.literal("duel").executes(BotModCommands::startDuel));

		// /duel — меню выбора арены и настроек бота.
		dispatcher.register(Commands.literal("duel")
				.executes(context -> {
					DuelGuis.open(context.getSource().getPlayerOrException(), new DuelSettings());
					return 1;
				})
				.then(Commands.literal("start")
						.then(Commands.argument("arena", StringArgumentType.word())
								.suggests(ARENA_SUGGESTIONS)
								.executes(context -> {
									DuelSettings settings = new DuelSettings();
									settings.setArena(StringArgumentType.getString(context, "arena"));
									DuelManager.start(context.getSource().getPlayerOrException(), settings);
									return 1;
								})))
				.then(Commands.literal("stop")
						.executes(context -> {
							DuelManager.stop(context.getSource().getPlayerOrException(), false);
							return 1;
						})));
		root.then(Commands.literal("stop").executes(BotModCommands::stopDuel));
		root.then(Commands.literal("remove").executes(BotModCommands::removeNearest));
		root.then(Commands.literal("list").executes(BotModCommands::listBots));

		dispatcher.register(root);
	}

	// ------------------------------------------------------------------
	// spawn
	// ------------------------------------------------------------------

	private static int spawn(CommandContext<CommandSourceStack> context, String modeName, String kitName) {
		ServerLevel level = context.getSource().getLevel();
		Vec3 position = context.getSource().getPosition();
		float yaw = context.getSource().getRotation().y;

		BotMode mode = parseMode(modeName, BotMode.SPARRING);
		BotKit kit = parseKit(kitName, BotKit.IRON_SWORD);

		if (!canSpawnMore(context.getSource(), kit)) {
			return 0;
		}

		double offsetX = -Mth.sin(yaw * Mth.DEG_TO_RAD) * 2.5D;
		double offsetZ = Mth.cos(yaw * Mth.DEG_TO_RAD) * 2.5D;

		TrainingBotEntity bot = ModEntities.TRAINING_BOT.create(level, EntitySpawnReason.COMMAND);
		if (bot == null) {
			context.getSource().sendFailure(Component.translatable("botmod.message.spawn_failed"));
			return 0;
		}

		// Поворачиваем и голову, и корпус: иначе бот стоит спиной,
		// а голова не может довернуться на 180° (лимит ±75°).
		bot.setPos(position.x + offsetX, position.y, position.z + offsetZ);
		bot.setYRot(yaw + 180.0F);
		bot.setXRot(0.0F);
		bot.setYBodyRot(yaw + 180.0F);
		bot.setMode(mode);
		bot.setKit(kit);

		Entity executor = context.getSource().getEntity();
		if (executor instanceof LivingEntity living) {
			bot.setOwnerId(living.getUUID());
		}

		level.addFreshEntity(bot);

		context.getSource().sendSuccess(
				() -> Component.translatable("botmod.message.spawned", mode.displayName(), bot.getName()),
				false
		);
		return 1;
	}

	private static boolean canSpawnMore(CommandSourceStack source, BotKit kit) {
		int limit = BotModConfig.get().maxBotsPerPlayer;
		if (limit <= 0) {
			return true;
		}

		UUID owner = source.getEntity() instanceof LivingEntity living ? living.getUUID() : null;
		int count = 0;

		for (TrainingBotEntity bot : source.getLevel().getEntitiesOfClass(TrainingBotEntity.class,
				new AABB(source.getPosition(), source.getPosition()).inflate(64.0D), entity -> true)) {
			if (owner != null && owner.equals(bot.getOwnerId())) {
				count++;
			}
		}

		if (count >= limit) {
			source.sendFailure(Component.translatable("botmod.message.limit_reached", limit));
			return false;
		}
		return true;
	}

	// ------------------------------------------------------------------
	// mode / kit
	// ------------------------------------------------------------------

	private static int changeMode(CommandContext<CommandSourceStack> context, String modeName) {
		BotMode mode = parseMode(modeName, null);
		if (mode == null) {
			context.getSource().sendFailure(Component.translatable("botmod.message.unknown_mode", modeName));
			return 0;
		}

		LivingEntity actor = actor(context.getSource());
		if (actor == null) {
			context.getSource().sendFailure(Component.translatable("botmod.message.players_only"));
			return 0;
		}

		TrainingBotEntity bot = findNearest(context.getSource(), actor);
		if (bot == null) {
			return 0;
		}

		bot.setMode(mode);
		context.getSource().sendSuccess(
				() -> Component.translatable("botmod.message.mode_set", mode.displayName()), false);
		return 1;
	}

	private static int changeKit(CommandContext<CommandSourceStack> context, String kitName) {
		BotKit kit = parseKit(kitName, null);
		if (kit == null) {
			context.getSource().sendFailure(Component.translatable("botmod.message.unknown_kit", kitName));
			return 0;
		}

		LivingEntity actor = actor(context.getSource());
		if (actor == null) {
			context.getSource().sendFailure(Component.translatable("botmod.message.players_only"));
			return 0;
		}

		TrainingBotEntity bot = findNearest(context.getSource(), actor);
		if (bot == null) {
			return 0;
		}

		bot.setKit(kit);
		context.getSource().sendSuccess(
				() -> Component.translatable("botmod.message.kit_set", kit.name().toLowerCase(Locale.ROOT)), false);
		return 1;
	}

	// ------------------------------------------------------------------
	// лаборатория
	// ------------------------------------------------------------------

	private static int labInfo(CommandContext<CommandSourceStack> context) {
		BotModConfig config = BotModConfig.get();
		context.getSource().sendSuccess(() -> Component.literal(
				"BotLab: " + (config.labFramesEnabled ? "вкл" : "выкл")
						+ " · " + config.labUrl
						+ " · " + config.labFramesPerSecond + " кадр/сек"), false);
		return 1;
	}

	private static int labToggle(CommandContext<CommandSourceStack> context, boolean enabled) {
		BotModConfig config = BotModConfig.get();
		config.labFramesEnabled = enabled;
		BotModConfig.save();
		context.getSource().sendSuccess(() -> Component.literal(
				"Кадры в BotLab " + (enabled ? "включены" : "выключены")), false);
		return 1;
	}

	private static int labUrl(CommandContext<CommandSourceStack> context, String url) {
		if (!url.startsWith("http://") && !url.startsWith("https://")) {
			context.getSource().sendFailure(Component.literal("URL должен начинаться с http:// или https://"));
			return 0;
		}

		BotModConfig config = BotModConfig.get();
		config.labUrl = url.trim();
		config.labFramesEnabled = true;
		BotModConfig.save();

		context.getSource().sendSuccess(() -> Component.literal(
				"BotLab: кадры и телеметрия пойдут на " + config.labUrl), false);
		return 1;
	}

	// ------------------------------------------------------------------
	// duel / stop / remove / list
	// ------------------------------------------------------------------

	private static int startDuel(CommandContext<CommandSourceStack> context) {
		LivingEntity player = actor(context.getSource());
		if (player == null) {
			context.getSource().sendFailure(Component.translatable("botmod.message.players_only"));
			return 0;
		}

		TrainingBotEntity bot = findNearest(context.getSource(), player);
		if (bot == null) {
			return 0;
		}

		if (!bot.getMode().fights()) {
			bot.setMode(BotMode.SPARRING);
		}

		bot.setDuelTarget(player);
		context.getSource().sendSuccess(
				() -> Component.translatable("botmod.message.duel_started", bot.getName()), false);
		return 1;
	}

	private static int stopDuel(CommandContext<CommandSourceStack> context) {
		LivingEntity player = actor(context.getSource());
		if (player == null) {
			context.getSource().sendFailure(Component.translatable("botmod.message.players_only"));
			return 0;
		}

		TrainingBotEntity bot = findNearest(context.getSource(), player);
		if (bot == null) {
			return 0;
		}

		bot.setDuelTarget(null);
		context.getSource().sendSuccess(
				() -> Component.translatable("botmod.message.duel_stopped", bot.getName()), false);
		return 1;
	}

	private static int removeNearest(CommandContext<CommandSourceStack> context) {
		LivingEntity player = actor(context.getSource());
		if (player == null) {
			context.getSource().sendFailure(Component.translatable("botmod.message.players_only"));
			return 0;
		}

		TrainingBotEntity bot = findNearest(context.getSource(), player);
		if (bot == null) {
			return 0;
		}

		bot.discard();
		context.getSource().sendSuccess(
				() -> Component.translatable("botmod.message.removed", bot.getName()), false);
		return 1;
	}

	private static int listBots(CommandContext<CommandSourceStack> context) {
		List<TrainingBotEntity> bots = context.getSource().getLevel()
				.getEntitiesOfClass(TrainingBotEntity.class,
						new AABB(context.getSource().getPosition(), context.getSource().getPosition()).inflate(64.0D),
						entity -> true);

		if (bots.isEmpty()) {
			context.getSource().sendSuccess(
					() -> Component.translatable("botmod.message.list_empty"), false);
			return 0;
		}

		context.getSource().sendSuccess(
				() -> Component.translatable("botmod.message.list_header", bots.size()), false);

		for (TrainingBotEntity bot : bots) {
			String mode = bot.getMode().name().toLowerCase(Locale.ROOT);
			String kit = bot.getKit().name().toLowerCase(Locale.ROOT);
			context.getSource().sendSuccess(
					() -> Component.translatable("botmod.message.list_entry",
							bot.getName(), mode, kit, (int) bot.getHealth()),
					false);
		}
		return bots.size();
	}

	// ------------------------------------------------------------------
	// Утилиты
	// ------------------------------------------------------------------

	/** Кто действует: игрок из source, а для консоли — первый игрок онлайн. */
	private static LivingEntity actor(CommandSourceStack source) {
		if (source.getEntity() instanceof LivingEntity living) {
			return living;
		}
		if (source.getServer() == null) {
			return null;
		}
		List<ServerPlayer> players = source.getServer().getPlayerList().getPlayers();
		return players.isEmpty() ? null : players.get(0);
	}

	private static TrainingBotEntity findNearest(CommandSourceStack source, LivingEntity actor) {
		Vec3 position = actor.position();
		AABB box = new AABB(position, position).inflate(32.0D);

		TrainingBotEntity nearest = null;
		double best = Double.MAX_VALUE;

		for (TrainingBotEntity bot : actor.level().getEntitiesOfClass(TrainingBotEntity.class, box, entity -> true)) {
			double distance = bot.distanceToSqr(position);
			if (distance < best) {
				best = distance;
				nearest = bot;
			}
		}

		if (nearest == null) {
			source.sendFailure(Component.translatable("botmod.message.no_bot"));
		}
		return nearest;
	}

	private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestNames(
			SuggestionsBuilder builder, List<String> names) {
		String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);

		for (String name : names) {
			if (name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
				builder.suggest(name);
			}
		}

		return builder.buildFuture();
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
