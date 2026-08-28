package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.api.addldata.ADIotaHolder;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadOffhandItem;
import at.petrak.hexcasting.common.casting.actions.rw.OpRead;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.server.hexicon.HexiconData;
import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.staff_assembly.StaffAssemblyData;
import com.bluup.hexwright.server.staff_assembly.StaffPowers;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.stream.Collectors;

@Mixin(OpRead.class)
public abstract class OpReadMixin {
    @Inject(
        method = "execute(Ljava/util/List;Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;)Ljava/util/List;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void hexwright$readFromHeldHexicon(List<Iota> args, CastingEnvironment env,
                                                 CallbackInfoReturnable<List<Iota>> cir) {
        if (!(env.getCastingEntity() instanceof Player player)) {
            return;
        }

        Pair<InteractionHand, ItemStack> held = HexiconData.findHeldSpellbook(player);
        if (held == null || hexwright$yieldsToHeldIotaHolder(held.getSecond(), env)) {
            return;
        }

        Iota iota = HexiconData.readSelectedSpell(player, held.getSecond());
        if (iota == null) {
            throw MishapBadOffhandItem.of(held.getSecond(), "iota.read");
        }

        cir.setReturnValue(List.of(iota));
    }

    private static boolean hexwright$yieldsToHeldIotaHolder(ItemStack book, CastingEnvironment env) {
        if (!book.is(HexwrightItems.CONFIGURABLE_STAFF)) {
            return false;
        }
        return env.getHeldItemToOperateOn(stack -> {
            ADIotaHolder holder = IXplatAbstractions.INSTANCE.findDataHolder(stack);
            return holder != null && holder.readIota(env.getWorld()) != null;
        }) != null;
    }

    @Inject(
        method = "execute(Ljava/util/List;Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;)Ljava/util/List;",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void hexwright$readAreaCoreBind(List<Iota> args, CastingEnvironment env,
                                             CallbackInfoReturnable<List<Iota>> cir) {
        if (!(env instanceof StaffCastEnv)) {
            return;
        }

        ItemStack held = env.getCastingEntity().getItemInHand(env.getCastingHand());
        if (!held.is(HexwrightItems.CONFIGURABLE_STAFF) || !StaffPowers.hasEntityListBindingCore(held)) {
            return;
        }

        boolean yieldsToRealHolder = env.getHeldItemToOperateOn(stack -> {
            ADIotaHolder holder = IXplatAbstractions.INSTANCE.findDataHolder(stack);
            return holder != null && holder.readIota(env.getWorld()) != null;
        }) != null;
        if (yieldsToRealHolder) {
            return;
        }

        List<HexPattern> patterns = StaffAssemblyData.getAreaCastPatterns(held);
        if (patterns.isEmpty()) {
            throw MishapBadOffhandItem.of(held, "iota.read");
        }

        List<Iota> patternIotas = patterns.stream().map(PatternIota::new).collect(Collectors.toList());
        cir.setReturnValue(List.of(new ListIota(patternIotas)));
    }
}
