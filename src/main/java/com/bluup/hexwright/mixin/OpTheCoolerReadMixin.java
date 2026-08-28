package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.Vec3Iota;
import at.petrak.hexcasting.common.casting.actions.rw.OpTheCoolerRead;
import com.bluup.hexwright.server.block.ResonanceTowerBlockEntity;
import com.bluup.hexwright.server.block.ResonantAnchorBlockEntity;
import com.bluup.hexwright.server.network.ResonanceIota;
import com.bluup.hexwright.server.network.ResonanceNames;
import com.bluup.hexwright.server.network.ResonantAttunement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = OpTheCoolerRead.class, priority = 500)
public abstract class OpTheCoolerReadMixin {

    @Inject(
        method = "execute(Ljava/util/List;Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;)Ljava/util/List;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void hexwright$readTowerNetwork(List<? extends Iota> args, CastingEnvironment env,
                                            CallbackInfoReturnable<List<Iota>> cir) {
        if (args.isEmpty() || !(args.get(0) instanceof Vec3Iota vecIota)) {
            return;
        }
        Vec3 vec = vecIota.getVec3();
        BlockPos pos = BlockPos.containing(vec);
        if (!(env.getWorld().getBlockEntity(pos) instanceof ResonanceTowerBlockEntity)) {
            return;
        }

        env.assertVecInRange(vec);

        String networkKey = ResonantAttunement.networkKey(env.getWorld(), pos);
        ResonanceNames.nameOrAssign(env.getWorld().getServer(), networkKey);
        cir.setReturnValue(List.of(new ResonanceIota(networkKey)));
    }

    @Inject(
        method = "execute(Ljava/util/List;Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;)Ljava/util/List;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void hexwright$readAnchor(List<? extends Iota> args, CastingEnvironment env,
                                      CallbackInfoReturnable<List<Iota>> cir) {
        if (args.isEmpty() || !(args.get(0) instanceof Vec3Iota vecIota)) {
            return;
        }
        Vec3 vec = vecIota.getVec3();
        BlockPos pos = BlockPos.containing(vec);
        if (!(env.getWorld().getBlockEntity(pos) instanceof ResonantAnchorBlockEntity)) {
            return;
        }

        env.assertVecInRange(vec);

        Vec3 landing = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        cir.setReturnValue(List.of(new Vec3Iota(landing)));
    }
}
