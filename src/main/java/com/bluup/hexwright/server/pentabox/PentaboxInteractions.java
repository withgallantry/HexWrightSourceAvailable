package com.bluup.hexwright.server.pentabox;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class PentaboxInteractions {

    public static InteractionResult forwardUseOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        InteractionHand hand = context.getHand();
        ItemStack outer = context.getItemInHand();
        int index = PentaboxData.getSelectedIndex(outer);
        ItemStack inner = PentaboxData.getSelectedStack(outer);
        if (inner.isEmpty()) {
            return InteractionResult.PASS;
        }

        BlockHitResult hit = new BlockHitResult(context.getClickLocation(), context.getClickedFace(), context.getClickedPos(), context.isInside());
        player.setItemInHand(hand, inner);
        InteractionResult result;
        try {
            result = inner.getItem().useOn(new UseOnContext(player, hand, hit));
        } finally {
            ItemStack mutated = player.getItemInHand(hand);
            player.setItemInHand(hand, outer);
            PentaboxData.setSlot(outer, index, mutated);
        }
        return result;
    }

    public static InteractionResult forwardInteractLivingEntity(ItemStack outer, Player player, LivingEntity target, InteractionHand hand) {
        int index = PentaboxData.getSelectedIndex(outer);
        ItemStack inner = PentaboxData.getSelectedStack(outer);
        if (inner.isEmpty()) {
            return InteractionResult.PASS;
        }

        player.setItemInHand(hand, inner);
        InteractionResult result;
        try {
            result = inner.getItem().interactLivingEntity(inner, player, target, hand);
        } finally {
            ItemStack mutated = player.getItemInHand(hand);
            player.setItemInHand(hand, outer);
            PentaboxData.setSlot(outer, index, mutated);
        }
        return result;
    }

    public static boolean forwardHurtEnemy(ItemStack outer, LivingEntity target, LivingEntity attacker) {
        int index = PentaboxData.getSelectedIndex(outer);
        ItemStack inner = PentaboxData.getSelectedStack(outer);
        if (inner.isEmpty()) {
            return false;
        }
        boolean result = inner.getItem().hurtEnemy(inner, target, attacker);
        PentaboxData.setSlot(outer, index, inner);
        return result;
    }

    public static boolean forwardMineBlock(ItemStack outer, Level level, BlockState state, BlockPos pos, LivingEntity entity) {
        int index = PentaboxData.getSelectedIndex(outer);
        ItemStack inner = PentaboxData.getSelectedStack(outer);
        if (inner.isEmpty()) {
            return false;
        }
        boolean result = inner.getItem().mineBlock(inner, level, state, pos, entity);
        PentaboxData.setSlot(outer, index, inner);
        return result;
    }

    public static float forwardDestroySpeed(ItemStack outer, BlockState state) {
        ItemStack inner = PentaboxData.getSelectedStack(outer);
        if (inner.isEmpty()) {
            return 1.0f;
        }
        return inner.getItem().getDestroySpeed(inner, state);
    }

    private PentaboxInteractions() {
    }
}
