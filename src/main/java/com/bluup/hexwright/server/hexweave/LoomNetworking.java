package com.bluup.hexwright.server.hexweave;

import com.bluup.hexwright.Hexwright;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.GZIPInputStream;

public final class LoomNetworking {

    public static final ResourceLocation OPEN_S2C = Hexwright.id("hex_loom_open");
    public static final ResourceLocation SAVE_C2S = Hexwright.id("hex_loom_save");
    public static final ResourceLocation SAVED_S2C = Hexwright.id("hex_loom_saved");

    public static final int MAX_SAVE_BYTES = 32_000;
    private static final long MAX_UNPACKED = 2_097_152L;

    private LoomNetworking() {
    }

    static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(SAVE_C2S, (server, player, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            int revision = buf.readVarInt();
            byte[] packed = buf.readByteArray();
            server.execute(() -> {
                CompoundTag weave;
                try {
                    weave = unpack(packed);
                } catch (IOException | RuntimeException e) {
                    sendSaved(player, pos, HexLoomEditing.refusal("message.hexwright.hex_loom.unreadable", revision));
                    return;
                }
                sendSaved(player, pos, HexLoomEditing.save(player, pos, revision, weave));
            });
        });
    }

    public static byte[] pack(CompoundTag weave) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            NbtIo.writeCompressed(weave, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    public static CompoundTag unpack(byte[] packed) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
            new GZIPInputStream(new ByteArrayInputStream(packed))))) {
            return NbtIo.read(in, new NbtAccounter(MAX_UNPACKED));
        }
    }

    static void sendOpen(ServerPlayer player, BlockPos pos, int revision, CompoundTag weave) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(pos);
        buf.writeVarInt(revision);
        buf.writeByteArray(pack(weave));
        ServerPlayNetworking.send(player, OPEN_S2C, buf);
    }

    private static void sendSaved(ServerPlayer player, BlockPos pos, HexLoomEditing.SaveResult result) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeBlockPos(pos);
        buf.writeBoolean(result.saved());
        buf.writeVarInt(result.revision());
        buf.writeComponent(result.message());
        ServerPlayNetworking.send(player, SAVED_S2C, buf);
    }
}
