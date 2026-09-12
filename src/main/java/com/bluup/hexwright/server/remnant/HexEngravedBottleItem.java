package com.bluup.hexwright.server.remnant;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.item.IotaHolderItem;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.block.PlacedBottleBlock;
import com.bluup.hexwright.common.remnant.Remnant;
import com.bluup.hexwright.server.fluid.TankRemnants;
import com.bluup.hexwright.server.progression.MakersMark;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class HexEngravedBottleItem extends Item implements IotaHolderItem {

    private static final int DRINK_TICKS = 32;

    public HexEngravedBottleItem(Properties properties) {
        super(properties);
    }


    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return DRINK_TICKS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (BottleData.isEmpty(stack)) {
            return InteractionResultHolder.pass(stack);
        }
        return ItemUtils.startUsingInstantly(level, player, hand);
    }


    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!context.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }

        BlockPlaceContext placement = new BlockPlaceContext(context);
        if (!placement.canPlace()) {
            return InteractionResult.FAIL;
        }

        Level level = context.getLevel();
        BlockPos pos = placement.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        if (!HexwrightBlocks.PLACED_BOTTLE_BLOCK.defaultBlockState().canSurvive(level, pos)) {
            return InteractionResult.FAIL;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!PlacedBottleBlock.place(level, pos, stack, player == null ? 0.0f : player.getYRot(), player)) {
            return InteractionResult.FAIL;
        }
        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        TankRemnants contents = BottleData.getMixture(stack);
        if (!contents.isEmpty() && !level.isClientSide && user instanceof Player player) {
            RemnantDrinking.drink(player, contents);
            level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, 0.7f, 1.4f);
        }
        BottleData.empty(stack);
        return stack;
    }


    @Override
    public @Nullable CompoundTag readIotaTag(ItemStack stack) {
        Remnant contents = BottleData.getContents(stack);
        return contents == null ? null : IotaType.serialize(new RemnantIota(contents));
    }

    @Override
    public boolean writeable(ItemStack stack) {
        return true;
    }

    @Override
    public boolean canWrite(ItemStack stack, @Nullable Iota iota) {
        if (iota == null) {
            return !BottleData.isEmpty(stack);
        }
        if (!(iota instanceof RemnantIota remnantIota)) {
            return false;
        }
        return BottleData.canAccept(stack, remnantIota.getRemnant());
    }

    @Override
    public void writeDatum(ItemStack stack, @Nullable Iota iota) {
        if (iota == null) {
            BottleData.empty(stack);
            return;
        }
        if (iota instanceof RemnantIota remnantIota) {
            BottleData.pour(stack, remnantIota.getRemnant());
        }
    }


    @Override
    public Component getName(ItemStack stack) {
        TankRemnants mix = BottleData.getMixture(stack);
        if (mix.isEmpty()) {
            return super.getName(stack);
        }
        if (mix.kinds() == 1) {
            return Component.translatable("item.hexwright.hex_engraved_bottle.filled",
                mix.contents().get(0).type().label());
        }
        return Component.translatable("item.hexwright.hex_engraved_bottle.blend", mix.kinds());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        MakersMark.appendTooltip(stack, tooltip);

        TankRemnants mix = BottleData.getMixture(stack);
        if (mix.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.hexwright.hex_engraved_bottle.empty",
                BottleData.capacity(stack)).withStyle(ChatFormatting.GRAY));
            return;
        }

        if (mix.kinds() > 1) {
            tooltip.add(Component.translatable("tooltip.hexwright.hex_engraved_bottle.blend",
                (int) Math.round(mix.total()), BottleData.capacity(stack)).withStyle(ChatFormatting.GRAY));
        }
        for (Remnant part : mix.contents()) {
            tooltip.add(mix.kinds() > 1
                ? Component.translatable("tooltip.hexwright.hex_engraved_bottle.part",
                    part.type().label(), part.wholeDrams()).withStyle(part.type().textColour())
                : Component.translatable("tooltip.hexwright.hex_engraved_bottle.contents",
                    part.type().label(), part.wholeDrams(), BottleData.capacity(stack))
                    .withStyle(part.type().textColour()));
            tooltip.add(RemnantDrinking.describe(part).withStyle(ChatFormatting.GRAY));
        }
    }
}
