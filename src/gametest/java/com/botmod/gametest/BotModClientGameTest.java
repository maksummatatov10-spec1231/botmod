package com.botmod.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Клиентский GameTest: поднимает настоящий клиент, создаёт мир и делает скриншот.
 * В CI запускается задачей runProductionClientGameTest (Xvfb).
 */
@SuppressWarnings("UnstableApiUsage")
public class BotModClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			context.takeScreenshot("botmod-smoke");
		}
	}
}
