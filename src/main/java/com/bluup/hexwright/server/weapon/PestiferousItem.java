package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.common.animation.PlayerAnimationLayer;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.boss.Bosses;
import com.bluup.hexwright.server.effect.HexwrightEffects;
import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.soulforge.SoulBond;
import com.bluup.hexwright.server.soulforge.Soulforge;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PestiferousItem extends SoulforgedBladeItem {

    static final int SLASH_COOLDOWN_TICKS = 320;

    public static final int MAX_CARRIED = 3;

    public static final int BLOW_AFFLICTION_TICKS = 20;

    public static final int AFFLICTED_PERCENT = 25;

    private static final int MAX_AMPLIFIER = 1;
    private static final int MAX_AMPLIFIER_SHOWN = MAX_AMPLIFIER + 1;

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

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean hurt = super.hurtEnemy(stack, target, attacker);
        if (attacker instanceof ServerPlayer wielder
            && SoulBond.isOwnedBy(stack, wielder) && target.isAlive()) {
            afflict(stack, target, wielder, BLOW_AFFLICTION_TICKS);
        }
        return hurt;
    }


    public static boolean capturable(MobEffect effect) {
        return !effect.isInstantenous();
    }

    @Nullable
    public static MobEffectInstance strongestCapturable(LivingEntity source) {
        return source.getActiveEffects().stream()
            .filter(instance -> capturable(instance.getEffect()))
            .max(Comparator.comparingInt(MobEffectInstance::getAmplifier)
                .thenComparingInt(instance -> instance.isInfiniteDuration() ? -1 : instance.getDuration()))
            .orElse(null);
    }

    public static boolean hasRoomFor(ItemStack blade, MobEffect effect) {
        List<MobEffectInstance> carried = carried(blade, 0);
        return carried.size() < MAX_CARRIED || indexOf(carried, effect) >= 0;
    }

    public static void drawOut(ItemStack blade, LivingEntity source, MobEffect effect, int amplifier) {
        source.removeEffect(effect);
        if (effect == HexwrightEffects.SILENCED && source instanceof ServerPlayer player) {
            Silence.lift(player);
        }
        List<MobEffectInstance> carried = carried(blade, 0);
        MobEffectInstance drawn = new MobEffectInstance(effect, 0, Math.min(amplifier, MAX_AMPLIFIER));
        int existing = indexOf(carried, effect);
        if (existing >= 0) {
            carried.set(existing, drawn);
        } else {
            carried.add(drawn);
        }
        write(blade, carried);
        HexwrightDebug.log(HexwrightDebug.WEAPON, "[weapon] pestiferous drew {} out of {}",
            BuiltInRegistries.MOB_EFFECT.getKey(effect), source);
    }

    public static void purge(ItemStack blade, int index) {
        List<MobEffectInstance> carried = carried(blade, 0);
        MobEffectInstance purged = carried.remove(index);
        write(blade, carried);
        HexwrightDebug.log(HexwrightDebug.WEAPON, "[weapon] pestiferous let {} go",
            BuiltInRegistries.MOB_EFFECT.getKey(purged.getEffect()));
    }

    public static List<MobEffectInstance> carried(ItemStack blade, int ticks) {
        List<MobEffectInstance> carried = new ArrayList<>();
        CompoundTag tag = blade.getTag();
        if (tag == null) {
            return carried;
        }
        for (Tag entry : tag.getList(CARRIED_TAG, Tag.TAG_COMPOUND)) {
            CompoundTag status = (CompoundTag) entry;
            MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(ResourceLocation.tryParse(status.getString(EFFECT_KEY)));
            if (effect != null && capturable(effect)) {
                carried.add(new MobEffectInstance(effect, ticks, Math.min(status.getInt(AMPLIFIER_KEY), MAX_AMPLIFIER)));
            }
        }
        return carried;
    }

    static void afflict(ItemStack blade, LivingEntity victim, ServerPlayer source, int ticks) {
        if (Bosses.is(victim)) {
            return;
        }
        for (MobEffectInstance status : carried(blade, ticks)) {
            if (status.getEffect() == HexwrightEffects.SILENCED) {
                Silence.strike(victim, source, ticks);
            } else {
                victim.addEffect(status, source);
            }
        }
    }

    private static int indexOf(List<MobEffectInstance> carried, MobEffect effect) {
        for (int i = 0; i < carried.size(); i++) {
            if (carried.get(i).getEffect() == effect) {
                return i;
            }
        }
        return -1;
    }

    private static void write(ItemStack blade, List<MobEffectInstance> carried) {
        if (carried.isEmpty()) {
            blade.removeTagKey(CARRIED_TAG);
            return;
        }
        ListTag list = new ListTag();
        for (MobEffectInstance status : carried) {
            CompoundTag entry = new CompoundTag();
            entry.putString(EFFECT_KEY, BuiltInRegistries.MOB_EFFECT.getKey(status.getEffect()).toString());
            entry.putInt(AMPLIFIER_KEY, status.getAmplifier());
            list.add(entry);
        }
        blade.getOrCreateTag().put(CARRIED_TAG, list);
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
        return Component.translatable("tooltip.hexwright.pestiferous.blow", BLOW_AFFLICTION_TICKS / 20);
    }

    @Override
    public Component heavyStrike() {
        return Component.translatable("tooltip.hexwright.pestiferous.slash", PestilentSlash.AFFLICTION_TICKS / 20,
            MAX_CARRIED);
    }

    @Override
    public void appendHeld(ItemStack stack, List<Component> tooltip) {
        List<MobEffectInstance> carried = carried(stack, PestilentSlash.AFFLICTION_TICKS);
        Component line = carried.isEmpty()
            ? Component.translatable("tooltip.hexwright.pestiferous.carrying_nothing")
            : Component.translatable("tooltip.hexwright.pestiferous.carrying",
                ComponentUtils.formatList(carried, PestiferousItem::name));
        WeaponTooltips.add(tooltip, line, CARRIED_COLOUR);
    }

    private static Component name(MobEffectInstance instance) {
        Component name = Component.translatable(instance.getDescriptionId());
        return instance.getAmplifier() <= 0 ? name
            : Component.translatable("potion.withAmplifier", name,
                Component.translatable("potion.potency." + instance.getAmplifier()));
    }
}
