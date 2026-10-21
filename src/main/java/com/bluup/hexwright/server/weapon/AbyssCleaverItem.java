package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.common.animation.PlayerAnimationLayer;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.boss.Bosses;
import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.soulforge.SoulBond;
import com.bluup.hexwright.server.soulforge.Soulforge;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class AbyssCleaverItem extends SoulforgedBladeItem {

    static final int STRIKE_COOLDOWN_TICKS = 320;

    static final int EXECUTE_PERCENT = 25;

    static final float ABSORPTION_CAP = 8.0F;

    static final int TITHE_PERCENT = 10;

    public static final float MORTAL_HEALTH = 20.0F;

    private static final int STRIKE_CLIP_IMPACT_TICKS = 11;

    private static final int STRIKE_CLIP_START_TICKS = ViciousStrike.CHARGE_TICKS - STRIKE_CLIP_IMPACT_TICKS;

    private static final int CHANNEL_TICKS = STRIKE_CLIP_START_TICKS + 28;

    public AbyssCleaverItem(int attackDamageModifier, float attackSpeedModifier, Properties properties) {
        super(attackDamageModifier, attackSpeedModifier, STRIKE_COOLDOWN_TICKS, properties);
    }

    @Override
    protected boolean unleash(ServerLevel level, ServerPlayer wielder, ItemStack stack) {
        ViciousStrike.charge(level, wielder);
        SlamWindUp.schedule(level.getServer(), STRIKE_CLIP_START_TICKS, () -> {
            if (!wielder.isRemoved() && wielder.isAlive() && wielder.getMainHandItem().is(this)) {
                HexwrightNetworking.sendPlayerAnimation(wielder, PlayerAnimationLayer.ONE_SHOT,
                    BattleHammerItem.SLAM_CLIP);
            }
        });
        SlamWindUp.schedule(level.getServer(), ViciousStrike.CHARGE_TICKS, () -> {
            if (wielder.getMainHandItem().is(this)) {
                ViciousStrike.release(level, wielder);
            }
        });
        return true;
    }

    @Override
    protected int channelTicks() {
        return CHANNEL_TICKS;
    }

    public static float blowDamage(Player attacker, Entity target, float damage) {
        ItemStack blade = attacker.getMainHandItem();
        if (!(target instanceof Player victim) || !(blade.getItem() instanceof AbyssCleaverItem)
            || !SoulBond.isOwnedBy(blade, attacker)) {
            return damage;
        }
        float pool = victim.getMaxHealth() + victim.getAbsorptionAmount();
        return damage * Math.max(1.0F, pool / MORTAL_HEALTH);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean hurt = super.hurtEnemy(stack, target, attacker);
        if (attacker.level() instanceof ServerLevel level && attacker instanceof Player wielder
            && SoulBond.isOwnedBy(stack, wielder) && !(target instanceof Player) && !Bosses.is(target)
            && target.isAlive() && target.getHealth() < target.getMaxHealth() * EXECUTE_PERCENT / 100.0F) {
            execute(level, wielder, target);
        }
        return hurt;
    }

    private static void execute(ServerLevel level, Player wielder, LivingEntity victim) {
        DamageSource source = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
            .getHolderOrThrow(DamageTypes.GENERIC_KILL), wielder);
        victim.hurt(source, Float.MAX_VALUE);
        Vec3 hit = victim.getBoundingBox().getCenter();
        HexwrightNetworking.sendSkyfallVfx(level, PiercingSkyfall.VFX_HIT, hit, 0.0F, 0);
        level.playSound(null, hit.x, hit.y, hit.z, StarfallSounds.HIT_THUD.get(), SoundSource.PLAYERS, 0.6F, 0.8F);
        HexwrightDebug.log(HexwrightDebug.WEAPON, "[weapon] abyss cleaver executed {}", victim);
    }

    public static float absorptionFor(Player player, float amount) {
        return player instanceof ServerPlayer owner && Soulforge.owns(owner, HexwrightItems.ABYSS_CLEAVER)
            ? Math.min(amount, ABSORPTION_CAP) : amount;
    }

    @Override
    public int tithePercent() {
        return TITHE_PERCENT;
    }

    @Override
    public void bondTick(ServerPlayer owner) {
        if (owner.getAbsorptionAmount() > ABSORPTION_CAP) {
            owner.setAbsorptionAmount(ABSORPTION_CAP);
        }
    }

    @Override
    public int materialiseColour() {
        return 0x48040F;
    }

    @Override
    public List<Component> costLines() {
        return List.of(Component.translatable("tooltip.hexwright.abyss_cleaver.unshielded",
            (int) (ABSORPTION_CAP / 2.0F)));
    }

    @Override
    public Component normalStrike() {
        return Component.translatable("tooltip.hexwright.abyss_cleaver.blow", EXECUTE_PERCENT);
    }

    @Override
    public Component heavyStrike() {
        return Component.translatable("tooltip.hexwright.abyss_cleaver.vicious_strike", (int) ViciousStrike.REACH);
    }
}
