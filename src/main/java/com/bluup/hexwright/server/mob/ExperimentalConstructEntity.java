package com.bluup.hexwright.server.mob;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.RawAnimation;

import java.util.List;

public class ExperimentalConstructEntity extends ConstructEntity {

    public enum Clip implements Action {
        NONE(null, 0, false),
        MELEE("melee", 36, false),
        ROLL_START("roll_start", 14, false),
        ROLL("roll_loop", 77, true),
        CRASH("crash", 56, false);

        private final RawAnimation animation;
        private final int length;

        Clip(@Nullable String clip, int length, boolean loops) {
            this.length = length;
            this.animation = clip == null ? null
                : loops ? RawAnimation.begin().thenLoop(clip) : RawAnimation.begin().thenPlay(clip);
        }

        @Nullable
        @Override
        public RawAnimation animation() {
            return this.animation;
        }

        @Override
        public int length() {
            return this.length;
        }

        private static final Clip[] BY_ID = values();
    }

    private static final double MELEE_START_RANGE = 3.0D;
    private static final double MELEE_HIT_RANGE = 3.2D;
    private static final int MELEE_TRACK_UNTIL = 12;
    private static final int MELEE_LUNGE_TICK = 15;
    private static final int MELEE_HIT_TICK = 17;
    private static final double MELEE_CONE = 0.5D;

    private static final double ROLL_MIN_RANGE = 5.0D;
    private static final double ROLL_MAX_RANGE = 15.0D;
    private static final int ROLL_COOLDOWN = 200;
    private static final double ROLL_SPEED = 1.7D;
    private static final int ROLL_PULSE = 7;
    private static final float ROLL_DAMAGE_SCALE = 1.5F;

    private static final int RECOVER_TICKS = 6;

    private static final int DEATH_TICKS = 42;

    private static final float PER_HIT_CAP = 15.0F;
    private static final float PER_SECOND_CAP = 30.0F;

    private int recover;
    private int repath;
    private int rollCooldown = ROLL_COOLDOWN / 2;

    public ExperimentalConstructEntity(EntityType<? extends ExperimentalConstructEntity> type, Level level) {
        super(type, level, PER_HIT_CAP, PER_SECOND_CAP, 1.1D, 0.7D);
        this.xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 30.0D)
            .add(Attributes.ARMOR, 6.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.25D)
            .add(Attributes.ATTACK_DAMAGE, 5.0D)
            .add(Attributes.ATTACK_KNOCKBACK, 0.5D)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
            .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected Action actionById(int id) {
        return id >= 0 && id < Clip.BY_ID.length ? Clip.BY_ID[id] : Clip.NONE;
    }

    private void finish() {
        end();
        this.recover = RECOVER_TICKS;
    }


    @Override
    protected void combatTick(@Nullable LivingEntity target) {
        if (this.rollCooldown > 0) {
            this.rollCooldown--;
        }
        switch ((Clip) action()) {
            case NONE -> choose(target);
            case MELEE -> tickMelee(target);
            case ROLL_START -> tickRollStart(target);
            case ROLL -> tickRoll(target);
            case CRASH -> tickCrash();
        }
    }

    private void choose(@Nullable LivingEntity target) {
        if (target == null) {
            return;
        }
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (this.recover > 0) {
            this.recover--;
            this.getNavigation().stop();
            return;
        }
        double distance = this.distanceTo(target);
        boolean sees = this.getSensing().hasLineOfSight(target);
        if (sees && distance <= MELEE_START_RANGE) {
            this.getNavigation().stop();
            begin(Clip.MELEE);
            return;
        }
        if (sees && this.rollCooldown <= 0 && distance >= ROLL_MIN_RANGE && distance <= ROLL_MAX_RANGE
            && this.onGround()) {
            this.getNavigation().stop();
            this.rollCooldown = ROLL_COOLDOWN;
            begin(Clip.ROLL_START);
            return;
        }
        if (--this.repath <= 0) {
            this.repath = 4 + this.getRandom().nextInt(4);
            this.getNavigation().moveTo(target, 1.0D);
        }
    }

    private void tickMelee(@Nullable LivingEntity target) {
        int tick = ++this.actionTick;
        this.getNavigation().stop();
        if (target != null && tick <= MELEE_TRACK_UNTIL) {
            face(target, 20.0F);
        }
        if (tick == MELEE_LUNGE_TICK) {
            this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, 0.7F);
            if (target != null && this.distanceTo(target) > 2.0D) {
                lunge(0.45D);
            }
        }
        if (tick == MELEE_HIT_TICK) {
            this.playSound(SoundEvents.GOAT_RAM_IMPACT, 0.7F, 0.8F);
            if (target != null && inReach(target, MELEE_HIT_RANGE, MELEE_CONE)) {
                this.doHurtTarget(target);
            }
        }
        if (tick >= Clip.MELEE.length()) {
            finish();
        }
    }

    private void tickRollStart(@Nullable LivingEntity target) {
        int tick = ++this.actionTick;
        this.getNavigation().stop();
        if (target != null) {
            face(target, 15.0F);
        }
        if (tick >= Clip.ROLL_START.length()) {
            if (target == null) {
                finish();
                return;
            }
            begin(Clip.ROLL);
        }
    }

    private void tickRoll(@Nullable LivingEntity target) {
        int tick = ++this.actionTick;
        if (target == null || tick > Clip.ROLL.length()) {
            this.getNavigation().stop();
            finish();
            return;
        }
        if (tick % 4 == 1 && !this.getNavigation().moveTo(target, ROLL_SPEED)) {
            this.getNavigation().stop();
            finish();
            return;
        }
        if (tick % ROLL_PULSE == 0 && this.level() instanceof ServerLevel level) {
            this.playSound(SoundEvents.ZOGLIN_STEP, 0.8F, 0.7F);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, this.getX(), this.getY() + 0.1D, this.getZ(),
                5, 0.4D, 0.2D, 0.4D, 0.03D);
        }
        List<Player> struck = this.level().getEntitiesOfClass(Player.class,
            this.getBoundingBox().inflate(0.4D), EntitySelector.NO_CREATIVE_OR_SPECTATOR.and(Entity::isAlive));
        if (!struck.isEmpty()) {
            rollInto(struck.get(0));
        }
    }

    private void rollInto(Player victim) {
        float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * ROLL_DAMAGE_SCALE;
        if (victim.hurt(this.damageSources().mobAttack(this), damage)) {
            victim.knockback(1.2D, this.getX() - victim.getX(), this.getZ() - victim.getZ());
            this.doEnchantDamageEffects(this, victim);
        }
        this.getNavigation().stop();
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.0D, 1.0D, 0.0D));
        this.playSound(SoundEvents.IRON_GOLEM_REPAIR, 0.8F, 0.6F);
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SMOKE, this.getX(), this.getY() + 1.0D, this.getZ(),
                20, 0.45D, 0.45D, 0.45D, 0.03D);
        }
        begin(Clip.CRASH);
    }

    private void tickCrash() {
        this.getNavigation().stop();
        if (++this.actionTick >= Clip.CRASH.length()) {
            finish();
        }
    }


    @Override
    protected void playHurtSound(DamageSource source) {
        this.playSound(SoundEvents.TURTLE_HURT, 0.5F, 0.6F);
        this.playSound(SoundEvents.DEEPSLATE_HIT, 1.0F, 0.8F);
    }

    @Override
    protected int deathTicks() {
        return DEATH_TICKS;
    }

    @Override
    protected void deathBeat(ServerLevel level, int tick) {
        switch (tick) {
            case 2 -> breakOff(level, -1.0D, 1.1D, 0.7D, 20, 0.3D);
            case 10 -> breakOff(level, 1.0D, 1.1D, 0.7D, 20, 0.3D);
            case 22 -> breakOff(level, -1.0D, 0.6D, 0.3D, 15, 0.3D);
            case 23 -> breakOff(level, 1.0D, 0.6D, 0.3D, 15, 0.3D);
            case 40 -> breakOff(level, 0.0D, 0.0D, 1.1D, 30, 0.9D);
            default -> {
            }
        }
    }
}
