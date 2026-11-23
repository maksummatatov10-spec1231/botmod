package com.botmod;

import com.botmod.command.BotModCommands;
import com.botmod.config.BotModConfig;
import com.botmod.duel.DuelManager;
import com.botmod.entity.ModEntities;
import com.botmod.lab.BotLabBridge;
import com.botmod.lab.BotLabServer;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Точка входа мода. Отвечает только за порядок инициализации подсистем.
 *
 * <p>Minecraft 26.3, Fabric Loader 0.20.1, Fabric API 0.166.2+26.3, Java 25.</p>
 */
public class BotMod implements ModInitializer {
	public static final String MOD_ID = "botmod";
	public static final String VERSION = "0.1.0";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		BotModConfig.load();
		ModEntities.initialize();
		BotModCommands.register();
		BotLabBridge.init();
		BotLabServer.start();
		DuelManager.init();

		LOGGER.info("BotMod {} initialized", BotModConfig.get().modVersion);
	}

	public static String configVersion() {
		return VERSION;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
