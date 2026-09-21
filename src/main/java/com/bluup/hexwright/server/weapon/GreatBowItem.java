package com.bluup.hexwright.server.weapon;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.item.IotaHolderItem;
import at.petrak.hexcasting.api.utils.NBTHelper;
import com.bluup.hexwright.server.armour.VenatorPowers;
import com.bluup.hexwright.server.item.ArtifactItem;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class GreatBowItem extends BowItem implements IotaHolderItem {

    private static final String TAG_HEX_DATA = "data";

    private static final String ROOT_TAG = "hexwright_great_bow";
    private static final String TAG_NEXT_HEX_TICK = "NextHexTick";
    private static final String TAG_HEX_COOLDOWN = "HexCooldown";

    private final PocketCasterData.Quality quality;

    public GreatBowItem(PocketCasterData.Quality quality, Properties properties) {
        super(properties);
        this.quality = quality;
    }

    public PocketCasterData.Quality quality() {
        return this.quality;
    }

    protected double arrowBaseDamage() {
        return GreatBowGrade.arrowBaseDamage(this.quality);
    }

    protected int hexCooldownTicks() {
        return GreatBowGrade.hexCooldownTicks(this.quality);
    }

    protected boolean hasInfiniteAmmo() {
        return false;
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


    private static long nextHexTick(ItemStack stack) {
        CompoundTag root = stack.getTagElement(ROOT_TAG);
        return root == null ? 0L : root.getLong(TAG_NEXT_HEX_TICK);
    }

    private static void setNextHexTick(ItemStack stack, long tick) {
        stack.getOrCreateTagElement(ROOT_TAG).putLong(TAG_NEXT_HEX_TICK, tick);
    }

    private static int runningCooldown(ItemStack stack, int gradeCooldown) {
        CompoundTag root = stack.getTagElement(ROOT_TAG);
        int stored = root == null ? 0 : root.getInt(TAG_HEX_COOLDOWN);
        return stored > 0 ? stored : gradeCooldown;
    }

    private static void setRunningCooldown(ItemStack stack, int ticks) {
        stack.getOrCreateTagElement(ROOT_TAG).putInt(TAG_HEX_COOLDOWN, ticks);
    }

    private static long rechargeRemaining(ItemStack stack, Level level, int cooldownTicks) {
        long remaining = nextHexTick(stack) - level.getGameTime();
        return Math.max(0L, Math.min(remaining, cooldownTicks));
    }

    public static float rechargeFraction(ItemStack stack, Level level, float partialTick) {
        if (!(stack.getItem() instanceof GreatBowItem bow)) {
            return 0.0F;
        }
        int cooldown = runningCooldown(stack, bow.hexCooldownTicks());
        long remaining = rechargeRemaining(stack, level, cooldown);
        return Math.max(sweepOf(remaining, cooldown, partialTick),
            bow.powerRechargeFraction(stack, level, partialTick));
    }

    protected float powerRechargeFraction(ItemStack stack, Level level, float partialTick) {
        return 0.0F;
    }

    protected static float sweepOf(long remaining, int total, float partialTick) {
        if (remaining <= 0L || total <= 0) {
            return 0.0F;
        }
        return Math.min(1.0F, Math.max(0.0F, (remaining - partialTick) / total));
    }


    @Override
    public void releaseUsing(ItemStack bow, Level level, LivingEntity shooter, int timeLeft) {
        if (!(shooter instanceof Player player)) {
            return;
        }
        boolean infinite = hasInfiniteAmmo()
            || player.getAbilities().instabuild
            || EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS, bow) > 0;
        ItemStack ammo = player.getProjectile(bow);
        if (ammo.isEmpty() && !infinite) {
            return;
        }
        if (ammo.isEmpty()) {
            ammo = new ItemStack(Items.ARROW);
        }

        float power = getPowerForTime(this.getUseDuration(bow) - timeLeft);
        if (power < 0.1F) {
            return;
        }
        boolean freeArrow = infinite && ammo.is(Items.ARROW);

        if (!level.isClientSide) {
            AbstractArrow arrow = createArrow(level, ammo, player);
            arrow.setBaseDamage(arrowBaseDamage());

            if (arm(bow, level, arrow)) {
                int cooldown = VenatorPowers.hexCooldownFor(player, hexCooldownTicks());
                setRunningCooldown(bow, cooldown);
                setNextHexTick(bow, level.getGameTime() + cooldown);
                announceArmedShot((ServerLevel) level, player);
            }

            arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, power * 3.0F, 1.0F);
            if (power == 1.0F) {
                arrow.setCritArrow(true);
            }
            int powerLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, bow);
            if (powerLevel > 0) {
                arrow.setBaseDamage(arrow.getBaseDamage() + powerLevel * 0.5 + 0.5);
            }
            int punchLevel = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS, bow);
            if (punchLevel > 0) {
                arrow.setKnockback(punchLevel);
            }
            if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS, bow) > 0) {
                arrow.setSecondsOnFire(100);
            }
            bow.hurtAndBreak(1, player, broken -> broken.broadcastBreakEvent(player.getUsedItemHand()));
            if (freeArrow || player.getAbilities().instabuild
                && (ammo.is(Items.SPECTRAL_ARROW) || ammo.is(Items.TIPPED_ARROW))) {
                arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            }
            level.addFreshEntity(arrow);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT,
            SoundSource.PLAYERS, 1.0F, 1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + power * 0.5F);
        boolean refunded = !level.isClientSide && !freeArrow && !player.getAbilities().instabuild
            && VenatorPowers.refundsArrow(player);
        if (!freeArrow && !player.getAbilities().instabuild && !refunded) {
            ammo.shrink(1);
            if (ammo.isEmpty()) {
                player.getInventory().removeItem(ammo);
            }
        }
        if (refunded && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.inventoryMenu.sendAllDataToRemote();
        }
        player.awardStat(Stats.ITEM_USED.get(this));
    }

    private boolean arm(ItemStack bow, Level level, AbstractArrow arrow) {
        if (!(arrow instanceof HexArrowEntity hexArrow)) {
            return false;
        }
        if (rechargeRemaining(bow, level, runningCooldown(bow, hexCooldownTicks())) > 0L) {
            return false;
        }
        CompoundTag hex = readIotaTag(bow);
        if (hex == null || hex.isEmpty()) {
            return false;
        }
        hexArrow.setHex(hex.copy());
        return true;
    }

    private static void announceArmedShot(ServerLevel level, Player player) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.4F);
        level.sendParticles(ParticleTypes.WITCH, player.getX(), player.getEyeY() - 0.2, player.getZ(),
            8, 0.25, 0.25, 0.25, 0.02);
    }

    private static AbstractArrow createArrow(Level level, ItemStack ammo, Player player) {
        ArrowItem arrowItem = (ArrowItem) (ammo.getItem() instanceof ArrowItem ? ammo.getItem() : Items.ARROW);
        if (arrowItem == Items.ARROW) {
            return new HexArrowEntity(level, player);
        }
        return arrowItem.createArrow(level, ammo, player);
    }

    @Override
    public Component getName(ItemStack stack) {
        return WeaponTooltips.graded(stack,
            Component.translatable("item.hexwright.archer_great_bow"), this.quality);
    }

    protected void appendExtraTooltip(ItemStack stack, List<Component> tooltip) {
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        if (this instanceof ArtifactItem) {
            tooltip.add(ArtifactItem.tooltipLine());
        } else {
            tooltip.add(Component.translatable("tooltip.hexwright.archer_great_bow.quality",
                    Component.translatable(this.quality.translationKey()).withStyle(this.quality.color()))
                .withStyle(ChatFormatting.GRAY));
        }
        int cooldown = hexCooldownTicks();
        WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.archer_great_bow.cast"),
            ChatFormatting.DARK_PURPLE);
        WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.archer_great_bow.recharge",
            String.format("%.1f", cooldown / 20.0F)), ChatFormatting.GRAY);

        CompoundTag hex = readIotaTag(stack);
        if (hex == null || hex.isEmpty()) {
            WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.archer_great_bow.uninscribed"),
                ChatFormatting.DARK_GRAY);
        }

        appendExtraTooltip(stack, tooltip);

        IotaHolderItem.appendHoverText(this, stack, tooltip, flag);
    }
}
