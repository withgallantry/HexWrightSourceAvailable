package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BattleHammerItem extends BerserkWeaponItem {

    public static final String SLAM_CLIP = "hammer_overhead_slam";

    public static final String SPRINT_SLAM_CLIP = "hammer_overhead_slam_running";

    public static final String IDLE_CLIP = "hammer_one_handed_idle";

    public static final String STRIKE_CLIP = "hammer_one_handed_strike";

    public static final String SPRINT_STRIKE_CLIP = "one_handed_slam_running";

    private static final int WIND_UP_TICKS = 11;

    private static final int STRIKE_IMPACT_TICKS = 10;

    private static final double SLAM_AIM_REACH = 4.5D;

    private final float slamRadius;

    @Nullable
    private final PocketCasterData.Quality quality;

    public BattleHammerItem(PocketCasterData.Quality quality, Properties properties) {
        this(BattleHammerGrade.tier(quality),
            BattleHammerGrade.attackDamageModifier(quality),
            BattleHammerGrade.attackSpeedModifier(quality),
            BattleHammerGrade.slamRadius(quality),
            BattleHammerGrade.slamCooldownTicks(quality),
            quality, properties);
    }

    protected BattleHammerItem(Tier tier, int attackDamageModifier, float attackSpeedModifier,
                               float slamRadius, int slamCooldownTicks, Properties properties) {
        this(tier, attackDamageModifier, attackSpeedModifier, slamRadius, slamCooldownTicks,
            null, properties);
    }

    private BattleHammerItem(Tier tier, int attackDamageModifier, float attackSpeedModifier,
                             float slamRadius, int slamCooldownTicks,
                             @Nullable PocketCasterData.Quality quality, Properties properties) {
        super(tier, attackDamageModifier, attackSpeedModifier, SLAM_CLIP, IDLE_CLIP,
            WIND_UP_TICKS, slamCooldownTicks, properties);
        this.slamRadius = slamRadius;
        this.quality = quality;
    }

    @Override
    @Nullable
    public PocketCasterData.Quality quality() {
        return this.quality;
    }

    @Override
    public Component getName(ItemStack stack) {
        Component name = this.quality == null
            ? super.getName(stack)
            : Component.translatable("item.hexwright.battle_hammer");
        return WeaponTooltips.graded(stack, name, this.quality);
    }

    @Override
    protected String swingClip(Player player) {
        return player.isSprinting() ? SPRINT_SLAM_CLIP : SLAM_CLIP;
    }

    @Override
    public String strikeClip(Player player, int comboStep) {
        return player.isSprinting() ? SPRINT_STRIKE_CLIP : STRIKE_CLIP;
    }

    @Override
    public SoundEvent strikeSound() {
        return SoundEvents.ANVIL_LAND;
    }

    @Override
    public float strikeSoundVolume() {
        return 0.35F;
    }

    @Override
    public float strikeSoundPitch() {
        return 1.2F;
    }

    @Override
    public int strikeSoundDelayTicks() {
        return STRIKE_IMPACT_TICKS;
    }

    @Override
    public int strikeHitDelayTicks() {
        return STRIKE_IMPACT_TICKS;
    }

    @Override
    protected boolean canSwing(Level level, Player player) {
        return findImpact(level, player) != null;
    }

    @Override
    protected boolean land(ServerLevel level, ServerPlayer player, InteractionHand hand) {
        BlockPos impact = findImpact(level, player);
        if (impact == null) {
            return false;
        }
        List<LivingEntity> caught = GroundSlam.slam(level, player, impact, this.slamRadius);
        castOnCaught(player, player.getItemInHand(hand), caught, GroundSlam.centreOf(impact));
        return true;
    }

    @Override
    protected boolean castsOnCaught() {
        return true;
    }

    @Override
    protected Component castTooltip() {
        return Component.translatable("tooltip.hexwright.battle_hammer.cast");
    }

    @Override
    protected Component swingTooltip() {
        return Component.translatable("tooltip.hexwright.battle_hammer.slam",
            Component.keybind("key.use"), blocks(this.slamRadius));
    }

    @Nullable
    private static BlockPos findImpact(Level level, Player player) {
        HitResult aimed = player.pick(SLAM_AIM_REACH, 1.0F, false);
        if (aimed.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = ((BlockHitResult) aimed).getBlockPos();
            if (isStrikable(level, pos)) {
                return pos;
            }
        }

        BlockPos underfoot = BlockPos.containing(player.getX(), player.getY() - 0.15D, player.getZ());
        return isStrikable(level, underfoot) ? underfoot : null;
    }

    private static boolean isStrikable(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir()
            && state.getFluidState().isEmpty()
            && !state.getCollisionShape(level, pos).isEmpty();
    }
}
