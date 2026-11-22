package com.botmod.client.keymap;

import com.botmod.BotMod;
import com.botmod.config.BotModConfig;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

/**
 * Клавиши мода. Пока одна — переключение HUD (по умолчанию H).
 *
 * <p>26.3: GLFW заменён на SDL3, поэтому вместо констант GLFW используются
 * {@link InputConstants}, а категория бинда создаётся через
 * {@link KeyMapping.Category#register}.</p>
 */
public final class BotModKeyMappings {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(BotMod.id("botmod"));

	private static KeyMapping toggleHud;

	private BotModKeyMappings() {
	}

	public static void register() {
		toggleHud = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.botmod.toggle_hud",
				InputConstants.Type.KEYBOARD,
				InputConstants.KEY_H,
				CATEGORY
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (toggleHud.consumeClick()) {
				BotModConfig config = BotModConfig.get();
				config.hudEnabled = !config.hudEnabled;

				if (client.player != null) {
					// 26.3: displayClientMessage(Component, boolean) заменён на sendOverlayMessage.
					client.player.sendOverlayMessage(
							Component.translatable(config.hudEnabled
									? "botmod.message.hud_shown"
									: "botmod.message.hud_hidden")
					);
				}
			}
		});
	}
}
