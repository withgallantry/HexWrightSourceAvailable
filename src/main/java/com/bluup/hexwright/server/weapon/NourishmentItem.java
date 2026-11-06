package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.common.animation.PlayerAnimationLayer;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.soulforge.SoulBond;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class NourishmentItem extends SoulforgedBladeItem {

    public static final int LITRE = 1000;
    public static final int CAPACITY_LITRES = 10;
    public static final int CAPACITY = CAPACITY_LITRES * LITRE;

    static final int BLOOD_PER_HEALTH = 25;

    static final int REGROW_TICKS = 200;

    static final int ARMOUR_PERCENT = 15;

    private static final AttributeModifier THIN_ARMOUR = new AttributeModifier(
        UUID.fromString("e31d3143-86a1-456b-bdd9-772b4057cc2a"), "hexwright_nourishment_thin_armour",
        -ARMOUR_PERCENT / 100.0D, AttributeModifier.Operation.MULTIPLY_TOTAL);
    private static final AttributeModifier THIN_TOUGHNESS = new AttributeModifier(
        UUID.fromString("41e47bc8-a5d7-4df0-aaf0-dc34d2bf3b7e"), "hexwright_nourishment_thin_toughness",
        -ARMOUR_PERCENT / 100.0D, AttributeModifier.Operation.MULTIPLY_TOTAL);

    private static final int SWING_COOLDOWN_TICKS = BattleHammerItem.WIND_UP_TICKS + SWING_RECOVERY_TICKS;

    private static final String BLOOD_TAG = "hexwright_blood";

    private static final int BLOOD_COLOUR = 0xA3102C;
    private static final TextColor HELD_COLOUR = TextColor.fromRgb(0xC4304A);
    private static final DustParticleOptions BLOOD_MIST =
        new DustParticleOptions(new Vector3f(0.55F, 0.02F, 0.08F), 1.3F);

    public NourishmentItem(int attackDamageModifier, float attackSpeedModifier, Properties properties) {
        super(attackDamageModifier, attackSpeedModifier, SWING_COOLDOWN_TICKS, properties);
    }

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
            if (!(source.getEntity() instanceof ServerPlayer killer) || victim instanceof ArmorStand) {
                return;
            }
            ItemStack blade = killer.getMainHandItem();
            if (blade.getItem() instanceof NourishmentItem && SoulBond.isOwnedBy(blade, killer)) {
                drink(killer, blade, victim);
            }
        });
    }

    @Override
    protected boolean unleash(ServerLevel level, ServerPlayer wielder, ItemStack stack) {
        if (blood(stack) < LITRE) {
            wielder.displayClientMessage(Component.translatable("message.hexwright.nourishment.thirsty",
                litres(blood(stack))), true);
            return false;
        }
        HexwrightNetworking.sendPlayerAnimation(wielder, PlayerAnimationLayer.ONE_SHOT,
            wielder.isSprinting() ? BattleHammerItem.SPRINT_SLAM_CLIP : BattleHammerItem.SLAM_CLIP);
        boolean forPlanterAlone = wielder.isShiftKeyDown();
        SlamWindUp.schedule(level.getServer(), BattleHammerItem.WIND_UP_TICKS, () -> {
            ItemStack blade = wielder.getMainHandItem();
            BloodTreeEntity tree = blade.is(this)
                ? BloodTreeEntity.plant(level, wielder, blood(blade), forPlanterAlone) : null;
            if (tree == null) {
                wielder.getCooldowns().removeCooldown(this);
                return;
            }
            setBlood(blade, 0);
            wielder.getCooldowns().addCooldown(this, tree.lifeTicks() + REGROW_TICKS);
        });
        return true;
    }

    @Override
    protected int channelTicks() {
        return SWING_COOLDOWN_TICKS;
    }


    public static int blood(ItemStack blade) {
        return blade.hasTag() ? blade.getTag().getInt(BLOOD_TAG) : 0;
    }

    private static void setBlood(ItemStack blade, int millilitres) {
        if (millilitres <= 0) {
            blade.removeTagKey(BLOOD_TAG);
        } else {
            blade.getOrCreateTag().putInt(BLOOD_TAG, millilitres);
        }
    }

    static String litres(int millilitres) {
        return String.format(Locale.ROOT, "%.1f", millilitres / (float) LITRE);
    }

    private static void drink(ServerPlayer killer, ItemStack blade, LivingEntity victim) {
        int before = blood(blade);
        int after = Math.min(CAPACITY, before + Math.round(victim.getMaxHealth() * BLOOD_PER_HEALTH));
        if (after == before) {
            return;
        }
        setBlood(blade, after);

        ServerLevel level = killer.serverLevel();
        Vec3 body = victim.getBoundingBox().getCenter();
        level.sendParticles(BLOOD_MIST, body.x, body.y, body.z, 16,
            victim.getBbWidth() * 0.35D, victim.getBbHeight() * 0.3D, victim.getBbWidth() * 0.35D, 0.0D);
        level.playSound(null, killer.getX(), killer.getY(), killer.getZ(), NourishmentSounds.DRINK.get(),
            SoundSource.PLAYERS, 0.8F, 0.9F + level.getRandom().nextFloat() * 0.2F);
        HexwrightDebug.log(HexwrightDebug.WEAPON, "[weapon] nourishment of {} drank {} ml from {}: {} ml",
            killer.getScoreboardName(), after - before, victim, after);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return blood(stack) > 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * blood(stack) / CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BLOOD_COLOUR;
    }


    @Override
    public int materialiseColour() {
        return 0x5A0A22;
    }

    @Override
    public int tithePercent() {
        return 0;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> bondCosts() {
        return ImmutableMultimap.of(Attributes.ARMOR, THIN_ARMOUR, Attributes.ARMOR_TOUGHNESS, THIN_TOUGHNESS);
    }

    @Override
    public List<Component> costLines() {
        return List.of(Component.translatable("tooltip.hexwright.nourishment.thin_armour", ARMOUR_PERCENT));
    }

    @Override
    public Component normalStrike() {
        return Component.translatable("tooltip.hexwright.nourishment.blow", CAPACITY_LITRES);
    }

    @Override
    public Component heavyStrike() {
        return Component.translatable("tooltip.hexwright.nourishment.blood_tree",
            BloodTreeEntity.SHORTEST_TICKS / 20, BloodTreeEntity.LONGEST_TICKS / 20, CAPACITY_LITRES,
            BloodTreeEntity.LITRES_PER_SACK);
    }

    @Override
    public void appendHeld(ItemStack stack, List<Component> tooltip) {
        WeaponTooltips.add(tooltip, Component.translatable("tooltip.hexwright.nourishment.holding",
            litres(blood(stack)), CAPACITY_LITRES), HELD_COLOUR);
    }
}
