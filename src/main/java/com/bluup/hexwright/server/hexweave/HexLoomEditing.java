package com.bluup.hexwright.server.hexweave;

import at.petrak.hexcasting.api.addldata.ADIotaHolder;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.HexwrightDebug;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class HexLoomEditing {

    public record Opening(@Nullable WeaveDraft draft, @Nullable Component refusal) {
    }

    public record SaveResult(boolean saved, Component message, int revision) {
    }

    private HexLoomEditing() {
    }

    public static Opening opening(ItemStack orb) {
        if (!(orb.getItem() instanceof WovenOrbItem)) {
            return refused("message.hexwright.hex_loom.empty");
        }
        if (WovenOrbItem.isLocked(orb)) {
            return refused("message.hexwright.hex_loom.locked");
        }
        if (!WovenOrbItem.hasWeave(orb)) {
            return new Opening(WeaveDraft.blank(), null);
        }
        Hexweave weave;
        try {
            weave = WovenOrbItem.weave(orb);
        } catch (IllegalArgumentException e) {
            return refused("message.hexwright.hex_loom.broken");
        }
        return new Opening(WeaveDraft.of(weave, WovenOrbItem.layout(orb)), null);
    }

    public static void open(ServerPlayer player, HexLoomBlockEntity loom) {
        Opening opening = opening(loom.orb());
        WeaveDraft draft = opening.draft();
        if (draft == null) {
            player.displayClientMessage(opening.refusal(), true);
            return;
        }
        HexwrightDebug.log(HexwrightDebug.WEAVE, "[loom] {} opened the Loom at {} (revision {}, {} Threads, {} links)",
            player.getScoreboardName(), loom.getBlockPos().toShortString(), loom.revision(), draft.threads().size(),
            draft.links().size());
        LoomNetworking.sendOpen(player, loom.getBlockPos(), loom.revision(),
            HexweaveNbt.write(draft.toWeave(), draft.layout()));
    }

    public static SaveResult save(ServerPlayer player, BlockPos pos, int revision, CompoundTag submitted) {
        if (!(player.level().getBlockEntity(pos) instanceof HexLoomBlockEntity loom)
            || !Container.stillValidBlockEntity(loom, player)) {
            return refusal("message.hexwright.hex_loom.gone", -1);
        }
        if (loom.revision() != revision) {
            return refusal("message.hexwright.hex_loom.stale", loom.revision());
        }
        Opening opening = opening(loom.orb());
        if (opening.draft() == null) {
            return new SaveResult(false, opening.refusal(), loom.revision());
        }

        Hexweave weave;
        try {
            weave = HexweaveNbt.read(submitted);
        } catch (IllegalArgumentException e) {
            return refusal("message.hexwright.hex_loom.invalid", loom.revision(), e.getMessage());
        }
        if (weave.threads().isEmpty()) {
            return refusal("message.hexwright.hex_loom.no_threads", loom.revision());
        }
        if (weave.triggers().stream().allMatch(trigger -> trigger.targets().isEmpty())) {
            return refusal("message.hexwright.hex_loom.no_trigger", loom.revision());
        }
        Set<CompoundTag> embeddable = null;
        for (WeaveThread thread : weave.threads()) {
            for (int i = 0; i < thread.entries().size(); i++) {
                ThreadEntry entry = thread.entries().get(i);
                if (!ThreadDraft.inBounds(entry)) {
                    return refusal("message.hexwright.hex_loom.out_of_bounds", loom.revision(), i + 1, thread.id());
                }
                if (entry instanceof EmbeddedIota embedded) {
                    if (embeddable == null) {
                        embeddable = embeddable(player, loom.orb());
                    }
                    if (!embeddable.contains(embedded.iota())) {
                        return refusal("message.hexwright.hex_loom.embed_not_held", loom.revision(), i + 1, thread.id());
                    }
                }
            }
        }
        WeaveLayout layout = HexweaveNbt.readLayout(submitted).forWeave(weave);

        WovenOrbItem.bind(loom.orb(), weave, layout);
        loom.changed();
        HexwrightDebug.log(HexwrightDebug.WEAVE,
            "[loom] {} saved {} Threads, {} triggers and {} links into the orb at {} (revision {})",
            player.getScoreboardName(), weave.threads().size(), weave.triggers().size(), weave.links().size(),
            pos.toShortString(), loom.revision());
        return new SaveResult(true, Component.translatable("message.hexwright.hex_loom.saved")
            .withStyle(ChatFormatting.AQUA), loom.revision());
    }

    private static Set<CompoundTag> embeddable(ServerPlayer player, ItemStack orb) {
        Set<CompoundTag> found = new HashSet<>();
        if (WovenOrbItem.hasWeave(orb)) {
            try {
                for (WeaveThread thread : WovenOrbItem.weave(orb).threads()) {
                    collectEmbedded(thread.entries(), found);
                }
            } catch (IllegalArgumentException e) {
            }
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ADIotaHolder holder = IXplatAbstractions.INSTANCE.findDataHolder(inventory.getItem(slot));
            CompoundTag stored = holder == null ? null : holder.readIotaTag();
            if (stored != null) {
                collectEmbedded(ThreadEntry.copiedFrom(stored), found);
            }
        }
        return found;
    }

    private static void collectEmbedded(List<ThreadEntry> entries, Set<CompoundTag> into) {
        for (ThreadEntry entry : entries) {
            if (entry instanceof EmbeddedIota embedded) {
                into.add(embedded.iota());
            }
        }
    }

    private static Opening refused(String key) {
        return new Opening(null, Component.translatable(key).withStyle(ChatFormatting.GRAY));
    }

    static SaveResult refusal(String key, int revision, Object... args) {
        return new SaveResult(false, Component.translatable(key, args).withStyle(ChatFormatting.RED), revision);
    }
}
