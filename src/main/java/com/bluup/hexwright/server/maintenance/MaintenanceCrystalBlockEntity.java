package com.bluup.hexwright.server.maintenance;

import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.server.dungeon.DungeonDimension;
import com.bluup.hexwright.server.dungeon.DungeonInstance;
import com.bluup.hexwright.server.dungeon.DungeonInstances;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class MaintenanceCrystalBlockEntity extends BlockEntity {

    static final int EVENT_RIGHT = 1;
    static final int EVENT_WRONG = 2;
    static final int EVENT_ENGAGED = 3;

    private static final int REPEAT_TICKS = 8;
    private static final double TOLD_WITHIN = 48.0;

    private static final String ENGAGED_MESSAGE = "message.hexwright.maintenance.engaged";
    private static final String UNDER_WAY_MESSAGE = "message.hexwright.maintenance.under_way";

    private int progress;
    private boolean engaged;
    private int lastSlot = -1;
    private long lastPressAt;

    private int flashSlot = -1;
    private boolean flashWrong;
    private long flashedAt;

    public MaintenanceCrystalBlockEntity(BlockPos pos, BlockState state) {
        super(KeystoneStudy.MAINTENANCE_CRYSTAL_BLOCK_ENTITY, pos, state);
    }

    public int progress() {
        return this.progress;
    }

    public boolean engaged() {
        return this.engaged;
    }

    public int flashSlot() {
        return this.flashSlot;
    }

    public boolean flashWrong() {
        return this.flashWrong;
    }

    public long flashedAt() {
        return this.flashedAt;
    }

    void press(ServerLevel level, ServerPlayer player, int slot) {
        long now = level.getGameTime();
        boolean repeat = slot == this.lastSlot && now - this.lastPressAt < REPEAT_TICKS;
        this.lastSlot = slot;
        this.lastPressAt = now;
        if (repeat || this.engaged) {
            return;
        }
        if (PlanarMaintenance.engaged(level.getServer())) {
            player.displayClientMessage(Component.translatable(UNDER_WAY_MESSAGE), true);
            return;
        }
        Block block = getBlockState().getBlock();
        if (slot == MaintenanceRunes.expected(this.progress)) {
            this.progress++;
            level.playSound(null, this.worldPosition, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.0F,
                0.7F + 0.12F * this.progress);
            level.blockEvent(this.worldPosition, block, EVENT_RIGHT, slot);
            HexwrightDebug.log(HexwrightDebug.MAINTENANCE, "[maintenance] {} pressed rune {} of {} at {}",
                player.getGameProfile().getName(), this.progress, MaintenanceRunes.ORDER.length,
                this.worldPosition.toShortString());
            if (this.progress == MaintenanceRunes.ORDER.length) {
                engage(level, player);
            }
        } else {
            this.progress = 0;
            level.playSound(null, this.worldPosition, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS,
                0.8F, 1.3F);
            level.blockEvent(this.worldPosition, block, EVENT_WRONG, slot);
        }
        sync(level);
    }

    private void engage(ServerLevel level, ServerPlayer player) {
        this.engaged = true;
        DungeonInstance workshop = level.dimension() == DungeonDimension.KEY
            ? DungeonInstances.at(level.getServer(), Vec3.atCenterOf(this.worldPosition)) : null;
        PlanarMaintenance.engage(level.getServer(), workshop == null ? -1 : workshop.id());
        for (BlockPos half : new BlockPos[]{this.worldPosition, this.worldPosition.above()}) {
            BlockState state = level.getBlockState(half);
            if (state.getBlock() instanceof MaintenanceCrystalBlock) {
                level.setBlock(half, state.setValue(MaintenanceCrystalBlock.ENGAGED, true), Block.UPDATE_ALL);
            }
        }
        Vec3 centre = MaintenanceRunes.centre(this.worldPosition);
        level.playSound(null, this.worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5F, 0.7F);
        level.playSound(null, this.worldPosition, SoundEvents.CONDUIT_ACTIVATE, SoundSource.BLOCKS, 1.5F, 0.8F);
        level.sendParticles(ParticleTypes.END_ROD, centre.x, centre.y, centre.z, 80, 0.3, 0.6, 0.3, 0.12);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, centre.x, centre.y, centre.z, 40, 0.4, 0.8, 0.4, 0.3);
        level.blockEvent(this.worldPosition, getBlockState().getBlock(), EVENT_ENGAGED, 0);
        Component told = Component.translatable(ENGAGED_MESSAGE).withStyle(ChatFormatting.AQUA);
        for (ServerPlayer near : level.getPlayers(p -> p.distanceToSqr(centre) < TOLD_WITHIN * TOLD_WITHIN)) {
            near.sendSystemMessage(told);
        }
        HexwrightDebug.log(HexwrightDebug.MAINTENANCE, "[maintenance] engaged by {} at {}",
            player.getGameProfile().getName(), this.worldPosition.toShortString());
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (this.level == null || !this.level.isClientSide) {
            return id >= EVENT_RIGHT && id <= EVENT_ENGAGED;
        }
        if (id == EVENT_RIGHT || id == EVENT_WRONG) {
            this.flashSlot = param;
            this.flashWrong = id == EVENT_WRONG;
            this.flashedAt = this.level.getGameTime();
            Vec3 at = runeCentre(param);
            for (int i = 0; i < 6; i++) {
                this.level.addParticle(id == EVENT_RIGHT ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.SMOKE,
                    at.x, at.y, at.z, (this.level.random.nextDouble() - 0.5) * 0.08, 0.03,
                    (this.level.random.nextDouble() - 0.5) * 0.08);
            }
            return true;
        }
        if (id == EVENT_ENGAGED) {
            this.flashSlot = -1;
            this.flashedAt = this.level.getGameTime();
            return true;
        }
        return false;
    }

    private Vec3 runeCentre(int slot) {
        double angle = MaintenanceRunes.facetAngle(slot / MaintenanceRunes.ROWS);
        double out = MaintenanceRunes.apothem() + 0.05;
        return MaintenanceRunes.centre(this.worldPosition).add(Math.cos(angle) * out,
            MaintenanceRunes.rowHeight(slot % MaintenanceRunes.ROWS), Math.sin(angle) * out);
    }

    private void sync(ServerLevel level) {
        setChanged();
        BlockState state = getBlockState();
        level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.progress = tag.getInt("Progress");
        this.engaged = tag.getBoolean("Engaged");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Progress", this.progress);
        tag.putBoolean("Engaged", this.engaged);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
