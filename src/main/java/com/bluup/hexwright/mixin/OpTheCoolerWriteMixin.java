package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.RenderedSpell;
import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.Vec3Iota;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.casting.mishaps.MishapOthersName;
import at.petrak.hexcasting.common.casting.actions.rw.OpTheCoolerWrite;
import com.bluup.hexwright.server.block.ResonanceTowerBlockEntity;
import com.bluup.hexwright.server.block.ResonantAnchorBlockEntity;
import com.bluup.hexwright.server.block.WardingBoxBlockEntity;
import com.bluup.hexwright.server.network.ResonanceNames;
import com.bluup.hexwright.server.network.ResonantAttunement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import ram.talia.moreiotas.api.casting.iota.StringIota;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = OpTheCoolerWrite.class, priority = 500)
public abstract class OpTheCoolerWriteMixin {

    @Inject(
        method = "execute(Ljava/util/List;Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;)Lat/petrak/hexcasting/api/casting/castables/SpellAction$Result;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void hexwright$writeToWardingBox(List<? extends Iota> args, CastingEnvironment env,
                                             CallbackInfoReturnable<SpellAction.Result> cir) {
        if (args.size() < 2 || !(args.get(0) instanceof Vec3Iota vecIota)) {
            return;
        }
        Vec3 vec = vecIota.getVec3();
        BlockPos pos = BlockPos.containing(vec);
        if (!(env.getWorld().getBlockEntity(pos) instanceof WardingBoxBlockEntity box)) {
            return;
        }

        env.assertVecInRange(vec);

        Iota datum = args.get(1);
        Player trueName = MishapOthersName.getTrueNameFromDatum(datum, null);
        if (trueName != null) {
            throw new MishapOthersName(trueName);
        }

        cir.setReturnValue(new SpellAction.Result(new RenderedSpell() {
            @Override
            public void cast(CastingEnvironment castEnv) {
                box.writeSpell(datum);
            }

            @Override
            public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                cast(castEnv);
                return image;
            }
        }, 0L, List.of(ParticleSpray.burst(Vec3.atCenterOf(pos), 0.5, 10)), 0L));
    }

    @Inject(
        method = "execute(Ljava/util/List;Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;)Lat/petrak/hexcasting/api/casting/castables/SpellAction$Result;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void hexwright$nameTowerNetwork(List<? extends Iota> args, CastingEnvironment env,
                                            CallbackInfoReturnable<SpellAction.Result> cir) {
        if (args.size() < 2 || !(args.get(0) instanceof Vec3Iota vecIota)) {
            return;
        }
        Vec3 vec = vecIota.getVec3();
        BlockPos pos = BlockPos.containing(vec);
        if (!(env.getWorld().getBlockEntity(pos) instanceof ResonanceTowerBlockEntity)) {
            return;
        }

        env.assertVecInRange(vec);

        Iota datum = args.get(1);
        String name = datum instanceof StringIota stringIota ? ResonanceNames.normalize(stringIota.getString()) : null;
        if (name == null) {
            throw MishapInvalidIota.ofType(datum, 0, "hexwright.network_name");
        }

        MinecraftServer server = env.getWorld().getServer();
        String networkKey = ResonantAttunement.networkKey(env.getWorld(), pos);

        cir.setReturnValue(new SpellAction.Result(new RenderedSpell() {
            @Override
            public void cast(CastingEnvironment castEnv) {
                ResonanceNames.rename(server, networkKey, name);
            }

            @Override
            public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                cast(castEnv);
                return image;
            }
        }, 0L, List.of(ParticleSpray.burst(Vec3.atCenterOf(pos), 0.5, 10)), 0L));
    }

    @Inject(
        method = "execute(Ljava/util/List;Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;)Lat/petrak/hexcasting/api/casting/castables/SpellAction$Result;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void hexwright$tuneAnchor(List<? extends Iota> args, CastingEnvironment env,
                                      CallbackInfoReturnable<SpellAction.Result> cir) {
        if (args.size() < 2 || !(args.get(0) instanceof Vec3Iota vecIota)) {
            return;
        }
        Vec3 vec = vecIota.getVec3();
        BlockPos pos = BlockPos.containing(vec);
        if (!(env.getWorld().getBlockEntity(pos) instanceof ResonantAnchorBlockEntity anchor)) {
            return;
        }

        env.assertVecInRange(vec);

        Iota datum = args.get(1);
        String key = ResonantAnchorBlockEntity.keyOf(datum);
        if (key == null) {
            throw MishapInvalidIota.ofType(datum, 0, "hexwright.anchor_key");
        }

        cir.setReturnValue(new SpellAction.Result(new RenderedSpell() {
            @Override
            public void cast(CastingEnvironment castEnv) {
                anchor.attune(key);
            }

            @Override
            public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                cast(castEnv);
                return image;
            }
        }, 0L, List.of(ParticleSpray.burst(Vec3.atCenterOf(pos), 0.5, 10)), 0L));
    }
}
