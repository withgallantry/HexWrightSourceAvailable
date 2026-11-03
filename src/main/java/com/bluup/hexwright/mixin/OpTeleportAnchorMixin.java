package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.RenderedSpell;
import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.Vec3Iota;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadLocation;
import at.petrak.hexcasting.api.casting.mishaps.MishapImmuneEntity;
import at.petrak.hexcasting.api.mod.HexConfig;
import at.petrak.hexcasting.api.mod.HexTags;
import at.petrak.hexcasting.common.casting.actions.spells.great.OpTeleport;
import com.bluup.hexwright.server.block.ResonantAnchorBlockEntity;
import com.bluup.hexwright.server.staff_assembly.StaffCorePatternPower;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

import static at.petrak.hexcasting.api.casting.OperatorUtils.getEntity;

@Mixin(OpTeleport.class)
public abstract class OpTeleportAnchorMixin {

    @Inject(
        method = "execute(Ljava/util/List;Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;)Lat/petrak/hexcasting/api/casting/castables/SpellAction$Result;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void hexwright$anchorTeleport(List<? extends Iota> args, CastingEnvironment env,
                                          CallbackInfoReturnable<SpellAction.Result> cir) {
        if (args.size() < 2 || !(args.get(1) instanceof Vec3Iota vecIota)) {
            return;
        }

        Entity teleportee = getEntity(args, 0, 2);
        Vec3 rawTarget = teleportee.position().add(vecIota.getVec3());

        BlockPos aimedBlock = BlockPos.containing(rawTarget);
        BlockPos anchorPos;
        if (env.getWorld().getBlockEntity(aimedBlock.below()) instanceof ResonantAnchorBlockEntity) {
            anchorPos = aimedBlock.below();
        } else if (env.getWorld().getBlockEntity(aimedBlock) instanceof ResonantAnchorBlockEntity) {
            anchorPos = aimedBlock;
        } else {
            return;
        }

        Vec3 target = new Vec3(anchorPos.getX() + 0.5, anchorPos.getY() + 1.0, anchorPos.getZ() + 0.5);
        Vec3 offset = target.subtract(teleportee.position());

        env.assertEntityInRange(teleportee);

        if (teleportee.getType().is(HexTags.Entities.CANNOT_TELEPORT)) {
            throw new MishapImmuneEntity(teleportee);
        }
        if (teleportee.getType().is(HexTags.Entities.STICKY_TELEPORTERS)) {
            for (Entity passenger : teleportee.getPassengers()) {
                if (passenger.getType().is(HexTags.Entities.CANNOT_TELEPORT)) {
                    throw new MishapImmuneEntity(passenger);
                }
            }
        }

        if (!HexConfig.server().canTeleportInThisDimension(env.getWorld().dimension())) {
            throw new MishapBadLocation(target, "bad_dimension");
        }
        env.assertVecInWorld(target);
        if (!env.isVecInWorld(target.subtract(0.0, 1.0, 0.0))) {
            throw new MishapBadLocation(target, "too_close_to_out");
        }

        Vec3 targetMiddlePos = teleportee.position().add(0.0, teleportee.getEyeHeight() / 2.0, 0.0);

        cir.setReturnValue(new SpellAction.Result(
            new RenderedSpell() {
                @Override
                public void cast(CastingEnvironment castEnv) {
                    ServerLevel world = castEnv.getWorld();
                    OpTeleport.INSTANCE.teleportRespectSticky(teleportee, offset, world);
                }

                @Override
                public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                    cast(castEnv);
                    return image;
                }
            },
            0L,
            List.of(ParticleSpray.cloud(targetMiddlePos, 2.0, 20), ParticleSpray.burst(target, 2.0, 20)),
            1L
        ));
    }

    @ModifyReturnValue(
        method = "execute(Ljava/util/List;Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;)Lat/petrak/hexcasting/api/casting/castables/SpellAction$Result;",
        at = @At("RETURN"),
        remap = false
    )
    private SpellAction.Result hexwright$travellerDiscount(SpellAction.Result original,
                                                           List<? extends Iota> args, CastingEnvironment env) {
        if (args.size() < 2) {
            return original;
        }
        return StaffCorePatternPower.discountGreaterTeleport(env, original, getEntity(args, 0, 2));
    }
}
