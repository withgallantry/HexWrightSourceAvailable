package com.bluup.hexwright.server.worldgen.decadentvault;

import com.bluup.hexwright.server.block.ResonantAnchorBlockEntity;
import com.bluup.hexwright.server.worldgen.TeleportWards;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public final class DecadentVaultWard {

    private DecadentVaultWard() {
    }

    public static void register() {
        TeleportWards.register(new TeleportWards.Check() {
            @Override
            public boolean isEmpty(ServerLevel level) {
                return DecadentVaultRegistry.get(level.getServer()).isEmptyIn(level.dimension());
            }

            @Override
            public boolean refuses(ServerLevel level, Vec3 from, Vec3 to) {
                DecadentVaultRegistry registry = DecadentVaultRegistry.get(level.getServer());
                var dimension = level.dimension();
                boolean insideFrom = registry.isInside(dimension, from) && !isAttunedAnchor(level, from);
                boolean insideTo = registry.isInside(dimension, to) && !isAttunedAnchor(level, to);
                return insideFrom || insideTo;
            }

            @Override
            public void notifyRefused(ServerPlayer player) {
                player.sendSystemMessage(
                    Component.translatable("message.hexwright.decadent_vault.sealed")
                        .withStyle(ChatFormatting.GOLD)
                );
            }
        });
    }

    private static boolean isAttunedAnchor(ServerLevel level, Vec3 pos) {
        BlockPos block = BlockPos.containing(pos);
        return level.getBlockEntity(block) instanceof ResonantAnchorBlockEntity anchor
            && anchor.getAttunementKey() != null;
    }
}
