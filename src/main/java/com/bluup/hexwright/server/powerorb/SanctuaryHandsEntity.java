package com.bluup.hexwright.server.powerorb;

import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

public class SanctuaryHandsEntity extends Entity implements GeoEntity {

    public static final int LIFETIME = 124;
    public static final double RADIUS = 4.0D;

    private static final int PERIOD = 5;
    private static final int PULSE_FROM = 10;
    private static final int PULSE_TO = 115;
    private static final int HEAL_SOUND_AT = 15;
    private static final int SPARKLE_INTERVAL = 10;

    private static final double HEIGHT = 6.0D;
    private static final double LIFT_TO = 3.0D;
    private static final float HEAL = 1.0F;
    private static final int LEVITATION_AMPLIFIER = 4;
    private static final int RESISTANCE_AMPLIFIER = 4;
    private static final int HOVER_TICKS = 30;
    private static final int LET_DOWN_TICKS = 80;

    private static final Vector3f SPARKLE_FROM = new Vector3f(1.0F, 0.976F, 0.765F);
    private static final Vector3f SPARKLE_TO = new Vector3f(0.682F, 1.0F, 0.169F);

    private static final RawAnimation EMBRACE = RawAnimation.begin().thenPlayAndHold("animation");

    private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);

    @Nullable
    private UUID casterId;

    public SanctuaryHandsEntity(EntityType<? extends SanctuaryHandsEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public SanctuaryHandsEntity(Level level, Vec3 at, ServerPlayer caster) {
        this(PowerOrbEntities.SANCTUARY_HANDS, level);
        this.casterId = caster.getUUID();
        this.moveTo(at.x, at.y, at.z, caster.getYRot(), 0.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (this.tickCount >= LIFETIME) {
            letDown();
            this.discard();
            return;
        }
        if (this.tickCount == HEAL_SOUND_AT) {
            level.playSound(null, getX(), getY(), getZ(), PowerOrbSounds.SANCTUARY_HEAL.get(), SoundSource.PLAYERS,
                1.0F, 0.9F + this.random.nextFloat() * 0.2F);
        }
        if (this.tickCount >= PULSE_FROM && this.tickCount <= PULSE_TO && this.tickCount % PERIOD == 0) {
            boolean sparkle = this.tickCount % SPARKLE_INTERVAL == 0;
            for (LivingEntity held : held()) {
                embrace(level, held, sparkle);
            }
        }
    }

    private void embrace(ServerLevel level, LivingEntity held, boolean sparkle) {
        if (held.getY() - this.getY() < LIFT_TO) {
            held.addEffect(new MobEffectInstance(MobEffects.LEVITATION, PERIOD + 2, LEVITATION_AMPLIFIER,
                true, false, false));
        }
        held.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, HOVER_TICKS, 0, true, false, false));
        held.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, PERIOD * 2, RESISTANCE_AMPLIFIER,
            true, false, true));
        held.heal(HEAL);
        if (sparkle) {
            level.sendParticles(new DustColorTransitionOptions(SPARKLE_FROM, SPARKLE_TO, 0.9F),
                held.getX(), held.getY() + held.getBbHeight() * 0.5D, held.getZ(), 12,
                0.4D, held.getBbHeight() * 0.35D, 0.4D, 0.0D);
            level.playSound(null, held.getX(), held.getY(), held.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.6F, 1.1F + this.random.nextFloat() * 0.1F);
        }
    }

    private void letDown() {
        for (LivingEntity held : held()) {
            if (!held.onGround()) {
                held.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, LET_DOWN_TICKS, 0, true, false, true));
            }
        }
    }

    private Iterable<LivingEntity> held() {
        AABB reach = new AABB(getX() - RADIUS, getY() - 1.0D, getZ() - RADIUS,
            getX() + RADIUS, getY() + HEIGHT, getZ() + RADIUS);
        return this.level().getEntitiesOfClass(LivingEntity.class, reach, entity -> entity.isAlive()
            && inside(entity) && isFriend(entity));
    }

    private boolean inside(Entity entity) {
        double dx = entity.getX() - getX();
        double dz = entity.getZ() - getZ();
        return dx * dx + dz * dz <= RADIUS * RADIUS;
    }

    private boolean isFriend(LivingEntity entity) {
        if (entity instanceof Player player) {
            return !player.isSpectator();
        }
        return this.casterId != null && entity instanceof OwnableEntity owned
            && this.casterId.equals(owned.getOwnerUUID());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "embrace", 0,
            state -> state.setAndContinue(EMBRACE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animatableCache;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity other) {
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }
}
