package com.bluup.hexwright.server.vault;

import com.bluup.hexwright.server.portal.PortalPair;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

public final class VaultAccess {

    public static final String TAG_ACCESS = "Access";

    public static final String TAG_ALLOWED = "Allowed";

    private VaultAccess() {
    }


    public static boolean mayEnter(VaultRecord record, @Nullable VaultPortalSession session, UUID player) {
        if (record.access() == VaultRecord.Access.EVERYONE) {
            return true;
        }
        if (player.equals(record.owner())) {
            return true;
        }
        if (session != null && player.equals(session.opener())) {
            return true;
        }
        return record.allowed().containsKey(player);
    }

    public static boolean mayCross(PortalPair pair, int side, Entity entity) {
        if (!pair.isSessionBound() || !(entity instanceof ServerPlayer player)) {
            return true;
        }
        if (VaultDimension.isVaultLevel(player.level())) {
            return true;
        }
        VaultPortalSession session = VaultManager.sessionByPair(pair.id());
        if (session == null) {
            return true;
        }
        VaultRecord record = VaultManager.getVault(player.server, session.vaultId());
        return record == null || mayEnter(record, session, player.getUUID());
    }


    public static VaultRecord.Access toggleMode(MinecraftServer server, VaultRecord record) {
        VaultRecord.Access flipped = record.access() == VaultRecord.Access.EVERYONE
            ? VaultRecord.Access.ALLOWED_ONLY
            : VaultRecord.Access.EVERYONE;
        record.setAccess(flipped);
        VaultRegistry.get(server).setDirty();
        return flipped;
    }

    public static boolean toggleGuest(MinecraftServer server, VaultRecord record, ServerPlayer guest) {
        UUID uuid = guest.getUUID();
        boolean invited;
        if (record.allowed().containsKey(uuid)) {
            record.revoke(uuid);
            invited = false;
        } else {
            record.invite(uuid, guest.getGameProfile().getName());
            invited = true;
        }
        VaultRegistry.get(server).setDirty();
        return invited;
    }


    static void refreshMirror(MinecraftServer server, ItemStack key) {
        Integer vaultId = VaultKeyItem.boundVault(key);
        if (vaultId == null) {
            return;
        }
        VaultRecord record = VaultRegistry.get(server).byId(vaultId);
        if (record == null) {
            return;
        }
        CompoundTag tag = key.getOrCreateTag();
        if (mirrorMatches(tag, record)) {
            return;
        }
        tag.putString(TAG_ACCESS, record.access().name());
        tag.putBoolean(VaultKeyItem.TAG_ARTIFACT, record.artifact());
        if (record.allowed().isEmpty()) {
            tag.remove(TAG_ALLOWED);
            return;
        }
        ListTag names = new ListTag();
        for (Map.Entry<UUID, String> guest : record.allowed().entrySet()) {
            names.add(StringTag.valueOf(displayName(guest)));
        }
        tag.put(TAG_ALLOWED, names);
    }

    private static boolean mirrorMatches(CompoundTag tag, VaultRecord record) {
        if (!record.access().name().equals(tag.getString(TAG_ACCESS))) {
            return false;
        }
        if (!tag.contains(VaultKeyItem.TAG_ARTIFACT)
            || tag.getBoolean(VaultKeyItem.TAG_ARTIFACT) != record.artifact()) {
            return false;
        }
        if (record.allowed().isEmpty()) {
            return !tag.contains(TAG_ALLOWED);
        }
        ListTag names = tag.getList(TAG_ALLOWED, Tag.TAG_STRING);
        if (names.size() != record.allowed().size()) {
            return false;
        }
        int index = 0;
        for (Map.Entry<UUID, String> guest : record.allowed().entrySet()) {
            if (!displayName(guest).equals(names.getString(index++))) {
                return false;
            }
        }
        return true;
    }

    private static String displayName(Map.Entry<UUID, String> guest) {
        String name = guest.getValue();
        return name == null || name.isEmpty() ? guest.getKey().toString().substring(0, 8) : name;
    }
}
