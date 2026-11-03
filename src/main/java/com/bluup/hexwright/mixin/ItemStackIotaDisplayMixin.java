package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.reliquary.BulkCount;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(targets = "ram.talia.moreiotas.api.casting.iota.ItemStackIota$1", remap = false)
public abstract class ItemStackIotaDisplayMixin {

    private static final String TRANSLATABLE =
        "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;";

    @ModifyArg(method = "display", at = @At(value = "INVOKE", target = TRANSLATABLE, remap = true), index = 0)
    private String hexwright$supportedFormat(String key) {
        return "hexwright.iota.stack.display";
    }

    @ModifyArg(method = "display", at = @At(value = "INVOKE", target = TRANSLATABLE, remap = true), index = 1)
    private Object[] hexwright$bulkCount(Object[] args) {
        if (args.length > 0 && args[0] instanceof Integer count) {
            args[0] = BulkCount.format(count);
        }
        return args;
    }
}
