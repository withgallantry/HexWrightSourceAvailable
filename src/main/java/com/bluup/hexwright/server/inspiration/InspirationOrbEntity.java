package com.bluup.hexwright.server.inspiration;

import com.bluup.hexwright.server.staff_assembly.HexwrightEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class InspirationOrbEntity extends Entity {

    private static final int LIFETIME_TICKS = 6000;

    private static final int PICKUP_DELAY_TICKS = 8;

    private static final double FOLLOW_RANGE = 8.0;

    private static final String TAG_AGE = "Age";

    private int age;

    private @Nullable Player following;

    public InspirationOrbEntity(EntityType<? extends InspirationOrbEntity> type, Level level) {
        super(type, level);
    }

    public InspirationOrbEntity(Level level, Vec3 pos) {
        this(HexwrightEntities.INSPIRATION_ORB, level);
        setPos(pos.x, pos.y, pos.z);
        setDeltaMovement(
            (random.nextDouble() - 0.5) * 0.2,
            random.nextDouble() * 0.2 + 0.1,
            (random.nextDouble() - 0.5) * 0.2);
    }

    public int age() {
        return age;
    }

    @Override
    public void tick() {
        super.tick();

        if (!isNoGravity()) {
            setDeltaMovement(getDeltaMovement().add(0.0, -0.03, 0.0));
        }

        if (tickCount % 20 == 1) {
            following = level().getNearestPlayer(this, FOLLOW_RANGE);
        }
        if (following != null && (following.isRemoved() || following.isSpectator() || following.isDeadOrDying())) {
            following = null;
        }
        if (following != null) {
            Vec3 toward = following.position()
                .add(0.0, following.getBbHeight() * 0.5, 0.0)
                .subtract(position());
            double distance = toward.length();
            if (distance > 0.0 && distance < FOLLOW_RANGE) {
                double pull = 1.0 - distance / FOLLOW_RANGE;
                setDeltaMovement(getDeltaMovement().add(toward.scale(pull * pull * 0.1 / distance)));
            }
        }

        move(MoverType.SELF, getDeltaMovement());

        float drag = 0.98f;
        if (onGround()) {
            BlockPos below = BlockPos.containing(getX(), getY() - 1.0, getZ());
            drag = level().getBlockState(below).getBlock().getFriction() * 0.98f;
        }
        setDeltaMovement(getDeltaMovement().multiply(drag, 0.98, drag));
        if (onGround()) {
            setDeltaMovement(getDeltaMovement().multiply(1.0, -0.9, 1.0));
        }

        age++;
        if (!level().isClientSide && age >= LIFETIME_TICKS) {
            discard();
        }
    }

    @Override
    public void playerTouch(Player player) {
        if (level().isClientSide || age < PICKUP_DELAY_TICKS || player.isSpectator()) {
            return;
        }
        if (player instanceof ServerPlayer collector) {
            Inspiration.collect(collector, this);
            discard();
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt(TAG_AGE, age);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        age = tag.getInt(TAG_AGE);
    }
}
