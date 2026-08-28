package com.bluup.hexwright.server.talisman;

import com.bluup.hexwright.server.hexpatterns.HexwrightSpellAction;
import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.RenderedSpell;
import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadLocation;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class TalismanActions {

    public static final long BINDING_COST = 5 * MediaConstants.DUST_UNIT;

    private TalismanActions() {
    }

    public static void register() {
        Registry<ActionRegistryEntry> registry = IXplatAbstractions.INSTANCE.getActionRegistry();
        Registry.register(registry, Hexwright.id("talismans_binding"),
            new ActionRegistryEntry(HexPattern.fromAngles("aqede", HexDir.NORTH_WEST), TALISMANS_BINDING));
    }

    private static final SpellAction TALISMANS_BINDING = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 2;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            int triggerOrdinal = OperatorUtils.getPositiveIntUnderInclusive(
                args, 0, TalismanData.Trigger.values().length - 1, getArgc());
            int contextOrdinal = OperatorUtils.getPositiveIntUnderInclusive(
                args, 1, TalismanData.Context.values().length - 1, getArgc());

            if (!(env.getCastingEntity() instanceof ServerPlayer player)) {
                throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_no_talisman");
            }

            CastingEnvironment.HeldItemInfo held =
                env.getHeldItemToOperateOn(stack -> stack.getItem() instanceof TalismanItem);
            if (held == null) {
                throw new MishapBadLocation(player.position(), "hexwright_no_talisman");
            }
            ItemStack talisman = held.stack();

            TalismanData.Trigger trigger = TalismanData.Trigger.values()[triggerOrdinal];
            TalismanData.Context context = TalismanData.Context.values()[contextOrdinal];

            return new Result(new RenderedSpell() {
                @Override
                public void cast(CastingEnvironment castEnv) {
                    TalismanData.setTrigger(talisman, trigger);
                    TalismanData.setContext(talisman, context);
                    TalismanData.setNextFire(talisman, 0L);
                }

                @Override
                public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                    cast(castEnv);
                    return image;
                }
            }, BINDING_COST, List.of(ParticleSpray.burst(player.position(), 0.5, 10)), 1L);
        }
    };
}
