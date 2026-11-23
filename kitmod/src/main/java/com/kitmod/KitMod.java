package com.kitmod;

import com.kitmod.data.KitStorage;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kit Editor — редактор китов с GUI.
 *
 * <p>Киты хранятся в {@code config/kitmod/kits/<id>.json}. Эту папку можно
 * скопировать и передать другому моду (PvP-боту) или другому человеку.</p>
 */
public class KitMod implements ModInitializer {
	public static final String MOD_ID = "kitmod";
	public static final Logger LOGGER = LoggerFactory.getLogger("kitmod");

	private static KitStorage storage;
	private static KitChatInput chatInput;

	@Override
	public void onInitialize() {
		this.storage = new KitStorage(
				FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("kits"));
		this.chatInput = new KitChatInput();

		KitCommands.register();

		ServerLifecycleEvents.SERVER_STARTED.register(server -> this.storage.loadAll(server));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> this.chatInput.clear());

		LOGGER.info("KitMod initialized");
	}

	public static KitStorage storage() {
		return storage;
	}

	public static KitChatInput chat() {
		return chatInput;
	}
}
