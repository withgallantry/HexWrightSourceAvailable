package com.bluup.hexwright.server.mob;

import com.bluup.hexwright.server.boss.BossDamage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
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

import java.util.EnumSet;

public abstract class ConstructEntity extends Monster implements GeoEntity {

    public interface Action {
        int ordinal();

        @Nullable
        RawAnimation animation();

        int length();
    }

    protected static final int BLEND_TICKS = 3;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");

    private static final EntityDataAccessor<Byte> DATA_ACTION =
        SynchedEntityData.defineId(ConstructEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> DATA_ACTION_STAMP =
        SynchedEntityData.defineId(ConstructEntity.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);
    private final BossDamage damage;
    private final double platingHeight;
    private final double platingRadius;

    private Action action;
    protected int actionTick;

    protected ConstructEntity(EntityType<? extends ConstructEntity> type, Level level,
                              float perHitCap, float perSecondCap, double platingHeight, double platingRadius) {
        super(type, level);
        this.damage = new BossDamage(perHitCap, perSecondCap);
        this.platingHeight = platingHeight;
        this.platingRadius = platingRadius;
        this.action = actionById(0);
    }

    protected abstract Action actionById(int id);

    protected abstract void combatTick(@Nullable LivingEntity target);

    protected boolean wantsCombat() {
        return true;
    }

    protected abstract int deathTicks();

    protected abstract void deathBeat(ServerLevel level, int tick);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ACTION, (byte) 0);
        this.entityData.define(DATA_ACTION_STAMP, 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new CombatGoal());
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }


    protected Action action() {
        return this.action;
    }

    protected boolean busy() {
        return this.action.ordinal() != 0;
    }

    protected void begin(Action next) {
        this.action = next;
        this.actionTick = 0;
        this.entityData.set(DATA_ACTION, (byte) next.ordinal());
        this.entityData.set(DATA_ACTION_STAMP, this.entityData.get(DATA_ACTION_STAMP) + 1);
    }

    protected void end() {
        begin(actionById(0));
    }

    protected int clipTick() {
        return this.actionTick - BLEND_TICKS;
    }

    private class CombatGoal extends Goal {
        CombatGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return busy() || (wantsCombat() && liveTarget() != null);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void stop() {
            getNavigation().stop();
            if (busy()) {
                end();
            }
        }

        @Override
        public void tick() {
            combatTick(liveTarget());
        }
    }

    @Nullable
    protected LivingEntity liveTarget() {
        LivingEntity target = this.getTarget();
        return target != null && target.isAlive() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(target)
            ? target : null;
    }


    protected void face(LivingEntity target, float maxTurn) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        if (dx * dx + dz * dz < 1.0E-4D) {
            return;
        }
        float wanted = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float yaw = Mth.approachDegrees(this.getYRot(), wanted, maxTurn);
        this.setYRot(yaw);
        this.setYHeadRot(yaw);
        this.yBodyRot = yaw;
    }

    protected boolean inReach(LivingEntity target, double range, double cone) {
        Vec3 middle = this.position().add(0.0D, this.getBbHeight() * 0.5D, 0.0D);
        AABB box = target.getBoundingBox();
        Vec3 nearest = new Vec3(Mth.clamp(middle.x, box.minX, box.maxX),
            Mth.clamp(middle.y, box.minY, box.maxY), Mth.clamp(middle.z, box.minZ, box.maxZ));
        if (middle.distanceTo(nearest) > range - this.getBbWidth() * 0.5D) {
            return false;
        }
        Vec3 to = target.position().subtract(this.position()).multiply(1.0D, 0.0D, 1.0D);
        if (to.lengthSqr() < 1.0E-4D) {
            return true;
        }
        return to.normalize().dot(Vec3.directionFromRotation(0.0F, this.getYRot())) >= cone;
    }

    protected void lunge(double strength) {
        Vec3 forward = Vec3.directionFromRotation(0.0F, this.getYRot());
        this.setDeltaMovement(this.getDeltaMovement().add(forward.x * strength, 0.0D, forward.z * strength));
    }


    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide() || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
            || this.isInvulnerableTo(source)) {
            return super.hurt(source, amount);
        }
        float allowed = this.damage.limit(this, source, amount);
        if (allowed <= 0.0F) {
            BossDamage.rebuff(this, source, this.platingHeight, this.platingRadius);
            return false;
        }
        float health = this.getHealth();
        boolean hurt = super.hurt(source, allowed);
        this.damage.spend(health - this.getHealth());
        return hurt;
    }

    @Override
    protected float getDamageAfterMagicAbsorb(DamageSource source, float amount) {
        amount = super.getDamageAfterMagicAbsorb(source, amount);
        return source.is(DamageTypeTags.WITCH_RESISTANT_TO) ? amount * 0.15F : amount;
    }


    @Override
    public void playAmbientSound() {
        this.playSound(SoundEvents.TURTLE_AMBIENT_LAND, 0.75F, 0.75F);
    }

    @Override
    public int getAmbientSoundInterval() {
        return 280;
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.STONE_STEP, 0.6F, 0.8F);
    }


    @Override
    protected void tickDeath() {
        this.deathTime++;
        if (this.level() instanceof ServerLevel level) {
            deathBeat(level, this.deathTime);
        }
        if (this.deathTime >= deathTicks() && !this.level().isClientSide() && !this.isRemoved()) {
            this.remove(RemovalReason.KILLED);
        }
    }

    protected void breakOff(ServerLevel level, double side, double out, double up, int count, double spread) {
        float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
        double x = this.getX() + Mth.cos(yaw) * out * side;
        double z = this.getZ() + Mth.sin(yaw) * out * side;
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState()),
            x, this.getY() + up, z, count, spread, spread, spread, 0.0D);
        this.playSound(SoundEvents.DEEPSLATE_BREAK, 1.0F, 0.7F + this.random.nextFloat() * 0.2F);
        this.playSound(SoundEvents.HOGLIN_STEP, 0.7F, 0.7F + this.random.nextFloat() * 0.2F);
    }


    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", BLEND_TICKS, this::predicate));
    }

    private int lastSeenStamp = -1;

    protected Action syncedAction() {
        return actionById(this.entityData.get(DATA_ACTION));
    }

    protected RawAnimation ambientAnimation(AnimationState<ConstructEntity> state) {
        return state.isMoving() ? WALK : IDLE;
    }

    @Nullable
    protected RawAnimation deathAnimation() {
        return DEATH;
    }

    private PlayState predicate(AnimationState<ConstructEntity> state) {
        if (this.isDeadOrDying()) {
            RawAnimation death = deathAnimation();
            return death == null ? PlayState.STOP : state.setAndContinue(death);
        }
        Action current = syncedAction();
        int stamp = this.entityData.get(DATA_ACTION_STAMP);
        if (stamp != this.lastSeenStamp) {
            this.lastSeenStamp = stamp;
            if (current.animation() != null) {
                state.getController().forceAnimationReset();
            }
        }
        if (current.animation() != null) {
            return state.setAndContinue(current.animation());
        }
        return state.setAndContinue(ambientAnimation(state));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animatableCache;
    }
}
