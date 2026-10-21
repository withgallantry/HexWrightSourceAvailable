package com.bluup.hexwright.server.soulforge;

import at.petrak.hexcasting.common.lib.HexAttributes;
import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.soulforge.SoulforgeLedger.Record;
import com.bluup.hexwright.server.soulforge.SoulforgeLedger.State;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FastColor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Soulforge {

    public static final int TITHE_PERCENT = 25;

    private static final UUID TITHE_ID = UUID.fromString("a41e6c2d-7b39-4f85-9d10-3c8e5f2b6a74");

    public static final int ABANDON_DAYS = 21;
    private static final long ABANDON_MILLIS = ABANDON_DAYS * 24L * 60L * 60L * 1000L;

    public static final int UNCLAIMED_MINUTES = 60;
    private static final long UNCLAIMED_MILLIS = UNCLAIMED_MINUTES * 60L * 1000L;

    private static final int SYNC_INTERVAL_TICKS = 20;

    private static final int REMEMBER_INTERVAL_TICKS = 20;

    private static final int ABSENCE_INTERVAL_TICKS = 1200;

    private static final long ABSENCE_STEP_CAP_MILLIS = 120_000L;

    private static final int TOGGLE_COOLDOWN_TICKS = 10;

    private record Sighting(ServerPlayer holder, ItemStack stack) {
    }

    private static final List<Sighting> SIGHTINGS = new ArrayList<>();
    private static final Map<UUID, Long> LAST_TOGGLE = new HashMap<>();
    private static long lastAbsenceCheck;

    @Nullable
    private static List<SoulforgedItem> soulforged;

    private Soulforge() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Soulforge::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST_TOGGLE.remove(handler.player.getUUID()));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> syncCosts(newPlayer));
        ServerEntityEvents.ENTITY_LOAD.register(Soulforge::onEntityLoad);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SIGHTINGS.clear();
            LAST_TOGGLE.clear();
            lastAbsenceCheck = 0L;
        });
        SoulforgeLoot.register();
        SoulforgeCommand.register();
    }

    private static void tick(MinecraftServer server) {
        long now = server.getTickCount();
        if (!SIGHTINGS.isEmpty()) {
            settle(server, now);
        }
        if (now % SYNC_INTERVAL_TICKS == 0) {
            SoulforgeLedger ledger = SoulforgeLedger.get(server);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                Record record = ledger.ownedBy(player.getUUID());
                syncCosts(player, record);
                if (record != null && player.isAlive() && record.item() instanceof SoulforgedItem weapon) {
                    weapon.bondTick(player);
                }
            }
        }
        if (now % ABSENCE_INTERVAL_TICKS == 0) {
            age(server);
        }
    }


    public static void inventoryTick(ItemStack stack, Level level, Entity holder) {
        if (!level.isClientSide() && holder instanceof ServerPlayer player && !stack.isEmpty()) {
            SIGHTINGS.add(new Sighting(player, stack));
        }
    }

    private static void settle(MinecraftServer server, long now) {
        Map<Item, List<Sighting>> byWeapon = new LinkedHashMap<>();
        for (Sighting sighting : SIGHTINGS) {
            if (!sighting.stack.isEmpty() && !sighting.holder.isRemoved()) {
                byWeapon.computeIfAbsent(sighting.stack.getItem(), item -> new ArrayList<>()).add(sighting);
            }
        }
        SIGHTINGS.clear();
        SoulforgeLedger ledger = SoulforgeLedger.get(server);
        for (Map.Entry<Item, List<Sighting>> entry : byWeapon.entrySet()) {
            settle(ledger, ledger.record(entry.getKey()), entry.getValue(), now);
        }
    }

    private static void settle(SoulforgeLedger ledger, Record record, List<Sighting> sightings, long now) {
        List<Sighting> current = new ArrayList<>();
        Sighting unissued = null;
        for (Sighting sighting : sightings) {
            if (record.isCurrent(sighting.stack)) {
                current.add(sighting);
            } else if (SoulBond.generation(sighting.stack) == 0 && record.state == State.POOL
                && drops(record.item()) && unissued == null) {
                unissued = sighting;
            } else {
                crumble(sighting.holder, sighting.stack);
            }
        }
        if (unissued != null) {
            ledger.release(record, unissued.stack);
            current.add(unissued);
        }
        if (current.isEmpty()) {
            return;
        }
        Sighting keeper = keeper(record, current, now);
        if (current.size() > 1) {
            for (Sighting copy : current) {
                if (copy != keeper) {
                    crumble(copy.holder, copy.stack);
                }
            }
            ledger.restamp(record, keeper.stack);
            HexwrightDebug.log(HexwrightDebug.SOULFORGE, "[soulforge] {} copies of {} carried at once; kept {}'s",
                current.size(), record.item, keeper.holder.getScoreboardName());
        }
        carried(ledger, record, keeper, now);
    }

    private static Sighting keeper(Record record, List<Sighting> current, long now) {
        Sighting best = current.get(0);
        int bestScore = -1;
        for (Sighting sighting : current) {
            int score = 0;
            if (sighting.holder.getUUID().equals(record.owner)) {
                score += 4;
            }
            if (sighting.holder.getUUID().equals(record.lastHolder) && record.carriedTick == now - 1) {
                score += 2;
            }
            if (sighting.holder.getMainHandItem() == sighting.stack) {
                score += 1;
            }
            if (score > bestScore) {
                bestScore = score;
                best = sighting;
            }
        }
        return best;
    }

    private static void carried(SoulforgeLedger ledger, Record record, Sighting sighting, long now) {
        ServerPlayer holder = sighting.holder;
        ItemStack stack = sighting.stack;
        boolean pickedUp = record.carriedTick != now - 1 || !holder.getUUID().equals(record.lastHolder);
        if (record.state == State.LOOSE) {
            if (ledger.isBlacklisted(holder.getUUID())) {
                refuse(ledger, record, holder, stack);
                return;
            }
            Record owned = ledger.ownedBy(holder.getUUID());
            if (owned == null) {
                claim(ledger, record, holder, stack);
            } else if (pickedUp) {
                ledger.restamp(record, stack);
                holder.displayClientMessage(cannotKeep(owned, record), true);
            }
        } else if (!holder.getUUID().equals(record.owner)) {
            returnToOwner(holder, stack, ledger, record);
            return;
        } else if (pickedUp) {
            ledger.restamp(record, stack);
        } else if (now % REMEMBER_INTERVAL_TICKS == 0) {
            ledger.remember(record, stack);
        }
        record.carriedTick = now;
        record.lastHolder = holder.getUUID();
    }

    private static void claim(SoulforgeLedger ledger, Record record, ServerPlayer finder, ItemStack stack) {
        ledger.bind(record, finder, stack);
        syncCosts(finder);
        ServerLevel level = finder.serverLevel();
        burst(level, finder, ParticleTypes.SCULK_SOUL, 40);
        play(level, finder, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, 0.8F);
        play(level, finder, SoundEvents.SOUL_ESCAPE, 1.0F);
        finder.displayClientMessage(Component.translatable("message.hexwright.soulforge.bound", stack.getHoverName())
            .withStyle(style -> style.withColor(SoulforgedItem.COLOUR)), false);
        announce(finder.server, Component.translatable("message.hexwright.soulforge.claimed",
            finder.getDisplayName(), stack.getHoverName()));
        HexwrightDebug.log(HexwrightDebug.SOULFORGE, "[soulforge] {} claimed {}", finder.getScoreboardName(), record.item);
    }

    private static Component cannotKeep(Record owned, Record found) {
        if (!drops(found.item())) {
            return Component.translatable("message.hexwright.soulforge.already_bound", owned.item().getDescription())
                .withStyle(ChatFormatting.RED);
        }
        long minutesLeft = Math.max(1L, (UNCLAIMED_MILLIS - found.absentMillis + 59_999L) / 60_000L);
        return Component.translatable("message.hexwright.soulforge.cannot_keep", owned.item().getDescription(),
            found.item().getDescription(), minutesLeft).withStyle(ChatFormatting.RED);
    }

    private static void refuse(SoulforgeLedger ledger, Record record, ServerPlayer holder, ItemStack stack) {
        Component name = stack.getHoverName();
        stack.setCount(0);
        ledger.returnToPool(record);
        ServerLevel level = holder.serverLevel();
        burst(level, holder, ParticleTypes.SOUL, 16);
        play(level, holder, SoundEvents.SOUL_ESCAPE, 0.8F);
        holder.displayClientMessage(Component.translatable("message.hexwright.soulforge.blacklisted", name)
            .withStyle(ChatFormatting.GRAY), true);
        announce(holder.server, returned(record.item(), name));
        HexwrightDebug.log(HexwrightDebug.SOULFORGE, "[soulforge] {} refused blacklisted {}",
            record.item, holder.getScoreboardName());
    }

    public static boolean mayWield(Player player, ItemStack stack) {
        if (SoulBond.isOwnedBy(stack, player)) {
            return true;
        }
        if (player instanceof ServerPlayer server && !SoulBond.isBound(stack)) {
            Record owned = SoulforgeLedger.get(server.server).ownedBy(server.getUUID());
            if (owned != null) {
                server.displayClientMessage(Component.translatable("message.hexwright.soulforge.already_bound",
                    owned.item().getDescription()).withStyle(ChatFormatting.RED), true);
            }
        }
        return false;
    }

    public static boolean ownsOne(ServerPlayer player) {
        return SoulforgeLedger.get(player.server).ownedBy(player.getUUID()) != null;
    }

    public static boolean owns(ServerPlayer player, Item item) {
        Record record = SoulforgeLedger.get(player.server).ownedBy(player.getUUID());
        return record != null && record.item() == item;
    }


    public static void toggle(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }
        long now = player.server.getTickCount();
        Long last = LAST_TOGGLE.get(player.getUUID());
        if (last != null && now - last < TOGGLE_COOLDOWN_TICKS) {
            return;
        }
        LAST_TOGGLE.put(player.getUUID(), now);

        SoulforgeLedger ledger = SoulforgeLedger.get(player.server);
        Record record = ledger.ownedBy(player.getUUID());
        if (record == null) {
            player.displayClientMessage(Component.translatable("message.hexwright.soulforge.no_bond")
                .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        int slot = find(player.getInventory(), record);
        if (slot == player.getInventory().selected) {
            dismiss(player, ledger, record, slot);
        } else {
            toHand(player, ledger, record, slot);
        }
    }

    public static boolean recall(ServerPlayer player) {
        SoulforgeLedger ledger = SoulforgeLedger.get(player.server);
        Record record = ledger.ownedBy(player.getUUID());
        if (record == null) {
            return false;
        }
        int slot = find(player.getInventory(), record);
        if (slot != player.getInventory().selected) {
            toHand(player, ledger, record, slot);
        }
        return true;
    }

    public static boolean dismiss(ServerPlayer player) {
        SoulforgeLedger ledger = SoulforgeLedger.get(player.server);
        Record record = ledger.ownedBy(player.getUUID());
        if (record == null) {
            return false;
        }
        int slot = find(player.getInventory(), record);
        if (slot >= 0) {
            dismiss(player, ledger, record, slot);
        } else if (!record.dismissed) {
            ledger.stow(record, null);
            dismissed(player, record, null);
        }
        return true;
    }

    private static void toHand(ServerPlayer player, SoulforgeLedger ledger, Record record, int slot) {
        if (slot >= 0) {
            handOver(player, player.getInventory().removeItemNoUpdate(slot));
            manifest(player, record, InteractionHand.MAIN_HAND, true);
            return;
        }
        boolean fromSoul = record.dismissed;
        handOver(player, ledger.draw(record));
        record.carriedTick = player.server.getTickCount();
        record.lastHolder = player.getUUID();
        manifest(player, record, InteractionHand.MAIN_HAND, true);
        HexwrightDebug.log(HexwrightDebug.SOULFORGE, "[soulforge] {} recalled {} (generation {}, {})",
            player.getScoreboardName(), record.item, record.generation, fromSoul ? "from the soul" : "from the world");
    }

    private static void dismiss(ServerPlayer player, SoulforgeLedger ledger, Record record, int slot) {
        Inventory inventory = player.getInventory();
        ledger.stow(record, inventory.getItem(slot));
        inventory.setItem(slot, ItemStack.EMPTY);
        InteractionHand hand = slot == inventory.selected ? InteractionHand.MAIN_HAND
            : slot == Inventory.SLOT_OFFHAND ? InteractionHand.OFF_HAND
            : null;
        dismissed(player, record, hand);
    }

    private static void dismissed(ServerPlayer player, Record record, @Nullable InteractionHand hand) {
        manifest(player, record, hand, false);
        HexwrightDebug.log(HexwrightDebug.SOULFORGE, "[soulforge] {} dismissed their weapon",
            player.getScoreboardName());
    }

    private static void manifest(ServerPlayer player, Record record, @Nullable InteractionHand hand,
                                 boolean arriving) {
        if (!(record.item() instanceof SoulforgedItem item)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (hand != null) {
            HexwrightNetworking.sendSoulforgedMaterialise(player, record.item(), hand, arriving);
        } else {
            int colour = item.materialiseColour();
            DustParticleOptions dust = new DustParticleOptions(new Vector3f(FastColor.ARGB32.red(colour) / 255.0F,
                FastColor.ARGB32.green(colour) / 255.0F, FastColor.ARGB32.blue(colour) / 255.0F), 1.0F);
            level.sendParticles(dust, player.getX(), player.getY() + 1.0D, player.getZ(), 16, 0.3D, 0.5D, 0.3D, 0.0D);
        }
        item.playManifest(level, player, arriving);
    }

    private static void handOver(ServerPlayer player, ItemStack weapon) {
        Inventory inventory = player.getInventory();
        ItemStack displaced = inventory.getSelected();
        inventory.setItem(inventory.selected, weapon);
        if (!displaced.isEmpty() && !inventory.add(displaced)) {
            player.drop(displaced, false);
        }
    }

    private static void takeFrom(@Nullable ServerPlayer player, Record record) {
        if (player == null) {
            return;
        }
        int slot = find(player.getInventory(), record);
        if (slot >= 0) {
            player.getInventory().setItem(slot, ItemStack.EMPTY);
        }
    }

    private static int find(Inventory inventory, Record record) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (record.isCurrent(inventory.getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }


    public static void onDied(ServerPlayer player) {
        SoulforgeLedger ledger = SoulforgeLedger.get(player.server);
        Record record = ledger.ownedBy(player.getUUID());
        if (record == null) {
            return;
        }
        int slot = find(player.getInventory(), record);
        if (slot >= 0) {
            ledger.stow(record, player.getInventory().getItem(slot));
            player.getInventory().setItem(slot, ItemStack.EMPTY);
        }
    }

    public static boolean onThrown(Player thrower, ItemStack stack) {
        if (!(thrower instanceof ServerPlayer player) || !(stack.getItem() instanceof SoulforgedItem)) {
            return false;
        }
        SoulforgeLedger ledger = SoulforgeLedger.get(player.server);
        Record record = ledger.record(stack.getItem());
        if (!record.isCurrent(stack)) {
            if (SoulBond.generation(stack) == 0 && record.state == State.POOL && drops(record.item())) {
                return false;
            }
            crumble(player, stack);
            return true;
        }
        if (record.state == State.LOOSE) {
            return false;
        }
        if (player.getUUID().equals(record.owner)) {
            ledger.stow(record, stack);
            stack.setCount(0);
            dismissed(player, record, null);
        } else {
            returnToOwner(player, stack, ledger, record);
        }
        return true;
    }

    private static void onEntityLoad(Entity entity, ServerLevel level) {
        if (entity instanceof ItemEntity item && item.getItem().getItem() instanceof SoulforgedItem) {
            item.setUnlimitedLifetime();
        }
    }

    public static void onDestroyed(ItemEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        ItemStack stack = entity.getItem();
        SoulforgeLedger ledger = SoulforgeLedger.get(level.getServer());
        Record record = ledger.record(stack.getItem());
        if (record.state == State.LOOSE && record.isCurrent(stack)) {
            ledger.returnToPool(record);
            announce(level.getServer(), returned(record.item(), stack.getHoverName()));
        }
    }

    private static void crumble(ServerPlayer holder, ItemStack stack) {
        Component name = stack.getHoverName();
        stack.setCount(0);
        ServerLevel level = holder.serverLevel();
        burst(level, holder, ParticleTypes.ASH, 20);
        play(level, holder, SoundEvents.SOUL_SAND_BREAK, 0.7F);
        holder.displayClientMessage(Component.translatable("message.hexwright.soulforge.crumbled", name)
            .withStyle(ChatFormatting.DARK_GRAY), true);
    }

    private static void returnToOwner(ServerPlayer holder, ItemStack stack, SoulforgeLedger ledger, Record record) {
        Component name = stack.getHoverName();
        ledger.stow(record, stack);
        stack.setCount(0);
        ServerLevel level = holder.serverLevel();
        burst(level, holder, ParticleTypes.SOUL, 16);
        play(level, holder, SoundEvents.SOUL_ESCAPE, 0.8F);
        holder.displayClientMessage(Component.translatable("message.hexwright.soulforge.refused", name, record.ownerName)
            .withStyle(ChatFormatting.GRAY), true);
        ServerPlayer owner = record.owner == null ? null : holder.server.getPlayerList().getPlayer(record.owner);
        if (owner != null) {
            owner.displayClientMessage(Component.translatable("message.hexwright.soulforge.returned", name)
                .withStyle(style -> style.withColor(SoulforgedItem.COLOUR)), true);
        }
        HexwrightDebug.log(HexwrightDebug.SOULFORGE, "[soulforge] {}'s weapon slipped from {} back to its owner",
            record.ownerName, holder.getScoreboardName());
    }


    @Nullable
    public static Item unbind(MinecraftServer server, UUID owner) {
        SoulforgeLedger ledger = SoulforgeLedger.get(server);
        Record record = ledger.ownedBy(owner);
        if (record == null) {
            return null;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        takeFrom(player, record);
        Item item = record.item();
        ledger.returnToPool(record);
        if (player != null) {
            syncCosts(player, null);
            ServerLevel level = player.serverLevel();
            burst(level, player, ParticleTypes.SOUL, 40);
            play(level, player, SoundEvents.SOUL_ESCAPE, 0.5F);
            play(level, player, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 0.8F);
        }
        announce(server, returned(item, item.getDescription()));
        HexwrightDebug.log(HexwrightDebug.SOULFORGE, "[soulforge] {} unbound from {}", record.item, owner);
        return item;
    }

    public static boolean release(MinecraftServer server, Item item) {
        SoulforgeLedger ledger = SoulforgeLedger.get(server);
        Record record = ledger.record(item);
        if (record.state == State.POOL) {
            return false;
        }
        if (record.state == State.BOUND && record.owner != null) {
            unbind(server, record.owner);
        } else {
            ledger.returnToPool(record);
            announce(server, returned(item, item.getDescription()));
        }
        return true;
    }

    static boolean inPool(MinecraftServer server, Item item) {
        return drops(item) && SoulforgeLedger.get(server).record(item).state == State.POOL;
    }

    static boolean releaseFromPool(MinecraftServer server, ItemStack stack) {
        return drops(stack.getItem()) && issue(server, stack, "dropped from the pool");
    }

    public static void onGiven(MinecraftServer server, ItemStack stack) {
        if (!drops(stack.getItem())) {
            issue(server, stack, "given by command");
        }
    }

    private static boolean issue(MinecraftServer server, ItemStack stack, String how) {
        SoulforgeLedger ledger = SoulforgeLedger.get(server);
        Record record = ledger.record(stack.getItem());
        if (record.state != State.POOL) {
            return false;
        }
        ledger.release(record, stack);
        HexwrightDebug.log(HexwrightDebug.SOULFORGE, "[soulforge] {} {}", record.item, how);
        return true;
    }

    static boolean drops(Item item) {
        return !(item instanceof SoulforgedItem forged) || forged.drops();
    }

    private static void age(MinecraftServer server) {
        long wall = System.currentTimeMillis();
        long step = lastAbsenceCheck == 0L ? 0L : Math.max(0L, Math.min(ABSENCE_STEP_CAP_MILLIS, wall - lastAbsenceCheck));
        lastAbsenceCheck = wall;
        if (step == 0L) {
            return;
        }
        SoulforgeLedger ledger = SoulforgeLedger.get(server);
        for (Record record : List.copyOf(ledger.records())) {
            if (!drops(record.item())) {
                continue;
            }
            boolean tended = switch (record.state) {
                case POOL -> true;
                case LOOSE -> false;
                case BOUND -> record.owner != null && server.getPlayerList().getPlayer(record.owner) != null;
            };
            ledger.age(record, tended ? -1L : step);
            long limit = record.state == State.LOOSE ? UNCLAIMED_MILLIS : ABANDON_MILLIS;
            if (record.absentMillis >= limit) {
                abandon(server, ledger, record);
            }
        }
    }

    private static void abandon(MinecraftServer server, SoulforgeLedger ledger, Record record) {
        Item item = record.item();
        if (record.owner != null) {
            ledger.noteLapsed(record.owner, record);
        } else if (record.lastHolder != null) {
            takeFrom(server.getPlayerList().getPlayer(record.lastHolder), record);
        }
        ledger.returnToPool(record);
        announce(server, returned(item, item.getDescription()));
        HexwrightDebug.log(HexwrightDebug.SOULFORGE, "[soulforge] {} went untended and returned to the pool", record.item);
    }

    private static void onJoin(ServerPlayer player) {
        SoulforgeLedger ledger = SoulforgeLedger.get(player.server);
        Record record = ledger.ownedBy(player.getUUID());
        if (record != null) {
            ledger.age(record, -1L);
        }
        syncCosts(player, record);
        Item lapsed = ledger.takeLapsed(player.getUUID());
        if (lapsed != null) {
            player.displayClientMessage(Component.translatable("message.hexwright.soulforge.lapsed",
                lapsed.getDescription(), ABANDON_DAYS).withStyle(ChatFormatting.GRAY), false);
        }
    }


    public static boolean blacklist(MinecraftServer server, GameProfile player) {
        if (!SoulforgeLedger.get(server).addToBlacklist(player.getId(), player.getName())) {
            return false;
        }
        unbind(server, player.getId());
        HexwrightDebug.log(HexwrightDebug.SOULFORGE, "[soulforge] {} blacklisted", player.getName());
        return true;
    }

    public static boolean unblacklist(MinecraftServer server, UUID player) {
        return SoulforgeLedger.get(server).removeFromBlacklist(player);
    }

    public static List<String> blacklisted(MinecraftServer server) {
        return SoulforgeLedger.get(server).blacklistedNames().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }


    @Nullable
    public static Component describe(MinecraftServer server, UUID owner) {
        Record record = SoulforgeLedger.get(server).ownedBy(owner);
        return record == null ? null : describe(record);
    }

    public static List<Component> describeAll(MinecraftServer server) {
        SoulforgeLedger ledger = SoulforgeLedger.get(server);
        List<Component> lines = new ArrayList<>();
        for (SoulforgedItem weapon : soulforged()) {
            lines.add(describe(ledger.record((Item) weapon)));
        }
        return lines;
    }

    private static Component describe(Record record) {
        long days = record.absentMillis / (24L * 60L * 60L * 1000L);
        return switch (record.state) {
            case POOL -> Component.translatable("message.hexwright.soulforge.info_pool", record.item().getDescription());
            case LOOSE -> Component.translatable("message.hexwright.soulforge.info_loose",
                record.item().getDescription(), record.generation, record.absentMillis / 60_000L);
            case BOUND -> Component.translatable(record.dismissed
                    ? "message.hexwright.soulforge.info_dismissed" : "message.hexwright.soulforge.info_carried",
                record.ownerName, record.item().getDescription(), record.generation, days);
        };
    }


    public static void syncCosts(ServerPlayer player) {
        syncCosts(player, SoulforgeLedger.get(player.server).ownedBy(player.getUUID()));
    }

    private static void syncCosts(ServerPlayer player, @Nullable Record record) {
        Item weapon = record == null ? null : record.item();
        int tithe = weapon instanceof SoulforgedItem forged ? forged.tithePercent() : 0;
        assertModifier(player, HexAttributes.MEDIA_CONSUMPTION_MODIFIER, new AttributeModifier(TITHE_ID,
            "hexwright_soul_tithe", tithe / 100.0D, AttributeModifier.Operation.ADDITION), tithe > 0);
        for (SoulforgedItem item : soulforged()) {
            boolean owned = item == weapon;
            for (Map.Entry<Attribute, AttributeModifier> cost : item.bondCosts().entries()) {
                assertModifier(player, cost.getKey(), cost.getValue(), owned);
            }
        }
    }

    private static void assertModifier(ServerPlayer player, Attribute attribute, AttributeModifier modifier,
                                       boolean wanted) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier present = instance.getModifier(modifier.getId());
        if (present != null && (!wanted || present.getAmount() != modifier.getAmount())) {
            instance.removeModifier(modifier.getId());
            present = null;
        }
        if (wanted && present == null) {
            instance.addTransientModifier(modifier);
        }
    }

    private static List<SoulforgedItem> soulforged() {
        if (soulforged == null) {
            soulforged = BuiltInRegistries.ITEM.stream()
                .filter(SoulforgedItem.class::isInstance)
                .map(SoulforgedItem.class::cast)
                .toList();
        }
        return soulforged;
    }


    private static Component returned(Item item, Component name) {
        return Component.translatable(drops(item) ? "message.hexwright.soulforge.returned_to_world"
            : "message.hexwright.soulforge.faded", name);
    }

    private static void announce(MinecraftServer server, Component message) {
        server.getPlayerList().broadcastSystemMessage(message.copy().withStyle(style -> style.withColor(SoulforgedItem.COLOUR)),
            false);
    }

    private static void burst(ServerLevel level, ServerPlayer player, ParticleOptions particle, int count) {
        level.sendParticles(particle, player.getX(), player.getY() + 1.0D, player.getZ(),
            count, 0.4D, 0.6D, 0.4D, 0.03D);
    }

    static void play(ServerLevel level, ServerPlayer player, SoundEvent sound, float pitch) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 0.9F, pitch);
    }
}
