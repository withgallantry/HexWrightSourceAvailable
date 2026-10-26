package com.bluup.hexwright.server.dungeon;

import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.server.progression.RecipeUnlocks;
import com.bluup.hexwright.server.sound.HexwrightSoundEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class AncientTabletItem extends Item {

    private static final String TAG = "hexwright_ancient_tablet";
    private static final String TAG_FOR = "For";
    private static final String TAG_FOR_NAME = "ForName";

    private final AncientPortalVariant variant;

    public AncientTabletItem(AncientPortalVariant variant, Properties properties) {
        super(properties);
        this.variant = variant;
    }

    public AncientPortalVariant variant() {
        return this.variant;
    }

    static ItemStack issue(AncientPortalVariant variant, @Nullable Player finder) {
        ItemStack stack = new ItemStack(variant.tablet());
        if (finder != null) {
            bind(stack, finder);
        }
        return stack;
    }

    private static void bind(ItemStack stack, Player player) {
        CompoundTag tag = stack.getOrCreateTagElement(TAG);
        tag.putUUID(TAG_FOR, player.getUUID());
        tag.putString(TAG_FOR_NAME, player.getGameProfile().getName());
    }

    private static @Nullable UUID boundTo(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        return tag != null && tag.hasUUID(TAG_FOR) ? tag.getUUID(TAG_FOR) : null;
    }

    private static String boundToName(ItemStack stack) {
        CompoundTag tag = stack.getTagElement(TAG);
        return tag != null ? tag.getString(TAG_FOR_NAME) : "";
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || stack.isEmpty() || !(entity instanceof ServerPlayer player)
            || player.getAbilities().instabuild) {
            return;
        }
        UUID owner = boundTo(stack);
        if (owner == null) {
            bind(stack, player);
        } else if (!owner.equals(player.getUUID())) {
            crumble(stack, player);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer reader)) {
            return InteractionResultHolder.success(stack);
        }
        UUID owner = boundTo(stack);
        boolean creative = reader.getAbilities().instabuild;
        if (owner != null && !owner.equals(reader.getUUID()) && !creative) {
            crumble(stack, reader);
            return InteractionResultHolder.consume(stack);
        }
        String recipe = this.variant.recipeKey();
        if (!RecipeUnlocks.unlock(reader, recipe)) {
            reader.displayClientMessage(Component.translatable("message.hexwright.ancient_tablet.known",
                Component.translatable(recipe)).withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        level.playSound(null, reader.blockPosition(), HexwrightSoundEvents.recipeUnlock(), SoundSource.PLAYERS, 0.9F,
            0.8F);
        reader.displayClientMessage(Component.translatable("message.hexwright.ancient_tablet.learned",
            Component.translatable(recipe).withStyle(ChatFormatting.LIGHT_PURPLE)).withStyle(ChatFormatting.GOLD), false);
        HexwrightDebug.log(HexwrightDebug.TABLET, "[tablet] {} read an ancient tablet, learned {}",
            reader.getGameProfile().getName(), recipe);
        if (!creative) {
            stack.shrink(1);
        }
        return InteractionResultHolder.consume(stack);
    }

    private static void crumble(ItemStack stack, ServerPlayer holder) {
        ServerLevel level = holder.serverLevel();
        level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stack.copyWithCount(1)),
            holder.getX(), holder.getEyeY() - 0.3, holder.getZ(), 16, 0.2, 0.2, 0.2, 0.06);
        level.playSound(null, holder.blockPosition(), SoundEvents.DECORATED_POT_SHATTER, SoundSource.PLAYERS, 0.8F,
            0.7F);
        holder.displayClientMessage(Component.translatable("message.hexwright.ancient_tablet.crumbled",
            boundToName(stack)).withStyle(ChatFormatting.DARK_PURPLE), false);
        HexwrightDebug.log(HexwrightDebug.TABLET, "[tablet] an ancient tablet bound to {} crumbled in {}'s inventory",
            boundToName(stack), holder.getGameProfile().getName());
        stack.shrink(stack.getCount());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.hexwright.ancient_tablet.teaches",
            Component.translatable(this.variant.recipeKey()).withStyle(ChatFormatting.LIGHT_PURPLE))
            .withStyle(ChatFormatting.GRAY));
        String bound = boundToName(stack);
        tooltip.add((bound.isEmpty()
            ? Component.translatable("tooltip.hexwright.ancient_tablet.unbound")
            : Component.translatable("tooltip.hexwright.ancient_tablet.bound", bound)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.hexwright.ancient_tablet.crumbles")
            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.hexwright.ancient_tablet.use")
            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
