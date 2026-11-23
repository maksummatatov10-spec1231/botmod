package com.kitmod;

import com.kitmod.data.KitData;
import com.kitmod.gui.KitGuis;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ввод текста через чат: после клика по «Название» меню закрывается,
 * следующее сообщение игрока становится названием кита.
 */
public class KitChatInput {
	private enum Kind { RENAME, CREATE }

	private record Pending(Kind kind, String kitId) {
	}

	private final Map<UUID, Pending> pending = new HashMap<>();

	public KitChatInput() {
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, boundChatType) -> {
			Pending request = this.pending.remove(sender.getUUID());
			if (request == null) {
				return true;
			}

			String text = message.decoratedContent().getString().trim();
			if (text.isEmpty()) {
				return false;
			}
			if (text.equalsIgnoreCase("отмена") || text.equalsIgnoreCase("cancel")) {
				sender.sendSystemMessage(Component.literal("§eВвод отменён."));
				return false;
			}

			if (request.kind() == Kind.CREATE) {
				String id = sanitize(text);
				if (id.isEmpty()) {
					sender.sendSystemMessage(Component.literal("§cНазвание должно содержать буквы или цифры."));
					return false;
				}
				if (KitMod.storage().exists(id)) {
					sender.sendSystemMessage(Component.literal("§cКит «" + id + "» уже существует."));
					return false;
				}
				KitData kit = new KitData(id, text);
				KitMod.storage().save(kit, sender.level().getServer());
				sender.sendSystemMessage(Component.literal("§aКит «" + text + "» создан. Настройте его и закройте меню."));
				KitGuis.openEditor(sender, id);
				return false;
			}

			KitData kit = KitMod.storage().get(request.kitId());
			if (kit == null) {
				return false;
			}
			kit.setDisplayName(text);
			KitMod.storage().save(kit, sender.level().getServer());
			sender.sendSystemMessage(Component.literal("§aНазвание кита: " + text));
			KitGuis.openConfig(sender, request.kitId());
			return false;
		});
	}

	/** Игрок нажал «Название» — закрываем меню и ждём сообщение. */
	public void requestRename(ServerPlayer player, String kitId) {
		this.pending.put(player.getUUID(), new Pending(Kind.RENAME, kitId));
		player.closeContainer();
		player.sendSystemMessage(Component.literal("§eНапишите новое название кита в чат (или «отмена»)."));
	}

	/** Игрок нажал «Создать кит» — ждём название нового кита. */
	public void requestCreate(ServerPlayer player) {
		this.pending.put(player.getUUID(), new Pending(Kind.CREATE, ""));
		player.closeContainer();
		player.sendSystemMessage(Component.literal("§eНапишите название нового кита в чат (или «отмена»)."));
	}

	public void clear() {
		this.pending.clear();
	}

	/** Идентификатор файла: маленькие буквы, цифры, подчёркивание. */
	public static String sanitize(String name) {
		StringBuilder sb = new StringBuilder();
		for (char ch : name.toLowerCase(java.util.Locale.ROOT).toCharArray()) {
			if (Character.isLetterOrDigit(ch) && ch < 128) {
				sb.append(ch);
			} else if (ch == ' ' || ch == '-' || ch == '_') {
				sb.append('_');
			}
		}
		return sb.toString().replaceAll("^_+|_+$", "");
	}
}
