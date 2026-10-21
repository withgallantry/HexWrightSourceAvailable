package com.bluup.hexwright.server.research;

import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.inspiration.InspirationLevels;
import com.bluup.hexwright.server.journal.InvestigationProgress;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
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

public class ResearchTableBlockEntity extends BlockEntity implements Container, IUIHolder.BlockEntityUI {

    public static final int SLOT = 0;

    private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
    private final ResearchSession session = new ResearchSession();

    private int viewers;

    public ResearchTableBlockEntity(BlockPos pos, BlockState state) {
        super(HexwrightBlocks.WRIGHT_RESEARCH_TABLE_BLOCK_ENTITY, pos, state);
    }

    public ResearchSession session() {
        return session;
    }

    public ItemStack tablet() {
        return items.get(SLOT);
    }


    public static void serverTick(Level level, BlockPos pos, BlockState state, ResearchTableBlockEntity table) {
        if (table.session.tick() == ResearchSession.Outcome.RAN_OUT) {
            table.ranOut();
        }
    }

    public void select(int levels) {
        session.select(levels);
        setChanged();
    }

    public @Nullable Component use(ServerPlayer player) {
        if (session.isRunning() || session.isLingering()) {
            return null;
        }
        ItemStack tablet = tablet();
        if (!(tablet.getItem() instanceof RuinedTabletItem) || !session.hasTablet()) {
            return Component.translatable("message.hexwright.research.no_tablet");
        }
        if (!ResearchRewards.anythingLeft(player, session.damage())) {
            return Component.translatable("message.hexwright.research.nothing_new");
        }
        int levels = session.stakeLevels();
        if (!InspirationLevels.spend(player, levels)) {
            return Component.translatable("message.hexwright.research.cannot_afford", levels);
        }

        session.begin(player.getUUID(), player.getRandom());
        setChanged();
        playAt(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7f, 1.2f);
        HexwrightDebug.log(HexwrightDebug.TABLET, "[tablet] {} staked {} level(s) on a {} tablet at {}",
            player.getGameProfile().getName(), levels, session.damage().getSerializedName(), worldPosition);
        return null;
    }

    public void redeal(ServerPlayer player) {
        if (!session.isRunning()) {
            return;
        }
        session.deal(player.getRandom());
        setChanged();
        playAt(SoundEvents.BOOK_PAGE_TURN, 0.8f, 1.0f);
    }

    public @Nullable Component cancel(ServerPlayer player) {
        if (!session.isRunning()) {
            return null;
        }
        if (!player.getUUID().equals(session.owner())) {
            return Component.translatable("message.hexwright.research.not_yours");
        }
        abandon();
        playAt(SoundEvents.AMETHYST_BLOCK_BREAK, 0.6f, 0.9f);
        HexwrightDebug.log(HexwrightDebug.TABLET, "[tablet] {} cancelled the attempt at {}",
            player.getGameProfile().getName(), worldPosition);
        return null;
    }

    public void place(ServerPlayer player, int traySlot, int breakIndex) {
        switch (session.place(traySlot, breakIndex, player.getRandom())) {
            case PLACED -> playAt(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6f, 1.4f);
            case WRONG -> playAt(SoundEvents.STONE_BREAK, 0.7f, 0.8f);
            case COMPLETED -> completed(player);
            case RAN_OUT -> ranOut();
            default -> {
                return;
            }
        }
        setChanged();
    }

    private void completed(ServerPlayer placer) {
        float remaining = session.remaining();
        ServerPlayer owner = ownerOr(placer);
        session.learned(ResearchRewards.grant(owner, session.damage(), placer.getRandom()));
        InvestigationProgress.recordTabletRestored(owner);
        if (tablet().getItem() instanceof RuinedTabletItem item) {
            item.restored(tablet(), owner);
        }
        items.set(SLOT, ItemStack.EMPTY);
        playAt(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.6f);
        HexwrightDebug.log(HexwrightDebug.TABLET, "[tablet] restored at {} by {} ({})",
            worldPosition, placer.getGameProfile().getName(), ResearchRewards.fidelity(remaining));
    }

    private void ranOut() {
        @Nullable ServerPlayer owner = ownerOr(null);
        abandon();
        playAt(SoundEvents.AMETHYST_BLOCK_BREAK, 0.9f, 0.7f);
        if (owner != null) {
            owner.displayClientMessage(
                Component.translatable("message.hexwright.research.ran_out").withStyle(ChatFormatting.RED), true);
        }
    }

    private void abandon() {
        crumble();
        session.clear();
        setChanged();
    }

    private void crumble() {
        ItemStack tablet = tablet();
        if (!(tablet.getItem() instanceof RuinedTabletItem)) {
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, tablet),
                worldPosition.getX() + 0.5, worldPosition.getY() + 1.35, worldPosition.getZ() + 0.5,
                24, 0.2, 0.05, 0.2, 0.06);
        }
        playAt(SoundEvents.GRAVEL_BREAK, 1.0f, 0.7f);
        items.set(SLOT, ItemStack.EMPTY);
        HexwrightDebug.log(HexwrightDebug.TABLET, "[tablet] a {} tablet crumbled at {}",
            session.damage().getSerializedName(), worldPosition);
    }

    private @Nullable ServerPlayer ownerOr(@Nullable ServerPlayer fallback) {
        if (session.owner() == null || !(level instanceof ServerLevel serverLevel)) {
            return fallback;
        }
        ServerPlayer owner = serverLevel.getServer().getPlayerList().getPlayer(session.owner());
        return owner == null ? fallback : owner;
    }

    private void playAt(SoundEvent sound, float volume, float pitch) {
        if (level != null) {
            level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, volume, pitch);
        }
    }

    public void dropContents(Level level, BlockPos pos) {
        if (session.isRunning()) {
            crumble();
        }
        Containers.dropContents(level, pos, items);
        items.clear();
    }

    private void slotChanged() {
        if (level == null || level.isClientSide) {
            return;
        }
        ItemStack tablet = tablet();
        if (tablet.getItem() instanceof RuinedTabletItem) {
            RuinedTabletItem.roll(tablet, level.random);
            session.setTablet(RuinedTabletItem.seedOf(tablet), RuinedTabletItem.damageOf(tablet));
        } else if (!session.isRunning() && !session.isLingering()) {
            session.clear();
        }
    }


    @Override
    public ModularUI createUI(Player player) {
        ModularUI ui = new ModularUI(ResearchTableWindow.build(this, player), this, player);
        if (!player.level().isClientSide) {
            viewers++;
            ui.registerCloseListener(this::windowClosed);
        }
        return ui;
    }

    private void windowClosed() {
        if (--viewers <= 0) {
            viewers = 0;
            session.dismiss();
            setChanged();
        }
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
            slotChanged();
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack removed = ContainerHelper.takeItem(items, slot);
        slotChanged();
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        slotChanged();
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return !session.isRunning() && stack.getItem() instanceof RuinedTabletItem;
    }

    @Override
    public boolean canTakeItem(Container target, int slot, ItemStack stack) {
        return canTakeTablet();
    }

    public boolean canTakeTablet() {
        return !session.isRunning();
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
        slotChanged();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.clear();
        ContainerHelper.loadAllItems(tag, items);
        session.load(tag.getCompound("Session"));
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        CompoundTag saved = new CompoundTag();
        session.save(saved);
        tag.put("Session", saved);
    }
}
