package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.common.animation.PlayerAnimationLayer;
import com.bluup.hexwright.inits.HexwrightNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class SilverStarlightItem extends EternalBladeItem {

    static final int HAILPIERCER_COOLDOWN_TICKS = 240;

    private static final int SLAM_IMPACT_TICKS = 11;

    private static final int SLAM_START_TICKS = Hailpiercer.ERUPT_TICKS - SLAM_IMPACT_TICKS;

    private static final int CHANNEL_TICKS = SLAM_START_TICKS + 28;

    public SilverStarlightItem(int attackDamageModifier, float attackSpeedModifier, Properties properties) {
        super(attackDamageModifier, attackSpeedModifier, HAILPIERCER_COOLDOWN_TICKS, properties);
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

        player.getCooldowns().addCooldown(this, HAILPIERCER_COOLDOWN_TICKS);
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer wielder) {
            Hailpiercer.start(serverLevel, wielder, inscribedHex(stack));
            SlamWindUp.schedule(serverLevel.getServer(), SLAM_START_TICKS, () -> {
                if (!wielder.isRemoved() && wielder.isAlive() && wielder.getMainHandItem().is(this)) {
                    HexwrightNetworking.sendPlayerAnimation(wielder, PlayerAnimationLayer.ONE_SHOT,
                        BattleHammerItem.SLAM_CLIP);
                }
            });
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public boolean isMidSwing(Player player) {
        ItemCooldowns cooldowns = player.getCooldowns();
        if (!cooldowns.isOnCooldown(this)) {
            return false;
        }
        float elapsed = HAILPIERCER_COOLDOWN_TICKS * (1.0F - cooldowns.getCooldownPercent(this, 0.0F));
        return elapsed < CHANNEL_TICKS;
    }

    @Override
    @Nullable
    public WeaponSlash slashFor(String playerClip) {
        return null;
    }

    @Override
    protected boolean land(ServerLevel level, ServerPlayer player, InteractionHand hand) {
        return false;
    }

    @Override
    protected Component castTooltip() {
        return Component.translatable("tooltip.hexwright.silver_starlight.cast");
    }

    @Override
    protected Component swingTooltip() {
        return Component.translatable("tooltip.hexwright.silver_starlight.hailpiercer",
            Component.keybind("key.use"), (int) Hailpiercer.BURST_DAMAGE,
            Hailpiercer.SLOW_TICKS / 20);
    }

    @Override
    @Nullable
    protected Component swingTooltipExtra() {
        return null;
    }
}
