package com.bluup.hexwright.server.item;

import com.bluup.hexwright.server.block.FieldMarkerBlockEntity;
import com.bluup.hexwright.server.block.ResonantFieldRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FieldTunerItem extends Item {

    private static final String ROOT_TAG = "hexwright_field_tuner";
    private static final String TAG_DIMENSION = "Dimension";
    private static final String TAG_PENDING = "Pending";
    private static final int GROUP_SIZE = 8;
    private static final int MAX_AXIS_SIZE = 200;

    public FieldTunerItem(Properties properties) {
        super(properties);
    }

    public static void rightClickMarker(ItemStack tuner, ServerLevel level, ServerPlayer player, FieldMarkerBlockEntity marker) {
        boolean sneaking = player.isShiftKeyDown();
        BlockPos pos = marker.getBlockPos();

        if (marker.isTuned()) {
            if (sneaking) {
                marker.forget();
                level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.6f, 1.2f);
                player.displayClientMessage(
                    Component.translatable("message.hexwright.field_tuner.dissolved").withStyle(ChatFormatting.YELLOW), true
                );
            } else {
                player.displayClientMessage(
                    Component.translatable("message.hexwright.field_tuner.already_tuned").withStyle(ChatFormatting.GRAY), true
                );
            }
            return;
        }

        List<BlockPos> pending = new ArrayList<>(getPending(tuner, level));

        if (sneaking) {
            if (pending.remove(pos)) {
                if (pending.isEmpty()) {
                    clearPending(tuner);
                } else {
                    setPending(tuner, level, pending);
                }
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.5f, 0.8f);
                player.displayClientMessage(
                    Component.translatable("message.hexwright.field_tuner.removed", pending.size(), GROUP_SIZE)
                        .withStyle(ChatFormatting.GRAY),
                    true
                );
            } else {
                player.displayClientMessage(
                    Component.translatable("message.hexwright.field_tuner.not_pending").withStyle(ChatFormatting.GRAY),
                    true
                );
            }
            return;
        }

        if (!pending.contains(pos)) {
            pending.add(pos);
        }

        if (pending.size() < GROUP_SIZE) {
            setPending(tuner, level, pending);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 0.7f, 1.0f + 0.1f * pending.size());
            player.displayClientMessage(
                Component.translatable("message.hexwright.field_tuner.progress", pending.size(), GROUP_SIZE)
                    .withStyle(ChatFormatting.AQUA),
                true
            );
            return;
        }

        List<BlockPos> valid = new ArrayList<>();
        for (BlockPos candidate : pending) {
            if (level.getBlockEntity(candidate) instanceof FieldMarkerBlockEntity be && !be.isTuned()) {
                valid.add(candidate);
            }
        }

        if (valid.size() < GROUP_SIZE) {
            setPending(tuner, level, valid);
            player.displayClientMessage(
                Component.translatable("message.hexwright.field_tuner.invalidated").withStyle(ChatFormatting.RED), true
            );
            return;
        }

        if (!formsCuboidCorners(valid)) {
            clearPending(tuner);
            player.displayClientMessage(
                Component.translatable("message.hexwright.field_tuner.malformed").withStyle(ChatFormatting.RED), true
            );
            return;
        }

        if (exceedsMaxAxisSize(valid)) {
            clearPending(tuner);
            player.displayClientMessage(
                Component.translatable("message.hexwright.field_tuner.too_large", MAX_AXIS_SIZE)
                    .withStyle(ChatFormatting.RED),
                true
            );
            return;
        }

        ResonantFieldRegistry registry = ResonantFieldRegistry.get(level.getServer());
        ResonantFieldRegistry.Field field = registry.create(level.dimension(), valid);
        for (BlockPos markerPos : valid) {
            ((FieldMarkerBlockEntity) level.getBlockEntity(markerPos)).commit(field.id());
        }
        clearPending(tuner);
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8f, 1.5f);
        player.displayClientMessage(
            Component.translatable("message.hexwright.field_tuner.tuned").withStyle(ChatFormatting.LIGHT_PURPLE), true
        );
    }

    private static boolean formsCuboidCorners(List<BlockPos> markers) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : markers) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        if (minX == maxX || minY == maxY || minZ == maxZ) {
            return false;
        }
        Set<BlockPos> expectedCorners = new HashSet<>();
        for (int x : new int[]{minX, maxX}) {
            for (int y : new int[]{minY, maxY}) {
                for (int z : new int[]{minZ, maxZ}) {
                    expectedCorners.add(new BlockPos(x, y, z));
                }
            }
        }
        return expectedCorners.equals(new HashSet<>(markers));
    }

    private static boolean exceedsMaxAxisSize(List<BlockPos> markers) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : markers) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        int sizeX = maxX - minX + 1;
        int sizeY = maxY - minY + 1;
        int sizeZ = maxZ - minZ + 1;
        return sizeX > MAX_AXIS_SIZE || sizeY > MAX_AXIS_SIZE || sizeZ > MAX_AXIS_SIZE;
    }

    private static List<BlockPos> getPending(ItemStack tuner, ServerLevel level) {
        CompoundTag root = tuner.getTagElement(ROOT_TAG);
        if (root == null) {
            return List.of();
        }
        if (!root.getString(TAG_DIMENSION).equals(level.dimension().location().toString())) {
            return List.of();
        }
        ListTag list = root.getList(TAG_PENDING, Tag.TAG_COMPOUND);
        List<BlockPos> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            result.add(NbtUtils.readBlockPos(list.getCompound(i)));
        }
        return result;
    }

    private static void setPending(ItemStack tuner, ServerLevel level, List<BlockPos> pending) {
        CompoundTag root = tuner.getOrCreateTagElement(ROOT_TAG);
        root.putString(TAG_DIMENSION, level.dimension().location().toString());
        ListTag list = new ListTag();
        for (BlockPos pos : pending) {
            list.add(NbtUtils.writeBlockPos(pos));
        }
        root.put(TAG_PENDING, list);
    }

    private static void clearPending(ItemStack tuner) {
        tuner.removeTagKey(ROOT_TAG);
    }

    private static boolean clearPendingFromSneakReset(ItemStack tuner, ServerPlayer player) {
        CompoundTag root = tuner.getTagElement(ROOT_TAG);
        if (root == null) {
            return false;
        }
        clearPending(tuner);
        player.displayClientMessage(
            Component.translatable("message.hexwright.field_tuner.cleared").withStyle(ChatFormatting.GRAY),
            true
        );
        return true;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        Player player = context.getPlayer();
        if (!(player instanceof ServerPlayer serverPlayer) || !serverPlayer.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (serverLevel.getBlockEntity(context.getClickedPos()) instanceof FieldMarkerBlockEntity) {
            return InteractionResult.PASS;
        }
        ItemStack tuner = context.getItemInHand();
        return clearPendingFromSneakReset(tuner, serverPlayer) ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack tuner = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(tuner);
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !serverPlayer.isShiftKeyDown()) {
            return InteractionResultHolder.pass(tuner);
        }
        return clearPendingFromSneakReset(tuner, serverPlayer)
            ? InteractionResultHolder.consume(tuner)
            : InteractionResultHolder.pass(tuner);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        CompoundTag root = stack.getTagElement(ROOT_TAG);
        int count = root == null ? 0 : root.getList(TAG_PENDING, Tag.TAG_COMPOUND).size();
        if (count == 0) {
            tooltip.add(Component.translatable("tooltip.hexwright.field_tuner.empty").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.hexwright.field_tuner.progress", count, GROUP_SIZE)
                .withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.translatable("tooltip.hexwright.field_tuner.hint")
            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
