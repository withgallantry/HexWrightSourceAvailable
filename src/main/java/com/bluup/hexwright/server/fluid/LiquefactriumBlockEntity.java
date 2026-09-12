package com.bluup.hexwright.server.fluid;

import com.bluup.hexwright.common.remnant.Remnant;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.remnant.BottleData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class LiquefactriumBlockEntity extends BlockEntity {

    public static final double DRAMS_PER_SECOND = 100.0;

    public static final int PERIOD_TICKS = 2;

    public static final double STEP_DRAMS = DRAMS_PER_SECOND * PERIOD_TICKS / 20.0;

    private static final String TAG_BOTTLE = "Bottle";

    private ItemStack bottle = ItemStack.EMPTY;
    private int countdown;

    public LiquefactriumBlockEntity(BlockPos pos, BlockState state) {
        super(HexwrightBlocks.LIQUEFACTRIUM_BLOCK_ENTITY, pos, state);
    }

    public ItemStack getBottle() {
        return bottle;
    }

    public boolean isEmpty() {
        return bottle.isEmpty();
    }

    public void setBottle(ItemStack stack) {
        ItemStack one = stack.copy();
        one.setCount(1);
        bottle = one;
        setChanged();
        sync();
    }

    public ItemStack takeBottle() {
        ItemStack taken = bottle;
        bottle = ItemStack.EMPTY;
        setChanged();
        sync();
        return taken;
    }


    public List<HexidTankBlockEntity> portColumns() {
        Level level = this.level;
        Direction port = LiquefactriumBlock.portOf(getBlockState());
        if (level == null || port == null) {
            return List.of();
        }
        BlockPos pos = worldPosition.relative(port);
        BlockState state = level.getBlockState(pos);
        if (HexidTankColumn.isTank(state)) {
            HexidTankBlockEntity column = HexidTankColumn.controller(level, pos);
            return column == null ? List.of() : List.of(column);
        }
        if (state.is(HexwrightBlocks.HEXID_PIPE_BLOCK)) {
            return HexidPipeNetwork.tanksOn(level, pos);
        }
        return List.of();
    }

    private TankRemnants pooled(List<HexidTankBlockEntity> columns) {
        TankRemnants pool = TankRemnants.EMPTY;
        for (HexidTankBlockEntity column : columns) {
            pool = pool.plusAll(column.remnants());
        }
        return pool;
    }


    public void serverTick() {
        if (bottle.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (countdown > 0) {
            countdown--;
            return;
        }
        countdown = PERIOD_TICKS - 1;

        double room = BottleData.headroom(bottle);
        if (room < TankRemnants.MIN_DRAMS) {
            return;
        }
        List<HexidTankBlockEntity> columns = portColumns();
        if (columns.isEmpty()) {
            return;
        }
        TankRemnants pool = BottleData.acceptableFrom(bottle, pooled(columns));
        double batch = Math.min(Math.min(STEP_DRAMS, room), pool.total());
        if (batch < TankRemnants.MIN_DRAMS) {
            return;
        }

        TankRemnants drawn = draw(columns, pool.cappedTo(batch));
        if (drawn.isEmpty()) {
            return;
        }
        boolean wasEmpty = BottleData.isEmpty(bottle);
        double poured = BottleData.pourMixture(bottle, drawn);
        if (poured < drawn.total()) {
            give(columns, drawn.portion(1.0 - poured / drawn.total()));
        }
        if (poured <= 0.0) {
            return;
        }
        setChanged();
        sync();
        if (wasEmpty || BottleData.isFull(bottle)) {
            serverLevel.playSound(null, worldPosition, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS,
                0.7f, BottleData.isFull(bottle) ? 1.3f : 1.0f);
        }
    }


    public double bottleHeadroom() {
        return bottle.isEmpty() ? 0.0 : BottleData.headroom(bottle);
    }

    public double takePour(TankRemnants blend) {
        if (bottle.isEmpty()) {
            return 0.0;
        }
        boolean wasEmpty = BottleData.isEmpty(bottle);
        double poured = BottleData.pourMixture(bottle, blend);
        if (poured <= 0.0) {
            return 0.0;
        }
        setChanged();
        sync();
        if (level != null && (wasEmpty || BottleData.isFull(bottle))) {
            level.playSound(null, worldPosition, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS,
                0.7f, BottleData.isFull(bottle) ? 1.3f : 1.0f);
        }
        return poured;
    }

    private TankRemnants draw(List<HexidTankBlockEntity> columns, TankRemnants wanted) {
        TankRemnants taken = TankRemnants.EMPTY;
        for (Remnant part : wanted.contents()) {
            double left = part.drams();
            for (HexidTankBlockEntity column : columns) {
                if (left < TankRemnants.MIN_DRAMS) {
                    break;
                }
                left -= column.drawRemnant(part.type(), left);
            }
            double got = part.drams() - left;
            if (got >= TankRemnants.MIN_DRAMS) {
                taken = taken.plus(part.withDrams(got));
            }
        }
        return taken;
    }

    private void give(List<HexidTankBlockEntity> columns, TankRemnants spare) {
        TankRemnants left = spare;
        for (HexidTankBlockEntity column : columns) {
            if (left.isEmpty()) {
                return;
            }
            double poured = column.pourMixture(left);
            left = poured >= left.total()
                ? TankRemnants.EMPTY
                : left.portion(1.0 - poured / left.total());
        }
    }

    public TankRemnants portContents() {
        return pooled(portColumns());
    }


    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        bottle = tag.contains(TAG_BOTTLE)
            ? ItemStack.of(tag.getCompound(TAG_BOTTLE))
            : ItemStack.EMPTY;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!bottle.isEmpty()) {
            tag.put(TAG_BOTTLE, bottle.save(new CompoundTag()));
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = saveWithoutMetadata();
        tag.put(TAG_BOTTLE, bottle.save(new CompoundTag()));
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
