package com.bluup.hexwright.server.sky;

import com.bluup.hexwright.Hexwright;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.List;

public final class CellSkies {

    public static final ResourceLocation CHANNEL = Hexwright.id("cell_skies_s2c");

    private CellSkies() {
    }

    public record Region(int minX, int minZ, int maxX, int maxZ, ResourceLocation sky) {

        public boolean covers(double x, double z) {
            return x >= this.minX && x < this.maxX && z >= this.minZ && z < this.maxZ;
        }

        public void write(FriendlyByteBuf buf) {
            buf.writeInt(this.minX);
            buf.writeInt(this.minZ);
            buf.writeInt(this.maxX);
            buf.writeInt(this.maxZ);
            buf.writeResourceLocation(this.sky);
        }

        public static Region read(FriendlyByteBuf buf) {
            return new Region(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readResourceLocation());
        }
    }

    public static void send(ServerPlayer player, ResourceKey<Level> dimension, List<Region> regions) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeResourceKey(dimension);
        buf.writeCollection(regions, (out, region) -> region.write(out));
        ServerPlayNetworking.send(player, CHANNEL, buf);
    }

    public static ResourceKey<Level> readDimension(FriendlyByteBuf buf) {
        return buf.readResourceKey(Registries.DIMENSION);
    }
}
