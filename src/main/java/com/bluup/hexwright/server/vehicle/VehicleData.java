package com.bluup.hexwright.server.vehicle;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class VehicleData {

    public static final String ROOT_TAG = "hexwright_vehicle";

    private static final String TAG_VARIANT = "Variant";
    private static final String TAG_QUALITY = "Quality";
    private static final String TAG_MEDIA = "Media";
    private static final String TAG_HEX = "Hex";
    private static final String TAG_MEMORY = "Memory";
    private static final String TAG_PREV_COMMAND_X = "PrevCommandX";
    private static final String TAG_PREV_COMMAND_Y = "PrevCommandY";
    private static final String TAG_PREV_COMMAND_Z = "PrevCommandZ";
    private static final String TAG_CHEST = "Chest";

    public static final String DEFAULT_VARIANT = "ethereal";

    private VehicleData() {
    }


    public static String getVariant(CompoundTag data) {
        return data.contains(TAG_VARIANT) ? data.getString(TAG_VARIANT) : DEFAULT_VARIANT;
    }

    public static void setVariant(CompoundTag data, String variant) {
        data.putString(TAG_VARIANT, variant);
    }


    public static PocketCasterData.Quality getQuality(CompoundTag data) {
        return data.contains(TAG_QUALITY)
            ? PocketCasterData.Quality.byName(data.getString(TAG_QUALITY))
            : PocketCasterData.Quality.CRUDE;
    }

    public static void setQuality(CompoundTag data, PocketCasterData.Quality quality) {
        data.putString(TAG_QUALITY, quality.name());
    }

    public static double gradeFraction(PocketCasterData.Quality quality) {
        return quality.ordinal() / (double) PocketCasterData.Quality.MASTERWORK.ordinal();
    }


    public static long getMedia(CompoundTag data) {
        return Math.max(0L, data.getLong(TAG_MEDIA));
    }

    public static void setMedia(CompoundTag data, long media, long capacity) {
        data.putLong(TAG_MEDIA, Math.max(0L, Math.min(capacity, media)));
    }


    public static @Nullable Iota getHex(CompoundTag data, ServerLevel level) {
        if (!data.contains(TAG_HEX)) {
            return null;
        }
        return IotaType.deserialize(data.getCompound(TAG_HEX), level);
    }

    public static void setHex(CompoundTag data, @Nullable Iota hex) {
        if (hex == null) {
            data.remove(TAG_HEX);
        } else {
            data.put(TAG_HEX, IotaType.serialize(hex));
        }
    }

    public static @Nullable CompoundTag getHexRawTag(CompoundTag data) {
        return data.contains(TAG_HEX) ? data.getCompound(TAG_HEX) : null;
    }


    public static ListIota getMemory(CompoundTag data, ServerLevel level) {
        if (!data.contains(TAG_MEMORY)) {
            return new ListIota(List.of());
        }
        Iota iota = IotaType.deserialize(data.getCompound(TAG_MEMORY), level);
        return iota instanceof ListIota list ? list : new ListIota(List.of());
    }

    public static void setMemory(CompoundTag data, ListIota memory) {
        data.put(TAG_MEMORY, IotaType.serialize(memory));
    }


    public static Vec3 getPreviousCommand(CompoundTag data) {
        return new Vec3(
            data.getDouble(TAG_PREV_COMMAND_X),
            data.getDouble(TAG_PREV_COMMAND_Y),
            data.getDouble(TAG_PREV_COMMAND_Z)
        );
    }

    public static void setPreviousCommand(CompoundTag data, Vec3 command) {
        data.putDouble(TAG_PREV_COMMAND_X, command.x);
        data.putDouble(TAG_PREV_COMMAND_Y, command.y);
        data.putDouble(TAG_PREV_COMMAND_Z, command.z);
    }


    public static void setChest(CompoundTag data, NonNullList<ItemStack> items) {
        CompoundTag chestTag = new CompoundTag();
        ContainerHelper.saveAllItems(chestTag, items);
        data.put(TAG_CHEST, chestTag);
    }

    public static void loadChest(CompoundTag data, NonNullList<ItemStack> items) {
        items.clear();
        if (data.contains(TAG_CHEST)) {
            ContainerHelper.loadAllItems(data.getCompound(TAG_CHEST), items);
        }
    }
}
