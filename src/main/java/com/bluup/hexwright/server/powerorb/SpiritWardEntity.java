package com.bluup.hexwright.server.powerorb;

import com.bluup.hexwright.server.boss.BossDamage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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

public class SpiritWardEntity extends Entity {

    public static final double RADIUS = 6.0D;
    public static final int DURATION = 200;
    public static final int FADE_IN = 10;
    public static final int FADE_OUT = 20;

    private static final int RESISTANCE_AMPLIFIER = 2;
    private static final int SHELTER_INTERVAL = 10;
    private static final int REPEL_INTERVAL = 5;
    private static final double REPEL_HORIZONTAL = 0.9D;
    private static final double REPEL_VERTICAL = 0.25D;
    private static final double HEIGHT = 4.0D;

    @Nullable
    private SpiritGolemEntity golem;

    public SpiritWardEntity(EntityType<? extends SpiritWardEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static void cast(SpiritGolemEntity golem) {
        SpiritWardEntity ward = PowerOrbEntities.SPIRIT_WARD.create(golem.level());
        if (ward == null) {
            return;
        }
        ward.golem = golem;
        ward.moveTo(golem.getX(), golem.getY(), golem.getZ(), 0.0F, 0.0F);
        if (golem.level().addFreshEntity(ward)) {
            golem.level().playSound(null, golem.getX(), golem.getY(), golem.getZ(), SoundEvents.BEACON_POWER_SELECT,
                SoundSource.NEUTRAL, 0.8F, 1.0F);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            if (this.tickCount % 4 == 0 && this.tickCount < DURATION - FADE_OUT) {
                ringParticles();
            }
            return;
        }
        if (this.tickCount >= DURATION) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_DEACTIVATE,
                SoundSource.NEUTRAL, 0.6F, 1.2F);
            this.discard();
            return;
        }
        if (this.golem == null || !this.golem.isAlive()) {
            return;
        }
        if (this.tickCount % SHELTER_INTERVAL == 1) {
            shelter();
        }
        if (this.tickCount % REPEL_INTERVAL == 0) {
            repel();
        }
    }

    private AABB reach() {
        return new AABB(this.getX() - RADIUS, this.getY() - 1.0D, this.getZ() - RADIUS,
            this.getX() + RADIUS, this.getY() + HEIGHT, this.getZ() + RADIUS);
    }

    private boolean inside(Entity entity) {
        double dx = entity.getX() - this.getX();
        double dz = entity.getZ() - this.getZ();
        return dx * dx + dz * dz <= RADIUS * RADIUS;
    }

    private void shelter() {
        SpiritGolemEntity golem = this.golem;
        for (LivingEntity ally : this.level().getEntitiesOfClass(LivingEntity.class, reach(), entity ->
            entity.isAlive() && inside(entity) && (entity instanceof Player player && !player.isSpectator()
                || entity == golem || entity instanceof OwnableEntity owned && golem.getOwnerUUID() != null
                && golem.getOwnerUUID().equals(owned.getOwnerUUID())))) {
            ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, SHELTER_INTERVAL * 4,
                RESISTANCE_AMPLIFIER, true, false, true));
        }
    }

    private void repel() {
        SpiritGolemEntity golem = this.golem;
        Vec3 centre = this.position();
        for (LivingEntity enemy : this.level().getEntitiesOfClass(LivingEntity.class, reach(),
            entity -> inside(entity) && golem.isEnemy(entity))) {
            BossDamage.knock(enemy, enemy.position().subtract(centre), REPEL_HORIZONTAL, REPEL_VERTICAL);
        }
    }

    private void ringParticles() {
        int points = 24;
        float offset = this.tickCount * 0.05F;
        for (int i = 0; i < points; i++) {
            float angle = offset + i * Mth.TWO_PI / points;
            this.level().addParticle(SpiritGolemEntity.dust(),
                this.getX() + Mth.cos(angle) * RADIUS, this.getY() + 0.1D + this.random.nextDouble() * 0.6D,
                this.getZ() + Mth.sin(angle) * RADIUS, 0.0D, 0.02D, 0.0D);
        }
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
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }
}
