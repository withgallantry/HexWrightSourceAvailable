package com.bluup.hexwright.server.powerorb;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
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

public abstract class OrbConstructEntity extends PathfinderMob implements GeoEntity, OwnableEntity {

    public interface Clip {
        RawAnimation animation();

        int length();

        int ordinal();
    }

    protected static final int BLEND_TICKS = 3;

    private static final double GUARD_RADIUS = 16.0D;
    protected static final double LEASH = 24.0D;
    private static final double FOLLOW_START = 6.0D;
    private static final double FOLLOW_STOP = 3.5D;
    private static final double TELEPORT_DISTANCE = 20.0D;
    private static final double TARGET_HEIGHT = 6.0D;
    private static final int RECENT_HIT_TICKS = 100;

    private static final int REGEN_INTERVAL = 40;
    private static final int REGEN_AFTER = 100;

    private static final EntityDataAccessor<Byte> DATA_ACTION =
        SynchedEntityData.defineId(OrbConstructEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_ACTION_STAMP =
        SynchedEntityData.defineId(OrbConstructEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);

    @Nullable
    private UUID ownerId;
    @Nullable
    private Clip action;
    private int actionTick;
    private int retarget;
    protected int repath;

    protected OrbConstructEntity(EntityType<? extends OrbConstructEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    @Nullable
    protected static <T extends OrbConstructEntity> T create(EntityType<T> type, ServerLevel level, Player owner,
                                                             Vec3 at, float health) {
        T construct = type.create(level);
        if (construct == null) {
            return null;
        }
        ((OrbConstructEntity) construct).ownerId = owner.getUUID();
        float yaw = owner.getYRot();
        construct.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        construct.setYHeadRot(yaw);
        construct.yBodyRot = yaw;
        construct.setHealth(Mth.clamp(health, 1.0F, construct.getMaxHealth()));
        return construct;
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

    protected abstract boolean isClaimed();

    @Nullable
    protected abstract Clip clipByOrdinal(int ordinal);

    protected abstract RawAnimation idleAnimation();

    protected abstract RawAnimation walkAnimation();

    @Nullable
    protected RawAnimation deathAnimation() {
        return null;
    }

    protected abstract void tickCooldowns();

    protected abstract void tickAction(Clip clip, int tick);

    protected void finishAction(Clip clip) {
    }

    protected abstract void act(Player owner, @Nullable LivingEntity target);

    protected abstract void dismissEffects(ServerLevel level);

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
            || entity instanceof ArmorStand || entity instanceof OrbConstructEntity
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
        if (owner == null || !isClaimed()) {
            this.discard();
            return;
        }
        tickCooldowns();
        regenerate();

        if (this.action != null) {
            Clip clip = this.action;
            int tick = ++this.actionTick;
            this.getNavigation().stop();
            tickAction(clip, tick);
            if (this.action == clip && tick >= clip.length()) {
                finishAction(clip);
                end();
            }
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
        act(owner, target);
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

    protected void follow(Player owner) {
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

    protected boolean teleportTo(Player owner) {
        if (!(this.level() instanceof ServerLevel level)) {
            return false;
        }
        Vec3 at = spotNear(level, owner, this.getType());
        if (at == null) {
            return false;
        }
        this.getNavigation().stop();
        this.moveTo(at.x, at.y, at.z, this.getYRot(), this.getXRot());
        return true;
    }

    protected int enemiesWithin(double radius) {
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

    protected void face(LivingEntity target) {
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


    protected void begin(Clip next) {
        this.action = next;
        this.actionTick = 0;
        this.entityData.set(DATA_ACTION, (byte) (next.ordinal() + 1));
        this.entityData.set(DATA_ACTION_STAMP, this.entityData.get(DATA_ACTION_STAMP) + 1);
    }

    protected void end() {
        this.action = null;
        this.actionTick = 0;
        this.entityData.set(DATA_ACTION, (byte) 0);
        this.entityData.set(DATA_ACTION_STAMP, this.entityData.get(DATA_ACTION_STAMP) + 1);
    }

    protected boolean inClip() {
        return this.action != null;
    }

    @Nullable
    protected Clip playing() {
        return this.action;
    }

    protected int playingTick() {
        return this.actionTick;
    }


    public void dismiss() {
        if (this.level() instanceof ServerLevel level) {
            dismissEffects(level);
        }
        this.discard();
    }


    @Override
    public boolean hurt(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker != null && (this.isAlliedTo(attacker) || attacker instanceof OrbConstructEntity)) {
            return false;
        }
        if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.IN_WALL)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
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


    private int lastSeenStamp = -1;

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", BLEND_TICKS, this::predicate));
    }

    private PlayState predicate(AnimationState<OrbConstructEntity> state) {
        RawAnimation dying = deathAnimation();
        if (this.isDeadOrDying() && dying != null) {
            return state.setAndContinue(dying);
        }
        Clip current = clipByOrdinal(this.entityData.get(DATA_ACTION) - 1);
        int stamp = this.entityData.get(DATA_ACTION_STAMP);
        if (stamp != this.lastSeenStamp) {
            this.lastSeenStamp = stamp;
            if (current != null) {
                state.getController().forceAnimationReset();
            }
        }
        if (current != null) {
            return state.setAndContinue(current.animation());
        }
        return state.setAndContinue(state.isMoving() ? walkAnimation() : idleAnimation());
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animatableCache;
    }
}
