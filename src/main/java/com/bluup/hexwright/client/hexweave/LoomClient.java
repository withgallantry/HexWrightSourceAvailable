package com.bluup.hexwright.client.hexweave;

import com.bluup.hexwright.HexwrightDev;
import com.bluup.hexwright.server.hexweave.HexLoom;
import com.bluup.hexwright.server.hexweave.HexLoomBlockEntity;
import com.bluup.hexwright.server.hexweave.HexweaveNbt;
import com.bluup.hexwright.server.hexweave.LoomNetworking;
import com.bluup.hexwright.server.hexweave.WeaveDraft;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

@Environment(EnvType.CLIENT)
public final class LoomClient {

    private LoomClient() {
    }

    public static void register() {
        BlockEntityRenderers.register(HexLoom.BLOCK_ENTITY, HexLoomRenderer::new);
        BlockRenderLayerMap.INSTANCE.putBlock(HexLoom.BLOCK, RenderType.cutout());
        ClientPlayNetworking.registerGlobalReceiver(LoomNetworking.OPEN_S2C, (client, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            int revision = buf.readVarInt();
            byte[] packed = buf.readByteArray();
            client.execute(() -> {
                WeaveDraft draft;
                try {
                    CompoundTag weave = LoomNetworking.unpack(packed);
                    draft = WeaveDraft.of(HexweaveNbt.read(weave), HexweaveNbt.readLayout(weave));
                } catch (IOException | RuntimeException e) {
                    return;
                }
                LoomWindow.open(LoomEditor.forLoom(pos, revision, draft));
            });
        });
        ClientPlayNetworking.registerGlobalReceiver(LoomNetworking.SAVED_S2C, (client, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            boolean saved = buf.readBoolean();
            int revision = buf.readVarInt();
            Component message = buf.readComponent();
            client.execute(() -> {
                LoomEditor editor = LoomWindow.current();
                if (editor != null && editor.editing(pos)) {
                    editor.onSaveResult(saved, revision, message);
                }
            });
        });
        if (HexwrightDev.LOOM_TOOLS) {
            LoomDevTools.register();
        }
    }

    public static @Nullable BlockHitResult pickDockedOrb(Player player, double reach, @Nullable HitResult vanillaHit) {
        Vec3 eye = player.getEyePosition(1.0f);
        double limit = reach;
        if (vanillaHit != null && vanillaHit.getType() != HitResult.Type.MISS) {
            limit = Math.min(limit, vanillaHit.getLocation().distanceTo(eye));
        }
        Vec3 end = eye.add(player.getViewVector(1.0f).scale(limit));
        return BlockGetter.traverseBlocks(eye, end, player.level(), (level, cell) -> {
            BlockPos loomPos = cell.below().immutable();
            if (!(level.getBlockEntity(loomPos) instanceof HexLoomBlockEntity loom) || loom.orb().isEmpty()) {
                return null;
            }
            return HexLoomRenderer.ORB_BOX.move(loomPos).clip(eye, end)
                .map(hit -> new BlockHitResult(new Vec3(hit.x, Math.min(hit.y, loomPos.getY() + 1.5), hit.z),
                    Direction.UP, loomPos, false))
                .orElse(null);
        }, level -> null);
    }

    static void sendSave(BlockPos pos, int revision, byte[] packed) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(pos);
        buf.writeVarInt(revision);
        buf.writeByteArray(packed);
        ClientPlayNetworking.send(LoomNetworking.SAVE_C2S, buf);
    }
}
