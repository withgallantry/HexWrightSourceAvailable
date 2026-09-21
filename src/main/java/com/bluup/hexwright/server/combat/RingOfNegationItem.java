package com.bluup.hexwright.server.combat;

import com.bluup.hexwright.server.accessory.WornAccessories;
import com.bluup.hexwright.server.item.ArtifactItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class RingOfNegationItem extends Item implements ArtifactItem {

    public enum Kind {
        NEGATION,
        REPRISAL;

        boolean covers(Kind other) {
            return this == REPRISAL || other == NEGATION;
        }
    }

    private final Kind kind;

    public RingOfNegationItem(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return this.kind;
    }

    public static @Nullable Kind wornBy(LivingEntity entity) {
        Kind best = null;
        for (ItemStack stack : WornAccessories.slotContents(entity, "ring")) {
            if (stack.getItem() instanceof RingOfNegationItem ring
                && (best == null || ring.kind.covers(best))) {
                best = ring.kind;
            }
        }
        return best;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return this.kind == Kind.REPRISAL;
    }

    @Override
    public boolean isArtifact(ItemStack stack) {
        return this.kind == Kind.REPRISAL;
    }

    @Override
    public Component getName(ItemStack stack) {
        Component name = super.getName(stack);
        return ArtifactItem.is(stack) ? name.copy().withStyle(ArtifactItem.COLOUR) : name;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
                                TooltipFlag flag) {
        if (ArtifactItem.is(stack)) {
            tooltip.add(ArtifactItem.tooltipLine());
        }
        tooltip.add(Component.translatable("item.hexwright.ring_of_negation.tip")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.hexwright.ring_of_negation.tip.caps",
            (int) SpellDamage.PER_HIT_CAP, (int) SpellDamage.PER_SECOND_CAP)
            .withStyle(ChatFormatting.DARK_GRAY));
        if (this.kind == Kind.REPRISAL) {
            tooltip.add(Component.translatable("item.hexwright.ring_of_reprisal.tip",
                (int) (Reprisal.FRACTION * 100.0F)).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
    }
}
