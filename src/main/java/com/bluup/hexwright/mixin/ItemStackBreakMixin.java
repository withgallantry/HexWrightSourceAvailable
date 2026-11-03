package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.talisman.TalismanCasting;
import com.bluup.hexwright.server.talisman.TalismanData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(ItemStack.class)
public abstract class ItemStackBreakMixin {

    @Inject(
        method = "hurtAndBreak",
        at = @At(value = "INVOKE", target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V")
    )
    private void hexwright$fireBreakTalismans(int amount, LivingEntity entity, Consumer<?> onBroken,
                                              CallbackInfo ci) {
        if (entity instanceof ServerPlayer player && !TalismanCasting.isCasting()) {
            TalismanCasting.onTrigger(player, TalismanData.Trigger.BREAK, null, null);
        }
    }
}
