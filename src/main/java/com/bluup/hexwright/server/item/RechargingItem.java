package com.bluup.hexwright.server.item;

import com.bluup.hexwright.mixin.ItemCooldownInstanceAccessor;
import com.bluup.hexwright.mixin.ItemCooldownsAccessor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
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

        @Nullable
        public static Recharge ofCooldown(Player player, Item item, float partialTick) {
            ItemCooldownsAccessor cooldowns = (ItemCooldownsAccessor) player.getCooldowns();
            if (!(cooldowns.hexwright$cooldowns().get(item) instanceof ItemCooldownInstanceAccessor timer)) {
                return null;
            }
            float left = timer.hexwright$endTime() - (cooldowns.hexwright$tickCount() + partialTick);
            int total = timer.hexwright$endTime() - timer.hexwright$startTime();
            return left <= 0.0f || total <= 0 ? null : new Recharge(left, total);
        }

        public float fraction() {
            return this.totalTicks <= 0 ? 0.0f : Mth.clamp(this.remainingTicks / this.totalTicks, 0.0f, 1.0f);
        }
    }
}
