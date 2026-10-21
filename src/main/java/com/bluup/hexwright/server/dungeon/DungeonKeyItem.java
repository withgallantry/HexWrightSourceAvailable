package com.bluup.hexwright.server.dungeon;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class DungeonKeyItem extends Item {

    private final List<DungeonInstance.Kind> kinds;

    public DungeonKeyItem(Properties properties, List<DungeonInstance.Kind> kinds) {
        super(properties);
        this.kinds = List.copyOf(kinds);
    }

    public DungeonInstance.Kind draw(RandomSource random) {
        return this.kinds.get(random.nextInt(this.kinds.size()));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable("message.hexwright.travel_stone.key_needs_stone"), true);
        }
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(this.getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.hexwright.dungeon_key.tooltip_time",
            DungeonInstances.duration(DungeonInstances.RIFT_OPEN_TICKS)).withStyle(ChatFormatting.DARK_GRAY));
    }
}
