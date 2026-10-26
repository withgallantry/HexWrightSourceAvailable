package com.bluup.hexwright.server.soulforge;

import at.petrak.hexcasting.common.lib.HexAttributes;
import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.soulforge.SoulforgeLedger.Bond;
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

    public static final int DOOM_MINUTES = 30;
    private static final long DOOM_MILLIS = DOOM_MINUTES * 60L * 1000L;

    private static final int SYNC_INTERVAL_TICKS = 20;

    private static final int REMEMBER_INTERVAL_TICKS = 20;

    private static final int TOGGLE_COOLDOWN_TICKS = 10;

    private record Sighting(ServerPlayer holder, ItemStack stack) {
    }

    private static final List<Sighting> SIGHTINGS = new ArrayList<>();
    private static final Map<UUID, Long> LAST_TOGGLE = new HashMap<>();

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
            long wall = System.currentTimeMillis();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                Bond bond = ledger.ownedBy(player.getUUID());
                if (bond != null && bond.doomAt != 0L && wall >= bond.doomAt) {
                    crumbleAway(player, ledger, bond);
                    bond = null;
                }
                syncCosts(player, bond);
                if (bond != null && player.isAlive() && bond.item() instanceof SoulforgedItem weapon) {
                    weapon.bondTick(player);
                }
            }
        }
    }


    public static void inventoryTick(ItemStack stack, Level level, Entity holder) {
        if (!level.isClientSide() && holder instanceof ServerPlayer player && !stack.isEmpty()) {
            SIGHTINGS.add(new Sighting(player, stack));
        }
    }

    private static void settle(MinecraftServer server, long now) {
        SoulforgeLedger ledger = SoulforgeLedger.get(server);
        long wall = System.currentTimeMillis();
        Map<Bond, List<Sighting>> bound = new LinkedHashMap<>();
        Map<ServerPlayer, List<Sighting>> unbound = new LinkedHashMap<>();
        for (Sighting sighting : SIGHTINGS) {
            ItemStack stack = sighting.stack;
            if (stack.isEmpty() || sighting.holder.isRemoved()) {
                continue;
            }
            UUID owner = SoulBond.owner(stack);
            if (owner != null) {
                Bond bond = ledger.ownedBy(owner);
                if (bond == null || !bond.isCurrent(stack)) {
                    crumble(sighting.holder, stack);
                } else {
                    bound.computeIfAbsent(bond, key -> new ArrayList<>()).add(sighting);
                }
                continue;
            }
            if (SoulBond.isUnissued(stack)) {
                SoulBond.issue(stack, sighting.holder.getUUID(), sighting.holder.getGameProfile().getName());
            }
            if (isDust(ledger, stack, wall)) {
                crumble(sighting.holder, stack);
            } else {
                unbound.computeIfAbsent(sighting.holder, key -> new ArrayList<>()).add(sighting);
            }
        }
        SIGHTINGS.clear();
        for (Map.Entry<Bond, List<Sighting>> entry : bound.entrySet()) {
            settleBound(ledger, entry.getKey(), entry.getValue(), now);
        }
        for (Map.Entry<ServerPlayer, List<Sighting>> entry : unbound.entrySet()) {
            settleUnbound(ledger, entry.getKey(), entry.getValue());
        }
    }

    private static boolean isDust(SoulforgeLedger ledger, ItemStack stack, long wall) {
        long doom = SoulBond.doomAt(stack);
        if (doom != 0L ? wall >= doom : ledger.isSpent(SoulBond.instance(stack))) {
            return true;
        }
        UUID instance = SoulBond.instance(stack);
        for (Bond bond : ledger.bonded()) {
            if (instance != null && instance.equals(bond.instance)) {
                return true;
            }
        }
        return false;
    }

    private static void settleBound(SoulforgeLedger ledger, Bond bond, List<Sighting> current, long now) {
        Sighting keeper = keeper(bond, current);
        if (current.size() > 1) {
            for (Sighting copy : current) {
                if (copy != keeper) {
                    crumble(copy.holder, copy.stack);
                }
            }
            ledger.restamp(bond, keeper.stack);
            HexwrightDebug.log(HexwrightDebug.SOULFORGE, "[soulforge] {} copies of {}'s {} carried at once; kept {}'s",
                current.size(), bond.ownerName, bond.item, keeper.holder.getScoreboardName());
        }
        ServerPlayer holder = keeper.holder;
        if (!holder.getUUID().equals(bond.owner)) {
            returnToOwner(holder, keeper.stack, ledger, bond);
            return;
        }
        if (bond.carriedTick != now - 1) {
            ledger.restamp(bond, keeper.stack);
        } else if (now % REMEMBER_INTERVAL_TICKS == 0) {
            ledger.remember(bond, keeper.stack);
        }
        bond.carriedTick = now;
    }

    private static Sighting keeper(Bond bond, List<Sighting> current) {
        Sighting best = current.get(0);
        int bestScore = -1;
        for (Sighting sighting : current) {
            int score = (sighting.holder.getUUID().equals(bond.owner) ? 2 : 0)
                + (sighting.holder.getMainHandItem() == sighting.stack ? 1 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = sighting;
            }
        }
        return best;
    }

    private static void settleUnbound(SoulforgeLedger ledger, ServerPlayer holder, List<Sighting> carried) {
        if (ledger.ownedBy(holder.getUUID()) != null || ledger.isBlacklisted(holder.getUUID())) {
            return;
        }
        Sighting chosen = null;
        int bestScore = -1;
        for (Sighting sighting : carried) {
            if (!mayBind(holder, sighting.stack)) {
                continue;
            }
            int score = (SoulBond.isDoomed(sighting.stack) ? 0 : 2)
                + (holder.getMainHandItem() == sighting.stack ? 1 : 0);
            if (score > bestScore) {
                bestScore = score;
                chosen = sighting;
            }
        }
        if (chosen != null) {
            claim(ledger, holder, chosen.stack);
        }
    }

    private static boolean mayBind(ServerPlayer holder, ItemStack stack) {
        return SoulBond.isDoomed(stack)
            ? !holder.getUUID().equals(SoulBond.severedBy(stack))
            : holder.getUUID().equals(SoulBond.issuedTo(stack));
    }

    private static void claim(SoulforgeLedger ledger, ServerPlayer finder, ItemStack stack) {
        Bond bond = ledger.bind(finder, stack);
        syncCosts(finder);
        ServerLevel level = finder.serverLevel();
        burst(level, finder, ParticleTypes.SCULK_SOUL, 40);
        play(level, finder, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, 0.8F);
        play(level, finder, SoundEvents.SOUL_ESCAPE, 1.0F);
        if (bond.doomAt != 0L) {
            finder.displayClientMessage(Component.translatable("message.hexwright.soulforge.bound_doomed",
                    stack.getHoverName(), minutesUntil(bond.doomAt))
                .withStyle(style -> style.withColor(SoulforgedItem.COLOUR)), false);
        } else {
            finder.displayClientMessage(Component.translatable("message.hexwright.soulforge.bound", stack.getHoverName())
                .withStyle(style -> style.withColor(SoulforgedItem.COLOUR)), false);
            announce(finder.server, Component.translatable("message.hexwright.soulforge.claimed",
                finder.getDisplayName(), stack.getHoverName()));
        }
        HexwrightDebug.log(HexwrightDebug.SOULFORGE, "[soulforge] {} bound {}{}", finder.getScoreboardName(),
            bond.item, bond.doomAt != 0L ? " (doomed)" : "");
    }

    public static boolean mayWield(Player player, ItemStack stack) {
        if (SoulBond.isOwnedBy(stack, player)) {
            return true;
        }
        if (player instanceof ServerPlayer server && !SoulBond.isBound(stack)) {
            Component why = whyNot(server, stack);
            if (why != null) {
                server.displayClientMessage(why.copy().withStyle(ChatFormatting.RED), true);
            }
        }
        return false;
    }

    @Nullable
    private static Component whyNot(ServerPlayer player, ItemStack stack) {
        SoulforgeLedger ledger = SoulforgeLedger.get(player.server);
        if (ledger.isBlacklisted(player.getUUID())) {
            return Component.translatable("message.hexwright.soulforge.barred");
        }
        if (SoulBond.isDoomed(stack) && player.getUUID().equals(SoulBond.severedBy(stack))) {
            return Component.translatable("message.hexwright.soulforge.severed_by_you", stack.getHoverName(),
                Component.keybind("key.hexwright.soulforge"), minutesUntil(SoulBond.doomAt(stack)));
        }
        Bond owned = ledger.ownedBy(player.getUUID());
        if (owned != null) {
            return Component.translatable("message.hexwright.soulforge.already_bound", owned.item().getDescription());
        }
        if (!SoulBond.isDoomed(stack) && !player.getUUID().equals(SoulBond.issuedTo(stack))) {
            return Component.translatable("message.hexwright.soulforge.forged_for", stack.getHoverName(),
                SoulBond.issuedToName(stack));
        }
        return null;
    }

    public static boolean ownsOne(ServerPlayer player) {
        return SoulforgeLedger.get(player.server).ownedBy(player.getUUID()) != null;
    }

    public static boolean owns(ServerPlayer player, Item item) {
        Bond bond = SoulforgeLedger.get(player.server).ownedBy(player.getUUID());
        return bond != null && bond.item() == item;
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
        Bond bond = ledger.ownedBy(player.getUUID());
        if (bond == null) {
            if (!reclaim(player)) {
                player.displayClientMessage(Component.translatable("message.hexwright.soulforge.no_bond")
                    .withStyle(ChatFormatting.GRAY), true);
            }
            return;
        }
        int slot = find(player.getInventory(), bond);
        if (slot == player.getInventory().selected) {
            dismiss(player, ledger, bond, slot);
        } else {
            toHand(player, ledger, bond, slot);
        }
    }

    public static boolean recall(ServerPlayer player) {
        SoulforgeLedger ledger = SoulforgeLedger.get(player.server);
        Bond bond = ledger.ownedBy(player.getUUID());
        if (bond == null) {
            return reclaim(player);
        }
        int slot = find(player.getInventory(), bond);
        if (slot != player.getInventory().selected) {
            toHand(player, ledger, bond, slot);
        }
        return true;
    }

    public static boolean dismiss(ServerPlayer player) {
        SoulforgeLedger ledger = SoulforgeLedger.get(player.server);
        Bond bond = ledger.ownedBy(player.getUUID());
        if (bond == null) {
            return false;
        }
        int slot = find(player.getInventory(), bond);
        if (slot >= 0) {
            dismiss(player, ledger, bond, slot);
        } else if (!bond.dismissed) {
            ledger.stow(bond, null);
            dismissed(player, bond, null);
        }
        return true;
    }

    private static void toHand(ServerPlayer player, SoulforgeLedger ledger, Bond bond, int slot) {
        if (slot >= 0) {
            handOver(player, player.getInventory().removeItemNoUpdate(slot));
            manifest(player, bond, InteractionHand.MAIN_HAND, true);
            return;
        }
        boolean fromSoul = bond.dismissed;
        handOver(player, ledger.draw(bond));
        bond.carriedTick = player.server.getTickCount();
        manifest(player, bond, InteractionHand.MAIN_HAND, true);
        HexwrightDebug.log(HexwrightDebug.SOULFORGE, "[soulforge] {} recalled {} (generation {}, {})",
            player.getScoreboardName(), bond.item, bond.generation, fromSoul ? "from the soul" : "from the world");
    }

    private static void dismiss(ServerPlayer player, SoulforgeLedger ledger, Bond bond, int slot) {
        Inventory inventory = player.getInventory();
        ledger.stow(bond, inventory.getItem(slot));
        inventory.setItem(slot, ItemStack.EMPTY);
        InteractionHand hand = slot == inventory.selected ? InteractionHand.MAIN_HAND
            : slot == Inventory.SLOT_OFFHAND ? InteractionHand.OFF_HAND
            : null;
        dismissed(player, bond, hand);
    }

    private static void dismissed(ServerPlayer player, Bond bond, @Nullable InteractionHand hand) {
        manifest(player, bond, hand, false);
        HexwrightDebug.log(HexwrightDebug.SOULFORGE, "[soulforge] {} dismissed their weapon",
            player.getScoreboardName());
    }

    private static void manifest(ServerPlayer player, Bond bond, @Nullable InteractionHand hand,
                                 boolean arriving) {
        if (!(bond.item() instanceof SoulforgedItem item)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (hand != null) {
            HexwrightNetworking.sendSoulforgedMaterialise(player, bond.item(), hand, arriving);
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

    private static int find(Inventory inventory, Bond bond) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (bond.isCurrent(inventory.getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }


    public static void onDied(ServerPlayer player) {
        SoulforgeLedger ledger = SoulforgeLedger.get(player.server);
        Bond bond = ledger.ownedBy(player.getUUID());
        if (bond == null) {
            return;
        }
        int slot = find(player.getInventory(), bond);
        if (slot >= 0) {
            ledger.stow(bond, player.getInventory().getItem(slot));
            player.getInventory().setItem(slot, ItemStack.EMPTY);
        }
    }

    public static boolean onThrown(Player thrower, ItemStack stack) {
        if (!(thrower instanceof ServerPlayer player) || !(stack.getItem() instanceof SoulforgedItem)) {
            return false;
        }
        SoulforgeLedger ledger = SoulforgeLedger.get(player.server);
        UUID owner = SoulBond.owner(stack);
        if (owner == null) {
            if (!SoulBond.isUnissued(stack) && isDust(ledger, stack, System.currentTimeMillis())) {
                crumble(player, stack);
                return true;
            }
            return false;
        }
        Bond bond = ledger.ownedBy(owner);
        if (bond == null || !bond.isCurrent(stack)) {
            crumble(player, stack);
            return true;
        }
        if (player.getUUID().equals(bond.owner)) {
            ledger.stow(bond, stack);
            stack.setCount(0);
            dismissed(player, bond, null);
        } else {
            returnToOwner(player, stack, ledger, bond);
        }
        return true;
    }

    private static void onEntityLoad(Entity entity, ServerLevel level) {
        if (entity instanceof ItemEntity item && item.getItem().getItem() instanceof SoulforgedItem) {
            item.setUnlimitedLifetime();
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

    private static void returnToOwner(ServerPlayer holder, ItemStack stack, SoulforgeLedger ledger, Bond bond) {
        Component name = stack.getHoverName();
        ledger.stow(bond, stack);
        stack.setCount(0);
        ServerLevel level = holder.serverLevel();
        burst(level, holder, ParticleTypes.SOUL, 16);
        play(level, holder, SoundEvents.SOUL_ESCAPE, 0.8F);
        holder.displayClientMessage(Component.translatable("message.hexwright.soulforge.refused", name, bond.ownerName)
            .withStyle(ChatFormatting.GRAY), true);
        ServerPlayer owner = holder.server.getPlayerList().getPlayer(bond.owner);
        if (owner != null) {
            owner.displayClientMessage(Component.translatable("message.hexwright.soulforge.returned", name)
                .withStyle(style -> style.withColor(SoulforgedItem.COLOUR)), true);
        }
        HexwrightDebug.log(HexwrightDebug.SOULFORGE, "[soulforge] {}'s weapon slipped from {} back to its owner",
            bond.ownerName, holder.getScoreboardName());
    }


    @Nullable
    public static Item unbind(MinecraftServer server, UUID owner, boolean leaveDoomed) {
        SoulforgeLedger ledger = SoulforgeLedger.get(server);
        Bond bond = ledger.ownedBy(owner);
        if (bond == null) {
            return null;
        }
        Item item = bond.item();
        long doomAt = bond.doomAt != 0L ? bond.doomAt : System.currentTimeMillis() + DOOM_MILLIS;
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player != null) {
            Inventory inventory = player.getInventory();
            int slot = find(inventory, bond);
            ItemStack weapon = slot >= 0 ? inventory.removeItemNoUpdate(slot) : ItemStack.EMPTY;
            if (leaveDoomed) {
                if (weapon.isEmpty()) {
                    weapon = ledger.draw(bond);
                }
                SoulBond.doom(weapon, owner, doomAt);
                if (slot >= 0) {
                    inventory.setItem(slot, weapon);
                } else if (!inventory.add(weapon)) {
                    player.drop(weapon, false);
                }
            }
        }
        ledger.end(bond);
        if (player != null) {
            syncCosts(player, null);
            ServerLevel level = player.serverLevel();
            burst(level, player, ParticleTypes.SOUL, 40);
            play(level, player, SoundEvents.SOUL_ESCAPE, 0.5F);
            play(level, player, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 0.8F);
            player.displayClientMessage(leaveDoomed
                ? Component.translatable("message.hexwright.soulforge.severed", item.getDescription(), minutesUntil(doomAt))
                : Component.translatable("message.hexwright.soulforge.severed_gone", item.getDescription()),
                false);
        }
        HexwrightDebug.log(HexwrightDebug.SOULFORGE, "[soulforge] {} unbound from {}{}", item, owner,
            leaveDoomed && player != null ? ", left doomed" : "");
        return item;
    }

    private static void crumbleAway(ServerPlayer player, SoulforgeLedger ledger, Bond bond) {
        Item item = bond.item();
        int slot = find(player.getInventory(), bond);
        if (slot >= 0) {
            player.getInventory().setItem(slot, ItemStack.EMPTY);
        }
        ledger.end(bond);
        syncCosts(player, null);
        ServerLevel level = player.serverLevel();
        burst(level, player, ParticleTypes.ASH, 30);
        play(level, player, SoundEvents.SOUL_SAND_BREAK, 0.6F);
        player.displayClientMessage(Component.translatable("message.hexwright.soulforge.crumbled_away", item.getDescription())
            .withStyle(ChatFormatting.GRAY), false);
        HexwrightDebug.log(HexwrightDebug.SOULFORGE, "[soulforge] {}'s doomed {} crumbled", player.getScoreboardName(), item);
    }

    public static boolean reclaim(ServerPlayer player) {
        InteractionHand hand = reclaimableHand(player);
        if (hand == null) {
            return false;
        }
        ItemStack stack = player.getItemInHand(hand);
        SoulBond.lift(stack);
        Bond bond = SoulforgeLedger.get(player.server).bind(player, stack);
        syncCosts(player);
        manifest(player, bond, hand, true);
        player.displayClientMessage(Component.translatable("message.hexwright.soulforge.reclaimed",
            stack.getHoverName()).withStyle(style -> style.withColor(SoulforgedItem.COLOUR)), false);
        HexwrightDebug.log(HexwrightDebug.SOULFORGE, "[soulforge] {} took back their severed {}",
            player.getScoreboardName(), bond.item);
        return true;
    }

    public static boolean mayReclaim(ServerPlayer player) {
        return reclaimableHand(player) != null;
    }

    @Nullable
    private static InteractionHand reclaimableHand(ServerPlayer player) {
        SoulforgeLedger ledger = SoulforgeLedger.get(player.server);
        if (ledger.ownedBy(player.getUUID()) != null || ledger.isBlacklisted(player.getUUID())) {
            return null;
        }
        long wall = System.currentTimeMillis();
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof SoulforgedItem && !SoulBond.isBound(stack)
                && player.getUUID().equals(SoulBond.severedBy(stack)) && !isDust(ledger, stack, wall)) {
                return hand;
            }
        }
        return null;
    }

    public static long minutesUntil(long doomAt) {
        return Math.max(1L, (doomAt - System.currentTimeMillis() + 59_999L) / 60_000L);
    }

    static boolean drops(Item item) {
        return !(item instanceof SoulforgedItem forged) || forged.drops();
    }

    private static void onJoin(ServerPlayer player) {
        SoulforgeLedger ledger = SoulforgeLedger.get(player.server);
        Bond bond = ledger.ownedBy(player.getUUID());
        if (bond != null && bond.doomAt != 0L && System.currentTimeMillis() >= bond.doomAt) {
            crumbleAway(player, ledger, bond);
            bond = null;
        }
        syncCosts(player, bond);
    }


    public static boolean blacklist(MinecraftServer server, GameProfile player) {
        if (!SoulforgeLedger.get(server).addToBlacklist(player.getId(), player.getName())) {
            return false;
        }
        unbind(server, player.getId(), false);
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
        Bond bond = SoulforgeLedger.get(server).ownedBy(owner);
        return bond == null ? null : describe(bond);
    }

    public static List<Component> describeAll(MinecraftServer server) {
        List<Component> lines = new ArrayList<>();
        for (Bond bond : SoulforgeLedger.get(server).bonded()) {
            lines.add(describe(bond));
        }
        return lines;
    }

    private static Component describe(Bond bond) {
        Component line = Component.translatable(bond.dismissed
                ? "message.hexwright.soulforge.info_dismissed" : "message.hexwright.soulforge.info_carried",
            bond.ownerName, bond.item().getDescription(), bond.generation);
        return bond.doomAt == 0L ? line : line.copy().append(" ").append(
            Component.translatable("message.hexwright.soulforge.info_doomed", minutesUntil(bond.doomAt)));
    }


    public static void syncCosts(ServerPlayer player) {
        syncCosts(player, SoulforgeLedger.get(player.server).ownedBy(player.getUUID()));
    }

    private static void syncCosts(ServerPlayer player, @Nullable Bond bond) {
        Item weapon = bond == null ? null : bond.item();
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
