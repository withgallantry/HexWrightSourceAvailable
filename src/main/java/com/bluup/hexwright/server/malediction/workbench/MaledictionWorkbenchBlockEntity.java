package com.bluup.hexwright.server.malediction.workbench;

import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.common.malediction.MaledictionFinish;
import com.bluup.hexwright.common.malediction.MaledictionForm;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.malediction.MaledictionBlankItem;
import com.bluup.hexwright.server.malediction.MaledictionState;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class MaledictionWorkbenchBlockEntity extends BlockEntity implements Container, IUIHolder.BlockEntityUI {

    public static final int SLOT = 0;

    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);

    public MaledictionWorkbenchBlockEntity(BlockPos pos, BlockState state) {
        super(HexwrightBlocks.MALEDICTION_WORKBENCH_BLOCK_ENTITY, pos, state);
    }

    public ItemStack held() {
        return items.get(SLOT);
    }

    public boolean hasBlank() {
        return held().getItem() instanceof MaledictionBlankItem;
    }

    public boolean holds(MaledictionForm form, MaledictionFinish finish) {
        return MaledictionBlankItem.form(held()) == form && MaledictionBlankItem.finish(held()) == finish;
    }

    public static boolean knows(ServerPlayer player, MaledictionForm form) {
        return player.isCreative() || MaledictionState.get(player.server).forms(player.getUUID()).contains(form);
    }

    public static boolean knows(ServerPlayer player, MaledictionFinish finish) {
        return player.isCreative() || MaledictionState.get(player.server).finishes(player.getUUID()).contains(finish);
    }

    public @Nullable Component fuse(ServerPlayer player, MaledictionForm form, MaledictionFinish finish) {
        if (!hasBlank()) {
            return Component.translatable("gui.hexwright.malediction_workbench.status.no_blank");
        }
        if (!knows(player, form)) {
            return Component.translatable("gui.hexwright.malediction_workbench.locked",
                Component.translatable("item.hexwright." + form.itemId()));
        }
        if (!knows(player, finish)) {
            return Component.translatable("gui.hexwright.malediction_workbench.locked",
                Component.translatable("item.hexwright." + finish.itemId()));
        }
        MaledictionBlankItem.fuse(held(), form, finish, player);
        setChanged();
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.8F, 0.7F);
            level.playSound(null, worldPosition, SoundEvents.EVOKER_CAST_SPELL, SoundSource.BLOCKS, 0.5F, 1.4F);
        }
        HexwrightDebug.log(HexwrightDebug.CASTING, "[malediction] {} fused {} {} at {}",
            player.getGameProfile().getName(), finish.id(), form.id(), worldPosition);
        return null;
    }

    public void dropContents(Level level, BlockPos pos) {
        Containers.dropContents(level, pos, items);
        items.clear();
    }


    @Override
    public ModularUI createUI(Player player) {
        return new ModularUI(MaledictionWorkbenchWindow.build(this, player), this, player);
    }


    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        return items.get(SLOT).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack removed = ContainerHelper.removeItem(items, slot, amount);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.getItem() instanceof MaledictionBlankItem;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.clear();
        ContainerHelper.loadAllItems(tag, items);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
    }
}
