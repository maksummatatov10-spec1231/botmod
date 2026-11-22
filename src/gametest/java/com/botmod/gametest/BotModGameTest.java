package com.botmod.gametest;

import com.botmod.entity.BotKit;
import com.botmod.entity.BotMode;
import com.botmod.entity.ModEntities;
import com.botmod.entity.TrainingBotEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.gametest.v1.GameTest;

/**
 * Серверные GameTest: проверяют ядро бота без клиента.
 * Запускаются задачей `build` (см. fabricApi.configureTests в build.gradle).
 */
public class BotModGameTest {
	@GameTest
	public void botSpawnsWithAttributes(GameTestHelper helper) {
		TrainingBotEntity bot = spawn(helper, new BlockPos(1, 1, 1));

		helper.assertValueEqual(BotMode.IDLE.id(), bot.getMode().id(), "Новый бот должен быть в режиме IDLE");
		helper.assertValueEqual(20.0F, bot.getMaxHealth(), "Максимум здоровья должен быть 20");

		helper.succeed();
	}

	@GameTest
	public void kitAppliesEquipment(GameTestHelper helper) {
		TrainingBotEntity bot = spawn(helper, new BlockPos(1, 1, 1));
		bot.setKit(BotKit.DIAMOND_SWORD);

		helper.assertTrue(bot.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND).is(Items.DIAMOND_SWORD),
				"В руке должен быть алмазный меч");
		helper.assertTrue(bot.getItemInHand(net.minecraft.world.InteractionHand.OFF_HAND).is(Items.SHIELD),
				"Во второй руке должен быть щит");
		helper.assertTrue(bot.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE),
				"На боте должен быть алмазный нагрудник");

		helper.succeed();
	}

	@GameTest
	public void botDealsDamage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos base = helper.absolutePos(new BlockPos(1, 1, 1));

		TrainingBotEntity attacker = spawn(helper, new BlockPos(1, 1, 1));
		TrainingBotEntity target = spawn(helper, new BlockPos(2, 1, 1));

		attacker.setKit(BotKit.DIAMOND_SWORD);
		attacker.setDuelTarget(target);
		target.setMode(BotMode.IDLE);

		float before = target.getHealth();
		attacker.tryAttack(target);

		helper.assertTrue(target.getHealth() < before,
				"Бот должен наносить урон (было " + before + ", стало " + target.getHealth() + ")");

		helper.succeed();
	}

	@GameTest
	public void modeCycles(GameTestHelper helper) {
		helper.assertValueEqual(BotMode.SPARRING, BotMode.IDLE.next(), "После IDLE идёт SPARRING");
		helper.assertValueEqual(BotMode.RUSH, BotMode.SPARRING.next(), "После SPARRING идёт RUSH");
		helper.assertValueEqual(BotMode.DEFENDER, BotMode.RUSH.next(), "После RUSH идёт DEFENDER");
		helper.assertValueEqual(BotMode.IDLE, BotMode.DEFENDER.next(), "После DEFENDER снова IDLE");
		helper.succeed();
	}

	private static TrainingBotEntity spawn(GameTestHelper helper, BlockPos offset) {
		ServerLevel level = helper.getLevel();
		Vec3 position = Vec3.atLowerCornerOf(helper.absolutePos(offset));

		TrainingBotEntity bot = ModEntities.TRAINING_BOT.create(level, EntitySpawnReason.COMMAND);
		helper.assertTrue(bot != null, "Сущность бота должна создаваться");

		bot.setPos(position.x, position.y, position.z);
		level.addFreshEntity(bot);
		return bot;
	}
}
