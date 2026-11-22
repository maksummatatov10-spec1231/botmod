package com.botmod.entity;

import com.botmod.config.BotModConfig;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Тренировочный бот. Внешне — игрок (модель HumanoidModel + стандартная 64x64 кожа),
 * по поведению — полноценный противник: держит дистанцию, страфит, прыгает на крит,
 * поднимает щит и бьёт с ванильным кулдауном атаки.
 *
 * <p>Может драться только с целью, которую ему назначили ({@link #setDuelTarget}),
 * поэтому бот не набрасывается на всех подряд — это тренировочный инструмент,
 * а не враждебный моб.</p>
 */
public class TrainingBotEntity extends PathfinderMob {
	private static final EntityDataAccessor<Integer> MODE =
			SynchedEntityData.defineId(TrainingBotEntity.class, EntityDataSerializers.INT);

	private static final double DUEL_MAX_DISTANCE_SQR = 64.0D * 64.0D;

	private BotKit kit = BotKit.IRON_SWORD;
	private LivingEntity duelTarget;
	private UUID ownerId;

	private int attackCooldown;
	private int idleHealTimer;
	private boolean kitApplied;

	public TrainingBotEntity(EntityType<? extends TrainingBotEntity> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 20.0D)
				.add(Attributes.MOVEMENT_SPEED, 0.30D)
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.0D)
				.add(Attributes.FOLLOW_RANGE, 32.0D)
				.add(Attributes.ATTACK_DAMAGE, 2.0D);
	}

	@Override
	protected void registerGoals() {
		// Бой — высший приоритет: пока идёт дуэль, бот не должен отвлекаться на прочие цели.
		this.goalSelector.addGoal(0, new com.botmod.entity.ai.BotCombatGoal(this));
		this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.goalSelector.addGoal(6, new RandomLookAroundGoal(this));
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(MODE, BotMode.IDLE.id());
	}

	// ------------------------------------------------------------------
	// Режим и снаряжение
	// ------------------------------------------------------------------

	public BotMode getMode() {
		return BotMode.byId(this.entityData.get(MODE));
	}

	public void setMode(BotMode mode) {
		this.entityData.set(MODE, mode.id());
		if (!this.level().isClientSide()) {
			this.getNavigation().stop();
			this.setSprinting(false);
		}
	}

	public BotKit getKit() {
		return this.kit;
	}

	public void setKit(BotKit kit) {
		this.kit = kit;
		this.kitApplied = false;
		this.applyKitIfNeeded();
	}

	private void applyKitIfNeeded() {
		if (this.kitApplied || this.level().isClientSide()) {
			return;
		}
		this.kit.applyTo(this);
		this.kitApplied = true;
	}

	// ------------------------------------------------------------------
	// Цель дуэли
	// ------------------------------------------------------------------

	public LivingEntity getDuelTarget() {
		return this.duelTarget;
	}

	public void setDuelTarget(LivingEntity target) {
		this.duelTarget = target;
		this.setTarget(target);
		if (target == null) {
			this.getNavigation().stop();
			this.setSprinting(false);
		}
	}

	public UUID getOwnerId() {
		return this.ownerId;
	}

	public void setOwnerId(UUID ownerId) {
		this.ownerId = ownerId;
	}

	// ------------------------------------------------------------------
	// Тик
	// ------------------------------------------------------------------

	@Override
	public void tick() {
		super.tick();

		if (this.level().isClientSide()) {
			return;
		}

		this.applyKitIfNeeded();

		if (this.attackCooldown > 0) {
			this.attackCooldown--;
		}

		LivingEntity target = this.duelTarget;
		if (target != null && (!target.isAlive() || this.distanceToSqr(target) > DUEL_MAX_DISTANCE_SQR)) {
			this.setDuelTarget(null);
			target = null;
		}

		if (BotModConfig.get().botHealsWhenIdle && target == null && this.getHealth() < this.getMaxHealth()) {
			if (++this.idleHealTimer >= 40) {
				this.idleHealTimer = 0;
				this.heal(1.0F);
			}
		} else {
			this.idleHealTimer = 0;
		}
	}

	@Override
	public boolean removeWhenFarAway(double distanceSqr) {
		// Тренировочный бот не должен исчезать, когда игрок отошёл.
		return false;
	}

	// ------------------------------------------------------------------
	// Урон и ответка
	// ------------------------------------------------------------------

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		boolean hurt = super.hurtServer(level, source, amount);

		if (hurt && !level.isClientSide()) {
			Entity attacker = source.getEntity();
			// В режиме IDLE бот отвечает только на удар — это «мешок», который не провоцирует.
			if (attacker instanceof LivingEntity living && this.duelTarget == null) {
				this.setDuelTarget(living);
			}
		}

		return hurt;
	}

	/**
	 * Атака с ванильным по духу поведением: кулдаун, крит в падении, отдача цели.
	 * Вызывается только из {@link com.botmod.entity.ai.BotCombatGoal}.
	 */
	public void tryAttack(LivingEntity target) {
		if (this.attackCooldown > 0 || !(this.level() instanceof ServerLevel serverLevel)) {
			return;
		}

		if (!this.isWithinMeleeAttackRange(target) || !this.getSensing().hasLineOfSight(target)) {
			return;
		}

		this.attackCooldown = this.kit.attackIntervalTicks();
		// 26.3: swing(InteractionHand) убран, вместо него SwingAnimation.
		this.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);

		float damage = this.kit.attackDamage() * BotModConfig.get().damageMultiplier;

		// Крит: бот в воздухе и падает — как у игрока.
		if (!this.onGround() && this.getDeltaMovement().y < 0.0D && !this.isInWater()) {
			damage *= 1.5F;
		}

		DamageSource source = this.damageSources().mobAttack(this);
		// Отдачу применяет сам LivingEntity#hurtServer через dealDefaultKnockback.
		target.hurtServer(serverLevel, source, damage);
	}

	/** Обработка щита: поднимаем его в ближнем бою, опускаем на дистанции. */
	public void tickShield() {
		if (!this.getMode().usesShield()) {
			return;
		}

		LivingEntity target = this.duelTarget;
		if (target == null) {
			if (this.isUsingItem()) {
				this.stopUsingItem();
			}
			return;
		}

		ItemStack offhand = this.getItemInHand(InteractionHand.OFF_HAND);
		if (offhand.isEmpty()) {
			return;
		}

		double distance = this.distanceTo(target);
		if (distance < 3.2D && !this.isUsingItem()) {
			this.startUsingItem(InteractionHand.OFF_HAND);
		} else if (distance > 4.5D && this.isUsingItem()) {
			this.stopUsingItem();
		}
	}

	// ------------------------------------------------------------------
	// Взаимодействие
	// ------------------------------------------------------------------

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (!this.level().isClientSide()) {
			BotMode next = this.getMode().next();
			this.setMode(next);
			// 26.3: displayClientMessage(Component, boolean) заменён на sendOverlayMessage.
			player.sendOverlayMessage(
					net.minecraft.network.chat.Component.translatable(
							"botmod.message.bot_mode", this.getName(), next.displayName())
			);
		}

		return InteractionResult.SUCCESS;
	}

	// ------------------------------------------------------------------
	// Сохранение
	// ------------------------------------------------------------------

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("Mode", this.getMode().id());
		output.putInt("Kit", this.kit.id());
		if (this.ownerId != null) {
			output.putString("Owner", this.ownerId.toString());
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.setMode(BotMode.byId(input.getInt("Mode").orElse(0)));
		this.kit = BotKit.byId(input.getInt("Kit").orElse(BotKit.IRON_SWORD.id()));
		this.kitApplied = false;
		input.getString("Owner").ifPresent(value -> {
			try {
				this.ownerId = UUID.fromString(value);
			} catch (IllegalArgumentException ignored) {
				// Повреждённый UUID — просто считаем бота без владельца.
			}
		});
	}

	// ------------------------------------------------------------------
	// Мелочи для ИИ и HUD
	// ------------------------------------------------------------------

	/** Дистанция, с которой бот ещё может ударить. */
	public double getAttackReach() {
		return 3.0D;
	}

	/** Плавный поворот к цели — используется целью боя вместо жёсткого setYRot. */
	public void faceTarget(LivingEntity target) {
		this.getLookControl().setLookAt(target, 30.0F, 30.0F);
	}

	public Vec3 getHorizontalVelocity() {
		Vec3 movement = this.getDeltaMovement();
		return new Vec3(movement.x, 0.0D, movement.z);
	}
}
