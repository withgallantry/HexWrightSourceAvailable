package com.bluup.hexwright.server.item;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.common.lib.HexAttributes;
import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.server.boss.Bosses;
import com.bluup.hexwright.server.soulforge.SoulBond;
import com.bluup.hexwright.server.soulforge.Soulforge;
import com.bluup.hexwright.server.soulforge.SoulforgedItem;
import com.bluup.hexwright.server.weapon.SpearStaff;
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
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DivinityItem extends ItemHexwrightStaff implements SoulforgedItem, FabricItem, SpearStaff {

    public static final double AMBIT_MULTIPLIER = 8.0;

    public static final double EXPOSURE_RADIUS = 48.0;

    public static final double AIRBORNE_EXPOSURE_RADIUS = 96.0;

    public static final int GROUNDED_TICKS = 200;

    private static final Map<UUID, Integer> GROUNDED_UNTIL = new HashMap<>();

    private static final int GLOW_TICKS = 400;

    private static final int GLOW_REFRESH_BELOW_TICKS = 300;

    private static final int OTHER_GLOW_MAX_TICKS = 200;

    private static final AttributeModifier AMBIT = new AttributeModifier(
        UUID.fromString("981cac01-2a7e-4a79-b2aa-37b5f84db038"),
        "Divinity Ambit", AMBIT_MULTIPLIER - 1.0, AttributeModifier.Operation.MULTIPLY_BASE);

    private static final AttributeModifier GRID_ZOOM = new AttributeModifier(
        UUID.fromString("6b0e4f27-d3a1-4c58-9e72-1f8a5c3d0b64"),
        "Divinity Grid Zoom", 0.5, AttributeModifier.Operation.MULTIPLY_BASE);

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
            return player.getAttributeValue(HexAttributes.AMBIT_RADIUS);
        }
        return 0.0;
    }

    public static boolean holdsOwn(Player player) {
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
            drawMonsters(owner, airborne(owner) ? AIRBORNE_EXPOSURE_RADIUS : EXPOSURE_RADIUS);
        } else if (glow != null && glow.getDuration() > OTHER_GLOW_MAX_TICKS) {
            owner.removeEffect(MobEffects.GLOWING);
        }
        if (!grounded(owner)) {
            GROUNDED_UNTIL.remove(owner.getUUID());
        }
    }

    private static void drawMonsters(ServerPlayer owner, double radius) {
        double radiusSqr = radius * radius;
        List<Mob> drawn = owner.serverLevel().getEntitiesOfClass(Mob.class,
            owner.getBoundingBox().inflate(radius),
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

    public static void onStruck(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator() || !airborne(player) || !holdsOwn(player)) {
            return;
        }
        GROUNDED_UNTIL.put(player.getUUID(), player.server.getTickCount() + GROUNDED_TICKS);
        ground(player);
        player.displayClientMessage(Component.translatable("message.hexwright.divinity.grounded",
            GROUNDED_TICKS / 20).withStyle(ChatFormatting.RED), true);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        super.inventoryTick(stack, level, holder, slot, selected);
        Soulforge.inventoryTick(stack, level, holder);
        if (holder instanceof ServerPlayer player && grounded(player) && SoulBond.isOwnedBy(stack, player)) {
            ground(player);
        }
    }

    private static boolean grounded(ServerPlayer player) {
        Integer until = GROUNDED_UNTIL.get(player.getUUID());
        return until != null && player.server.getTickCount() < until;
    }

    private static boolean airborne(Player player) {
        return player.getAbilities().flying || player.isFallFlying();
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
        WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.soulforged.tithe",
            tithePercent()), ChatFormatting.RED);
        WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.divinity.exposure",
            (int) EXPOSURE_RADIUS, (int) AIRBORNE_EXPOSURE_RADIUS), ChatFormatting.RED);
        WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.divinity.struck",
            GROUNDED_TICKS / 20), ChatFormatting.RED);
        WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.divinity.strain",
            DivineStrain.MAX_STRAIN_TICKS / 1200), ChatFormatting.RED);

        tooltip.add(Component.empty());
        WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.divinity.recall",
            Component.keybind("key.hexwright.soulforge")), ChatFormatting.DARK_GRAY);
        WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.divinity.severance"),
            ChatFormatting.DARK_GRAY);
    }
}
