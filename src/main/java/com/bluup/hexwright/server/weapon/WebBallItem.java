package com.bluup.hexwright.server.weapon;

import at.petrak.hexcasting.api.casting.iota.EntityIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.Vec3Iota;
import at.petrak.hexcasting.api.item.IotaHolderItem;
import at.petrak.hexcasting.api.utils.NBTHelper;
import com.bluup.hexwright.server.item.HexwrightItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class WebBallItem extends Item implements IotaHolderItem {

    public static final int AMBIT_TICKS = 6000;

    private static final double HOLD_MARGIN = 1.0D;

    private static final String TAG_HEX_DATA = "data";
    private static final String ROOT_TAG = "hexwright_web_ball";
    private static final String TAG_UNTIL = "Until";
    private static final String TAG_DIMENSION = "Dimension";

    public WebBallItem(Properties properties) {
        super(properties);
    }

    public static ItemStack spin(Iota caught, ServerLevel level) {
        ItemStack ball = new ItemStack(HexwrightItems.WEB_BALL);
        NBTHelper.put(ball, TAG_HEX_DATA, IotaType.serialize(caught));
        CompoundTag root = ball.getOrCreateTagElement(ROOT_TAG);
        root.putLong(TAG_UNTIL, level.getGameTime() + AMBIT_TICKS);
        root.putString(TAG_DIMENSION, level.dimension().location().toString());
        return ball;
    }

    @Nullable
    private static AABB holdOf(Iota held) {
        if (held instanceof EntityIota entity) {
            return entity.getEntity().isAlive() ? entity.getEntity().getBoundingBox().inflate(HOLD_MARGIN) : null;
        }
        if (held instanceof Vec3Iota vec) {
            return new AABB(BlockPos.containing(vec.getVec3())).inflate(HOLD_MARGIN);
        }
        return null;
    }

    public static long remaining(ItemStack ball, long gameTime) {
        CompoundTag root = ball.getTagElement(ROOT_TAG);
        long left = (root == null ? 0L : root.getLong(TAG_UNTIL)) - gameTime;
        return Math.max(0L, Math.min(left, AMBIT_TICKS));
    }

    public static boolean holds(Player carrier, ServerLevel level, Vec3 at) {
        String dimension = level.dimension().location().toString();
        Inventory inventory = carrier.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!(stack.getItem() instanceof WebBallItem ball) || remaining(stack, level.getGameTime()) <= 0L) {
                continue;
            }
            CompoundTag root = stack.getTagElement(ROOT_TAG);
            if (root != null && root.contains(TAG_DIMENSION) && !dimension.equals(root.getString(TAG_DIMENSION))) {
                continue;
            }
            AABB hold = holdOf(ball.readIota(stack, level));
            if (hold != null && hold.contains(at)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        if (!(level instanceof ServerLevel server) || remaining(stack, level.getGameTime()) > 0L) {
            return;
        }
        server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stack), holder.getX(), holder.getY(1.0D),
            holder.getZ(), 8, 0.2D, 0.2D, 0.2D, 0.05D);
        level.playSound(null, holder.getX(), holder.getY(), holder.getZ(), SoundEvents.WOOL_BREAK,
            SoundSource.PLAYERS, 0.6F, 1.4F);
        stack.setCount(0);
    }


    @Override
    public @Nullable CompoundTag readIotaTag(ItemStack stack) {
        return NBTHelper.getCompound(stack, TAG_HEX_DATA);
    }

    @Override
    public boolean writeable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean canWrite(ItemStack stack, @Nullable Iota iota) {
        return false;
    }

    @Override
    public void writeDatum(ItemStack stack, @Nullable Iota iota) {
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (level != null) {
            long left = remaining(stack, level.getGameTime());
            tooltip.add(Component.translatable("tooltip.hexwright.web_ball.hold",
                    String.format("%d:%02d", left / 1200, left / 20 % 60))
                .withStyle(ChatFormatting.GRAY));
        }
        IotaHolderItem.appendHoverText(this, stack, tooltip, flag);
    }
}
