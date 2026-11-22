package com.botmod.gametest;

import com.botmod.entity.ModEntities;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Клиентский GameTest: поднимает настоящий клиент, создаёт мир, спавнит бота
 * и делает скриншоты. В CI запускается задачей runProductionClientGameTest (Xvfb + Mesa).
 */
@SuppressWarnings("UnstableApiUsage")
public class BotModClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			// 1. Ждём, пока мир прогрузится и отрендерится.
			singleplayer.getConnection().waitForChunksRender();
			context.takeScreenshot("botmod-world");

			// 2. Спавним бота перед игроком (серверная команда /bot spawn).
			singleplayer.getServer().runCommand("bot spawn sparring diamond_sword");
			singleplayer.getConnection().waitForClientboundEntityUpdates(ModEntities.TRAINING_BOT);
			context.waitTicks(20); // интерполяция позиции LivingEntity
			context.takeScreenshot("botmod-bot");

			// 3. Дуэль: бот должен начать двигаться к игроку и махать мечом.
			singleplayer.getServer().runCommand("bot duel");
			context.waitTicks(40);
			context.takeScreenshot("botmod-duel");

			// 4. HUD: панель ближайшего бота уже включена по умолчанию.
			context.takeScreenshot("botmod-hud");
		}
	}
}
