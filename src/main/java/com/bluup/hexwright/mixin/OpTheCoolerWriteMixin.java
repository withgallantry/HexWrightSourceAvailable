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
import com.bluup.hexwright.server.block.PlacedBottleBlockEntity;
import com.bluup.hexwright.server.block.ResonanceTowerBlockEntity;
import com.bluup.hexwright.server.block.ResonantAnchorBlockEntity;
import com.bluup.hexwright.server.block.WardingBoxBlockEntity;
import com.bluup.hexwright.common.remnant.Remnant;
import com.bluup.hexwright.server.fluid.HexidTankBlockEntity;
import com.bluup.hexwright.server.fluid.TankRemnants;
import com.bluup.hexwright.server.network.ResonanceNames;
import com.bluup.hexwright.server.network.ResonantAttunement;
import com.bluup.hexwright.server.remnant.BottleData;
import com.bluup.hexwright.server.remnant.RemnantIota;
import com.bluup.hexwright.server.remnant.RemnantDrawState;
import com.bluup.hexwright.server.remnant.RemnantVessels;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import ram.talia.moreiotas.api.casting.iota.StringIota;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.UUID;

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
        if (args.get(1) instanceof RemnantIota) {
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

    @Inject(
        method = "execute(Ljava/util/List;Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;)Lat/petrak/hexcasting/api/casting/castables/SpellAction$Result;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void hexwright$pourRemnantIntoVessel(List<? extends Iota> args, CastingEnvironment env,
                                                 CallbackInfoReturnable<SpellAction.Result> cir) {
        if (args.size() < 2 || !(args.get(0) instanceof Vec3Iota vecIota)
            || !(args.get(1) instanceof RemnantIota remnantIota)) {
            return;
        }

        Vec3 vec = vecIota.getVec3();
        BlockPos pos = BlockPos.containing(vec);
        BlockEntity target = env.getWorld().getBlockEntity(pos);
        if (!RemnantVessels.isVessel(target)) {
            return;
        }

        env.assertVecInRange(vec);

        UUID draught = remnantIota.getDraught();
        MinecraftServer server = env.getWorld().getServer();
        RemnantDrawState ledger = RemnantDrawState.get(server);
        Remnant worth = draught == null ? null : ledger.peek(draught, server.overworld().getGameTime());
        if (worth == null) {
            throw MishapInvalidIota.ofType(args.get(1), 0, "hexwright.live_remnant");
        }
        if (!pour(target, worth, true)) {
            throw MishapInvalidIota.ofType(args.get(1), 0, "hexwright.remnant_vessel_room");
        }

        cir.setReturnValue(new SpellAction.Result(new RenderedSpell() {
            @Override
            public void cast(CastingEnvironment castEnv) {
                Remnant now = ledger.redeem(draught, server.overworld().getGameTime());
                if (now != null) {
                    pour(target, now, false);
                }
            }

            @Override
            public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                cast(castEnv);
                return image;
            }
        }, 0L, List.of(ParticleSpray.burst(Vec3.atCenterOf(pos), 0.5, 10)), 0L));
    }

    @Unique
    private static boolean pour(BlockEntity vessel, Remnant remnant, boolean simulate) {
        if (vessel instanceof HexidTankBlockEntity tank) {
            if (simulate) {
                return tank.canAcceptRemnants(remnant.type())
                    && tank.remnantHeadroom() >= TankRemnants.MIN_DRAMS;
            }
            return tank.addRemnant(remnant) > 0.0;
        }
        if (vessel instanceof PlacedBottleBlockEntity placed) {
            ItemStack bottle = placed.getBottle();
            if (bottle.isEmpty()) {
                return false;
            }
            if (simulate) {
                return BottleData.pour(bottle.copy(), remnant) > 0.0;
            }
            ItemStack poured = bottle.copy();
            if (BottleData.pour(poured, remnant) <= 0.0) {
                return false;
            }
            placed.setBottle(poured);
            return true;
        }
        return false;
    }
}
