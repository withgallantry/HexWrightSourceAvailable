package com.bluup.hexwright.server.item;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public interface RechargingItem {

    @Nullable
    Recharge recharge(ItemStack stack, LivingEntity holder, float partialTick);

    record Recharge(float remainingTicks, int totalTicks) {

        @Nullable
        public static Recharge of(long remainingTicks, int totalTicks, float partialTick) {
            if (totalTicks <= 0) {
                return null;
            }
            float left = Math.min(remainingTicks, totalTicks) - partialTick;
            return left <= 0.0f ? null : new Recharge(left, totalTicks);
        }

        public float fraction() {
            return this.totalTicks <= 0 ? 0.0f : Mth.clamp(this.remainingTicks / this.totalTicks, 0.0f, 1.0f);
        }
    }
}
