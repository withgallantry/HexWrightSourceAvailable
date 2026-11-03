package com.bluup.hexwright.server.path;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

public class PathIota extends Iota {

    public PathIota(Path path) {
        super(TYPE, path);
    }

    public Path getPath() {
        return (Path) this.payload;
    }

    @Override
    public boolean isTruthy() {
        return true;
    }

    @Override
    public boolean toleratesOther(Iota that) {
        return typesMatch(this, that)
            && that instanceof PathIota other
            && this.getPath().equals(other.getPath());
    }

    @Override
    public @NotNull Tag serialize() {
        return getPath().save();
    }

    public static final IotaType<PathIota> TYPE = new IotaType<>() {
        @Override
        public PathIota deserialize(Tag tag, ServerLevel world) throws IllegalArgumentException {
            if (!(tag instanceof CompoundTag compound)) {
                throw new IllegalArgumentException("Expected a compound tag for a path iota");
            }
            return new PathIota(Path.load(compound));
        }

        @Override
        public Component display(Tag tag) {
            if (tag instanceof CompoundTag compound) {
                try {
                    return Component.translatable("hexwright.iota.path.display", Path.load(compound).describe());
                } catch (IllegalArgumentException malformed) {
                }
            }
            return Component.translatable("hexwright.iota.path");
        }

        @Override
        public int color() {
            return 0xFF_7FD6C2;
        }
    };

    public static void register() {
        Registry.register(IXplatAbstractions.INSTANCE.getIotaTypeRegistry(), Hexwright.id("path"), TYPE);
    }
}
