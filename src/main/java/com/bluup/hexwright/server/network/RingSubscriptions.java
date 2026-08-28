package com.bluup.hexwright.server.network;

import com.bluup.hexwright.server.hexpatterns.StoredHex;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class RingSubscriptions {

    public static final int MAX_SUBSCRIPTIONS = 3;

    private static final String TAG_SUBSCRIPTIONS = "Subscriptions";
    private static final String TAG_HARMONIC = "Harmonic";
    private static final String TAG_HEX = "Hex";

    public record Subscription(int harmonic, CompoundTag hex) {
    }

    private RingSubscriptions() {
    }

    public static List<Subscription> list(ItemStack ring) {
        CompoundTag root = ring.getTagElement(ResonantRingItem.ROOT_TAG);
        if (root == null) {
            return List.of();
        }
        ListTag entries = root.getList(TAG_SUBSCRIPTIONS, Tag.TAG_COMPOUND);
        List<Subscription> out = new ArrayList<>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            out.add(new Subscription(entry.getInt(TAG_HARMONIC), entry.getCompound(TAG_HEX)));
        }
        return out;
    }

    public static int count(ItemStack ring) {
        return list(ring).size();
    }

    public static boolean set(ItemStack ring, int harmonic, Iota hex) {
        List<Subscription> current = list(ring);
        List<Subscription> updated = new ArrayList<>(current.size() + 1);
        boolean replaced = false;
        for (Subscription subscription : current) {
            if (subscription.harmonic() == harmonic) {
                updated.add(new Subscription(harmonic, IotaType.serialize(hex)));
                replaced = true;
            } else {
                updated.add(subscription);
            }
        }
        if (!replaced) {
            if (current.size() >= MAX_SUBSCRIPTIONS) {
                return false;
            }
            updated.add(new Subscription(harmonic, IotaType.serialize(hex)));
        }
        write(ring, updated);
        return true;
    }

    public static boolean clear(ItemStack ring, int harmonic) {
        List<Subscription> current = list(ring);
        List<Subscription> updated = new ArrayList<>(current.size());
        for (Subscription subscription : current) {
            if (subscription.harmonic() != harmonic) {
                updated.add(subscription);
            }
        }
        if (updated.size() == current.size()) {
            return false;
        }
        write(ring, updated);
        return true;
    }

    private static void write(ItemStack ring, List<Subscription> subscriptions) {
        CompoundTag root = ring.getOrCreateTagElement(ResonantRingItem.ROOT_TAG);
        ListTag entries = new ListTag();
        for (Subscription subscription : subscriptions) {
            CompoundTag entry = new CompoundTag();
            entry.putInt(TAG_HARMONIC, subscription.harmonic());
            entry.put(TAG_HEX, subscription.hex());
            entries.add(entry);
        }
        root.put(TAG_SUBSCRIPTIONS, entries);
    }

    public static @Nullable List<Iota> hexFor(ItemStack ring, int harmonic, ServerLevel level) {
        for (Subscription subscription : list(ring)) {
            if (subscription.harmonic() == harmonic) {
                return StoredHex.decode(IotaType.deserialize(subscription.hex().copy(), level));
            }
        }
        return null;
    }


}
