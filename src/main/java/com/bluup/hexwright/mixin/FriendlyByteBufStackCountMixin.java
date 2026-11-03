package com.bluup.hexwright.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(FriendlyByteBuf.class)
public abstract class FriendlyByteBufStackCountMixin {

    private static final int DEEP_COUNT_MARKER = -1;

    @WrapOperation(
        method = "writeItem",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/FriendlyByteBuf;writeByte(I)Lio/netty/buffer/ByteBuf;")
    )
    private ByteBuf hexwright$writeDeepCount(FriendlyByteBuf buf, int count, Operation<ByteBuf> original) {
        if (count <= Byte.MAX_VALUE) {
            return original.call(buf, count);
        }
        original.call(buf, DEEP_COUNT_MARKER);
        return buf.writeVarInt(count);
    }

    @ModifyVariable(method = "readItem", at = @At("STORE"), ordinal = 0)
    private int hexwright$readDeepCount(int count) {
        return count == DEEP_COUNT_MARKER ? ((FriendlyByteBuf) (Object) this).readVarInt() : count;
    }
}
