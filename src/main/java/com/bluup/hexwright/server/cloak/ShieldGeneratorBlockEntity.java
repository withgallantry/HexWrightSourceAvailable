package com.bluup.hexwright.server.cloak;

import at.petrak.hexcasting.api.misc.MediaConstants;
import com.bluup.hexwright.server.block.HexwrightBlockStates;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.fluid.HexidPipeNetwork;
import com.bluup.hexwright.server.fluid.HexidTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ShieldGeneratorBlockEntity extends BlockEntity {

    private static final String TAG_FIELD = "Field";
    private static final String TAG_GUARD = "Guard";

    public static final int PERIOD_TICKS = 36;

    public static final long MEDIA_PER_PORT = MediaConstants.DUST_UNIT;

    private static final long TICKS_PER_HOUR = 20L * 60 * 60;

    private enum Guard { NONE, WAITING, HOLDING }

    private @Nullable CloakField field;
    private Guard guard = Guard.NONE;
    private int countdown;

    public ShieldGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(HexwrightBlocks.SHIELD_GENERATOR_BLOCK_ENTITY, pos, state);
    }

    public @Nullable CloakField field() {
        return field;
    }

    public void guard(CloakField field) {
        this.field = field;
        this.guard = Guard.WAITING;
        setChanged();
    }

    public void configure(Vec3 center, float radius, float height, @Nullable UUID owner) {
        UUID id = field == null ? UUID.randomUUID() : field.id();
        boolean up = level instanceof ServerLevel server && isUp(server);
        if (up) {
            CloakFieldStore.remove((ServerLevel) level, id);
        }
        field = new CloakField(id, center, radius, height, owner);
        guard = Guard.NONE;
        setChanged();
        if (up) {
            CloakFieldStore.add((ServerLevel) level, field);
        }
    }

    public boolean isUp(ServerLevel level) {
        return field != null && CloakFieldStore.contains(level, field.id());
    }

    public void serverTick(ServerLevel level) {
        if (countdown > 0) {
            countdown--;
            return;
        }
        countdown = PERIOD_TICKS - 1;
        if (guard != Guard.NONE) {
            standGuard(level);
        } else if (field != null) {
            holdUp(level);
        }
        showActive(level);
    }

    private void standGuard(ServerLevel level) {
        if (isUp(level)) {
            return;
        }
        if (guard == Guard.WAITING) {
            CloakFieldStore.add(level, field);
            guard = Guard.HOLDING;
            setChanged();
            return;
        }
        field = null;
        guard = Guard.NONE;
        setChanged();
    }

    private void holdUp(ServerLevel level) {
        boolean up = isUp(level);
        Feed feed = feed();
        if (feed.status() != Status.FED) {
            if (up) {
                CloakFieldStore.remove(level, field.id());
                level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0f, 1.0f);
            }
            return;
        }
        burn(feed.first(), MEDIA_PER_PORT);
        burn(feed.second(), MEDIA_PER_PORT);
        if (!up) {
            CloakFieldStore.add(level, field);
            level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
    }

    private void showActive(ServerLevel level) {
        boolean active = isUp(level);
        BlockState state = getBlockState();
        if (state.getValue(HexwrightBlockStates.ACTIVE) != active) {
            level.setBlock(worldPosition, state.setValue(HexwrightBlockStates.ACTIVE, active), Block.UPDATE_ALL);
        }
    }

    public void shutDown(ServerLevel level) {
        if (field != null) {
            CloakFieldStore.remove(level, field.id());
        }
    }

    public enum Status { FED, UNFED }

    public record Feed(Status status, List<HexidTankBlockEntity> first, List<HexidTankBlockEntity> second) {

        public double hoursLeft() {
            double periods = Math.min(
                Math.min(media(first), media(second)) / (double) MEDIA_PER_PORT,
                media(union(first, second)) / (2.0 * MEDIA_PER_PORT));
            return periods * PERIOD_TICKS / TICKS_PER_HOUR;
        }
    }

    public Feed feed() {
        if (level == null) {
            return new Feed(Status.UNFED, List.of(), List.of());
        }
        Direction[] ports = ShieldGeneratorBlock.ports(getBlockState());
        List<HexidTankBlockEntity> first = hexidAt(worldPosition.relative(ports[0]));
        List<HexidTankBlockEntity> second = hexidAt(worldPosition.relative(ports[1]));
        boolean fed = media(first) >= MEDIA_PER_PORT && media(second) >= MEDIA_PER_PORT
            && media(union(first, second)) >= 2 * MEDIA_PER_PORT;
        return new Feed(fed ? Status.FED : Status.UNFED, first, second);
    }

    private List<HexidTankBlockEntity> hexidAt(BlockPos port) {
        return HexidPipeNetwork.columnsAt(level, port).stream()
            .filter(column -> !column.isRemnantStore())
            .toList();
    }

    private static List<HexidTankBlockEntity> union(List<HexidTankBlockEntity> first, List<HexidTankBlockEntity> second) {
        Set<HexidTankBlockEntity> columns = new LinkedHashSet<>(first);
        columns.addAll(second);
        return List.copyOf(columns);
    }

    private static long media(List<HexidTankBlockEntity> columns) {
        return columns.stream().mapToLong(HexidTankBlockEntity::totalMedia).sum();
    }

    private static void burn(List<HexidTankBlockEntity> columns, long media) {
        long left = media;
        for (HexidTankBlockEntity column : columns) {
            if (left <= 0) {
                break;
            }
            long held = column.totalMedia();
            if (held <= 0) {
                continue;
            }
            long take = Math.min(left, held);
            long amount = column.amountMb();
            long mb = take == held ? amount : Math.min(amount, Math.max(1, Math.round((double) take * amount / held)));
            column.store(amount - mb, held - take);
            left -= take;
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        field = tag.contains(TAG_FIELD) ? CloakField.load(tag.getCompound(TAG_FIELD)) : null;
        guard = tag.contains(TAG_GUARD) ? Guard.valueOf(tag.getString(TAG_GUARD)) : Guard.NONE;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (field != null) {
            tag.put(TAG_FIELD, field.save());
        }
        if (guard != Guard.NONE) {
            tag.putString(TAG_GUARD, guard.name());
        }
    }
}
