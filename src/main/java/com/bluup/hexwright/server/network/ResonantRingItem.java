package com.bluup.hexwright.server.network;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.item.IotaHolderItem;
import at.petrak.hexcasting.api.utils.NBTHelper;
import com.bluup.hexwright.common.network.ResonanceNameCache;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ResonantRingItem extends Item implements IotaHolderItem {

    static final String ROOT_TAG = "hexwright_resonant_ring";

    private static final String TAG_HEX_DATA = "data";

    public ResonantRingItem(Properties properties) {
        super(properties);
    }

    public static void attune(ItemStack ring, Level level, BlockPos towerPos) {
        ResonantAttunement.attune(ring, ROOT_TAG, level, towerPos);
    }

    public static @Nullable BlockPos attunedPos(ItemStack ring) {
        return ResonantAttunement.towerPos(ring, ROOT_TAG);
    }

    public static @Nullable String networkKey(ItemStack ring) {
        return ResonantAttunement.networkKey(ring, ROOT_TAG);
    }


    @Override
    public @Nullable CompoundTag readIotaTag(ItemStack stack) {
        return NBTHelper.getCompound(stack, TAG_HEX_DATA);
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
        if (iota == null) {
            stack.removeTagKey(TAG_HEX_DATA);
        } else {
            NBTHelper.put(stack, TAG_HEX_DATA, IotaType.serialize(iota));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        BlockPos pos = attunedPos(stack);
        if (pos == null) {
            tooltip.add(Component.translatable("tooltip.hexwright.resonant_ring.unattuned", Component.keybind("key.use"))
                .withStyle(ChatFormatting.GRAY));
        } else {
            String name = ResonanceNameCache.nameOf(networkKey(stack));
            if (name != null) {
                tooltip.add(Component.translatable("tooltip.hexwright.resonant_ring.network", name)
                    .withStyle(ChatFormatting.AQUA));
            }
            String dimension = ResonantAttunement.dimension(stack, ROOT_TAG);
            String dimensionPath = dimension.isEmpty() ? "?" : new ResourceLocation(dimension).getPath();
            tooltip.add(Component.translatable("tooltip.hexwright.resonant_ring.attuned",
                    pos.getX(), pos.getY(), pos.getZ(), dimensionPath)
                .withStyle(ChatFormatting.AQUA));
        }

        IotaHolderItem.appendHoverText(this, stack, tooltip, flag);

        List<RingSubscriptions.Subscription> subscriptions = RingSubscriptions.list(stack);
        if (subscriptions.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.hexwright.resonant_ring.no_subscriptions")
                .withStyle(ChatFormatting.GRAY));
        } else {
            for (RingSubscriptions.Subscription subscription : subscriptions) {
                tooltip.add(Component.translatable("tooltip.hexwright.resonant_ring.subscription",
                        subscription.harmonic())
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            tooltip.add(Component.translatable("tooltip.hexwright.resonant_ring.subscription_count",
                    subscriptions.size(), RingSubscriptions.MAX_SUBSCRIPTIONS)
                .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
