package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.server.powerorb.OrbConstructEntity;
import com.bluup.hexwright.server.staff_assembly.HexwrightEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.RawAnimation;

public class RisenThrallEntity extends OrbConstructEntity {

    public static final int LIFETIME_TICKS = 600;

    public static final float MAX_HEALTH = 20.0F;

    public static final float JAB_DAMAGE = 5.0F;

    private enum Clip implements OrbConstructEntity.Clip {
        RISE("spawn", 40),
        JAB("attack_1", 25);

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

    private static final int JAB_HIT_TICK = BLEND_TICKS + 5;
    private static final int JAB_TRACK_UNTIL = BLEND_TICKS + 4;
    private static final double JAB_START_RANGE = 2.6D;
    private static final double JAB_REACH = 3.2D;
    private static final double JAB_LUNGE = 0.35D;
    private static final int JAB_COOLDOWN = 8;

    private int jabCooldown;

    public RisenThrallEntity(EntityType<? extends RisenThrallEntity> type, Level level) {
        super(type, level);
        this.setMaxUpStep(1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, MAX_HEALTH)
            .add(Attributes.ARMOR, 4.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.3D)
            .add(Attributes.ATTACK_DAMAGE, JAB_DAMAGE)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.5D)
            .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Nullable
    public static RisenThrallEntity raise(ServerLevel level, Player master, Vec3 at) {
        RisenThrallEntity thrall = create(HexwrightEntities.RISEN_THRALL, level, master, at, MAX_HEALTH);
        if (thrall == null) {
            return null;
        }
        thrall.begin(Clip.RISE);
        if (!level.addFreshEntity(thrall)) {
            return null;
        }
        BlockState ground = level.getBlockState(BlockPos.containing(at.x, at.y - 0.2D, at.z));
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.1D, at.z,
                30, 0.4D, 0.05D, 0.4D, 0.1D);
        }
        level.sendParticles(ParticleTypes.SOUL, at.x, at.y + 0.3D, at.z, 12, 0.35D, 0.2D, 0.35D, 0.02D);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0F, 0.6F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SKELETON_AMBIENT, SoundSource.PLAYERS, 0.8F, 0.7F);
        return thrall;
    }

    @Override
    protected boolean isClaimed() {
        Player master = owner();
        return this.tickCount < LIFETIME_TICKS && master != null && master.isAlive();
    }

    @Override
    protected void customServerAiStep() {
        if (!isClaimed()) {
            dismiss();
            return;
        }
        super.customServerAiStep();
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
    protected void tickCooldowns() {
        if (this.jabCooldown > 0) {
            this.jabCooldown--;
        }
    }

    @Override
    protected void act(Player owner, @Nullable LivingEntity target) {
        if (target == null) {
            follow(owner);
            return;
        }
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double distance = this.distanceTo(target);
        if (this.jabCooldown <= 0 && distance <= JAB_START_RANGE) {
            this.getNavigation().stop();
            begin(Clip.JAB);
            return;
        }
        if (distance > 1.8D) {
            if (--this.repath <= 0) {
                this.repath = 5;
                this.getNavigation().moveTo(target, 1.2D);
            }
        } else {
            this.getNavigation().stop();
        }
    }


    @Override
    protected void tickAction(OrbConstructEntity.Clip clip, int tick) {
        if (clip != Clip.JAB) {
            return;
        }
        LivingEntity target = this.getTarget();
        if (target != null && tick <= JAB_TRACK_UNTIL) {
            face(target);
        }
        if (tick == JAB_HIT_TICK) {
            jab(target);
        }
    }

    @Override
    protected void finishAction(OrbConstructEntity.Clip clip) {
        if (clip == Clip.JAB) {
            this.jabCooldown = JAB_COOLDOWN;
        }
    }

    private void jab(@Nullable LivingEntity target) {
        this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.6F, 1.4F);
        Vec3 forward = Vec3.directionFromRotation(0.0F, this.getYRot());
        this.setDeltaMovement(this.getDeltaMovement().add(forward.x * JAB_LUNGE, 0.0D, forward.z * JAB_LUNGE));
        if (!isEnemy(target) || this.distanceTo(target) > JAB_REACH) {
            return;
        }
        if (target.hurt(this.damageSources().mobAttack(this), JAB_DAMAGE)) {
            Player master = owner();
            if (master != null) {
                target.setLastHurtByPlayer(master);
            }
            this.setLastHurtMob(target);
        }
    }


    @Override
    protected void dismissEffects(ServerLevel level) {
        crumble(level);
    }

    @Override
    public void die(DamageSource cause) {
        super.die(cause);
        if (this.level() instanceof ServerLevel level) {
            crumble(level);
        }
    }

    private void crumble(ServerLevel level) {
        level.sendParticles(ParticleTypes.SOUL, this.getX(), this.getY() + 1.0D, this.getZ(), 15, 0.25D, 0.5D,
            0.25D, 0.02D);
        level.sendParticles(ParticleTypes.ASH, this.getX(), this.getY() + 0.8D, this.getZ(), 30, 0.3D, 0.6D, 0.3D,
            0.0D);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SKELETON_DEATH,
            this.getSoundSource(), 0.7F, 1.2F);
    }


    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (playing() == Clip.RISE) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEAD;
    }


    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SKELETON_AMBIENT;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }

    @Override
    protected float getSoundVolume() {
        return 0.6F;
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SKELETON_HURT;
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SKELETON_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.SKELETON_STEP, 0.15F, 1.0F);
    }
}
