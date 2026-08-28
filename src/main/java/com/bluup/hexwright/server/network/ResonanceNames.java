package com.bluup.hexwright.server.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ResonanceNames extends SavedData {

    private static final String STORAGE_ID = "hexwright_resonance_names";

    public static final int MAX_NAME_LENGTH = 24;

    private static final String[] WORDS = {
        "Amber", "Azure", "Birch", "Briar", "Brine", "Cairn", "Cedar", "Chalk", "Chime", "Choir",
        "Cliff", "Cloud", "Coral", "Crane", "Creek", "Crown", "Crypt", "Delta", "Dryad", "Ember",
        "Fable", "Ferry", "Flame", "Flint", "Flock", "Flora", "Flute", "Forge", "Frost", "Gable",
        "Glade", "Glass", "Glint", "Gloom", "Grove", "Haven", "Hazel", "Heath", "Heron", "Ivory",
        "Jetty", "Knoll", "Lance", "Lapis", "Larch", "Ledge", "Lilac", "Linen", "Lotus", "Lunar",
        "Lyric", "Maple", "Marsh", "Mirth", "Mossy", "Myrrh", "Night", "North", "Ochre", "Olive",
        "Orbit", "Osier", "Otter", "Pearl", "Perch", "Pines", "Plume", "Prism", "Quail", "Quiet",
        "Quill", "Raven", "Realm", "Relic", "Ridge", "River", "Rowan", "Sable", "Sedge", "Shale",
        "Shard", "Shore", "Siren", "Slate", "Sleet", "Spire", "Spore", "Stone", "Storm", "Straw",
        "Surge", "Swift", "Talon", "Tempo", "Thorn", "Tidal", "Tithe", "Torch", "Tower", "Trace",
        "Umber", "Vapor", "Vault", "Verge", "Vigil", "Vixen", "Wharf", "Wheat", "Whorl", "Winch",
        "Witch", "Wrack", "Yeast",
    };

    private final Map<String, String> byNetwork = new HashMap<>();

    public static ResonanceNames get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(ResonanceNames::load, ResonanceNames::new, STORAGE_ID);
    }


    public @Nullable String nameOf(String networkKey) {
        return byNetwork.get(networkKey);
    }

    public static @Nullable String nameOf(MinecraftServer server, @Nullable String networkKey) {
        return networkKey == null ? null : get(server).nameOf(networkKey);
    }


    public static String nameOrAssign(MinecraftServer server, String networkKey) {
        ResonanceNames names = get(server);
        String existing = names.byNetwork.get(networkKey);
        if (existing != null) {
            return existing;
        }
        String assigned = names.assign(networkKey);
        names.setDirty();
        com.bluup.hexwright.inits.HexwrightNetworking.broadcastResonanceName(server, networkKey, assigned);
        return assigned;
    }

    public static void rename(MinecraftServer server, String networkKey, String name) {
        ResonanceNames names = get(server);
        if (name.equals(names.byNetwork.get(networkKey))) {
            return;
        }
        names.byNetwork.put(networkKey, name);
        names.setDirty();
        com.bluup.hexwright.inits.HexwrightNetworking.broadcastResonanceName(server, networkKey, name);
    }

    public static @Nullable String normalize(String requested) {
        String cleaned = requested.replace("§", "").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() <= MAX_NAME_LENGTH ? cleaned : cleaned.substring(0, MAX_NAME_LENGTH).trim();
    }

    private String assign(String networkKey) {
        Set<String> taken = new HashSet<>();
        for (String name : byNetwork.values()) {
            taken.add(name.toLowerCase(Locale.ROOT));
        }

        int start = Math.floorMod(networkKey.hashCode(), WORDS.length);
        for (int offset = 0; offset < WORDS.length; offset++) {
            String candidate = WORDS[(start + offset) % WORDS.length];
            if (!taken.contains(candidate.toLowerCase(Locale.ROOT))) {
                byNetwork.put(networkKey, candidate);
                return candidate;
            }
        }

        for (int suffix = 2; ; suffix++) {
            String candidate = WORDS[start] + " " + suffix;
            if (!taken.contains(candidate.toLowerCase(Locale.ROOT))) {
                byNetwork.put(networkKey, candidate);
                return candidate;
            }
        }
    }


    public void writeAll(FriendlyByteBuf buf) {
        buf.writeVarInt(byNetwork.size());
        for (Map.Entry<String, String> entry : byNetwork.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeUtf(entry.getValue());
        }
    }

    private ResonanceNames() {
    }

    private static ResonanceNames load(CompoundTag tag) {
        ResonanceNames names = new ResonanceNames();
        ListTag entries = tag.getList("Names", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            String networkKey = entry.getString("Network");
            String name = entry.getString("Name");
            if (!networkKey.isEmpty() && !name.isEmpty()) {
                names.byNetwork.put(networkKey, name);
            }
        }
        return names;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        for (Map.Entry<String, String> entry : byNetwork.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("Network", entry.getKey());
            entryTag.putString("Name", entry.getValue());
            entries.add(entryTag);
        }
        tag.put("Names", entries);
        return tag;
    }
}
