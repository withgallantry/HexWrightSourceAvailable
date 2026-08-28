package com.bluup.hexwright.server.block;

import com.bluup.hexwright.server.bindstone.BindstonePillar;
import com.bluup.hexwright.server.bindstone.BindstoneRegistry;
import com.bluup.hexwright.server.bindstone.BindstoneReward;
import com.bluup.hexwright.server.sound.HexwrightSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

public class BindstoneCoreBlockEntity extends BlockEntity {

    private static final String TAG_RUNES = "Runes";
    private static final String TAG_SPENT = "Spent";
    private static final String TAG_ACTIVE = "Active";

    private static final int SCAN_INTERVAL_TICKS = 10;

    private static final int AUDIENCE_CHECK_TICKS = 5;

    private int[] runes = new int[0];
    private boolean spent;

    private boolean active;

    private int scanCooldown;
    private boolean warding;

    private int strikeIn = 1;

    private final int[] bandLit = new int[BindstonePillar.RINGS];

    private boolean tidyBands = true;

    private int flashRing;

    private boolean audible;
    private int audienceCheck;

    public BindstoneCoreBlockEntity(BlockPos pos, BlockState state) {
        super(HexwrightBlocks.BINDSTONE_CORE_BLOCK_ENTITY, pos, state);
    }

    void serverTick() {
        if (level == null) {
            return;
        }

        if (spent) {
            stopWarding();
            return;
        }
        if (!warding) {
            BindstoneRegistry.registerPillar(level, worldPosition);
            warding = true;
        }

        if (active) {
            tickNotes();
            return;
        }

        if (--scanCooldown > 0) {
            return;
        }
        scanCooldown = SCAN_INTERVAL_TICKS;

        boolean visitor = level.hasNearbyAlivePlayer(
            worldPosition.getX() + 0.5,
            worldPosition.getY() + BindstonePillar.RINGS / 2.0,
            worldPosition.getZ() + 0.5,
            BindstonePillar.INFLUENCE_RADIUS
        );
        if (visitor) {
            wake();
        }
    }

    private void wake() {
        if (level == null) {
            return;
        }
        if (runes.length != BindstonePillar.RINGS) {
            runes = BindstonePillar.rollRunes(level.getRandom());
        }
        active = true;
        strikeIn = 1;
        flashRing = 0;
        setChanged();
    }

    private void tickNotes() {
        if (level == null) {
            return;
        }

        if (tidyBands) {
            BindstonePillar.clearAllRings(level, worldPosition);
            tidyBands = false;
        }

        for (int band = 0; band < bandLit.length; band++) {
            if (bandLit[band] > 0 && --bandLit[band] == 0) {
                BindstonePillar.setRing(level, worldPosition, band, 0);
            }
        }

        if (--audienceCheck <= 0) {
            audienceCheck = AUDIENCE_CHECK_TICKS;
            audible = hasAudience();
        }

        if (--strikeIn > 0) {
            return;
        }
        if (!audible) {
            strikeIn = BindstonePillar.noteTicks(BindstonePillar.NOTE_PITCH_MAX);
            return;
        }
        pluck();
    }

    private void pluck() {
        if (level == null) {
            return;
        }
        float pitch = BindstonePillar.nextPitch(level.getRandom());
        int noteTicks = BindstonePillar.noteTicks(pitch);

        BindstonePillar.setRing(level, worldPosition, flashRing, runes[flashRing]);
        bandLit[flashRing] = noteTicks;

        boolean topOfColumn = flashRing == BindstonePillar.RINGS - 1;
        strikeIn = topOfColumn
            ? noteTicks + BindstonePillar.nextRest(level.getRandom())
            : BindstonePillar.nextGap(level.getRandom(), noteTicks);
        flashRing = topOfColumn ? 0 : flashRing + 1;

        sendNote(pitch);
    }

    private void sendNote(float pitch) {
        if (level == null) {
            return;
        }
        Holder<SoundEvent> note = BuiltInRegistries.SOUND_EVENT
            .wrapAsHolder(HexwrightSoundEvents.bindstoneNote());
        Vec3 source = Vec3.atCenterOf(worldPosition);
        ClientboundSoundPacket packet = new ClientboundSoundPacket(
            note,
            SoundSource.BLOCKS,
            source.x,
            source.y,
            source.z,
            BindstonePillar.NOTE_VOLUME,
            pitch,
            level.getRandom().nextLong()
        );
        double reachSqr = BindstonePillar.INFLUENCE_RADIUS * BindstonePillar.INFLUENCE_RADIUS;
        for (Player player : level.players()) {
            if (player instanceof ServerPlayer listener && listener.distanceToSqr(source) <= reachSqr) {
                listener.connection.send(packet);
            }
        }
    }

    private boolean hasAudience() {
        if (level == null) {
            return false;
        }
        Vec3 source = Vec3.atCenterOf(worldPosition);
        double reachSqr = BindstonePillar.INFLUENCE_RADIUS * BindstonePillar.INFLUENCE_RADIUS;
        for (Player player : level.players()) {
            if (player.distanceToSqr(source) <= reachSqr) {
                return true;
            }
        }
        return false;
    }

    public boolean claim(ServerPlayer player) {
        if (level == null || spent || !active) {
            return false;
        }

        BindstonePillar.clearAllRings(level, worldPosition);
        active = false;
        java.util.Arrays.fill(bandLit, 0);
        spent = true;
        stopWarding();
        setChanged();

        level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0f, 1.4f);
        BindstoneReward.grant(player);
        return true;
    }

    private void stopWarding() {
        if (warding && level != null) {
            BindstoneRegistry.unregister(level, worldPosition);
            warding = false;
        }
    }

    @Override
    public void setRemoved() {
        stopWarding();
        super.setRemoved();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putIntArray(TAG_RUNES, runes);
        tag.putBoolean(TAG_SPENT, spent);
        tag.putBoolean(TAG_ACTIVE, active);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        runes = tag.getIntArray(TAG_RUNES);
        spent = tag.getBoolean(TAG_SPENT);
        active = tag.getBoolean(TAG_ACTIVE);
        strikeIn = 1;
        flashRing = 0;
        tidyBands = true;
    }
}
