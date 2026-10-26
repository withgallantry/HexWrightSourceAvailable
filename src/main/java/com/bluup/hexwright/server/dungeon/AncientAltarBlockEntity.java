package com.bluup.hexwright.server.dungeon;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

public class AncientAltarBlockEntity extends CrystalAltarBlockEntity {

    private static final double KEY_OUT = 1.0;
    private static final double KEY_UP = 1.1;

    private static final RawAnimation DARK = RawAnimation.begin().thenLoop("non_activated_idle");
    private static final RawAnimation LIT = RawAnimation.begin().thenLoop("activate_idle");
    private static final RawAnimation LIGHT = RawAnimation.begin().thenPlay("activate_animation").thenLoop("activate_idle");
    private static final RawAnimation DARKEN = RawAnimation.begin().thenPlay("close_animation").thenLoop("non_activated_idle");

    private @Nullable BlockPos portal;

    private @Nullable Boolean shown;

    public AncientAltarBlockEntity(BlockPos pos, BlockState state) {
        super(AncientPortals.ALTAR_BLOCK_ENTITY, pos, state);
    }

    public @Nullable BlockPos portal() {
        return this.portal;
    }

    void bindPortal(BlockPos portal) {
        this.portal = portal;
        setChanged();
    }

    @Override
    public Vec3 keyOffset() {
        Direction front = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        return new Vec3(front.getStepX() * KEY_OUT, KEY_UP, front.getStepZ() * KEY_OUT);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "altar", 0, state -> {
            boolean lit = lit();
            if (this.shown == null || this.shown != lit) {
                state.getController().setAnimation(this.shown == null ? (lit ? LIT : DARK) : (lit ? LIGHT : DARKEN));
                this.shown = lit;
            }
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.portal = tag.contains("Portal") ? BlockPos.of(tag.getLong("Portal")) : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (this.portal != null) {
            tag.putLong("Portal", this.portal.asLong());
        }
    }
}
