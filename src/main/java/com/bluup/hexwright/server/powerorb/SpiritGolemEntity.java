package com.bluup.hexwright.server.powerorb;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.core.animation.RawAnimation;

public class SpiritGolemEntity extends OrbConstructEntity {

    public static final float MAX_HEALTH = 100.0F;
    private static final float EXPLOSION_FACTOR = 0.5F;

    private enum Clip implements OrbConstructEntity.Clip {
        SPAWN("spawn", 32),
        SLAM("pull_slam", 42),
        WARD("summon_spirit_zone", 40);

        private static final Clip[] BY_ORDINAL = values();

        private final RawAnimation animation;
        private final int length;

        Clip(String clip, int length) {
            this.animation = RawAnimation.begin().thenPlay(clip);
            this.length = length;
        }

        @Override
        public RawAnimation animation() {
            return this.animation;
        }

        @Override
        public int length() {
            return this.length;
        }
    }

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

    private static final int DEATH_TICKS = 40;

    private int slamCooldown;
    private int wardCooldown = 60;

    public SpiritGolemEntity(EntityType<? extends SpiritGolemEntity> type, Level level) {
        super(type, level);
        this.setMaxUpStep(1.0F);
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
        SpiritGolemEntity golem = create(PowerOrbEntities.SPIRIT_GOLEM, level, owner, at, health);
        if (golem == null) {
            return null;
        }
        golem.begin(Clip.SPAWN);
        if (!level.addFreshEntity(golem)) {
            return null;
        }
        level.playSound(null, at.x, at.y, at.z, SoundEvents.IRON_GOLEM_REPAIR, golem.getSoundSource(),
            0.9F, 0.5F + level.random.nextFloat() * 0.1F);
        level.sendParticles(dust(), at.x, at.y + 0.05D, at.z, 40, 1.4D, 0.06D, 1.4D, 0.0D);
        return golem;
    }

    @Override
    protected ConstructOrbPower orbPower() {
        return ConstructOrbPower.GOLEM;
    }

    @Nullable
    @Override
    protected OrbConstructEntity.Clip clipByOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < Clip.BY_ORDINAL.length ? Clip.BY_ORDINAL[ordinal] : null;
    }

    @Override
    protected RawAnimation idleAnimation() {
        return IDLE;
    }

    @Override
    protected RawAnimation walkAnimation() {
        return WALK;
    }

    @Override
    protected RawAnimation deathAnimation() {
        return DESPAWN;
    }


    @Override
    protected void tickCooldowns() {
        if (this.slamCooldown > 0) {
            this.slamCooldown--;
        }
        if (this.wardCooldown > 0) {
            this.wardCooldown--;
        }
    }

    @Override
    protected void act(Player owner, @Nullable LivingEntity target) {
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


    @Override
    protected void tickAction(OrbConstructEntity.Clip clip, int tick) {
        LivingEntity target = this.getTarget();
        if (clip == Clip.SLAM) {
            if (target != null && tick <= SLAM_TRACK_UNTIL) {
                face(target);
            }
            if (tick == BLEND_TICKS + 2) {
                this.playSound(SoundEvents.IRON_GOLEM_ATTACK, 0.8F, 0.7F);
            }
            if (tick == SLAM_HIT_TICK) {
                slam();
            }
        } else if (clip == Clip.WARD) {
            if (tick == BLEND_TICKS + 1) {
                this.playSound(SoundEvents.BEACON_ACTIVATE, 0.8F, 1.2F);
            }
            if (tick == WARD_CAST_TICK) {
                SpiritWardEntity.cast(this);
            }
        }
    }

    @Override
    protected void finishAction(OrbConstructEntity.Clip clip) {
        if (clip == Clip.SLAM) {
            this.slamCooldown = SLAM_COOLDOWN;
        } else if (clip == Clip.WARD) {
            this.wardCooldown = WARD_COOLDOWN;
        }
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

    @Override
    protected void dismissEffects(ServerLevel level) {
        level.sendParticles(dust(), this.getX(), this.getY() + 1.2D, this.getZ(), 40, 0.6D, 1.0D, 0.6D, 0.0D);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.IRON_GOLEM_REPAIR,
            this.getSoundSource(), 0.6F, 0.8F);
    }


    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            amount *= EXPLOSION_FACTOR;
        }
        return super.hurt(source, amount);
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
}
