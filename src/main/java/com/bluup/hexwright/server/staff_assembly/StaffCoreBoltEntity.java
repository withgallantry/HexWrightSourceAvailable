package com.bluup.hexwright.server.staff_assembly;

import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.item.HexwrightItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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

import java.util.ArrayList;
import java.util.List;

public class StaffCoreBoltEntity extends ThrowableItemProjectile {
    private static final int MAX_LIFE_TICKS = 60;
    private static final String TAG_BOUND_PATTERNS = "BoundPatterns";
    private static final String TAG_IMPACT_AMBIT = "ImpactAmbit";

    private List<HexPattern> boundPatterns = List.of();
    private double impactAmbit = StaffCoreData.ECHO_MIN_IMPACT_AMBIT;

    public StaffCoreBoltEntity(EntityType<? extends StaffCoreBoltEntity> type, Level level) {
        super(type, level);
    }

    public StaffCoreBoltEntity(Level level, LivingEntity owner, List<HexPattern> boundPatterns, double impactAmbit) {
        super(HexwrightEntities.STAFF_CORE_BOLT, owner, level);
        this.boundPatterns = boundPatterns;
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
        StaffPowers.executeWithSeed(caster, boundPatterns, payload, scanBox);

        double spread = impactAmbit * 0.45;
        int particleCount = (int) Math.round(16.0 * impactAmbit / StaffCoreData.ECHO_MIN_IMPACT_AMBIT);
        level.playSound(null, impact.x, impact.y, impact.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.5f, 1.4f);
        level.sendParticles(ParticleTypes.WITCH, impact.x, impact.y, impact.z, particleCount, spread, spread, spread, 0.02);
        HexwrightNetworking.sendProjectileHit(level, impact);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ListTag patternsTag = new ListTag();
        for (HexPattern pattern : boundPatterns) {
            patternsTag.add(pattern.serializeToNBT());
        }
        tag.put(TAG_BOUND_PATTERNS, patternsTag);
        tag.putDouble(TAG_IMPACT_AMBIT, impactAmbit);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        List<HexPattern> loaded = new ArrayList<>();
        if (tag.contains(TAG_BOUND_PATTERNS, Tag.TAG_LIST)) {
            for (Tag entry : tag.getList(TAG_BOUND_PATTERNS, Tag.TAG_COMPOUND)) {
                if (entry instanceof CompoundTag patternTag && HexPattern.isPattern(patternTag)) {
                    loaded.add(HexPattern.fromNBT(patternTag));
                }
            }
        }
        this.boundPatterns = loaded;
        if (tag.contains(TAG_IMPACT_AMBIT, Tag.TAG_DOUBLE)) {
            this.impactAmbit = tag.getDouble(TAG_IMPACT_AMBIT);
        }
    }
}
