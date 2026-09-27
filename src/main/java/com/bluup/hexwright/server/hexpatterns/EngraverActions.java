package com.bluup.hexwright.server.hexpatterns;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.RenderedSpell;
import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.NullIota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadOffhandItem;
import at.petrak.hexcasting.api.casting.mishaps.MishapOthersName;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.item.EngravableItem;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class EngraverActions {

    private EngraverActions() {
    }

    public static void register() {
        Registry.register(IXplatAbstractions.INSTANCE.getActionRegistry(), Hexwright.id("engravers_gambit"),
            new ActionRegistryEntry(HexPattern.fromAngles("deeeed", HexDir.EAST), ENGRAVERS_GAMBIT));
    }

    private static final SpellAction ENGRAVERS_GAMBIT = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 1;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            Iota datum = args.get(0);
            @Nullable Iota written = datum instanceof NullIota ? null : datum;

            CastingEnvironment.HeldItemInfo found = env.getHeldItemToOperateOn(stack ->
                stack.getItem() instanceof EngravableItem item && item.canWrite(stack, written));
            if (found == null) {
                CastingEnvironment.HeldItemInfo any =
                    env.getHeldItemToOperateOn(stack -> stack.getItem() instanceof EngravableItem);
                if (any == null) {
                    throw MishapBadOffhandItem.of(null, "hexwright.engravable");
                }
                throw MishapBadOffhandItem.of(any.stack(), "iota.readonly", datum.display());
            }

            Player trueName = MishapOthersName.getTrueNameFromDatum(datum,
                env.getCastingEntity() instanceof ServerPlayer player ? player : null);
            if (trueName != null) {
                throw new MishapOthersName(trueName);
            }

            ItemStack stack = found.stack();
            EngravableItem item = (EngravableItem) stack.getItem();
            return new Result(new RenderedSpell() {
                @Override
                public void cast(CastingEnvironment castEnv) {
                    item.writeDatum(stack, written);
                }

                @Override
                public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                    cast(castEnv);
                    return image;
                }
            }, 0, List.of(), 1L);
        }
    };
}
