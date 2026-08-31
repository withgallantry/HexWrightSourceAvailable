package com.bluup.hexwright.server.weapon;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.item.IotaHolderItem;
import at.petrak.hexcasting.api.utils.NBTHelper;
import com.bluup.hexwright.common.animation.PlayerAnimationLayer;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.item.ArtifactItem;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class BerserkWeaponItem extends SwordItem implements AnimatedWeapon, IotaHolderItem {

    private static final int SWING_DURABILITY_COST = 2;

    private static final String TAG_HEX_DATA = "data";

    private static final int SWING_RECOVERY_TICKS = 12;

    private final String swingClip;

    @Nullable
    private final String idleClip;

    private final int windUpTicks;

    private final int cooldownTicks;

    protected BerserkWeaponItem(Tier tier, int attackDamageModifier, float attackSpeedModifier,
                                String swingClip, @Nullable String idleClip, int windUpTicks,
                                int cooldownTicks, Properties properties) {
        super(tier, attackDamageModifier, attackSpeedModifier, properties);
        this.swingClip = swingClip;
        this.idleClip = idleClip;
        this.windUpTicks = windUpTicks;
        this.cooldownTicks = cooldownTicks;
    }

    @Override
    @Nullable
    public String idleClip() {
        return this.idleClip;
    }

    protected String swingClip(Player player) {
        return this.swingClip;
    }

    @Override
    public boolean isMidSwing(Player player) {
        ItemCooldowns cooldowns = player.getCooldowns();
        if (!cooldowns.isOnCooldown(this)) {
            return false;
        }
        float elapsed = this.cooldownTicks * (1.0F - cooldowns.getCooldownPercent(this, 0.0F));
        return elapsed < this.windUpTicks + SWING_RECOVERY_TICKS;
    }

    @Override
    public float swingArcReach() {
        return 4.0F;
    }

    @Override
    public float swingArcDegrees() {
        return 100.0F;
    }

    protected abstract boolean land(ServerLevel level, ServerPlayer player, InteractionHand hand);

    protected boolean canSwing(Level level, Player player) {
        return true;
    }

    protected abstract Component swingTooltip();

    protected static String blocks(float distance) {
        return distance == Math.round(distance)
            ? String.valueOf(Math.round(distance))
            : String.format("%.2f", distance);
    }

    @Nullable
    protected Component swingTooltipExtra() {
        return null;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        if (!canSwing(level, player)) {
            return InteractionResultHolder.fail(stack);
        }

        player.getCooldowns().addCooldown(this, this.cooldownTicks);
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            HexwrightNetworking.sendPlayerAnimation(serverPlayer, PlayerAnimationLayer.ONE_SHOT, swingClip(player));
            SlamWindUp.schedule(serverLevel.getServer(), this.windUpTicks,
                () -> strike(serverLevel, serverPlayer, hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private void strike(ServerLevel level, ServerPlayer player, InteractionHand hand) {
        if (player.isRemoved() || !player.isAlive() || player.level() != level) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(this)) {
            return;
        }
        if (!land(level, player, hand)) {
            return;
        }
        stack.hurtAndBreak(SWING_DURABILITY_COST, player, breaker -> breaker.broadcastBreakEvent(hand));
    }

    protected boolean castsOnCaught() {
        return false;
    }

    @Nullable
    public PocketCasterData.Quality quality() {
        return null;
    }

    protected Component castTooltip() {
        return Component.translatable("tooltip.hexwright.berserk_weapon.cast");
    }

    protected void castOnCaught(ServerPlayer wielder, ItemStack stack,
                                List<? extends Entity> caught, Vec3 impact) {
        CompoundTag hex = inscribedHex(stack);
        if (hex == null || caught.isEmpty()) {
            return;
        }
        WeaponHexCasting.castOnCaught(wielder, hex, caught, impact);
    }

    @Nullable
    protected CompoundTag inscribedHex(ItemStack stack) {
        if (!castsOnCaught()) {
            return null;
        }
        CompoundTag hex = readIotaTag(stack);
        return hex == null || hex.isEmpty() ? null : hex;
    }


    @Override
    public @Nullable CompoundTag readIotaTag(ItemStack stack) {
        return NBTHelper.getCompound(stack, TAG_HEX_DATA);
    }

    @Override
    public boolean writeable(ItemStack stack) {
        return castsOnCaught();
    }

    @Override
    public boolean canWrite(ItemStack stack, @Nullable Iota iota) {
        return castsOnCaught();
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
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        appendQualityTooltip(tooltip);
        WeaponTooltips.add(tooltip, swingTooltip(), ChatFormatting.GRAY);
        Component extra = swingTooltipExtra();
        if (extra != null) {
            WeaponTooltips.add(tooltip, extra, ChatFormatting.GRAY);
        }
        WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.berserk_weapon.recharge",
            String.format("%.1f", this.cooldownTicks / 20.0F)), ChatFormatting.GRAY);

        if (!castsOnCaught()) {
            return;
        }
        WeaponTooltips.add(tooltip, castTooltip(), ChatFormatting.DARK_PURPLE);
        CompoundTag hex = readIotaTag(stack);
        if (hex == null || hex.isEmpty()) {
            WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.berserk_weapon.uninscribed"),
                ChatFormatting.DARK_GRAY);
        }
        IotaHolderItem.appendHoverText(this, stack, tooltip, flag);
    }

    protected final void appendQualityTooltip(List<Component> tooltip) {
        if (this instanceof ArtifactItem) {
            tooltip.add(ArtifactItem.tooltipLine());
            return;
        }
        PocketCasterData.Quality quality = quality();
        if (quality == null) {
            return;
        }
        tooltip.add(Component.translatable("tooltip.hexwright.berserk_weapon.quality",
                Component.translatable(quality.translationKey()).withStyle(quality.color()))
            .withStyle(ChatFormatting.GRAY));
    }
}
