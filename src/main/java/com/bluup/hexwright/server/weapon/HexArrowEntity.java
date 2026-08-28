package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.server.staff_assembly.HexwrightEntities;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

public class HexArrowEntity extends Arrow {

    private static final String TAG_HEX = "HexwrightHex";

    private static final ParticleOptions TRAIL = ParticleTypes.WITCH;
    private static final int TRAIL_EVERY_TICKS = 2;

    private CompoundTag hex = new CompoundTag();

    private boolean spent;

    public HexArrowEntity(EntityType<? extends HexArrowEntity> type, Level level) {
        super(type, level);
    }

    public HexArrowEntity(Level level, LivingEntity shooter) {
        super(HexwrightEntities.HEX_ARROW, level);
        this.setOwner(shooter);
        this.setPos(shooter.getX(), shooter.getEyeY() - 0.1, shooter.getZ());
        if (shooter instanceof net.minecraft.world.entity.player.Player) {
            this.pickup = Pickup.ALLOWED;
        }
    }

    public void setHex(CompoundTag hex) {
        this.hex = hex;
    }

    public boolean isArmed() {
        return !this.hex.isEmpty() && !this.spent;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel serverLevel && isArmed() && !this.inGround
            && this.tickCount % TRAIL_EVERY_TICKS == 0) {
            serverLevel.sendParticles(TRAIL, this.getX(), this.getY(), this.getZ(),
                1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (this.level().isClientSide || this.spent || this.hex.isEmpty()) {
            return;
        }
        this.spent = true;
        if (this.getOwner() instanceof ServerPlayer archer && archer.level() == this.level()) {
            WeaponHexCasting.castOnTarget(archer, this.hex, result.getEntity(), result.getLocation());
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (!this.hex.isEmpty()) {
            tag.put(TAG_HEX, this.hex);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.hex = tag.contains(TAG_HEX, Tag.TAG_COMPOUND) ? tag.getCompound(TAG_HEX) : new CompoundTag();
    }
}
