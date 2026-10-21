package com.bluup.hexwright.server.progression;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ArtifactProvenance extends SavedData {

    private static final String STORAGE_ID = "hexwright_artifact_provenance";
    private static final String ROOT_TAG = "hexwright_provenance";
    private static final String TAG_ID = "Id";

    private static final UUID CREATIVE = new UUID(0L, 0L);

    private static final String RUNNING_MARKER = STORAGE_ID + ".running";

    private static final class Line {
        private final Item item;
        private final @Nullable UUID owner;
        private int balance;

        private Line(Item item, @Nullable UUID owner, int balance) {
            this.item = item;
            this.owner = owner;
            this.balance = balance;
        }
    }

    private final Map<UUID, Line> lines = new HashMap<>();

    private final Map<UUID, Map<Item, UUID>> accounts = new HashMap<>();

    public static void register() {
        Registry.register(BuiltInRegistries.LOOT_FUNCTION_TYPE, Hexwright.id("stamp_provenance"), StampProvenanceFunc.TYPE);
        ServerLifecycleEvents.SERVER_STARTED.register(ArtifactProvenance::started);
        ServerLifecycleEvents.SERVER_STOPPED.register(ArtifactProvenance::stopped);
    }

    private static ArtifactProvenance get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(ArtifactProvenance::load, ArtifactProvenance::new, STORAGE_ID);
    }

    public static ItemStack stamp(ItemStack stack, MinecraftServer server, @Nullable ServerPlayer finder) {
        if (stack.isEmpty()) {
            return stack;
        }
        if (finder != null && finder.isCreative()) {
            setId(stack, CREATIVE);
            return stack;
        }
        ArtifactProvenance ledger = get(server);
        UUID id = finder != null ? ledger.accountOf(finder, stack.getItem()) : ledger.openLot(stack.getItem());
        ledger.credit(id, stack.getCount());
        setId(stack, id);
        HexwrightDebug.log(HexwrightDebug.PROVENANCE, "[provenance] issued {} x{} to {}",
            itemId(stack), stack.getCount(), finder != null ? finder.getGameProfile().getName() : "lot " + id);
        return stack;
    }

    public static void hold(ItemStack stack, Entity entity) {
        if (!(entity instanceof ServerPlayer holder) || stack.isEmpty()) {
            return;
        }
        UUID id = idOf(stack);
        if (holder.isCreative()) {
            if (!CREATIVE.equals(id)) {
                setId(stack, CREATIVE);
                restack(stack, holder.getInventory());
            }
            return;
        }
        ArtifactProvenance ledger = get(holder.server);
        UUID account = ledger.accountOf(holder, stack.getItem());
        if (account.equals(id)) {
            return;
        }
        int count = stack.getCount();
        String from = ledger.describe(holder.server, id);
        int shortfall = ledger.take(id, stack.getItem(), count);
        ledger.credit(account, count);
        setId(stack, account);
        if (shortfall > 0) {
            report(holder, stack, "picked up " + count + " of them", shortfall, count - shortfall, from);
        } else {
            HexwrightDebug.log(HexwrightDebug.PROVENANCE, "[provenance] {} took on {} x{} from {}",
                holder.getGameProfile().getName(), itemId(stack), count, from);
        }
        restack(stack, holder.getInventory());
    }

    private static void restack(ItemStack stack, Inventory inventory) {
        for (ItemStack other : inventory.items) {
            if (stack.isEmpty()) {
                return;
            }
            if (other != stack && ItemStack.isSameItemSameTags(other, stack) && other.getCount() < other.getMaxStackSize()) {
                int moved = Math.min(stack.getCount(), other.getMaxStackSize() - other.getCount());
                other.grow(moved);
                stack.shrink(moved);
            }
        }
    }

    public static void spend(ItemStack stack, int count, ServerPlayer spender, String where) {
        if (stack.isEmpty() || spender.isCreative()) {
            return;
        }
        UUID id = idOf(stack);
        ArtifactProvenance ledger = get(spender.server);
        String from = ledger.describe(spender.server, id);
        int shortfall = ledger.take(id, stack.getItem(), count);
        if (shortfall > 0) {
            report(spender, stack, "used " + count + " of them " + where, shortfall, count - shortfall, from);
        } else {
            HexwrightDebug.log(HexwrightDebug.PROVENANCE, "[provenance] {} used {} x{} {}",
                spender.getGameProfile().getName(), itemId(stack), count, where);
        }
    }

    private static @Nullable UUID idOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        CompoundTag tag = stack.getTagElement(ROOT_TAG);
        return tag != null && tag.hasUUID(TAG_ID) ? tag.getUUID(TAG_ID) : null;
    }

    private static void setId(ItemStack stack, UUID id) {
        stack.getOrCreateTagElement(ROOT_TAG).putUUID(TAG_ID, id);
    }

    private UUID accountOf(ServerPlayer player, Item item) {
        Map<Item, UUID> held = accounts.computeIfAbsent(player.getUUID(), uuid -> new HashMap<>());
        UUID existing = held.get(item);
        if (existing != null) {
            return existing;
        }
        UUID id = UUID.randomUUID();
        held.put(item, id);
        lines.put(id, new Line(item, player.getUUID(), 0));
        setDirty();
        return id;
    }

    private UUID openLot(Item item) {
        UUID id = UUID.randomUUID();
        lines.put(id, new Line(item, null, 0));
        setDirty();
        return id;
    }

    private void credit(UUID id, int count) {
        lines.get(id).balance += count;
        setDirty();
    }

    private int take(@Nullable UUID id, Item item, int count) {
        Line line = id == null ? null : lines.get(id);
        if (line == null || line.item != item) {
            return 0;
        }
        int shortfall = Math.max(count - line.balance, 0);
        line.balance = Math.max(line.balance - count, 0);
        setDirty();
        return shortfall;
    }

    private void reset() {
        lines.clear();
        accounts.clear();
        setDirty();
    }

    private String describe(MinecraftServer server, @Nullable UUID id) {
        if (id == null) {
            return "no account (never stamped)";
        }
        Line line = lines.get(id);
        if (line == null) {
            return "an id this ledger never issued (" + id + ")";
        }
        if (line.owner == null) {
            return "loot rolled for no one in particular (lot " + id + ")";
        }
        return "the account of " + nameOf(server, line.owner) + " (" + line.owner + "), the last player to carry them";
    }

    private static String nameOf(MinecraftServer server, UUID player) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        GameProfileCache profiles = server.getProfileCache();
        return profiles == null ? "an unknown player" : profiles.get(player).map(GameProfile::getName).orElse("an unknown player");
    }

    private static void report(ServerPlayer player, ItemStack stack, String action, int shortfall, int covered, String from) {
        String name = player.getGameProfile().getName();
        Hexwright.LOGGER.warn(
            "[provenance] {} more {} than were ever issued: {} ({}) {}, held under {}, which could only cover {}. {} was at {} {}",
            shortfall, itemId(stack), name, player.getUUID(), action, from, covered,
            name, player.level().dimension().location(), player.blockPosition().toShortString()
        );
    }

    private static String itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static void started(MinecraftServer server) {
        Path marker = runningMarker(server);
        if (Files.exists(marker)) {
            get(server).reset();
            Hexwright.LOGGER.warn("[provenance] The last run did not shut down cleanly, so the provenance ledger has been reset; "
                + "Golem Cores and Artifact Ruined Tablets already in the world are no longer checked for duplication");
        }
        try {
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, "");
        } catch (IOException e) {
            Hexwright.LOGGER.error("[provenance] Could not write {}; a crash this run will not reset the ledger", marker, e);
        }
    }

    private static void stopped(MinecraftServer server) {
        try {
            Files.deleteIfExists(runningMarker(server));
        } catch (IOException e) {
            Hexwright.LOGGER.error("[provenance] Could not delete {}; the next start will reset the ledger", runningMarker(server), e);
        }
    }

    private static Path runningMarker(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(RUNNING_MARKER);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag saved = new ListTag();
        for (Map.Entry<UUID, Line> entry : lines.entrySet()) {
            Line line = entry.getValue();
            CompoundTag out = new CompoundTag();
            out.putUUID("Id", entry.getKey());
            out.putString("Item", BuiltInRegistries.ITEM.getKey(line.item).toString());
            out.putInt("Balance", line.balance);
            if (line.owner != null) {
                out.putUUID("Owner", line.owner);
            }
            saved.add(out);
        }
        tag.put("Ledger", saved);
        return tag;
    }

    private static ArtifactProvenance load(CompoundTag tag) {
        ArtifactProvenance ledger = new ArtifactProvenance();
        for (Tag entry : tag.getList("Ledger", Tag.TAG_COMPOUND)) {
            CompoundTag in = (CompoundTag) entry;
            ResourceLocation itemId = ResourceLocation.tryParse(in.getString("Item"));
            Item item = itemId == null ? null : BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
            if (item == null) {
                continue;
            }
            UUID id = in.getUUID("Id");
            UUID owner = in.hasUUID("Owner") ? in.getUUID("Owner") : null;
            ledger.lines.put(id, new Line(item, owner, in.getInt("Balance")));
            if (owner != null) {
                ledger.accounts.computeIfAbsent(owner, uuid -> new HashMap<>()).put(item, id);
            }
        }
        return ledger;
    }
}
