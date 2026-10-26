package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.common.animation.PlayerAnimationLayer;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.effect.HexwrightEffects;
import com.bluup.hexwright.server.effect.MediaBurnEffect;
import com.bluup.hexwright.server.soulforge.SoulBond;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class DragonbornBroadswordItem extends SoulforgedBladeItem {

    static final int METEOR_COOLDOWN_TICKS = 900;

    public static final int IGNITE_SECONDS = 5;

    private static final float HUNGER_PER_SECOND = 0.2F;

    private static final int SLAM_IMPACT_TICKS = 11;

    public DragonbornBroadswordItem(int attackDamageModifier, float attackSpeedModifier, Properties properties) {
        super(attackDamageModifier, attackSpeedModifier, METEOR_COOLDOWN_TICKS, properties);
    }

    @Override
    protected boolean unleash(ServerLevel level, ServerPlayer wielder, ItemStack stack) {
        if (!MeteorOfDoom.start(level, wielder, this)) {
            return false;
        }
        SlamWindUp.schedule(level.getServer(), MeteorOfDoom.CHARGE_TICKS - SLAM_IMPACT_TICKS, () -> {
            if (!wielder.isRemoved() && wielder.isAlive() && wielder.getMainHandItem().is(this)) {
                HexwrightNetworking.sendPlayerAnimation(wielder, PlayerAnimationLayer.ONE_SHOT, BattleHammerItem.SLAM_CLIP);
            }
        });
        return true;
    }

    @Override
    protected int channelTicks() {
        return MeteorOfDoom.CHARGE_TICKS + 17;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean hurt = super.hurtEnemy(stack, target, attacker);
        if (!attacker.level().isClientSide() && attacker instanceof Player wielder
            && SoulBond.isOwnedBy(stack, wielder) && target.isAlive()) {
            target.setSecondsOnFire(IGNITE_SECONDS);
        }
        return hurt;
    }

    static void kindle(LivingEntity victim, int seconds) {
        victim.setSecondsOnFire(seconds);
        if (victim instanceof Player) {
            victim.addEffect(new MobEffectInstance(HexwrightEffects.MEDIA_BURN, seconds * 20, 0, false, false, true));
        }
    }

    @Override
    public int tithePercent() {
        return 0;
    }

    @Override
    public void bondTick(ServerPlayer owner) {
        owner.causeFoodExhaustion(HUNGER_PER_SECOND);
    }

    @Override
    public int materialiseColour() {
        return 0x651D05;
    }

    @Override
    public List<Component> costLines() {
        return List.of(
            Component.translatable("tooltip.hexwright.dragonborn_broadsword.hunger"),
            Component.translatable("tooltip.hexwright.dragonborn_broadsword.blood",
                Math.round(MeteorOfDoom.BLOOD_PRICE * 100.0F)),
            Component.translatable("tooltip.hexwright.dragonborn_broadsword.backlash"));
    }

    @Override
    public Component normalStrike() {
        return Component.translatable("tooltip.hexwright.dragonborn_broadsword.blow");
    }

    @Override
    public Component heavyStrike() {
        return Component.translatable("tooltip.hexwright.dragonborn_broadsword.meteor");
    }
}
