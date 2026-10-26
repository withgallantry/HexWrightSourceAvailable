package com.bluup.hexwright.server.dungeon;

import com.bluup.hexwright.HexwrightDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class AncientPortalBlockEntity extends TravelStoneBlockEntity {

    private static final Clips CLIPS = Clips.of("non_activated_idle", "activate_idle", "activate_animation",
        "close_animation");

    private static final double WINDOW_SILL = 0.625;

    private static final int OPEN_TICKS = 110;
    private static final int CLOSE_TICKS = 110;

    private static final long FIRST_RUNE_TICKS = 21;
    private static final long RUNE_INTERVAL_TICKS = 20;
    private static final int RUNES = 4;
    private static final long RISE_TICKS = 98;
    private static final long LAND_TICKS = 74;
    private static final long HUM_TICKS = 240;

    private @Nullable UUID owner;
    private String ownerName = "";

    public AncientPortalBlockEntity(BlockPos pos, BlockState state) {
        super(AncientPortals.PORTAL_BLOCK_ENTITY, pos, state);
    }

    public AncientPortalVariant variant() {
        return getBlockState().getBlock() instanceof AncientPortalBlock portal
            ? portal.variant()
            : AncientPortalVariant.STONE;
    }

    public String ownerName() {
        return this.ownerName;
    }

    public boolean answersTo(Player player) {
        return this.owner == null || this.owner.equals(player.getUUID()) || player.getAbilities().instabuild;
    }

    void setOwner(Player player) {
        this.owner = player.getUUID();
        this.ownerName = player.getGameProfile().getName();
        setChanged();
    }

    InteractionResult dismantle(ServerLevel level, ServerPlayer player) {
        if (!answersTo(player)) {
            player.displayClientMessage(Component.translatable("message.hexwright.ancient_portal.not_owner",
                this.ownerName), true);
            return InteractionResult.FAIL;
        }
        if (phase() != Phase.CLOSED || isOpen(level.getServer())) {
            player.displayClientMessage(Component.translatable("message.hexwright.ancient_portal.still_open"), true);
            return InteractionResult.FAIL;
        }
        ItemStack portal = new ItemStack(variant().portalItem());
        level.removeBlock(this.worldPosition, false);
        level.playSound(null, this.worldPosition, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 1.0F, 0.6F);
        player.getInventory().placeItemBackInInventory(portal);
        HexwrightDebug.log(HexwrightDebug.DUNGEON, "[dungeon] {} took down their ancient portal at {}",
            player.getGameProfile().getName(), this.worldPosition.toShortString());
        return InteractionResult.CONSUME;
    }

    @Override
    protected double windowSill() {
        return WINDOW_SILL;
    }

    @Override
    public int openTicks() {
        return OPEN_TICKS;
    }

    @Override
    protected int closeTicks() {
        return CLOSE_TICKS;
    }

    @Override
    protected Clips clips() {
        return CLIPS;
    }

    @Override
    protected void phaseSounds(ServerLevel level, long elapsed) {
        switch (phase()) {
            case OPENING -> {
                long sinceFirstRune = elapsed - FIRST_RUNE_TICKS;
                if (sinceFirstRune >= 0 && sinceFirstRune % RUNE_INTERVAL_TICKS == 0
                    && sinceFirstRune / RUNE_INTERVAL_TICKS < RUNES) {
                    play(level, SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F, 0.75F);
                } else if (elapsed == RISE_TICKS) {
                    play(level, SoundEvents.EVOKER_PREPARE_SUMMON, 1.5F, 0.5F);
                    play(level, SoundEvents.PORTAL_TRIGGER, 0.6F, 1.4F);
                }
            }
            case OPEN -> {
                if (elapsed % HUM_TICKS == 0) {
                    play(level, SoundEvents.PORTAL_AMBIENT, 0.7F, 1.0F);
                }
            }
            case CLOSING -> {
                if (elapsed == 0) {
                    play(level, SoundEvents.EVOKER_PREPARE_SUMMON, 1.0F, 0.6F);
                } else if (elapsed == LAND_TICKS) {
                    play(level, SoundEvents.TUFF_PLACE, 1.5F, 0.5F);
                }
            }
            case CLOSED -> {
            }
        }
    }

    private void play(ServerLevel level, SoundEvent sound, float volume, float pitch) {
        Vec3 center = windowCenter();
        level.playSound(null, center.x, center.y, center.z, sound, SoundSource.BLOCKS, volume, pitch);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        this.ownerName = tag.getString("OwnerName");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (this.owner != null) {
            tag.putUUID("Owner", this.owner);
            tag.putString("OwnerName", this.ownerName);
        }
    }
}
