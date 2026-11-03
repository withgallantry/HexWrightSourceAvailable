package com.bluup.hexwright.server.vehicle;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.item.IotaHolderItem;
import at.petrak.hexcasting.api.item.MediaHolderItem;
import com.bluup.hexwright.server.pentabox.PentaboxData;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class VehicleItem extends Item implements IotaHolderItem, MediaHolderItem {

    protected VehicleItem(Properties properties) {
        super(properties);
    }

    protected abstract VehicleEntity createEntity(ServerLevel level);

    protected abstract long getMediaCapacity(ItemStack stack);

    protected boolean mountsOnDeploy() {
        return false;
    }


    @Override
    public @Nullable CompoundTag readIotaTag(ItemStack stack) {
        return VehicleData.getHexRawTag(stack.getOrCreateTagElement(VehicleData.ROOT_TAG));
    }

    @Override
    public boolean writeable(ItemStack stack) {
        return true;
    }

    @Override
    public boolean canWrite(ItemStack stack, @Nullable Iota iota) {
        return true;
    }

    @Override
    public void writeDatum(ItemStack stack, @Nullable Iota iota) {
        VehicleData.setHex(stack.getOrCreateTagElement(VehicleData.ROOT_TAG), iota);
    }


    @Override
    public long getMedia(ItemStack stack) {
        return VehicleData.getMedia(stack.getOrCreateTagElement(VehicleData.ROOT_TAG));
    }

    @Override
    public long getMaxMedia(ItemStack stack) {
        return getMediaCapacity(stack);
    }

    @Override
    public void setMedia(ItemStack stack, long media) {
        VehicleData.setMedia(stack.getOrCreateTagElement(VehicleData.ROOT_TAG), media, getMediaCapacity(stack));
    }

    @Override
    public boolean canProvideMedia(ItemStack stack) {
        return false;
    }

    @Override
    public boolean canRecharge(ItemStack stack) {
        return true;
    }


    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.pass(stack);
        }

        Vec3 eye = player.getEyePosition();
        Vec3 reachEnd = eye.add(player.getLookAngle().scale(6.0));
        BlockHitResult hit = level.clip(new ClipContext(
            eye, reachEnd, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

        VehicleEntity entity = createEntity(serverLevel);
        entity.readFromItemStack(stack, serverLevel);

        if (!placeWhereItFits(entity, player, serverLevel, hit, reachEnd)) {
            return InteractionResultHolder.fail(stack);
        }

        if (!serverLevel.addFreshEntity(entity)) {
            return InteractionResultHolder.fail(stack);
        }

        if (mountsOnDeploy()) {
            player.startRiding(entity, true);
        }

        if (!player.getAbilities().instabuild) {
            if (!PentaboxData.withdrawProjection(player, hand)) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), false);
    }

    private boolean placeWhereItFits(
        VehicleEntity entity, Player player, ServerLevel level, BlockHitResult hit, Vec3 reachEnd
    ) {
        Vec3 aimed = findDeployPosition(hit, reachEnd, entity);
        List<Vec3> candidates = mountsOnDeploy()
            ? List.of(player.position().add(0.0, VehicleConfig.DEPLOY_HOVER_HEIGHT, 0.0), aimed)
            : List.of(aimed);

        for (Vec3 candidate : candidates) {
            entity.moveTo(candidate.x, candidate.y, candidate.z, player.getYRot(), 0.0f);
            if (level.noCollision(entity)) {
                return true;
            }
        }
        return false;
    }

    private static Vec3 findDeployPosition(BlockHitResult hit, Vec3 reachEnd, VehicleEntity entity) {
        if (hit.getType() != HitResult.Type.BLOCK) {
            return reachEnd;
        }
        Direction face = hit.getDirection();
        Vec3 base = hit.getLocation();
        if (face.getAxis() == Direction.Axis.Y) {
            return base.add(0.0, VehicleConfig.DEPLOY_HOVER_HEIGHT, 0.0);
        }
        Vec3 awayFromWall = Vec3.atLowerCornerOf(face.getNormal())
            .scale(entity.getBbWidth() / 2.0 + VehicleConfig.DEPLOY_WALL_CLEARANCE);
        return base.add(awayFromWall).add(0.0, VehicleConfig.DEPLOY_HOVER_HEIGHT, 0.0);
    }
}
