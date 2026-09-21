package com.bluup.hexwright.server.mob;

import com.bluup.hexwright.server.boss.BossDamage;
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

public class ServitorConstructEntity extends ConstructEntity {

    public enum Clip implements Action {
        NONE(null, 0),
        MELEE("melee", 34);

        private final RawAnimation animation;
        private final int length;

        Clip(@Nullable String clip, int length) {
            this.length = length;
            this.animation = clip == null ? null : RawAnimation.begin().thenPlay(clip);
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

    private static final double MELEE_START_RANGE = 3.2D;
    private static final double MELEE_HIT_RANGE = 3.6D;
    private static final int MELEE_TRACK_UNTIL = 12;
    private static final int MELEE_LUNGE_TICK = 16;
    private static final int MELEE_HIT_TICK = 18;
    private static final double MELEE_CONE = 0.35D;
    private static final double THROW_HORIZONTAL = 0.9D;
    private static final double THROW_VERTICAL = 0.3D;

    private static final int RECOVER_TICKS = 6;
    private static final int SPLIT_TICK = 31;
    private static final int DEATH_TICKS = 32;

    private static final float PER_HIT_CAP = 18.0F;
    private static final float PER_SECOND_CAP = 36.0F;

    private int recover;
    private int repath;

    public ServitorConstructEntity(EntityType<? extends ServitorConstructEntity> type, Level level) {
        super(type, level, PER_HIT_CAP, PER_SECOND_CAP, 1.3D, 0.6D);
        this.xpReward = 12;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 50.0D)
            .add(Attributes.ARMOR, 8.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.22D)
            .add(Attributes.ATTACK_DAMAGE, 7.0D)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
            .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected Action actionById(int id) {
        return id >= 0 && id < Clip.BY_ID.length ? Clip.BY_ID[id] : Clip.NONE;
    }

    @Override
    protected void combatTick(@Nullable LivingEntity target) {
        if (action() == Clip.MELEE) {
            tickMelee(target);
            return;
        }
        if (target == null) {
            return;
        }
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (this.recover > 0) {
            this.recover--;
            this.getNavigation().stop();
            return;
        }
        if (this.distanceTo(target) <= MELEE_START_RANGE && this.getSensing().hasLineOfSight(target)) {
            this.getNavigation().stop();
            begin(Clip.MELEE);
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
            face(target, 15.0F);
        }
        if (tick == MELEE_LUNGE_TICK) {
            this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, 0.7F);
            if (target != null && this.distanceTo(target) > 2.5D) {
                lunge(0.5D);
            }
        }
        if (tick == MELEE_HIT_TICK) {
            slam();
        }
        if (tick >= Clip.MELEE.length()) {
            end();
            this.recover = RECOVER_TICKS;
        }
    }

    private void slam() {
        this.playSound(SoundEvents.GOAT_RAM_IMPACT, 0.8F, 0.8F);
        float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        for (Player victim : this.level().getEntitiesOfClass(Player.class,
            this.getBoundingBox().inflate(MELEE_HIT_RANGE), EntitySelector.NO_CREATIVE_OR_SPECTATOR.and(Entity::isAlive))) {
            if (!inReach(victim, MELEE_HIT_RANGE, MELEE_CONE)) {
                continue;
            }
            if (victim.hurt(this.damageSources().mobAttack(this), damage)) {
                this.doEnchantDamageEffects(this, victim);
                BossDamage.knock(victim, victim.position().subtract(this.position()), THROW_HORIZONTAL, THROW_VERTICAL);
            }
        }
    }


    @Override
    protected void playHurtSound(DamageSource source) {
        this.playSound(SoundEvents.TURTLE_HURT, 0.5F, 0.8F);
        this.playSound(SoundEvents.DEEPSLATE_HIT, 1.0F, 0.9F);
    }

    @Override
    protected int deathTicks() {
        return DEATH_TICKS;
    }

    @Override
    protected void deathBeat(ServerLevel level, int tick) {
        switch (tick) {
            case 1 -> {
                for (double height : new double[] {0.45D, 0.95D, 1.5D, 2.1D}) {
                    breakOff(level, 0.0D, 0.0D, height, 10, 0.3D);
                }
                this.playSound(SoundEvents.IRON_GOLEM_REPAIR, 1.0F, 0.6F);
            }
            case 6 -> landed(level);
            case 10 -> landed(level);
            case 11 -> landed(level);
            case 15 -> this.playSound(SoundEvents.DEEPSLATE_HIT, 1.0F, 0.7F + this.random.nextFloat() * 0.2F);
            case SPLIT_TICK -> FracturedConstructEntity.splitFrom(this, level);
            default -> {
            }
        }
    }

    private void landed(ServerLevel level) {
        this.playSound(SoundEvents.DEEPSLATE_HIT, 1.0F, 0.7F + this.random.nextFloat() * 0.2F);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, this.getX(), this.getY() + 0.1D, this.getZ(),
            3, 0.5D, 0.1D, 0.5D, 0.01D);
    }
}
