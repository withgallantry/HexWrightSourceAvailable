package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.boss.Bosses;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class PestilentSlash {

    private static final double HIT_FORWARD = 3.0D;
    private static final double HIT_RADIUS = 2.0D;
    private static final double HIT_HEIGHT = 1.0D;

    public static final float DAMAGE = 8.0F;

    public static final int AFFLICTION_TICKS = 120;

    private static final double SLASH_FORWARD = 2.1D;
    private static final double SLASH_HEIGHT = 1.0D;

    private static final DustParticleOptions SPORE = new DustParticleOptions(new Vector3f(0.66F, 0.85F, 0.23F), 1.2F);

    private PestilentSlash() {
    }

    public static boolean release(ServerLevel level, ServerPlayer wielder, ItemStack blade) {
        if (wielder.isRemoved() || !wielder.isAlive() || wielder.level() != level) {
            return false;
        }
        float yaw = wielder.getYRot();
        float yawRad = yaw * Mth.DEG_TO_RAD;
        Vec3 heading = new Vec3(-Mth.sin(yawRad), 0.0D, Mth.cos(yawRad));
        Vec3 origin = wielder.position();
        Vec3 centre = origin.add(heading.scale(HIT_FORWARD));

        HexwrightNetworking.sendPestilentSlashVfx(level,
            origin.add(heading.scale(SLASH_FORWARD)).add(0.0D, SLASH_HEIGHT, 0.0D), yaw);
        level.playSound(null, origin.x, origin.y, origin.z, PestiferousSounds.SLASH.get(), SoundSource.PLAYERS,
            1.0F, 0.9F + level.getRandom().nextFloat() * 0.2F);

        List<LivingEntity> caught = level.getEntitiesOfClass(LivingEntity.class,
            new AABB(centre, centre).inflate(HIT_RADIUS + 1.0D, HIT_HEIGHT, HIT_RADIUS + 1.0D),
            victim -> TeleportStrike.strikeable(wielder, victim) && within(victim, centre));

        DamageSource source = level.damageSources().playerAttack(wielder);
        List<LivingEntity> struck = new ArrayList<>();
        for (LivingEntity victim : caught) {
            victim.invulnerableTime = 0;
            if (!victim.hurt(source, Bosses.sparedDamage(victim, DAMAGE))) {
                continue;
            }
            struck.add(victim);
            MobEffectInstance sickness = PestiferousItem.carried(blade, AFFLICTION_TICKS);
            if (sickness != null && !Bosses.is(victim)) {
                victim.addEffect(sickness, wielder);
            }
            Vec3 hit = victim.getBoundingBox().getCenter();
            level.sendParticles(SPORE, hit.x, hit.y, hit.z, 12, victim.getBbWidth() * 0.4D,
                victim.getBbHeight() * 0.3D, victim.getBbWidth() * 0.4D, 0.0D);
        }

        HexwrightDebug.log(HexwrightDebug.WEAPON, "[weapon] pestilent slash from {}: {} caught, {} struck",
            wielder.getScoreboardName(), caught.size(), struck.size());
        return !struck.isEmpty();
    }

    private static boolean within(LivingEntity victim, Vec3 centre) {
        double reach = HIT_RADIUS + victim.getBbWidth() / 2.0D;
        double dx = victim.getX() - centre.x;
        double dz = victim.getZ() - centre.z;
        return dx * dx + dz * dz <= reach * reach && Math.abs(victim.getY() - centre.y) <= HIT_HEIGHT;
    }
}
