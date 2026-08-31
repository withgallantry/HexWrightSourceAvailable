package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import org.jetbrains.annotations.Nullable;

public class BattleAxeItem extends BerserkWeaponItem {

    public static final String SLASH_CLIP = "axe_horizontal_heavy_slash";

    public static final String SPRINT_SLASH_CLIP = "axe_horizontal_heavy_slash_running";

    public static final String HACK_CLIP = "axe_cross_body_hack";

    public static final String SPRINT_HACK_CLIP = "axe_cross_body_hack_running";

    public static final String IDLE_CLIP = "axe_idle";

    private static final int WIND_UP_TICKS = 8;

    private static final int HACK_IMPACT_TICKS = 9;

    protected static final WeaponSlash CLEAVE_SLASH =
        new WeaponSlash("left", 0.7f, 0.0f, 0.25f, 6, 10, SlashStyle.PALE);

    private final float cleaveReach;

    @Nullable
    private final PocketCasterData.Quality quality;

    public BattleAxeItem(PocketCasterData.Quality quality, Properties properties) {
        this(BattleAxeGrade.tier(quality),
            BattleAxeGrade.attackDamageModifier(quality),
            BattleAxeGrade.attackSpeedModifier(quality),
            BattleAxeGrade.cleaveReach(quality),
            BattleAxeGrade.cleaveCooldownTicks(quality),
            quality, properties);
    }

    protected BattleAxeItem(Tier tier, int attackDamageModifier, float attackSpeedModifier,
                            float cleaveReach, int cleaveCooldownTicks, Properties properties) {
        this(tier, attackDamageModifier, attackSpeedModifier, cleaveReach, cleaveCooldownTicks,
            null, properties);
    }

    private BattleAxeItem(Tier tier, int attackDamageModifier, float attackSpeedModifier,
                          float cleaveReach, int cleaveCooldownTicks,
                          @Nullable PocketCasterData.Quality quality, Properties properties) {
        super(tier, attackDamageModifier, attackSpeedModifier, SLASH_CLIP, IDLE_CLIP, WIND_UP_TICKS,
            cleaveCooldownTicks, properties);
        this.cleaveReach = cleaveReach;
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
            : Component.translatable("item.hexwright.battle_axe");
        return WeaponTooltips.graded(stack, name, this.quality);
    }

    protected float cleaveReach() {
        return this.cleaveReach;
    }

    @Override
    protected String swingClip(Player player) {
        return player.isSprinting() ? SPRINT_SLASH_CLIP : SLASH_CLIP;
    }

    @Override
    public String strikeClip(Player player, int comboStep) {
        return player.isSprinting() ? SPRINT_HACK_CLIP : HACK_CLIP;
    }

    @Override
    @Nullable
    public SoundEvent strikeSound() {
        return SoundEvents.PLAYER_ATTACK_SWEEP;
    }

    @Override
    public int strikeSoundDelayTicks() {
        return HACK_IMPACT_TICKS;
    }

    @Override
    public int strikeHitDelayTicks() {
        return HACK_IMPACT_TICKS;
    }

    @Override
    @Nullable
    public WeaponSlash slashFor(String playerClip) {
        return SLASH_CLIP.equals(playerClip) || SPRINT_SLASH_CLIP.equals(playerClip)
            ? cleaveSlash() : null;
    }

    protected WeaponSlash cleaveSlash() {
        return CLEAVE_SLASH;
    }

    @Override
    protected boolean land(ServerLevel level, ServerPlayer player, InteractionHand hand) {
        SlashWaveEntity.launch(level, player, cleaveSlash().style(),
            inscribedHex(player.getItemInHand(hand)));
        return Cleave.cleave(level, player, this.cleaveReach);
    }

    @Override
    protected boolean castsOnCaught() {
        return true;
    }

    @Override
    protected Component castTooltip() {
        return Component.translatable("tooltip.hexwright.battle_axe.cast");
    }

    @Override
    protected Component swingTooltip() {
        return Component.translatable("tooltip.hexwright.battle_axe.cleave",
            blocks(this.cleaveReach), (int) Cleave.ARC_DEGREES);
    }

    @Override
    protected Component swingTooltipExtra() {
        return Component.translatable("tooltip.hexwright.battle_axe.wave");
    }
}
