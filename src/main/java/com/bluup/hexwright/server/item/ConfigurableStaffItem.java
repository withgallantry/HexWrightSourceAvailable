package com.bluup.hexwright.server.item;

import com.bluup.hexwright.common.staff_assembly.StaffPart;
import com.bluup.hexwright.common.staff_assembly.StaffPartCategory;
import com.bluup.hexwright.common.staff_assembly.StaffParts;
import com.bluup.hexwright.common.staff_assembly.calc.CoreData;
import com.bluup.hexwright.common.staff_assembly.calc.CoreRegistry;
import com.bluup.hexwright.common.staff_assembly.calc.EfficiencyRating;
import com.bluup.hexwright.server.hexicon.HexiconData;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import com.bluup.hexwright.server.staff_assembly.StaffAssemblyData;
import com.bluup.hexwright.server.staff_assembly.StaffCoreData;
import com.bluup.hexwright.server.staff_assembly.StaffCoreItem;
import com.bluup.hexwright.server.staff_assembly.StaffGreatSpellData;
import com.bluup.hexwright.server.staff_assembly.StaffPowers;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class ConfigurableStaffItem extends ItemHexwrightStaff {
    public ConfigurableStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (!level.isClientSide && StaffAssemblyData.hasStats(stack)) {
            StaffAssemblyData.ensureModifierTooltipHidden(stack);
            StaffAssemblyData.syncDerivedState(stack);
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        if (!StaffAssemblyData.hasStats(stack)) {
            return super.getName(stack);
        }

        EfficiencyRating overallQuality = StaffAssemblyData.getQualityRating(stack);
        Component qualityPrefix = Component.translatable(
            (overallQuality != null ? overallQuality : EfficiencyRating.CRUDE).translationKey());

        String modelId = StaffAssemblyData.getPart(stack, StaffPartCategory.MODEL);
        Optional<StaffPart> model = modelId != null ? StaffParts.find(StaffPartCategory.MODEL, modelId) : Optional.empty();
        Component baseName;
        if (model.isEmpty()) {
            baseName = Component.translatable("item.hexwright.configurable_staff.base");
        } else {
            Component modelName = Component.translatable("item.hexwright." + modelId);
            baseName = model.get().hasTag(StaffPart.TAG_NAME_OMITS_STAFF)
                ? Component.translatable("item.hexwright.configurable_staff.model_named", modelName)
                : modelName;
        }

        ItemStack coreItem = StaffAssemblyData.getCoreItem(stack);
        Optional<CoreData> core = CoreRegistry.lookup(coreItem.getItem());
        if (core.isEmpty()) {
            return Component.translatable("item.hexwright.configurable_staff.named_plain", qualityPrefix, baseName);
        }

        Component coreAffix = Component.translatable(coreGradeTranslationKey(StaffCoreData.getQuality(coreItem)));
        Component coreNoun = Component.translatable("core_noun.hexwright." + core.get().id());

        return Component.translatable("item.hexwright.configurable_staff.named", qualityPrefix, baseName, coreAffix, coreNoun);
    }

    private static String coreGradeTranslationKey(PocketCasterData.Quality grade) {
        return switch (grade) {
            case CRUDE -> "core_grade.hexwright.crude";
            case SOUND -> "core_grade.hexwright.sound";
            case FINE -> "core_grade.hexwright.fine";
            case EXQUISITE -> "core_grade.hexwright.exquisite";
            case MASTERWORK -> "core_grade.hexwright.masterwork";
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        if (StaffAssemblyData.hasStats(stack)) {
            EfficiencyRating quality = StaffAssemblyData.getQualityRating(stack);
            if (quality != null) {
                tooltip.add(Component.translatable("tooltip.hexwright.staff.quality", quality.label())
                    .withStyle(quality.color()));
            }

            double gambit = StaffAssemblyData.getGambit(stack);
            if (gambit > 0.0) {
                tooltip.add(Component.translatable("tooltip.hexwright.staff.grid_size", format(gambit))
                    .withStyle(ChatFormatting.BLUE));
            }

            double ambit = StaffAssemblyData.getAmbit(stack);
            if (ambit > 0.0) {
                tooltip.add(Component.translatable("tooltip.hexwright.staff.ambit", format(ambit))
                    .withStyle(ChatFormatting.BLUE));
            }

            int mediaDiscountPercent = (int) Math.round(StaffAssemblyData.getMediaDiscount(stack) * 100.0);
            if (mediaDiscountPercent > 0) {
                tooltip.add(Component.translatable("tooltip.hexwright.staff.media_discount", mediaDiscountPercent)
                    .withStyle(ChatFormatting.GREEN));
            }

            ItemStack coreItem = StaffAssemblyData.getCoreItem(stack);
            if (coreItem.getItem() instanceof StaffCoreItem staffCoreItem) {
                tooltip.add(CommonComponents.EMPTY);
                CoreRegistry.lookup(staffCoreItem).ifPresent(data -> {
                    tooltip.add(data.displayName().copy().withStyle(ChatFormatting.LIGHT_PURPLE));
                    data.description().forEach(line -> tooltip.add(line.copy().withStyle(ChatFormatting.GRAY)));
                });
                StaffCoreItem.appendEffectTooltip(coreItem, staffCoreItem.kind(), tooltip);
            }

            if (StaffPowers.hasHexiconCore(stack)) {
                int written = HexiconData.getCachedWrittenCount(stack);
                tooltip.add(Component.translatable("tooltip.hexwright.staff.hexicon", written).withStyle(ChatFormatting.GOLD));
            }

            int areaCastCount = StaffAssemblyData.getAreaCastPatterns(stack).size();
            if (areaCastCount > 0) {
                tooltip.add(Component.translatable("tooltip.hexwright.staff.area_cast", areaCastCount).withStyle(ChatFormatting.GOLD));
            }

            double areaWidth = StaffAssemblyData.getAreaWidth(stack);
            if (areaWidth > 0.0 && StaffPowers.hasAreaCastCore(stack)) {
                tooltip.add(Component.translatable("tooltip.hexwright.staff.area_width",
                    String.format(java.util.Locale.ROOT, "%.1f", areaWidth)).withStyle(ChatFormatting.GOLD));
            }

            if (StaffPowers.hasAmethystCore(stack)) {
                int learned = StaffGreatSpellData.count(stack);
                if (learned > 0) {
                    tooltip.add(Component.translatable("tooltip.hexwright.staff.amethyst_great_spells", learned, StaffGreatSpellData.maxLearned(stack))
                        .withStyle(ChatFormatting.GOLD));
                }
            }
        }
    }

    private static String format(double value) {
        return String.valueOf(Math.round(value));
    }
}
