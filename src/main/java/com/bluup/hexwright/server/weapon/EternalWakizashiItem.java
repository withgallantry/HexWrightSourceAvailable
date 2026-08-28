package com.bluup.hexwright.server.weapon;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.item.IotaHolderItem;
import at.petrak.hexcasting.api.utils.NBTHelper;
import com.bluup.hexwright.server.armour.VenatorPowers;
import com.bluup.hexwright.server.item.ArtifactItem;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class EternalWakizashiItem extends SwordItem
    implements AnimatedWeapon, IotaHolderItem, ArtifactItem {

    private static final String TAG_HEX_DATA = "data";

    private static final String ROOT_TAG = "hexwright_wakizashi";
    private static final String TAG_NEXT_HEX_TICK = "NextHexTick";

    public EternalWakizashiItem(int attackDamageModifier, float attackSpeedModifier,
                                Properties properties) {
        super(EternalTier.ETERNAL, attackDamageModifier, attackSpeedModifier, properties);
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


    private static int hexCooldownTicks(LivingEntity wielder) {
        if (!(wielder instanceof Player player)) {
            return 0;
        }
        return Math.max(1, Mth.ceil(player.getCurrentItemAttackStrengthDelay()));
    }

    private static long nextHexTick(ItemStack stack) {
        CompoundTag root = stack.getTagElement(ROOT_TAG);
        return root == null ? 0L : root.getLong(TAG_NEXT_HEX_TICK);
    }

    private static boolean armed(ItemStack stack, Level level, int cooldownTicks) {
        long remaining = nextHexTick(stack) - level.getGameTime();
        return Math.min(remaining, cooldownTicks) <= 0L;
    }


    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean hurt = super.hurtEnemy(stack, target, attacker);
        if (!(attacker instanceof ServerPlayer wielder)) {
            return hurt;
        }

        int cooldown = hexCooldownTicks(wielder);
        if (!armed(stack, wielder.level(), cooldown)) {
            return hurt;
        }
        CompoundTag hex = readIotaTag(stack);
        if (hex == null || hex.isEmpty()) {
            return hurt;
        }

        int spent = VenatorPowers.hexCooldownFor(wielder, cooldown);
        stack.getOrCreateTagElement(ROOT_TAG)
            .putLong(TAG_NEXT_HEX_TICK, wielder.level().getGameTime() + spent);

        WeaponHexCasting.castOnTarget(wielder, hex, target, target.position());
        return hurt;
    }


    @Override
    public int comboLength() {
        return ShortSwordItem.COMBO_CLIPS.length;
    }

    @Override
    public int comboResetTicks() {
        return ShortSwordItem.COMBO_RESET_TICKS;
    }

    @Override
    @Nullable
    public String strikeClip(Player player, int comboStep) {
        String[] clips = player.isSprinting()
            ? ShortSwordItem.SPRINT_COMBO_CLIPS : ShortSwordItem.COMBO_CLIPS;
        return clips[Math.floorMod(comboStep, clips.length)];
    }

    @Override
    public float clipSpeed(Player player, String clip) {
        if (!ShortSwordItem.ALL_COMBO_CLIPS.contains(clip)) {
            return 1.0F;
        }
        float interval = Math.max(1.0F, player.getCurrentItemAttackStrengthDelay());
        return Math.max(1.0F, ShortSwordItem.LONGEST_COMBO_CLIP_TICKS / interval);
    }

    @Override
    @Nullable
    public SoundEvent strikeSound() {
        return SoundEvents.PLAYER_ATTACK_SWEEP;
    }

    @Override
    public float strikeSoundVolume() {
        return 0.5F;
    }

    @Override
    public float strikeSoundPitch() {
        return 1.35F;
    }

    @Override
    public Component getName(ItemStack stack) {
        return ArtifactItem.name(super.getName(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.hexwright.eternal_wakizashi.cast")
            .withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("tooltip.hexwright.eternal_wakizashi.recharge")
            .withStyle(ChatFormatting.GRAY));

        CompoundTag hex = readIotaTag(stack);
        if (hex == null || hex.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.hexwright.eternal_wakizashi.uninscribed")
                .withStyle(ChatFormatting.DARK_GRAY));
        }

        IotaHolderItem.appendHoverText(this, stack, tooltip, flag);
    }
}
