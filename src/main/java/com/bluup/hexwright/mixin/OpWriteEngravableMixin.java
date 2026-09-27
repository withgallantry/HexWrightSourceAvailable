package com.bluup.hexwright.mixin;

import at.petrak.hexcasting.common.casting.actions.rw.OpWrite;
import com.bluup.hexwright.server.item.EngravableItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Predicate;

@Mixin(value = OpWrite.class, remap = false)
public abstract class OpWriteEngravableMixin {

    @ModifyArg(
        method = "execute(Ljava/util/List;Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;)Lat/petrak/hexcasting/api/casting/castables/SpellAction$Result;",
        at = @At(value = "INVOKE",
            target = "Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment;getHeldItemToOperateOn(Ljava/util/function/Predicate;)Lat/petrak/hexcasting/api/casting/eval/CastingEnvironment$HeldItemInfo;"),
        require = 2
    )
    private Predicate<ItemStack> hexwright$skipEngravable(Predicate<ItemStack> search) {
        return stack -> !(stack.getItem() instanceof EngravableItem) && search.test(stack);
    }
}
