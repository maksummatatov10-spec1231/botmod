package com.botmod.entity.ai;

import com.botmod.entity.BotMode;
import com.botmod.entity.TrainingBotEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Боевая цель бота.
 *
 * <p>Логика простая и читаемая: пока цель далеко — идём по навигации; как только
 * вошли в дистанцию удара — останавливаемся и страфим вокруг цели (орбита),
 * чтобы игрок не мог просто зажать ЛКМ по стоячей мишени.</p>
 *
 * <p>Отдельно:</p>
 * <ul>
 *   <li>прыжок при упоре в блок — чтобы бот не застревал;</li>
 *   <li>прыжок рядом с целью — криты в падении;</li>
 *   <li>спринт на дальней дистанции — быстрее сближение;</li>
 *   <li>щит в режиме DEFENDER — поднимается в ближнем бою.</li>
 * </ul>
 */
public class BotCombatGoal extends Goal {
	private static final double SPRINT_MIN_DISTANCE_SQR = 16.0D;
	private static final double SPRINT_MAX_DISTANCE_SQR = 400.0D;

	private final TrainingBotEntity bot;

	private int strafeTimer;
	private int strafeDirection = 1;
	private int pathRecalcTimer;
	/** Сколько тиков назад бот попал по цели (W-tap: короткий отход после удара). */
	private int wTapTicks;
	/** Счётчик попаданий на прошлом тике — чтобы заметить новый удар. */
	private int lastLandedHits;

	public BotCombatGoal(TrainingBotEntity bot) {
		this.bot = bot;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		return this.bot.getDuelTarget() != null && this.bot.getMode().fights();
	}

	@Override
	public boolean canContinueToUse() {
		LivingEntity target = this.bot.getDuelTarget();
		return target != null && target.isAlive() && this.bot.getMode().fights();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void start() {
		this.strafeTimer = 0;
		this.pathRecalcTimer = 0;
		this.wTapTicks = 0;
		this.lastLandedHits = this.bot.getLandedHits();
	}

	@Override
	public void stop() {
		this.bot.getNavigation().stop();
		this.bot.getMoveControl().setWait();
		this.bot.setSprinting(false);
	}

	@Override
	public void tick() {
		LivingEntity target = this.bot.getDuelTarget();
		if (target == null) {
			return;
		}

		this.bot.faceTarget(target);
		this.bot.tickShield();

		double distanceSqr = this.bot.distanceToSqr(target);
		double reach = this.bot.getAttackReach();
		double reachSqr = reach * reach;
		BotMode mode = this.bot.getMode();

		if (distanceSqr > reachSqr) {
			this.tickApproach(target, distanceSqr, mode);
		} else {
			this.tickOrbit(target, distanceSqr, reach, mode);
		}

		// W-tap: сразу после попадания бот на миг отходит — так сбрасывают спринт-отдачу.
		if (this.bot.getLandedHits() != this.lastLandedHits) {
			this.lastLandedHits = this.bot.getLandedHits();
			this.wTapTicks = 8;
		}
		if (this.wTapTicks > 0) {
			this.wTapTicks--;
		}

		// Адаптация: после пропущенного удара бот резко уходит в сторону.
		if (this.bot.getReactTicks() > 0) {
			this.bot.tickReact();
			this.strafeTimer = Math.max(this.strafeTimer, 8);
			this.strafeDirection = -this.strafeDirection;
		}

		// Атака — только в дистанции и при прямой видимости.
		if (distanceSqr <= reachSqr && this.bot.getSensing().hasLineOfSight(target)) {
			this.bot.tryAttack(target);
		}
	}

	private void tickApproach(LivingEntity target, double distanceSqr, BotMode mode) {
		if (--this.pathRecalcTimer <= 0) {
			this.pathRecalcTimer = 4 + this.bot.getRandom().nextInt(4);
			this.bot.getNavigation().moveTo(target, mode.movementSpeed());
		}

		boolean shieldUp = this.bot.isUsingItem();
		this.bot.setSprinting(this.bot.getDuelSettings().sprint() && !shieldUp
				&& distanceSqr > SPRINT_MIN_DISTANCE_SQR
				&& distanceSqr < SPRINT_MAX_DISTANCE_SQR);

		// Уперлись в блок на бегу — прыгаем, как делает игрок.
		if (this.bot.horizontalCollision && this.bot.onGround()) {
			this.bot.getJumpControl().jump();
		}
	}

	private void tickOrbit(LivingEntity target, double distanceSqr, double reach, BotMode mode) {
		this.bot.getNavigation().stop();
		this.bot.setSprinting(false);

		if (--this.strafeTimer <= 0) {
			this.strafeTimer = 30 + this.bot.getRandom().nextInt(40);
			int direction = this.bot.getRandom().nextBoolean() ? 1 : -1;
			// Иногда бот замирает на месте — игрок должен уметь ловить момент.
			this.strafeDirection = this.bot.getRandom().nextInt(4) == 0 ? 0 : direction;
		}

		// Агрессия растёт, когда бот попадает, и падает, когда его бьют.
		float aggression = this.bot.getAggression();
		boolean lowHealth = this.bot.getHealth() < this.bot.getMaxHealth() * 0.30F;

		// Держим дистанцию: если цель слишком близко — чуть отходим.
		float forward = distanceSqr < (reach * 0.55D) * (reach * 0.55D) ? -0.35F : 0.25F;
		// При низком HP бот отходит и пытается разорвать дистанцию (кайт).
		if (lowHealth) {
			forward = -0.65F;
		}
		// W-tap: короткий отход сразу после попадания (комбо-движение).
		if (this.wTapTicks > 0 && this.bot.getDuelSettings().wTap()) {
			forward = -0.85F;
		}

		float strafeBase = mode == BotMode.RUSH ? 0.55F : 0.40F;
		float strafe = this.strafeDirection * strafeBase * (0.7F + aggression * 0.3F);

		this.bot.getMoveControl().strafe(forward, strafe);

		// Крит-прыжок: в RUSH — часто, в остальных режимах — иногда.
		int jumpChance = mode == BotMode.RUSH ? Math.max(6, (int) (14 / aggression)) : Math.max(24, (int) (60 / aggression));
		boolean canJump = this.bot.getDuelSettings().crits() || this.bot.getDuelSettings().jumps();
		if (canJump && this.bot.onGround() && this.bot.getRandom().nextInt(jumpChance) == 0) {
			this.bot.getJumpControl().jump();
		}
	}
}
