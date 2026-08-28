package com.bluup.hexwright.server.pocketcaster;

import com.bluup.hexwright.server.hexpatterns.StoredHex;
import at.petrak.hexcasting.api.addldata.ADIotaHolder;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.iota.EntityIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.server.item.PocketCasterItem;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class PocketCasterCasting {

    private PocketCasterCasting() {
    }

    public static void cast(ServerPlayer player, InteractionHand hand, PocketCasterContainer container) {
        ItemStack caster = player.getItemInHand(hand);
        if (!(caster.getItem() instanceof PocketCasterItem)) {
            return;
        }
        ServerLevel level = player.serverLevel();

        ItemStack focus = container.getItem(PocketCasterContainer.FOCUS_SLOT);
        if (focus.isEmpty()) {
            feedback(player, "message.hexwright.pocket_caster.no_focus", ChatFormatting.RED);
            return;
        }
        ADIotaHolder holder = IXplatAbstractions.INSTANCE.findDataHolder(focus);
        Iota iota = holder == null ? null : holder.readIota(level);
        List<Iota> hex = StoredHex.decode(iota);
        if (hex == null || hex.isEmpty()) {
            feedback(player, "message.hexwright.pocket_caster.no_hex", ChatFormatting.RED);
            return;
        }

        List<ItemEntity> spawned = new ArrayList<>();
        for (int slot = PocketCasterContainer.ITEM_FIRST; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemEntity entity = new ItemEntity(level, player.getX(), player.getY() + 0.25, player.getZ(), stack.copy());
            entity.setDeltaMovement(Vec3.ZERO);
            entity.setPickUpDelay(40);
            level.addFreshEntity(entity);
            spawned.add(entity);
            container.setItem(slot, ItemStack.EMPTY);
        }
        if (spawned.isEmpty()) {
            feedback(player, "message.hexwright.pocket_caster.no_items", ChatFormatting.RED);
            return;
        }

        executeHex(player, hand, hex, spawned);

        int returned = 0;
        int consumed = 0;
        for (ItemEntity entity : spawned) {
            if (!entity.isAlive()) {
                consumed++;
                continue;
            }
            ItemStack remaining = entity.getItem().copy();
            entity.discard();
            if (!remaining.isEmpty()) {
                if (!player.getInventory().add(remaining) && !remaining.isEmpty()) {
                    player.drop(remaining, false);
                }
                returned++;
            } else {
                consumed++;
            }
        }
        container.setChanged();

        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8f, 1.4f);
        feedback(
            player,
            "message.hexwright.pocket_caster.cast",
            ChatFormatting.LIGHT_PURPLE,
            returned,
            consumed
        );
    }

    private static void executeHex(
        ServerPlayer player,
        InteractionHand hand,
        List<Iota> hex,
        List<ItemEntity> targets
    ) {
        try {
            List<Iota> entityList = new ArrayList<>(targets.size());
            for (ItemEntity target : targets) {
                entityList.add(new EntityIota(target));
            }

            CastingVM templateVm = IXplatAbstractions.INSTANCE.getStaffcastVM(player, hand);
            CastingImage template = templateVm.getImage();
            List<Iota> seededStack = List.of((Iota) new ListIota(entityList));
            CastingImage seededImage = template.copy(
                seededStack,
                0,
                List.of(),
                false,
                0L,
                new CompoundTag()
            );

            CastingVM vm = new CastingVM(seededImage, new PocketCasterCastEnv(player, hand));
            vm.queueExecuteAndWrapIotas(new ArrayList<>(hex), player.serverLevel());
        } catch (RuntimeException ignored) {
        }
    }


    private static void feedback(ServerPlayer player, String key, ChatFormatting color, Object... args) {
        player.displayClientMessage(Component.translatable(key, args).withStyle(color), true);
    }
}
