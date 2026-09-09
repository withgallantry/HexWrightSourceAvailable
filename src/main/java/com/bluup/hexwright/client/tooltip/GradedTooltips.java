package com.bluup.hexwright.client.tooltip;

import com.bluup.hexwright.common.staff_assembly.calc.EfficiencyRating;
import com.bluup.hexwright.server.armour.ArmourGemData;
import com.bluup.hexwright.server.armour.ArmourGemItem;
import com.bluup.hexwright.server.armour.HexwrightArmourItem;
import com.bluup.hexwright.server.item.ArtifactItem;
import com.bluup.hexwright.server.item.ConfigurableStaffItem;
import com.bluup.hexwright.server.item.PentaboxItem;
import com.bluup.hexwright.server.item.PocketCasterItem;
import com.bluup.hexwright.server.item.WardingBoxItem;
import com.bluup.hexwright.server.pentabox.PentaboxData;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import com.bluup.hexwright.server.staff_assembly.StaffAssemblyData;
import com.bluup.hexwright.server.staff_assembly.StaffCoreData;
import com.bluup.hexwright.server.staff_assembly.StaffCoreItem;
import com.bluup.hexwright.server.talisman.TalismanData;
import com.bluup.hexwright.server.talisman.TalismanItem;
import com.bluup.hexwright.server.vehicle.VehicleData;
import com.bluup.hexwright.server.vehicle.VehicleItem;
import com.bluup.hexwright.server.weapon.BattleAxeItem;
import com.bluup.hexwright.server.weapon.BattleHammerItem;
import com.bluup.hexwright.server.weapon.GreatBowItem;
import com.bluup.hexwright.server.weapon.ShortSwordItem;
import com.bluup.hexwright.server.wardingbox.WardingBoxData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class GradedTooltips {

    private static @Nullable ItemStack captured;

    private GradedTooltips() {
    }

    public static void capture(ItemStack stack) {
        captured = stack;
    }

    public static void clear() {
        captured = null;
    }

    public static @Nullable TooltipStyle peekStyle() {
        ItemStack stack = captured;
        return stack == null || stack.isEmpty() ? null : styleOf(stack);
    }

    public static boolean renderBackground(GuiGraphics graphics, int x, int y, int width, int height, int z) {
        ItemStack stack = captured;
        captured = null;
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        TooltipStyle style = styleOf(stack);
        if (style == null) {
            return false;
        }
        style.render(graphics, x, y, width, height, z);
        return true;
    }

    public static @Nullable TooltipStyle styleOf(ItemStack stack) {
        if (ArtifactItem.is(stack)) {
            return TooltipStyle.ARTIFACT;
        }
        if (stack.getItem() instanceof ConfigurableStaffItem) {
            EfficiencyRating rating = StaffAssemblyData.getQualityRating(stack);
            return styleFor(rating == null ? EfficiencyRating.CRUDE : rating);
        }
        PocketCasterData.Quality grade = gradeOf(stack);
        return grade == null ? null : styleFor(grade);
    }

    @Nullable
    public static PocketCasterData.Quality gradeOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (stack.getItem() instanceof PocketCasterItem) {
            return PocketCasterData.getQuality(stack);
        }
        if (stack.getItem() instanceof PentaboxItem) {
            return PentaboxData.getQuality(stack);
        }
        if (stack.getItem() instanceof TalismanItem) {
            return TalismanData.getQuality(stack);
        }
        if (stack.getItem() instanceof StaffCoreItem) {
            return StaffCoreData.getQuality(stack);
        }
        if (stack.getItem() instanceof ArmourGemItem) {
            return ArmourGemData.getQuality(stack);
        }
        if (stack.getItem() instanceof HexwrightArmourItem armour) {
            return armour.tier().displayQuality();
        }
        if (stack.getItem() instanceof GreatBowItem bow) {
            return bow.quality();
        }
        if (stack.getItem() instanceof ShortSwordItem sword) {
            return sword.quality();
        }
        if (stack.getItem() instanceof BattleAxeItem axe) {
            return axe.quality();
        }
        if (stack.getItem() instanceof BattleHammerItem hammer) {
            return hammer.quality();
        }
        if (stack.getItem() instanceof VehicleItem) {
            CompoundTag data = stack.getTagElement(VehicleData.ROOT_TAG);
            return data == null ? PocketCasterData.Quality.CRUDE : VehicleData.getQuality(data);
        }
        if (stack.getItem() instanceof WardingBoxItem) {
            return WardingBoxData.getQuality(stack).orElse(PocketCasterData.Quality.CRUDE);
        }
        if (stack.getItem() instanceof com.bluup.hexwright.server.vault.VaultKeyItem) {
            return ArtifactItem.is(stack) ? null
                : com.bluup.hexwright.server.vault.VaultKeyItem.grade(stack);
        }
        if (stack.getItem() instanceof com.bluup.hexwright.server.remnant.HexEngravedBottleItem) {
            return com.bluup.hexwright.server.remnant.BottleData.getQuality(stack);
        }
        return null;
    }

    public static TooltipStyle styleFor(PocketCasterData.Quality grade) {
        return switch (grade) {
            case CRUDE -> TooltipStyle.COMMON;
            case SOUND -> TooltipStyle.UNCOMMON;
            case FINE -> TooltipStyle.RARE;
            case EXQUISITE -> TooltipStyle.EPIC;
            case MASTERWORK -> TooltipStyle.LEGENDARY;
        };
    }

    public static TooltipStyle styleFor(EfficiencyRating rating) {
        return switch (rating) {
            case CRUDE -> TooltipStyle.COMMON;
            case SOUND -> TooltipStyle.UNCOMMON;
            case FINE -> TooltipStyle.RARE;
            case EXQUISITE -> TooltipStyle.EPIC;
            case MASTERWORK -> TooltipStyle.LEGENDARY;
        };
    }
}
