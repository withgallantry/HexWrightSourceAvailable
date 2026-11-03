package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.inits.HexwrightNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class GroundSlam {

    public static final float NETHERITE_RADIUS = 4.0F;

    public static final int NETHERITE_COOLDOWN_TICKS = 60;

    public static final float RISE_TICKS = 10.0F;

    public static final float SPEED_TICKS_PER_BLOCK = 2.0F;

    public static final float MAX_HEIGHT = 0.55F;

    public static final float EDGE_FALLOFF = 0.45F;

    private static final float DAMAGE = 6.0F;

    private static final double KNOCKBACK_HORIZONTAL = 0.85D;

    private static final double KNOCKBACK_VERTICAL = 0.55D;

    private static final double CATCH_BELOW = 1.5D;
    private static final double CATCH_ABOVE = 3.0D;

    private GroundSlam() {
    }

    public static List<LivingEntity> slam(ServerLevel level, @Nullable Player source, BlockPos impact, float radius) {
        Vec3 centre = centreOf(impact);

        List<LivingEntity> caught = shove(level, source, centre, radius);

        level.playSound(null, centre.x, centre.y, centre.z,
            SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.7F, 0.55F);
        level.playSound(null, centre.x, centre.y, centre.z,
            SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.5F, 0.6F);

        HexwrightNetworking.sendGroundSlam(level, impact, radius);
        return caught;
    }

    public static Vec3 centreOf(BlockPos impact) {
        return new Vec3(impact.getX() + 0.5D, impact.getY() + 1.0D, impact.getZ() + 0.5D);
    }

    private static List<LivingEntity> shove(ServerLevel level, @Nullable Player source, Vec3 centre, float radius) {
        AABB caught = new AABB(
            centre.x - radius, centre.y - CATCH_BELOW, centre.z - radius,
            centre.x + radius, centre.y + CATCH_ABOVE, centre.z + radius
        );
        DamageSource damage = source == null
            ? level.damageSources().generic()
            : level.damageSources().playerAttack(source);

        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, caught,
            victim -> victim.isAlive() && victim != source && !victim.isSpectator());
        List<LivingEntity> hit = new ArrayList<>(victims.size());
        for (LivingEntity victim : victims) {
            double dx = victim.getX() - centre.x;
            double dz = victim.getZ() - centre.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > radius) {
                continue;
            }

            hit.add(victim);
            double falloff = 1.0D - (distance / radius);
            victim.hurt(damage, (float) (DAMAGE * falloff));

            double outX = distance < 1.0E-4D ? 0.0D : dx / distance;
            double outZ = distance < 1.0E-4D ? 0.0D : dz / distance;
            victim.push(
                outX * KNOCKBACK_HORIZONTAL * falloff,
                KNOCKBACK_VERTICAL * falloff,
                outZ * KNOCKBACK_HORIZONTAL * falloff
            );
            syncVelocity(victim);
        }
        return hit;
    }

    private static void syncVelocity(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(player));
        } else {
            entity.hurtMarked = true;
        }
    }
}
