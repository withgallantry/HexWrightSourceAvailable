package com.bluup.hexwright.server.powerorb;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Comparator;
import java.util.UUID;

public class SpiritGolemEntity extends PathfinderMob implements GeoEntity, OwnableEntity {

    public static final float MAX_HEALTH = 100.0F;
    private static final float EXPLOSION_FACTOR = 0.5F;

    public enum Clip {
        NONE(null, 0),
        SPAWN("spawn", 32),
        SLAM("pull_slam", 42),
        WARD("summon_spirit_zone", 40);

        private final RawAnimation animation;
        private final int length;

        Clip(@Nullable String clip, int length) {
            this.length = length;
            this.animation = clip == null ? null : RawAnimation.begin().thenPlay(clip);
        }

        private static final Clip[] BY_ID = values();

        static Clip byId(int id) {
            return id >= 0 && id < BY_ID.length ? BY_ID[id] : NONE;
        }
    }

    private static final int BLEND_TICKS = 3;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation DESPAWN = RawAnimation.begin().thenPlayAndHold("despawn");

    public static final double SLAM_RADIUS = 6.5D;
    public static final float SLAM_DAMAGE = 6.0F;
    private static final double SLAM_START_RANGE = 4.0D;
    private static final int SLAM_HIT_TICK = BLEND_TICKS + 10;
    private static final int SLAM_TRACK_UNTIL = BLEND_TICKS + 8;
    private static final int SLAM_COOLDOWN = 20;
    private static final int STUN_TICKS = 40;

    private static final int WARD_CAST_TICK = BLEND_TICKS + 15;
    private static final int WARD_COOLDOWN = 400;
    private static final int WARD_MIN_ENEMIES = 2;

    private static final double GUARD_RADIUS = 16.0D;
    private static final double LEASH = 24.0D;
    private static final double FOLLOW_START = 6.0D;
    private static final double FOLLOW_STOP = 3.5D;
    private static final double TELEPORT_DISTANCE = 20.0D;
    private static final double TARGET_HEIGHT = 6.0D;
    private static final int RECENT_HIT_TICKS = 100;

    private static final int REGEN_INTERVAL = 40;
    private static final int REGEN_AFTER = 100;

    private static final int DEATH_TICKS = 40;

    private static final EntityDataAccessor<Byte> DATA_ACTION =
        SynchedEntityData.defineId(SpiritGolemEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_ACTION_STAMP =
        SynchedEntityData.defineId(SpiritGolemEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);

    @Nullable
    private UUID ownerId;
    private Clip action = Clip.NONE;
    private int actionTick;
    private int slamCooldown;
    private int wardCooldown = 60;
    private int retarget;
    private int repath;

    public SpiritGolemEntity(EntityType<? extends SpiritGolemEntity> type, Level level) {
        super(type, level);
        this.setMaxUpStep(1.0F);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, MAX_HEALTH)
            .add(Attributes.ARMOR, 10.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.3D)
            .add(Attributes.ATTACK_DAMAGE, SLAM_DAMAGE)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
            .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Nullable
    public static SpiritGolemEntity summon(ServerLevel level, Player owner, Vec3 at, float health) {
        SpiritGolemEntity golem = PowerOrbEntities.SPIRIT_GOLEM.create(level);
        if (golem == null) {
            return null;
        }
        golem.ownerId = owner.getUUID();
        float yaw = owner.getYRot();
        golem.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        golem.setYHeadRot(yaw);
        golem.yBodyRot = yaw;
        golem.setHealth(Mth.clamp(health, 1.0F, golem.getMaxHealth()));
        golem.begin(Clip.SPAWN);
        if (!level.addFreshEntity(golem)) {
            return null;
        }
        level.playSound(null, at.x, at.y, at.z, SoundEvents.IRON_GOLEM_REPAIR, golem.getSoundSource(),
            0.9F, 0.5F + level.random.nextFloat() * 0.1F);
        level.sendParticles(dust(), at.x, at.y + 0.05D, at.z, 40, 1.4D, 0.06D, 1.4D, 0.0D);
        return golem;
    }

    @Nullable
    public static Vec3 spotNear(ServerLevel level, Entity owner, EntityType<?> type) {
        BlockPos centre = owner.blockPosition();
        for (int attempt = 0; attempt < 12; attempt++) {
            int dx = Mth.randomBetweenInclusive(level.random, -3, 3);
            int dz = Mth.randomBetweenInclusive(level.random, -3, 3);
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
                continue;
            }
            for (int dy = 1; dy >= -2; dy--) {
                BlockPos pos = centre.offset(dx, dy, dz);
                if (WalkNodeEvaluator.getBlockPathTypeStatic(level, pos.mutable()) != BlockPathTypes.WALKABLE) {
                    continue;
                }
                Vec3 at = Vec3.atBottomCenterOf(pos);
                if (level.noCollision(type.getAABB(at.x, at.y, at.z))) {
                    return at;
                }
            }
        }
        return null;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ACTION, (byte) 0);
        this.entityData.define(DATA_ACTION_STAMP, 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
    }


    @Nullable
    @Override
    public UUID getOwnerUUID() {
        return this.ownerId;
    }

    @Nullable
    public Player owner() {
        return this.ownerId == null ? null : this.level().getPlayerByUUID(this.ownerId);
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        if (this.ownerId != null && (this.ownerId.equals(other.getUUID())
            || other instanceof OwnableEntity owned && this.ownerId.equals(owned.getOwnerUUID()))) {
            return true;
        }
        return super.isAlliedTo(other);
    }

    public boolean isEnemy(@Nullable LivingEntity entity) {
        if (entity == null || !entity.isAlive() || entity == this || entity instanceof Player
            || entity instanceof ArmorStand || entity instanceof SpiritGolemEntity
            || !entity.canBeSeenAsEnemy() || this.isAlliedTo(entity)) {
            return false;
        }
        Player owner = owner();
        if (entity instanceof NeutralMob neutral) {
            return entity == this.getTarget() || neutral.isAngryAt(this) || owner != null && neutral.isAngryAt(owner);
        }
        if (entity instanceof Enemy) {
            return true;
        }
        return entity == this.getTarget()
            || entity instanceof Mob mob && (mob.getTarget() == this || owner != null && mob.getTarget() == owner);
    }


    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        Player owner = owner();
        if (owner == null || !GolemOrbPower.claims(this)) {
            this.discard();
            return;
        }
        if (this.slamCooldown > 0) {
            this.slamCooldown--;
        }
        if (this.wardCooldown > 0) {
            this.wardCooldown--;
        }
        regenerate();

        if (this.action != Clip.NONE) {
            tickAction();
            return;
        }

        LivingEntity target = isEnemy(this.getTarget()) ? this.getTarget() : null;
        if (target != null && target.distanceToSqr(owner) > LEASH * LEASH) {
            target = null;
        }
        if (--this.retarget <= 0) {
            this.retarget = 10;
            target = pickTarget(owner, target);
        }
        this.setTarget(target);

        if (this.distanceToSqr(owner) > TELEPORT_DISTANCE * TELEPORT_DISTANCE) {
            teleportTo(owner);
            return;
        }
        if (this.wardCooldown <= 0 && enemiesWithin(SpiritWardEntity.RADIUS) >= WARD_MIN_ENEMIES) {
            this.getNavigation().stop();
            begin(Clip.WARD);
            return;
        }
        if (target != null) {
            fight(target);
        } else {
            follow(owner);
        }
    }

    @Nullable
    private LivingEntity pickTarget(Player owner, @Nullable LivingEntity current) {
        LivingEntity attacker = owner.getLastHurtByMob();
        if (recent(owner, owner.getLastHurtByMobTimestamp()) && isFightable(attacker, owner)) {
            return attacker;
        }
        LivingEntity victim = owner.getLastHurtMob();
        if (recent(owner, owner.getLastHurtMobTimestamp()) && victim != null && !(victim instanceof Player)
            && !this.isAlliedTo(victim) && victim.isAlive() && victim.distanceToSqr(owner) < LEASH * LEASH) {
            return victim;
        }
        LivingEntity own = this.getLastHurtByMob();
        if (recent(this, this.getLastHurtByMobTimestamp()) && isFightable(own, owner)) {
            return own;
        }
        if (current != null) {
            return current;
        }
        return this.level().getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(GUARD_RADIUS),
                entity -> isFightable(entity, owner) && entity instanceof Enemy)
            .stream()
            .min(Comparator.comparingDouble(this::distanceToSqr))
            .orElse(null);
    }

    private boolean isFightable(@Nullable LivingEntity entity, Player owner) {
        return isEnemy(entity)
            && entity.distanceToSqr(owner) < LEASH * LEASH
            && Math.abs(entity.getY() - this.getY()) < TARGET_HEIGHT;
    }

    private static boolean recent(LivingEntity entity, int timestamp) {
        return timestamp > 0 && entity.tickCount - timestamp < RECENT_HIT_TICKS;
    }

    private void fight(LivingEntity target) {
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double distance = this.distanceTo(target);
        if (this.slamCooldown <= 0 && distance <= SLAM_START_RANGE) {
            this.getNavigation().stop();
            begin(Clip.SLAM);
            return;
        }
        if (distance > 2.5D) {
            if (--this.repath <= 0) {
                this.repath = 5;
                this.getNavigation().moveTo(target, 1.1D);
            }
        } else {
            this.getNavigation().stop();
        }
    }

    private void follow(Player owner) {
        double distance = this.distanceToSqr(owner);
        if (distance > FOLLOW_START * FOLLOW_START) {
            if (--this.repath <= 0) {
                this.repath = 10;
                this.getNavigation().moveTo(owner, 1.0D);
            }
        } else if (distance < FOLLOW_STOP * FOLLOW_STOP) {
            this.getNavigation().stop();
        }
        if (this.getNavigation().isDone()) {
            this.getLookControl().setLookAt(owner, 10.0F, 40.0F);
        }
    }

    private void teleportTo(Player owner) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 at = spotNear(level, owner, this.getType());
        if (at != null) {
            this.getNavigation().stop();
            this.moveTo(at.x, at.y, at.z, this.getYRot(), this.getXRot());
        }
    }

    private int enemiesWithin(double radius) {
        return this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(radius, 4.0D, radius),
            entity -> isEnemy(entity) && entity.distanceToSqr(this) <= radius * radius).size();
    }

    private void regenerate() {
        if (this.getHealth() < this.getMaxHealth() && this.getTarget() == null
            && this.tickCount - this.getLastHurtByMobTimestamp() > REGEN_AFTER
            && this.tickCount % REGEN_INTERVAL == 0) {
            this.heal(1.0F);
        }
    }


    private void begin(Clip next) {
        this.action = next;
        this.actionTick = 0;
        this.entityData.set(DATA_ACTION, (byte) next.ordinal());
        this.entityData.set(DATA_ACTION_STAMP, this.entityData.get(DATA_ACTION_STAMP) + 1);
    }

    private void end() {
        begin(Clip.NONE);
    }

    private void tickAction() {
        int tick = ++this.actionTick;
        this.getNavigation().stop();
        LivingEntity target = this.getTarget();
        switch (this.action) {
            case SLAM -> {
                if (target != null && tick <= SLAM_TRACK_UNTIL) {
                    face(target);
                }
                if (tick == BLEND_TICKS + 2) {
                    this.playSound(SoundEvents.IRON_GOLEM_ATTACK, 0.8F, 0.7F);
                }
                if (tick == SLAM_HIT_TICK) {
                    slam();
                }
            }
            case WARD -> {
                if (tick == BLEND_TICKS + 1) {
                    this.playSound(SoundEvents.BEACON_ACTIVATE, 0.8F, 1.2F);
                }
                if (tick == WARD_CAST_TICK) {
                    SpiritWardEntity.cast(this);
                }
            }
            default -> {
            }
        }
        if (tick >= this.action.length) {
            if (this.action == Clip.SLAM) {
                this.slamCooldown = SLAM_COOLDOWN;
            } else if (this.action == Clip.WARD) {
                this.wardCooldown = WARD_COOLDOWN;
            }
            end();
        }
    }

    private void face(LivingEntity target) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        if (dx * dx + dz * dz < 1.0E-4D) {
            return;
        }
        float wanted = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float yaw = Mth.approachDegrees(this.getYRot(), wanted, 20.0F);
        this.setYRot(yaw);
        this.setYHeadRot(yaw);
        this.yBodyRot = yaw;
    }

    private void slam() {
        this.playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.0F, 0.5F);
        this.playSound(SoundEvents.GENERIC_EXPLODE, 0.5F, 1.6F);
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        level.sendParticles(dust(), this.getX(), this.getY() + 0.1D, this.getZ(), 60, SLAM_RADIUS * 0.4D, 0.05D,
            SLAM_RADIUS * 0.4D, 0.0D);
        level.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + 0.2D, this.getZ(), 12, 1.2D, 0.1D, 1.2D, 0.02D);
        Player owner = owner();
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
            this.getBoundingBox().inflate(SLAM_RADIUS, 3.0D, SLAM_RADIUS),
            entity -> isEnemy(entity) && entity.distanceToSqr(this) <= SLAM_RADIUS * SLAM_RADIUS)) {
            if (!victim.hurt(this.damageSources().mobAttack(this), SLAM_DAMAGE)) {
                continue;
            }
            if (owner != null) {
                victim.setLastHurtByPlayer(owner);
            }
            stun(level, victim);
        }
    }

    private static void stun(ServerLevel level, LivingEntity victim) {
        victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, STUN_TICKS, 6, false, false, true));
        victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, STUN_TICKS, 1, false, false, true));
        if (victim instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY() + victim.getBbHeight() + 0.3D,
            victim.getZ(), 10, 0.3D, 0.05D, 0.3D, 0.0D);
    }

    static DustColorTransitionOptions dust() {
        return new DustColorTransitionOptions(new Vector3f(0.918F, 1.0F, 0.741F), new Vector3f(0.659F, 1.0F, 0.216F), 1.2F);
    }


    public void dismiss() {
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(dust(), this.getX(), this.getY() + 1.2D, this.getZ(), 40, 0.6D, 1.0D, 0.6D, 0.0D);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.IRON_GOLEM_REPAIR,
                this.getSoundSource(), 0.6F, 0.8F);
        }
        this.discard();
    }


    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker != null && (this.isAlliedTo(attacker) || attacker instanceof SpiritGolemEntity)) {
            return false;
        }
        if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.IN_WALL)) {
            return false;
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            amount *= EXPLOSION_FACTOR;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void tickDeath() {
        this.deathTime++;
        if (this.deathTime == 1 && this.level() instanceof ServerLevel level) {
            level.sendParticles(dust(), this.getX(), this.getY() + 1.0D, this.getZ(), 30, 0.8D, 0.8D, 0.8D, 0.0D);
        }
        if (this.deathTime >= DEATH_TICKS && !this.level().isClientSide() && !this.isRemoved()) {
            this.remove(RemovalReason.KILLED);
        }
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }


    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.ownerId != null) {
            tag.putUUID("Owner", this.ownerId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }


    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.IRON_GOLEM_REPAIR;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 280;
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.6F;
    }

    @Override
    public float getVoicePitch() {
        return 0.6F + (this.random.nextFloat() - 0.5F) * 0.1F;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.IRON_GOLEM_STEP, 0.4F, 0.8F);
    }


    private int lastSeenStamp = -1;

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", BLEND_TICKS, this::predicate));
    }

    private PlayState predicate(AnimationState<SpiritGolemEntity> state) {
        if (this.isDeadOrDying()) {
            return state.setAndContinue(DESPAWN);
        }
        Clip current = Clip.byId(this.entityData.get(DATA_ACTION));
        int stamp = this.entityData.get(DATA_ACTION_STAMP);
        if (stamp != this.lastSeenStamp) {
            this.lastSeenStamp = stamp;
            if (current.animation != null) {
                state.getController().forceAnimationReset();
            }
        }
        if (current.animation != null) {
            return state.setAndContinue(current.animation);
        }
        return state.setAndContinue(state.isMoving() ? WALK : IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animatableCache;
    }
}
