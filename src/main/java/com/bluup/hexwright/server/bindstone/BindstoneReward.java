package com.bluup.hexwright.server.bindstone;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.dungeon.InstancedDungeons;
import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.worldgen.decadentvault.DecadentVaultHoard;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class BindstoneReward {

    private static final float DUNGEON_KEY_CHANCE = 1.0F / 6.0F;

    private static final float CONSTRUCT_EXPERIMENT_KEY_CHANCE = 1.0F / 40.0F;

    private static final int KYANITE_MIN = 2;
    private static final int KYANITE_SPREAD = 3;

    private static final int CHEST_TABLETS = 2;

    private static final int BINDSTONE_MIN = 8;
    private static final int BINDSTONE_SPREAD = 9;
    private static final int COLUMN_MIN = 4;
    private static final int COLUMN_SPREAD = 5;

    private BindstoneReward() {
    }

    public static void stockSpoils(ServerLevel level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof Container chest)) {
            Hexwright.LOGGER.warn("A bindstone pillar left a chest at {} with nothing to stock", pos.toShortString());
            return;
        }

        List<ItemStack> spoils = new ArrayList<>();
        spoils.add(new ItemStack(HexwrightItems.KYANITE, KYANITE_MIN + random.nextInt(KYANITE_SPREAD)));
        spoils.add(new ItemStack(HexwrightItems.RUINED_TABLET, CHEST_TABLETS));
        spoils.add(new ItemStack(HexwrightBlocks.BINDSTONE_ITEM, BINDSTONE_MIN + random.nextInt(BINDSTONE_SPREAD)));
        spoils.add(new ItemStack(HexwrightBlocks.BINDSTONE_COLUMN_ITEM, COLUMN_MIN + random.nextInt(COLUMN_SPREAD)));
        if (random.nextFloat() < DUNGEON_KEY_CHANCE) {
            spoils.add(new ItemStack(InstancedDungeons.DUNGEON_KEY));
        }
        if (random.nextFloat() < CONSTRUCT_EXPERIMENT_KEY_CHANCE) {
            spoils.add(new ItemStack(InstancedDungeons.CONSTRUCT_EXPERIMENT_KEY));
        }
        DecadentVaultHoard.scatter(chest, spoils, random);
        HexwrightDebug.log(HexwrightDebug.BINDSTONE, "[bindstone] the pillar at {} left {} stacks in its chest",
            pos.toShortString(), spoils.size());
    }

    public static void grantTablet(ServerPlayer player) {
        give(player, new ItemStack(HexwrightItems.RUINED_TABLET));
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        player.sendSystemMessage(
            Component.translatable("message.hexwright.bindstone.claimed", stack.getHoverName())
                .withStyle(ChatFormatting.LIGHT_PURPLE)
        );

        if (player.getInventory().add(stack)) {
            return;
        }
        ItemEntity dropped = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack);
        dropped.setNoPickUpDelay();
        dropped.setDeltaMovement(0.0, 0.0, 0.0);
        player.level().addFreshEntity(dropped);
    }
}
