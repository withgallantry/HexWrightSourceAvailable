package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.soulforge.SoulforgeLoot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Consumer;

@Mixin(LootTable.class)
public abstract class LootTableSoulforgeMixin {

    @ModifyVariable(
        method = "getRandomItemsRaw(Lnet/minecraft/world/level/storage/loot/LootContext;Ljava/util/function/Consumer;)V",
        at = @At("HEAD"), argsOnly = true)
    private Consumer<ItemStack> hexwright$withoutUndroppable(Consumer<ItemStack> output, LootContext context) {
        return SoulforgeLoot.withoutUndroppable(output);
    }
}
