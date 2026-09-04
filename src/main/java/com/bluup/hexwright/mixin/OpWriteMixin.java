package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.api.casting.RenderedSpell;
import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.mishaps.MishapOthersName;
import at.petrak.hexcasting.common.casting.actions.rw.OpWrite;
import com.bluup.hexwright.server.hexicon.HexiconData;
import com.bluup.hexwright.server.reliquary.ChestCastEnv;
import com.bluup.hexwright.server.staff_assembly.HeldIotaPrecedence;
import com.bluup.hexwright.server.staff_assembly.StaffPowers;
import com.bluup.hexwright.server.item.HexwrightItems;
import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv;
import com.mojang.datafixers.util.Pair;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.List;

@Mixin(OpWrite.class)
public abstract class OpWriteMixin {
    @Inject(
        method = "execute(Ljava/util/List;Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;)Lat/petrak/hexcasting/api/casting/castables/SpellAction$Result;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void hexwright$allowAreaCoreBindWithoutIotaHolder(List<? extends Iota> args, CastingEnvironment env,
                                                               CallbackInfoReturnable<SpellAction.Result> cir) {
        if (args.isEmpty()) {
            return;
        }

        Iota datum = args.get(0);

        if (!(env.getCastingEntity() instanceof Player player)) {
            return;
        }

        Pair<net.minecraft.world.InteractionHand, ItemStack> heldHexicon = HexiconData.findHeldSpellbook(player);
        if (heldHexicon != null && !hexwright$yieldsToHeldIotaHolder(heldHexicon.getSecond(), env, datum)) {
            ServerPlayer selfIsAllowed = env instanceof ChestCastEnv ? null : (player instanceof ServerPlayer sp ? sp : null);
            Player trueName = MishapOthersName.getTrueNameFromDatum(datum, selfIsAllowed);
            if (trueName != null) {
                throw new MishapOthersName(trueName);
            }

            cir.setReturnValue(new SpellAction.Result(new RenderedSpell() {
                @Override
                public void cast(CastingEnvironment castEnv) {
                    HexiconData.writeSelectedSpell(player, heldHexicon.getSecond(), datum);
                }

                @Override
                public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                    cast(castEnv);
                    return image;
                }
            }, 0L, Collections.emptyList(), 0L));
            return;
        }
        if (!(env instanceof StaffCastEnv)) {
            return;
        }

        ItemStack held = env.getCastingEntity().getItemInHand(env.getCastingHand());
        if (!held.is(HexwrightItems.CONFIGURABLE_STAFF) || !StaffPowers.hasEntityListBindingCore(held)) {
            return;
        }

        if (HeldIotaPrecedence.yieldsWrite(env, datum)) {
            return;
        }

        cir.setReturnValue(new SpellAction.Result(new RenderedSpell() {
            @Override
            public void cast(CastingEnvironment castEnv) {
                StaffPowers.bindFromWrite(castEnv, datum);
            }

            @Override
            public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                cast(castEnv);
                return image;
            }
        }, 0L, Collections.emptyList(), 0L));
    }

    private static boolean hexwright$yieldsToHeldIotaHolder(ItemStack book, CastingEnvironment env, Iota datum) {
        if (!book.is(HexwrightItems.CONFIGURABLE_STAFF)) {
            return false;
        }
        return HeldIotaPrecedence.yieldsWrite(env, datum);
    }

    @Inject(
        method = "execute(Ljava/util/List;Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;)Lat/petrak/hexcasting/api/casting/castables/SpellAction$Result;",
        at = @At("RETURN"),
        remap = false
    )
    private void hexwright$bindCorePattern(List<? extends Iota> args, CastingEnvironment env,
                                           CallbackInfoReturnable<SpellAction.Result> cir) {
        if (args.isEmpty()) {
            return;
        }
        Iota datum = args.get(0);
        if (HeldIotaPrecedence.yieldsWrite(env, datum)) {
            return;
        }
        StaffPowers.bindFromWrite(env, datum);
    }
}
