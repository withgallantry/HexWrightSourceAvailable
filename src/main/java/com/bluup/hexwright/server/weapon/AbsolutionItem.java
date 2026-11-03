package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.common.animation.PlayerAnimationLayer;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.soulforge.SoulBond;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class AbsolutionItem extends SoulforgedBladeItem {

    static final int STRIKE_COOLDOWN_TICKS = 700;

    static final int LANDED_COOLDOWN_TICKS = 1000;

    public static final float SIPHON = 1.0F;

    private static final int SLAM_CLIP_START_TICKS = Stillness.LAND_TICKS - 11;

    public AbsolutionItem(int attackDamageModifier, float attackSpeedModifier, Properties properties) {
        super(attackDamageModifier, attackSpeedModifier, STRIKE_COOLDOWN_TICKS, properties);
    }

    @Override
    protected boolean unleash(ServerLevel level, ServerPlayer wielder, ItemStack stack) {
        Stillness.start(level, wielder, this);
        SlamWindUp.schedule(level.getServer(), SLAM_CLIP_START_TICKS, () -> {
            if (!wielder.isRemoved() && wielder.isAlive() && wielder.getMainHandItem().is(this)) {
                HexwrightNetworking.sendPlayerAnimation(wielder, PlayerAnimationLayer.ONE_SHOT,
                    BattleHammerItem.SLAM_CLIP);
            }
        });
        return true;
    }

    @Override
    protected int channelTicks() {
        return Stillness.LAND_TICKS;
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean hurt = super.hurtEnemy(stack, target, attacker);
        if (!attacker.level().isClientSide() && attacker instanceof Player wielder && SoulBond.isOwnedBy(stack, wielder)) {
            attacker.heal(SIPHON);
        }
        return hurt;
    }

    @Override
    public int materialiseColour() {
        return 0x4C0B44;
    }

    @Override
    public List<Component> costLines() {
        return List.of(
            Component.translatable("tooltip.hexwright.absolution.spent"),
            Component.translatable("tooltip.hexwright.absolution.landed", LANDED_COOLDOWN_TICKS / 20));
    }

    @Override
    public Component normalStrike() {
        return Component.translatable("tooltip.hexwright.absolution.blow");
    }

    @Override
    public Component heavyStrike() {
        return Component.translatable("tooltip.hexwright.absolution.stillness", Stillness.ZONE_TICKS / 20,
            Silence.SILENCE_TICKS / 20);
    }
}
