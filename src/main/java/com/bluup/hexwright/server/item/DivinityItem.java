package com.bluup.hexwright.server.item;

import at.petrak.hexcasting.api.casting.castables.Action;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.common.lib.HexAttributes;
import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.server.boss.Bosses;
import com.bluup.hexwright.server.soulforge.SoulBond;
import com.bluup.hexwright.server.soulforge.Soulforge;
import com.bluup.hexwright.server.soulforge.SoulforgedItem;
import com.bluup.hexwright.server.weapon.WeaponTooltips;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.fabricmc.fabric.api.item.v1.FabricItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class DivinityItem extends ItemHexwrightStaff implements SoulforgedItem, FabricItem {

    public static final double AMBIT_MULTIPLIER = 5.0;

    public static final double EXPOSURE_RADIUS = 48.0;

    private static final int GLOW_TICKS = 400;

    private static final int GLOW_REFRESH_BELOW_TICKS = 300;

    private static final int OTHER_GLOW_MAX_TICKS = 200;

    private static final AttributeModifier AMBIT = new AttributeModifier(
        UUID.fromString("01cd9e72-a1af-434a-82c4-8390e3f8daaa"),
        "Divinity Ambit", AMBIT_MULTIPLIER - 1.0, AttributeModifier.Operation.MULTIPLY_BASE);

    private static final AttributeModifier GRID_ZOOM = new AttributeModifier(
        UUID.fromString("6b0e4f27-d3a1-4c58-9e72-1f8a5c3d0b64"),
        "Divinity Grid Zoom", 0.5, AttributeModifier.Operation.MULTIPLY_BASE);

    private static final AttributeModifier MEDIA_DISCOUNT = new AttributeModifier(
        UUID.fromString("c4a91d53-7e2f-4b06-8d3c-5a7e0f9b2c18"),
        "Divinity Discount", -0.3, AttributeModifier.Operation.MULTIPLY_TOTAL);

    private static final int GOLD = 0xC9A23A;

    private final Multimap<Attribute, AttributeModifier> spear;

    public DivinityItem(double attackDamageModifier, double attackSpeedModifier, Properties properties) {
        super(properties);
        this.spear = ImmutableMultimap.of(
            Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier",
                attackDamageModifier, AttributeModifier.Operation.ADDITION),
            Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier",
                attackSpeedModifier, AttributeModifier.Operation.ADDITION));
    }

    public static double raycastReach(CastingEnvironment env) {
        if (env.getCastingEntity() instanceof Player player && holdsOwn(player)) {
            return Math.max(Action.RAYCAST_DISTANCE, player.getAttributeValue(HexAttributes.AMBIT_RADIUS));
        }
        return Action.RAYCAST_DISTANCE;
    }

    private static boolean holdsOwn(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.getItem() instanceof DivinityItem && SoulBond.isOwnedBy(held, player)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!Soulforge.mayWield(player, stack)) {
            return InteractionResultHolder.fail(stack);
        }
        return super.use(level, player, hand);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND ? this.spear : super.getDefaultAttributeModifiers(slot);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(ItemStack stack, EquipmentSlot slot) {
        Multimap<Attribute, AttributeModifier> base = FabricItem.super.getAttributeModifiers(stack, slot);
        if ((slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND) || !SoulBond.isBound(stack)) {
            return base;
        }
        Multimap<Attribute, AttributeModifier> out = HashMultimap.create(base);
        out.put(HexAttributes.AMBIT_RADIUS, AMBIT);
        out.put(HexAttributes.GRID_ZOOM, GRID_ZOOM);
        out.put(HexAttributes.MEDIA_CONSUMPTION_MODIFIER, MEDIA_DISCOUNT);
        return out;
    }

    @Override
    public void bondTick(ServerPlayer owner) {
        boolean held = holdsOwn(owner);
        MobEffectInstance glow = owner.getEffect(MobEffects.GLOWING);
        if (held) {
            if (glow == null || glow.getDuration() < GLOW_REFRESH_BELOW_TICKS) {
                owner.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0, false, false, true));
            }
            drawMonsters(owner);
        } else if (glow != null && glow.getDuration() > OTHER_GLOW_MAX_TICKS) {
            owner.removeEffect(MobEffects.GLOWING);
        }
    }

    private static void drawMonsters(ServerPlayer owner) {
        double radiusSqr = EXPOSURE_RADIUS * EXPOSURE_RADIUS;
        List<Mob> drawn = owner.serverLevel().getEntitiesOfClass(Mob.class,
            owner.getBoundingBox().inflate(EXPOSURE_RADIUS),
            mob -> mob instanceof Enemy && !(mob instanceof NeutralMob) && mob.getTarget() == null
                && mob.distanceToSqr(owner) <= radiusSqr && !Bosses.is(mob) && mob.canAttack(owner));
        for (Mob mob : drawn) {
            mob.setTarget(owner);
        }
        if (!drawn.isEmpty()) {
            HexwrightDebug.log(HexwrightDebug.SOULFORGE, "[soulforge] Divinity exposed {} to {} monsters",
                owner.getScoreboardName(), drawn.size());
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        super.inventoryTick(stack, level, holder, slot, selected);
        Soulforge.inventoryTick(stack, level, holder);
        if (holder instanceof ServerPlayer player && !player.isCreative() && !player.isSpectator()
            && holdsOwn(player)) {
            ground(player);
        }
    }

    private static void ground(ServerPlayer player) {
        if (player.isFallFlying()) {
            player.stopFallFlying();
        }
        if (player.getAbilities().flying) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
    }

    @Override
    public void onDestroyed(ItemEntity entity) {
        super.onDestroyed(entity);
        Soulforge.onDestroyed(entity);
    }

    @Override
    public int tithePercent() {
        return 0;
    }

    @Override
    public int materialiseColour() {
        return GOLD;
    }

    @Override
    public Component getName(ItemStack stack) {
        return WeaponTooltips.graded(stack, super.getName(stack), null);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        SoulforgedItem.appendBond(stack, tooltip);

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.hexwright.divinity.reach_heading")
            .withStyle(style -> style.withColor(SoulforgedItem.COLOUR)));
        WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.divinity.reach",
            (int) AMBIT_MULTIPLIER), ChatFormatting.GRAY);

        tooltip.add(Component.empty());
        WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.divinity.exposure",
            (int) EXPOSURE_RADIUS), ChatFormatting.RED);

        tooltip.add(Component.empty());
        WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.divinity.recall",
            Component.keybind("key.hexwright.soulforge")), ChatFormatting.DARK_GRAY);
        WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.divinity.severance"),
            ChatFormatting.DARK_GRAY);
    }
}
