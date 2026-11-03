package com.bluup.hexwright.server.network;

import com.bluup.hexwright.server.block.ResonanceTowerBlockEntity;
import com.bluup.hexwright.server.item.EndlessPouchItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public final class EssenceNetwork {

    private static final Vector3f FLOW_PARTICLE_COLOR = new Vector3f(183f / 255f, 127f / 255f, 219f / 255f);
    private static final int FLOW_PARTICLE_STEPS = 6;

    private EssenceNetwork() {
    }

    public static boolean isEssenceSource(ItemStack stack) {
        return stack.getItem() instanceof EndlessPouchItem
            || stack.getItem() instanceof ResonantKeyItem;
    }

    public static ItemStack pouchAt(Level level, BlockPos pos) {
        var be = level.getBlockEntity(pos);
        ItemStack docked;
        if (be instanceof com.bluup.hexwright.server.block.ResonanceTowerBlockEntity tower) {
            docked = tower.dockedPouch();
        } else if (be instanceof com.bluup.hexwright.server.block.EssenceGaugeBlockEntity gauge) {
            docked = gauge.dockedSource();
        } else if (be instanceof com.bluup.hexwright.server.block.CrucibleBlockEntity crucible) {
            docked = crucible.getItem(com.bluup.hexwright.server.block.CrucibleBlockEntity.POUCH_SLOT);
        } else if (be instanceof com.bluup.hexwright.server.block.WorktableBlockEntity worktable) {
            docked = worktable.getItem(com.bluup.hexwright.server.block.WorktableBlockEntity.POUCH_SLOT);
        } else if (be instanceof com.bluup.hexwright.server.block.CoalescerBlockEntity coalescer) {
            docked = coalescer.getItem(com.bluup.hexwright.server.block.CoalescerBlockEntity.POUCH_SLOT);
        } else {
            return ItemStack.EMPTY;
        }
        return resolve(docked, level, pos);
    }

    public static ItemStack resolve(ItemStack docked, @Nullable Level level, @Nullable BlockPos machinePos) {
        if (docked.getItem() instanceof EndlessPouchItem) {
            return docked;
        }
        if (!(docked.getItem() instanceof ResonantKeyItem) || level == null) {
            return ItemStack.EMPTY;
        }

        BlockPos towerPos = ResonantKeyItem.attunedPos(docked);
        if (towerPos == null || !ResonantKeyItem.attunedDimensionMatches(docked, level)) {
            return ItemStack.EMPTY;
        }
        if (!(level.getBlockEntity(towerPos) instanceof ResonanceTowerBlockEntity tower)) {
            return ItemStack.EMPTY;
        }
        if (machinePos != null) {
            double radius = tower.radius();
            if (machinePos.distSqr(towerPos) > radius * radius) {
                return ItemStack.EMPTY;
            }
        }
        ItemStack pouch = tower.dockedPouch();
        if (!(pouch.getItem() instanceof EndlessPouchItem)) {
            return ItemStack.EMPTY;
        }
        if (!level.isClientSide) {
            tower.markUsed();
        }
        return pouch;
    }

    public static boolean isKeyOutOfRange(ItemStack docked, @Nullable Level level, @Nullable BlockPos machinePos) {
        if (level == null || machinePos == null || !(docked.getItem() instanceof ResonantKeyItem)) {
            return false;
        }
        BlockPos towerPos = ResonantKeyItem.attunedPos(docked);
        if (towerPos == null) {
            return false;
        }
        if (!ResonantKeyItem.attunedDimensionMatches(docked, level)) {
            return true;
        }
        double radius = ResonanceTowerBlockEntity.BASE_RADIUS;
        return machinePos.distSqr(towerPos) > radius * radius;
    }

    public static int keyRange() {
        return (int) ResonanceTowerBlockEntity.BASE_RADIUS;
    }

    public static void warnIfOutOfRange(ServerPlayer player, ItemStack docked, Level level, BlockPos machinePos) {
        if (!isKeyOutOfRange(docked, level, machinePos)) {
            return;
        }
        player.displayClientMessage(
            Component.translatable("message.hexwright.network.out_of_range", keyRange())
                .withStyle(ChatFormatting.RED),
            true);
    }

    public static void pulseFlow(Level level, BlockPos machinePos, ItemStack docked, boolean towardTower) {
        if (!(level instanceof ServerLevel serverLevel) || !(docked.getItem() instanceof ResonantKeyItem)) {
            return;
        }
        BlockPos towerPos = ResonantKeyItem.attunedPos(docked);
        if (towerPos == null || !ResonantKeyItem.attunedDimensionMatches(docked, level) || towerPos.equals(machinePos)) {
            return;
        }

        Vec3 machineVec = Vec3.atCenterOf(machinePos).add(0, 0.5, 0);
        Vec3 towerVec = Vec3.atCenterOf(towerPos).add(0, 0.8, 0);
        Vec3 from = towardTower ? machineVec : towerVec;
        Vec3 to = towardTower ? towerVec : machineVec;

        DustParticleOptions dust = new DustParticleOptions(FLOW_PARTICLE_COLOR, 1.1f);
        for (int i = 1; i <= FLOW_PARTICLE_STEPS; i++) {
            Vec3 p = from.lerp(to, (double) i / (FLOW_PARTICLE_STEPS + 1));
            serverLevel.sendParticles(dust, p.x, p.y, p.z, 1, 0.08, 0.08, 0.08, 0.0);
        }
    }
}
