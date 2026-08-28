package com.bluup.hexwright.server.hexpatterns;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.iota.Vec3Iota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.block.ResonantAnchorBlockEntity;
import com.bluup.hexwright.server.block.ResonantAnchorRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class ResonantAnchorActions {

    private ResonantAnchorActions() {
    }

    public static void register() {
        Registry<ActionRegistryEntry> registry = IXplatAbstractions.INSTANCE.getActionRegistry();

        Registry.register(registry, Hexwright.id("resonant_anchors_ledger"),
            new ActionRegistryEntry(HexPattern.fromAngles("qaqwwdwdw", HexDir.SOUTH_WEST), RESONANT_ANCHORS_LEDGER));
    }

    private static final ConstMediaAction RESONANT_ANCHORS_LEDGER = new HexwrightConstMediaAction() {
        @Override
        public int getArgc() {
            return 1;
        }

        @Override
        public long getMediaCost() {
            return 0;
        }

        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            Iota raw = args.get(0);
            String key = ResonantAnchorBlockEntity.keyOf(raw);
            if (key == null) {
                throw MishapInvalidIota.ofType(raw, 0, "hexwright.anchor_key");
            }

            ServerLevel world = env.getWorld();
            List<Iota> found = new ArrayList<>();
            for (ResonantAnchorRegistry.AnchorLocation anchor
                : ResonantAnchorRegistry.get(world.getServer()).anchorsFor(key)) {
                if (!anchor.dimension().equals(world.dimension())) {
                    continue;
                }
                BlockPos pos = anchor.pos();
                found.add(new Vec3Iota(new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5)));
            }
            return List.of(new ListIota(found));
        }
    };
}
