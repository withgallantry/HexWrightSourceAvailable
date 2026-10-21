package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.common.animation.PlayerAnimationLayer;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.effect.HexwrightEffects;
import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.soulforge.Soulforge;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

public class PestiferousItem extends SoulforgedBladeItem {

    static final int SLASH_COOLDOWN_TICKS = 600;

    public static final int AFFLICTED_PERCENT = 25;

    private static final int MAX_AMPLIFIER = 1;
    private static final int MAX_AMPLIFIER_SHOWN = MAX_AMPLIFIER + 1;

    public static final TagKey<MobEffect> UNCAPTURABLE = TagKey.create(Registries.MOB_EFFECT,
        Hexwright.id("uncapturable"));

    private static final String CARRIED_TAG = "hexwright_pestiferous";
    private static final String EFFECT_KEY = "Effect";
    private static final String AMPLIFIER_KEY = "Amplifier";

    private static final TextColor CARRIED_COLOUR = TextColor.fromRgb(0xA8D93A);

    public PestiferousItem(int attackDamageModifier, float attackSpeedModifier, Properties properties) {
        super(attackDamageModifier, attackSpeedModifier, SLASH_COOLDOWN_TICKS, properties);
    }

    @Override
    protected boolean unleash(ServerLevel level, ServerPlayer wielder, ItemStack stack) {
        HexwrightNetworking.sendPlayerAnimation(wielder, PlayerAnimationLayer.ONE_SHOT, swingClip(wielder));
        SlamWindUp.schedule(level.getServer(), BattleAxeItem.WIND_UP_TICKS, () -> {
            ItemStack blade = wielder.getMainHandItem();
            boolean landed = blade.is(this) && PestilentSlash.release(level, wielder, blade);
            if (!landed) {
                wielder.getCooldowns().removeCooldown(this);
            }
        });
        return true;
    }

    @Override
    protected int channelTicks() {
        return BattleAxeItem.WIND_UP_TICKS + BerserkWeaponItem.SWING_RECOVERY_TICKS;
    }


    public static boolean capturable(MobEffect effect) {
        return effect.getCategory() == MobEffectCategory.HARMFUL && !effect.isInstantenous()
            && !BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect).is(UNCAPTURABLE);
    }

    @Nullable
    public static MobEffectInstance strongestCapturable(LivingEntity source) {
        return source.getActiveEffects().stream()
            .filter(instance -> capturable(instance.getEffect()))
            .max(Comparator.comparingInt(MobEffectInstance::getAmplifier)
                .thenComparingInt(instance -> instance.isInfiniteDuration() ? Integer.MAX_VALUE : instance.getDuration()))
            .orElse(null);
    }

    public static void drawOut(ItemStack blade, LivingEntity source, MobEffect effect, int amplifier) {
        ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
        if (id == null) {
            return;
        }
        source.removeEffect(effect);
        CompoundTag carried = new CompoundTag();
        carried.putString(EFFECT_KEY, id.toString());
        carried.putInt(AMPLIFIER_KEY, Math.min(amplifier, MAX_AMPLIFIER));
        blade.getOrCreateTag().put(CARRIED_TAG, carried);
        HexwrightDebug.log(HexwrightDebug.WEAPON, "[weapon] pestiferous drew {} out of {}", id, source);
    }

    @Nullable
    static MobEffectInstance carried(ItemStack blade, int ticks) {
        CompoundTag tag = blade.getTag();
        if (tag == null || !tag.contains(CARRIED_TAG)) {
            return null;
        }
        CompoundTag carried = tag.getCompound(CARRIED_TAG);
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(ResourceLocation.tryParse(carried.getString(EFFECT_KEY)));
        if (effect == null || !capturable(effect)) {
            return null;
        }
        return new MobEffectInstance(effect, ticks, Math.min(carried.getInt(AMPLIFIER_KEY), MAX_AMPLIFIER));
    }


    public static MobEffectInstance afflictionFor(LivingEntity entity, MobEffectInstance instance) {
        MobEffect effect = instance.getEffect();
        if (!(entity instanceof ServerPlayer owner) || effect.getCategory() != MobEffectCategory.HARMFUL
            || effect.isInstantenous() || instance.isInfiniteDuration()
            || effect instanceof HexwrightEffects.StatusEffect
            || !Soulforge.owns(owner, HexwrightItems.PESTIFEROUS)) {
            return instance;
        }
        return new MobEffectInstance(effect, lengthened(instance.getDuration()), instance.getAmplifier(),
            instance.isAmbient(), instance.isVisible(), instance.showIcon());
    }

    public static int afflictedTicks(ServerPlayer player, int ticks) {
        return Soulforge.owns(player, HexwrightItems.PESTIFEROUS) ? lengthened(ticks) : ticks;
    }

    private static int lengthened(int ticks) {
        return ticks + ticks * AFFLICTED_PERCENT / 100;
    }


    @Override
    public int materialiseColour() {
        return 0x2C3A0A;
    }

    @Override
    public List<Component> costLines() {
        return List.of(Component.translatable("tooltip.hexwright.pestiferous.afflicted", AFFLICTED_PERCENT));
    }

    @Override
    public Component normalStrike() {
        return Component.translatable("tooltip.hexwright.pestiferous.blow");
    }

    @Override
    public Component heavyStrike() {
        return Component.translatable("tooltip.hexwright.pestiferous.slash", PestilentSlash.AFFLICTION_TICKS / 20);
    }

    @Override
    public void appendHeld(ItemStack stack, List<Component> tooltip) {
        MobEffectInstance carried = carried(stack, PestilentSlash.AFFLICTION_TICKS);
        Component line = carried == null
            ? Component.translatable("tooltip.hexwright.pestiferous.carrying_nothing")
            : Component.translatable("tooltip.hexwright.pestiferous.carrying", name(carried));
        WeaponTooltips.add(tooltip, line, CARRIED_COLOUR);
    }

    private static Component name(MobEffectInstance instance) {
        Component name = Component.translatable(instance.getDescriptionId());
        return instance.getAmplifier() <= 0 ? name
            : Component.translatable("potion.withAmplifier", name,
                Component.translatable("potion.potency." + instance.getAmplifier()));
    }
}
