package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.server.hexpatterns.StoredHex;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.iota.EntityIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import com.bluup.hexwright.server.staff_assembly.AreaCastRangeComponent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class WeaponHexCasting {

    private static final double IMPACT_REACH = 4.0;

    private WeaponHexCasting() {
    }

    public static void castOnTarget(ServerPlayer caster, CompoundTag hex, Entity target, Vec3 impact) {
        cast(caster, hex, List.of(new EntityIota(target)),
            new AABB(impact, impact).inflate(IMPACT_REACH), impact);
    }

    public static void castOnCaught(ServerPlayer caster, CompoundTag hex,
                                    List<? extends Entity> caught, Vec3 impact) {
        List<Iota> targets = new ArrayList<>(caught.size());
        AABB ambit = new AABB(impact, impact);
        for (Entity target : caught) {
            if (!target.isAlive()) {
                continue;
            }
            targets.add(new EntityIota(target));
            ambit = ambit.minmax(target.getBoundingBox());
        }
        if (targets.isEmpty()) {
            return;
        }
        cast(caster, hex, List.of(new ListIota(targets)), ambit.inflate(IMPACT_REACH), impact);
    }

    private static void cast(ServerPlayer caster, CompoundTag hex, List<Iota> seed,
                             AABB ambit, Vec3 fx) {
        ServerLevel level = caster.serverLevel();
        Iota inscribed = IotaType.deserialize(hex.copy(), level);
        List<Iota> patterns = StoredHex.decode(inscribed);
        if (patterns == null || patterns.isEmpty()) {
            return;
        }

        try {
            CastingImage seededImage = new CastingImage().copy(
                seed,
                0,
                List.of(),
                false,
                0L,
                new CompoundTag()
            );

            WeaponHexCastEnv env = new WeaponHexCastEnv(caster, InteractionHand.MAIN_HAND);
            env.addExtension(new AreaCastRangeComponent(ambit));

            CastingVM vm = new CastingVM(seededImage, env);
            vm.queueExecuteAndWrapIotas(new ArrayList<>(patterns), level);
        } catch (RuntimeException ignored) {
        }

        level.playSound(null, fx.x, fx.y, fx.z,
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F, 1.2F);
        level.sendParticles(ParticleTypes.WITCH, fx.x, fx.y, fx.z, 12, 0.3, 0.3, 0.3, 0.05);
    }
}
