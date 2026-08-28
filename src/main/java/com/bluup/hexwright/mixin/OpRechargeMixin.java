package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.RenderedSpell;
import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.Vec3Iota;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.common.casting.actions.spells.OpRecharge;
import com.bluup.hexwright.server.block.WardingBoxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(OpRecharge.class)
public abstract class OpRechargeMixin {

    @Inject(
        method = "execute(Ljava/util/List;Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;)Lat/petrak/hexcasting/api/casting/castables/SpellAction$Result;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void hexwright$rechargeWardingBox(List<? extends Iota> args, CastingEnvironment env,
                                              CallbackInfoReturnable<SpellAction.Result> cir) {
        if (args.isEmpty() || !(args.get(0) instanceof Vec3Iota vecIota)) {
            return;
        }
        Vec3 vec = vecIota.getVec3();
        BlockPos pos = BlockPos.containing(vec);
        if (!(env.getWorld().getBlockEntity(pos) instanceof WardingBoxBlockEntity box)) {
            return;
        }

        env.assertVecInRange(vec);

        cir.setReturnValue(new SpellAction.Result(new RenderedSpell() {
            @Override
            public void cast(CastingEnvironment castEnv) {
                box.drinkAreaMedia(castEnv.getWorld());
            }

            @Override
            public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                cast(castEnv);
                return image;
            }
        }, MediaConstants.SHARD_UNIT, List.of(ParticleSpray.burst(Vec3.atCenterOf(pos), 0.5, 10)), 0L));
    }
}
