package com.kitmod;

import com.kitmod.data.KitData;
import com.kitmod.gui.KitGuis;
import com.mojang.brigade.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Команды:
 * <pre>
 *   /kit create &lt;название&gt;      создать кит и открыть редактор
 *   /kit edit   &lt;название&gt;      открыть редактор существующего
 *   /kit &lt;название&gt; save         сохранить
 *   /kit &lt;название&gt; delete       удалить
 *   /kits                         список китов (меню)
 * </pre>
 */
public final class KitCommands {
	private KitCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.register((dispatcher, registryAccess, selection) -> {
			dispatcher.register(Commands.literal("kit")
					.then(Commands.literal("create")
							.then(Commands.argument("name", StringArgumentType.string())
									.executes(ctx -> create(
											ctx.getSource().getPlayerOrException(),
											StringArgumentType.getString(ctx, "name")))))
					.then(Commands.literal("edit")
							.then(Commands.argument("name", StringArgumentType.string())
									.executes(ctx -> edit(
											ctx.getSource().getPlayerOrException(),
											StringArgumentType.getString(ctx, "name")))))
					.then(Commands.argument("name", StringArgumentType.string())
							.executes(ctx -> edit(
									ctx.getSource().getPlayerOrException(),
									StringArgumentType.getString(ctx, "name")))
							.then(Commands.literal("save")
									.executes(ctx -> save(
											ctx.getSource().getPlayerOrException(),
											StringArgumentType.getString(ctx, "name"))))
							.then(Commands.literal("delete")
									.executes(ctx -> delete(
											ctx.getSource().getPlayerOrException(),
											StringArgumentType.getString(ctx, "name"))))));

			dispatcher.register(Commands.literal("kits")
					.executes(ctx -> {
						KitGuis.openList(ctx.getSource().getPlayerOrException());
						return 1;
					}));
		});
	}

	private static int create(ServerPlayer player, String name) {
		String id = KitChatInput.sanitize(name);
		if (id.isEmpty()) {
			player.sendSystemMessage(Component.literal("§cНазвание должно содержать буквы или цифры."));
			return 0;
		}
		if (KitMod.storage().exists(id)) {
			player.sendSystemMessage(Component.literal("§cКит «" + id + "» уже существует. Используйте /kit edit " + id));
			return 0;
		}

		KitData kit = new KitData(id, name);
		KitMod.storage().save(kit, player.getServer());
		player.sendSystemMessage(Component.literal("§aКит «" + name + "» создан."));
		KitGuis.openEditor(player, id);
		return 1;
	}

	private static int edit(ServerPlayer player, String name) {
		String id = KitChatInput.sanitize(name);
		KitData kit = KitMod.storage().get(id);
		if (kit == null) {
			player.sendSystemMessage(Component.literal("§cКит «" + name + "» не найден. Список: /kits"));
			return 0;
		}
		KitGuis.openEditor(player, id);
		return 1;
	}

	private static int save(ServerPlayer player, String name) {
		String id = KitChatInput.sanitize(name);
		KitData kit = KitMod.storage().get(id);
		if (kit == null) {
			player.sendSystemMessage(Component.literal("§cКит «" + name + "» не найден."));
			return 0;
		}
		KitMod.storage().save(kit, player.getServer());
		player.sendSystemMessage(Component.literal("§aКит «" + kit.getDisplayName() + "» сохранён: config/kitmod/kits/" + id + ".json"));
		return 1;
	}

	private static int delete(ServerPlayer player, String name) {
		String id = KitChatInput.sanitize(name);
		if (!KitMod.storage().delete(id)) {
			player.sendSystemMessage(Component.literal("§cКит «" + name + "» не найден."));
			return 0;
		}
		player.sendSystemMessage(Component.literal("§cКит «" + id + "» удалён."));
		return 1;
	}
}
