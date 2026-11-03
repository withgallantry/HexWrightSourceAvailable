package com.bluup.hexwright.server.staff_assembly;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.item.HexwrightItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class StaffCoreBoltEntity extends ThrowableItemProjectile {
    private static final int MAX_LIFE_TICKS = 60;
    private static final String TAG_BOUND_HEX = "BoundHex";
    private static final String TAG_BOUND_PATTERNS = "BoundPatterns";
    private static final String TAG_IMPACT_AMBIT = "ImpactAmbit";

    private @Nullable CompoundTag boundHex;
    private double impactAmbit = StaffCoreData.ECHO_MIN_IMPACT_AMBIT;

    public StaffCoreBoltEntity(EntityType<? extends StaffCoreBoltEntity> type, Level level) {
        super(type, level);
    }

    public StaffCoreBoltEntity(Level level, LivingEntity owner, @Nullable CompoundTag boundHex, double impactAmbit) {
        super(HexwrightEntities.STAFF_CORE_BOLT, owner, level);
        this.boundHex = boundHex == null ? null : boundHex.copy();
        this.impactAmbit = impactAmbit;
    }

    @Override
    protected Item getDefaultItem() {
        return HexwrightItems.ECHO_CORE;
    }

    @Override
    protected float getGravity() {
        return 0.0f;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.tickCount > MAX_LIFE_TICKS) {
            this.discard();
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.isAlive()) {
            return;
        }
        if (!this.level().isClientSide && this.getOwner() instanceof ServerPlayer caster) {
            castAtImpact(caster, result.getLocation());
        }
        this.discard();
    }

    private void castAtImpact(ServerPlayer caster, Vec3 impact) {
        ServerLevel level = caster.serverLevel();
        AABB scanBox = new AABB(impact, impact).inflate(impactAmbit);
        ListIota payload = StaffPowers.buildImpactPayload(level, impact, scanBox, List.of(this, caster));
        StaffPowers.executeWithSeed(caster, StaffAssemblyData.boundHexFromTag(boundHex, level), payload, scanBox);

        double spread = impactAmbit * 0.45;
        int particleCount = (int) Math.round(16.0 * impactAmbit / StaffCoreData.ECHO_MIN_IMPACT_AMBIT);
        level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.5f, 1.4f);
        level.sendParticles(ParticleTypes.WITCH, impact.x, impact.y, impact.z, particleCount, spread, spread, spread, 0.02);
        HexwrightNetworking.sendProjectileHit(level, impact);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (boundHex != null) {
            tag.put(TAG_BOUND_HEX, boundHex.copy());
        }
        tag.putDouble(TAG_IMPACT_AMBIT, impactAmbit);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_BOUND_HEX, Tag.TAG_COMPOUND)) {
            this.boundHex = tag.getCompound(TAG_BOUND_HEX).copy();
        } else if (tag.contains(TAG_BOUND_PATTERNS, Tag.TAG_LIST)) {
            List<Iota> patterns = new ArrayList<>();
            for (Tag entry : tag.getList(TAG_BOUND_PATTERNS, Tag.TAG_COMPOUND)) {
                if (entry instanceof CompoundTag patternTag && HexPattern.isPattern(patternTag)) {
                    patterns.add(new PatternIota(HexPattern.fromNBT(patternTag)));
                }
            }
            this.boundHex = patterns.isEmpty() ? null : IotaType.serialize(new ListIota(patterns));
        }
        if (tag.contains(TAG_IMPACT_AMBIT, Tag.TAG_DOUBLE)) {
            this.impactAmbit = tag.getDouble(TAG_IMPACT_AMBIT);
        }
    }
}
