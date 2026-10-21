package com.bluup.hexwright.server.item;

import com.bluup.hexwright.server.progression.ArtifactProvenance;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GolemCoreItem extends Item {

    public GolemCoreItem(Properties properties) {
        super(properties);
    }

    public static int carried(Player player) {
        return player.getInventory().countItem(HexwrightItems.GOLEM_CORE);
    }

    public static void consume(ServerPlayer player, int count, String where) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && count > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(HexwrightItems.GOLEM_CORE)) {
                continue;
            }
            int taken = Math.min(count, stack.getCount());
            ArtifactProvenance.spend(stack, taken, player, where);
            stack.shrink(taken);
            count -= taken;
        }
        inventory.setChanged();
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        ArtifactProvenance.hold(stack, entity);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.hexwright.golem_core").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.hexwright.golem_core.use")
            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
