package com.bluup.hexwright.server.flourish.workbench;

import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.common.flourish.Flourish;
import com.bluup.hexwright.common.flourish.FlourishDesign;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.flourish.FlourishBlankItem;
import com.bluup.hexwright.server.flourish.FlourishItem;
import com.bluup.hexwright.server.flourish.FlourishSounds;
import com.bluup.hexwright.server.flourish.FlourishState;
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
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

public class FlourishWorkbenchBlockEntity extends BlockEntity implements Container, IUIHolder.BlockEntityUI {

    public static final int SLOT = 0;

    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);

    public FlourishWorkbenchBlockEntity(BlockPos pos, BlockState state) {
        super(HexwrightBlocks.FLOURISH_WORKBENCH_BLOCK_ENTITY, pos, state);
    }

    public ItemStack blank() {
        return items.get(SLOT);
    }

    public boolean hasBlank() {
        return blank().getItem() instanceof FlourishBlankItem;
    }

    public static Set<Flourish> known(ServerPlayer player) {
        Set<Flourish> known = EnumSet.noneOf(Flourish.class);
        for (Flourish flourish : Flourish.values()) {
            if (flourish.rarity() == Rarity.UNCOMMON || player.isCreative()) {
                known.add(flourish);
            }
        }
        FlourishState state = FlourishState.get(player.server);
        known.addAll(state.found(player.getUUID()));
        Flourish worn = FlourishItem.of(state.worn(player.getUUID()));
        if (worn != null) {
            known.add(worn);
        }
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            Flourish carried = FlourishItem.of(inventory.getItem(i));
            if (carried != null) {
                known.add(carried);
            }
        }
        return known;
    }

    public @Nullable Component inscribe(ServerPlayer player, FlourishDesign design) {
        ItemStack blank = blank();
        if (!(blank.getItem() instanceof FlourishBlankItem)) {
            return Component.translatable("message.hexwright.flourish_workbench.no_blank");
        }
        Set<Flourish> known = known(player);
        for (Flourish source : design.sources()) {
            if (!known.contains(source)) {
                return Component.translatable("message.hexwright.flourish_workbench.locked",
                    Component.translatable("item.hexwright." + source.itemId()));
            }
        }
        FlourishBlankItem.inscribe(blank, design);
        setChanged();
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.8f, 1.3f);
            if (design.chime() != null) {
                level.playSound(null, worldPosition, FlourishSounds.get(design.chime()), SoundSource.BLOCKS, 0.7f, 1.0f);
            }
        }
        HexwrightDebug.log(HexwrightDebug.CASTING, "[flourish] {} inscribed a {}-layer flourish at {}",
            player.getGameProfile().getName(), design.layers().size(), worldPosition);
        return null;
    }

    public void dropContents(Level level, BlockPos pos) {
        Containers.dropContents(level, pos, items);
        items.clear();
    }


    @Override
    public ModularUI createUI(Player player) {
        return new ModularUI(FlourishWorkbenchWindow.build(this, player), this, player);
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
        return stack.getItem() instanceof FlourishBlankItem;
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
