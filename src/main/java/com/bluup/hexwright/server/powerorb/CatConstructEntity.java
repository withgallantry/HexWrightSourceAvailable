package com.bluup.hexwright.server.powerorb;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

public class CatConstructEntity extends OrbConstructEntity {

    public static final float MAX_HEALTH = 60.0F;

    private enum Clip implements OrbConstructEntity.Clip {
        SWIPE("skill", 38),
        PETTED("interact", 93);

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

    public static final double SWIPE_RANGE = 15.0D;
    public static final double CLAW_RADIUS = 4.0D;
    public static final float CLAW_DAMAGE = 6.0F;
    private static final int BLINK_OUT_TICK = BLEND_TICKS + 2;
    private static final int SCREAM_TICK = BLINK_OUT_TICK + 10;
    private static final int CLAW_TICK = BLINK_OUT_TICK + 16;
    private static final int TRACK_UNTIL = BLEND_TICKS + 8;
    private static final double LANDING_SPREAD = 1.5D;
    private static final int SWIPE_COOLDOWN = 120;
    private static final int FIZZLE_COOLDOWN = 20;
    private static final int FEAR_TICKS = 60;
    private static final double FEAR_SPEED = 0.2D;
    private static final int PURR_TICK = BLEND_TICKS + 34;

    private int swipeCooldown = 20;
    private final Map<LivingEntity, Fear> frightened = new WeakHashMap<>();

    private record Fear(Vec3 from, int until) {
    }

    public CatConstructEntity(EntityType<? extends CatConstructEntity> type, Level level) {
        super(type, level);
        this.setMaxUpStep(1.0F);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, MAX_HEALTH)
            .add(Attributes.ARMOR, 4.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.33D)
            .add(Attributes.ATTACK_DAMAGE, CLAW_DAMAGE)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
            .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Nullable
    public static CatConstructEntity summon(ServerLevel level, Player owner, Vec3 at, float health) {
        CatConstructEntity cat = create(PowerOrbEntities.CAT_CONSTRUCT, level, owner, at, health);
        if (cat == null || !level.addFreshEntity(cat)) {
            return null;
        }
        level.playSound(null, at.x, at.y, at.z, SoundEvents.CAT_AMBIENT, cat.getSoundSource(), 0.8F, 0.7F);
        cat.flicker(level);
        return cat;
    }

    @Override
    protected ConstructOrbPower orbPower() {
        return ConstructOrbPower.CAT;
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
        if (this.swipeCooldown > 0) {
            this.swipeCooldown--;
        }
        driveOffFrightened();
    }

    @Override
    protected void act(Player owner, @Nullable LivingEntity target) {
        if (target == null) {
            follow(owner);
            return;
        }
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double distance = this.distanceTo(target);
        if (distance > SWIPE_RANGE) {
            if (--this.repath <= 0) {
                this.repath = 5;
                this.getNavigation().moveTo(target, 1.2D);
            }
        } else if (this.swipeCooldown <= 0 && this.hasLineOfSight(target)) {
            this.getNavigation().stop();
            begin(Clip.SWIPE);
        } else {
            follow(owner);
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !player.getItemInHand(hand).isEmpty()
            || !player.getUUID().equals(this.getOwnerUUID())) {
            return super.mobInteract(player, hand);
        }
        if (!this.level().isClientSide() && !inClip() && this.getTarget() == null) {
            this.getNavigation().stop();
            begin(Clip.PETTED);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide());
    }


    @Override
    protected void tickAction(OrbConstructEntity.Clip clip, int tick) {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (clip == Clip.PETTED) {
            if (tick == PURR_TICK) {
                this.playSound(SoundEvents.CAT_PURREOW, 1.0F, 1.0F);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY() + 0.9D, this.getZ(),
                    15, 0.35D, 0.35D, 0.35D, 0.02D);
            }
            return;
        }
        LivingEntity target = this.getTarget();
        if (target != null && tick <= TRACK_UNTIL) {
            face(target);
        }
        if (tick == BLINK_OUT_TICK) {
            if (!isEnemy(target) || !blinkTo(level, target)) {
                this.swipeCooldown = FIZZLE_COOLDOWN;
                end();
            }
        } else if (tick == BLINK_OUT_TICK + 3) {
            flicker(level);
        } else if (tick == SCREAM_TICK) {
            this.playSound(SoundEvents.CAT_HURT, 1.0F, 1.6F);
        } else if (tick == CLAW_TICK) {
            claw(level);
        }
    }

    @Override
    protected void finishAction(OrbConstructEntity.Clip clip) {
        if (clip == Clip.SWIPE && this.level() instanceof ServerLevel level) {
            this.swipeCooldown = SWIPE_COOLDOWN;
            blinkHome(level);
        }
    }

    private boolean blinkTo(ServerLevel level, LivingEntity target) {
        Vec3 landing = landingNear(level, target);
        if (landing == null) {
            return false;
        }
        flicker(level);
        this.playSound(SoundEvents.FOX_TELEPORT, 1.0F, 1.2F);
        this.teleportTo(landing.x, landing.y, landing.z);
        face(target);
        this.playSound(SoundEvents.VEX_CHARGE, 0.7F, 0.7F);
        return true;
    }

    @Nullable
    private Vec3 landingNear(ServerLevel level, LivingEntity target) {
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = this.random.nextDouble() * Mth.TWO_PI;
            double reach = LANDING_SPREAD * (0.6D + this.random.nextDouble() * 0.4D);
            Vec3 at = target.position().add(Math.cos(angle) * reach, 0.0D, Math.sin(angle) * reach);
            if (level.noCollision(this, this.getType().getAABB(at.x, at.y, at.z))) {
                return at;
            }
        }
        return null;
    }

    private void blinkHome(ServerLevel level) {
        Player owner = owner();
        if (owner == null) {
            return;
        }
        flicker(level);
        if (teleportTo(owner)) {
            this.playSound(SoundEvents.FOX_TELEPORT, 1.0F, 1.2F);
            this.playSound(SoundEvents.VEX_CHARGE, 0.7F, 0.9F);
            flicker(level);
        }
    }

    private void claw(ServerLevel level) {
        this.playSound(SoundEvents.WITHER_SHOOT, 0.65F, 1.7F);
        Vec3 forward = Vec3.directionFromRotation(0.0F, this.getYRot());
        Vec3 origin = this.position().add(forward);
        slashes(level, origin, forward);
        Player owner = owner();
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
            this.getBoundingBox().move(forward).inflate(CLAW_RADIUS, 2.0D, CLAW_RADIUS),
            entity -> isEnemy(entity) && entity.distanceToSqr(origin) <= CLAW_RADIUS * CLAW_RADIUS)) {
            if (!victim.hurt(this.damageSources().mobAttack(this), CLAW_DAMAGE)) {
                continue;
            }
            if (owner != null) {
                victim.setLastHurtByPlayer(owner);
            }
            victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, FEAR_TICKS, 0, false, false, true));
            this.frightened.put(victim, new Fear(origin, this.tickCount + FEAR_TICKS));
            level.sendParticles(dust(), victim.getX(), victim.getY() + victim.getBbHeight() * 0.5D, victim.getZ(),
                20, 0.3D, 0.6D, 0.3D, 0.0D);
        }
    }

    private static void slashes(ServerLevel level, Vec3 origin, Vec3 forward) {
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x);
        DustColorTransitionOptions dust = dust();
        for (int claw = 0; claw < 4; claw++) {
            double offset = (claw % 2 == 0 ? -0.3D : 0.3D);
            double tilt = claw < 2 ? 1.0D : -1.0D;
            for (int step = 0; step <= 12; step++) {
                double angle = Math.PI * (step / 12.0D - 0.5D);
                double across = Math.sin(angle) * 1.25D;
                double up = 1.0D + Math.cos(angle) * 0.4D + tilt * Math.sin(angle) * 0.8D;
                Vec3 at = origin.add(side.scale(across + offset)).add(0.0D, up, 0.0D);
                level.sendParticles(dust, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, origin.x, origin.y + 1.0D, origin.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    private void driveOffFrightened() {
        Iterator<Map.Entry<LivingEntity, Fear>> it = this.frightened.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, Fear> entry = it.next();
            LivingEntity victim = entry.getKey();
            Fear fear = entry.getValue();
            if (!victim.isAlive() || victim.level() != this.level() || this.tickCount >= fear.until()) {
                it.remove();
                continue;
            }
            if (victim instanceof Mob mob) {
                mob.setTarget(null);
                mob.getNavigation().stop();
            }
            Vec3 away = victim.position().subtract(fear.from()).multiply(1.0D, 0.0D, 1.0D);
            if (!victim.onGround() || away.lengthSqr() < 1.0E-4D) {
                continue;
            }
            Vec3 push = away.normalize().scale(FEAR_SPEED);
            victim.setDeltaMovement(push.x, victim.getDeltaMovement().y, push.z);
            float yaw = (float) (Mth.atan2(push.z, push.x) * Mth.RAD_TO_DEG) - 90.0F;
            victim.setYRot(yaw);
            victim.setYHeadRot(yaw);
        }
    }

    private void flicker(ServerLevel level) {
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, this.getX(), this.getY() + 0.6D, this.getZ(),
            15, 0.35D, 0.5D, 0.35D, 0.05D);
    }

    private static DustColorTransitionOptions dust() {
        return new DustColorTransitionOptions(new Vector3f(0.847F, 1.0F, 0.878F), new Vector3f(0.227F, 0.878F, 0.690F), 0.8F);
    }

    @Override
    protected void dismissEffects(ServerLevel level) {
        flicker(level);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FOX_TELEPORT,
            this.getSoundSource(), 0.8F, 1.2F);
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        this.frightened.clear();
    }


    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.CAT_AMBIENT;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 300;
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.CAT_HURT;
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.CAT_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.6F;
    }

    @Override
    public float getVoicePitch() {
        return 0.7F + (this.random.nextFloat() - 0.5F) * 0.1F;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.WOLF_STEP, 0.3F, 1.2F);
    }
}
