package com.bluup.hexwright.server.staff_assembly;

import at.petrak.hexcasting.api.utils.NBTHelper;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.common.lib.HexAttributes;
import com.bluup.hexwright.common.staff_assembly.StaffPartCategory;
import com.bluup.hexwright.common.staff_assembly.calc.ComponentResult;
import com.bluup.hexwright.common.staff_assembly.calc.EfficiencyRating;
import com.bluup.hexwright.common.staff_assembly.calc.PersistedStaffStats;
import com.bluup.hexwright.common.staff_assembly.calc.StaffCalculationResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public final class StaffAssemblyData {
    public static final String TAG_ASSEMBLY_ROOT = "hexwright_staff_assembly";
    private static final String TAG_AREA_CAST = "area_cast";
    private static final String TAG_AREA_WIDTH = "area_width";
    private static final String TAG_MEDIA_RESERVE = "media_reserve";
    private static final String TAG_MEDIA_REGEN_PROGRESS = "media_regen_progress";
    private static final String TAG_STORED_MEDIA = "hexcasting:media";

    private static final UUID GRID_ZOOM_MODIFIER_ID = UUID.fromString("fda8cbf8-b585-4be9-8ece-aa5ba95dc581");
    private static final UUID AMBIT_MODIFIER_ID = UUID.fromString("1b5b45f1-3010-4f7e-bd7f-c45faa26d9a7");

    private StaffAssemblyData() {
    }

    public static @Nullable String getPart(ItemStack stack, StaffPartCategory category) {
        if (stack.isEmpty()) {
            return null;
        }

        CompoundTag assembly = getAssemblyOrEmpty(stack);
        String key = tagKey(category);
        return NBTHelper.hasString(assembly, key) ? NBTHelper.getString(assembly, key) : null;
    }

    public static void setPart(ItemStack stack, StaffPartCategory category, String partId) {
        CompoundTag assembly = NBTHelper.getOrCreateCompound(stack, TAG_ASSEMBLY_ROOT);
        NBTHelper.putString(assembly, tagKey(category), partId);
    }

    public static void setStats(ItemStack stack, StaffCalculationResult result) {
        CompoundTag assembly = NBTHelper.getOrCreateCompound(stack, TAG_ASSEMBLY_ROOT);
        assembly.putDouble("attunement", result.totalAttunement());
        assembly.putDouble("efficiency", result.overallEfficiency());
        assembly.putDouble("quality", result.quality());
        assembly.putDouble("gambit", result.wrap().output());
        assembly.putDouble("ambit", result.focus().output());
        assembly.putDouble(TAG_MEDIA_RESERVE, result.catalyst().output());
        assembly.putString("efficiency_rating", result.efficiencyRating().name());
        assembly.putString("quality_rating", result.qualityRating().name());

        assembly.putDouble("core_attunement", result.coreAttunement());
        assembly.putDouble("wrap_attunement", result.wrap().attunementCost());
        assembly.putDouble("wrap_efficiency", result.wrap().efficiency());
        assembly.putDouble("focus_attunement", result.focus().attunementCost());
        assembly.putDouble("focus_efficiency", result.focus().efficiency());
        assembly.putDouble("catalyst_attunement", result.catalyst().attunementCost());
        assembly.putDouble("catalyst_efficiency", result.catalyst().efficiency());

        setAttributeModifiers(stack, result.wrap().output(), result.focus().output());
        setStoredMedia(stack, getMaxMedia(stack));
        assembly.putDouble(TAG_MEDIA_REGEN_PROGRESS, 0.0);
    }

    private static void setAttributeModifiers(ItemStack stack, double gambit, double ambit) {
        ListTag modifiers = new ListTag();
        modifiers.add(attributeModifierTag(HexAttributes.GRID_ZOOM, GRID_ZOOM_MODIFIER_ID, "Hexwright Staff Grid Zoom", gambit / 100.0, AttributeModifier.Operation.MULTIPLY_BASE));
        modifiers.add(attributeModifierTag(HexAttributes.AMBIT_RADIUS, AMBIT_MODIFIER_ID, "Hexwright Staff Ambit", ambit, AttributeModifier.Operation.ADDITION));
        CompoundTag root = stack.getOrCreateTag();
        root.put("AttributeModifiers", modifiers);
        root.putInt("HideFlags", root.getInt("HideFlags") | ItemStack.TooltipPart.MODIFIERS.getMask());
    }

    private static CompoundTag attributeModifierTag(Attribute attribute, UUID id, String name, double amount, AttributeModifier.Operation operation) {
        CompoundTag tag = new AttributeModifier(id, name, amount, operation).save();
        tag.putString("AttributeName", BuiltInRegistries.ATTRIBUTE.getKey(attribute).toString());
        return tag;
    }

    public static boolean hasStats(ItemStack stack) {
        return getAssemblyOrEmpty(stack).contains("attunement");
    }

    public static void ensureModifierTooltipHidden(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        CompoundTag root = stack.getTag();
        if (root == null || !root.contains("AttributeModifiers", Tag.TAG_LIST)) {
            return;
        }

        int hiddenFlags = root.getInt("HideFlags") | ItemStack.TooltipPart.MODIFIERS.getMask();
        root.putInt("HideFlags", hiddenFlags);
    }

    public static void setCoreItem(ItemStack stack, ItemStack coreItem) {
        CompoundTag assembly = NBTHelper.getOrCreateCompound(stack, TAG_ASSEMBLY_ROOT);
        assembly.putString("core_item", BuiltInRegistries.ITEM.getKey(coreItem.getItem()).toString());
        CompoundTag coreTag = coreItem.getTag();
        if (coreTag != null) {
            assembly.put("core_tag", coreTag.copy());
        } else {
            assembly.remove("core_tag");
        }
    }

    public static ItemStack getCoreItem(ItemStack stack) {
        CompoundTag assembly = getAssemblyOrEmpty(stack);
        if (!assembly.contains("core_item")) {
            return ItemStack.EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(assembly.getString("core_item"));
        Item item = id != null ? BuiltInRegistries.ITEM.get(id) : Items.AIR;
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack coreItem = new ItemStack(item);
        if (assembly.contains("core_tag", Tag.TAG_COMPOUND)) {
            coreItem.setTag(assembly.getCompound("core_tag").copy());
        }
        return coreItem;
    }

    public static void setAreaCastPatterns(ItemStack stack, List<HexPattern> patterns) {
        CompoundTag assembly = NBTHelper.getOrCreateCompound(stack, TAG_ASSEMBLY_ROOT);
        if (patterns == null || patterns.isEmpty()) {
            assembly.remove(TAG_AREA_CAST);
            return;
        }

        ListTag listTag = new ListTag();
        for (HexPattern pattern : patterns) {
            listTag.add(pattern.serializeToNBT());
        }

        assembly.put(TAG_AREA_CAST, listTag);
    }

    public static List<HexPattern> getAreaCastPatterns(ItemStack stack) {
        CompoundTag assembly = getAssemblyOrEmpty(stack);
        if (!assembly.contains(TAG_AREA_CAST, Tag.TAG_LIST)) {
            return List.of();
        }

        ListTag listTag = assembly.getList(TAG_AREA_CAST, Tag.TAG_COMPOUND);
        if (listTag.isEmpty()) {
            return List.of();
        }

        List<HexPattern> out = new java.util.ArrayList<>(listTag.size());
        for (Tag entry : listTag) {
            if (!(entry instanceof CompoundTag patternTag)) {
                continue;
            }
            if (!HexPattern.isPattern(patternTag)) {
                continue;
            }
            out.add(HexPattern.fromNBT(patternTag));
        }
        return out;
    }

    public static double getAreaWidth(ItemStack stack) {
        return getAssemblyOrEmpty(stack).getDouble(TAG_AREA_WIDTH);
    }

    public static void setAreaWidth(ItemStack stack, double width) {
        CompoundTag assembly = NBTHelper.getOrCreateCompound(stack, TAG_ASSEMBLY_ROOT);
        if (width <= 0.0) {
            assembly.remove(TAG_AREA_WIDTH);
            return;
        }
        assembly.putDouble(TAG_AREA_WIDTH, width);
    }

    public static double getAttunement(ItemStack stack) {
        return getAssemblyOrEmpty(stack).getDouble("attunement");
    }

    public static double getGambit(ItemStack stack) {
        return getAssemblyOrEmpty(stack).getDouble("gambit");
    }

    public static double getAmbit(ItemStack stack) {
        return getAssemblyOrEmpty(stack).getDouble("ambit");
    }

    public static double getCatalystOutput(ItemStack stack) {
        return getAssemblyOrEmpty(stack).getDouble(TAG_MEDIA_RESERVE);
    }

    public static int getBatterySize(ItemStack stack) {
        return getBatterySize(getCatalystOutput(stack));
    }

    public static int getMediaPerMinute(ItemStack stack) {
        return getMediaPerMinute(getCatalystOutput(stack));
    }

    public static long getMaxMedia(ItemStack stack) {
        return (long) getBatterySize(stack) * MediaConstants.DUST_UNIT;
    }

    public static long getStoredMedia(ItemStack stack) {
        if (NBTHelper.hasInt(stack, TAG_STORED_MEDIA)) {
            return NBTHelper.getInt(stack, TAG_STORED_MEDIA);
        }
        return NBTHelper.getLong(stack, TAG_STORED_MEDIA);
    }

    public static void setStoredMedia(ItemStack stack, long media) {
        NBTHelper.putLong(stack, TAG_STORED_MEDIA, Math.max(0L, Math.min(media, getMaxMedia(stack))));
    }

    public static void syncDerivedState(ItemStack stack) {
        if (!hasStats(stack)) {
            return;
        }

        setAttributeModifiers(stack, getGambit(stack), getAmbit(stack));

        long maxMedia = getMaxMedia(stack);
        if (maxMedia <= 0) {
            setStoredMedia(stack, 0L);
            return;
        }

        CompoundTag root = stack.getOrCreateTag();
        if (!root.contains(TAG_STORED_MEDIA, Tag.TAG_ANY_NUMERIC)) {
            setStoredMedia(stack, maxMedia);
            return;
        }

        setStoredMedia(stack, getStoredMedia(stack));
    }

    public static void rechargeOneTick(ItemStack stack) {
        long maxMedia = getMaxMedia(stack);
        if (maxMedia <= 0) {
            return;
        }

        int mediaPerMinute = getMediaPerMinute(stack);
        if (mediaPerMinute <= 0) {
            return;
        }

        CompoundTag assembly = NBTHelper.getOrCreateCompound(stack, TAG_ASSEMBLY_ROOT);
        double perTick = mediaPerMinute * (double) MediaConstants.DUST_UNIT / (20.0 * 60.0);
        double progress = assembly.getDouble(TAG_MEDIA_REGEN_PROGRESS) + perTick;
        long wholeMedia = (long) progress;
        if (wholeMedia > 0) {
            long currentMedia = getStoredMedia(stack);
            long inserted = Math.min(wholeMedia, Math.max(0L, maxMedia - currentMedia));
            if (inserted > 0) {
                setStoredMedia(stack, currentMedia + inserted);
                progress -= inserted;
            } else {
                progress = 0.0;
            }
        }
        assembly.putDouble(TAG_MEDIA_REGEN_PROGRESS, progress);
    }

    public static int getBatterySize(double catalystOutput) {
        if (catalystOutput <= 0.0) {
            return 0;
        }
        if (catalystOutput <= 30.0) {
            return 50;
        }
        if (catalystOutput <= 60.0) {
            return 100;
        }
        if (catalystOutput <= 90.0) {
            return 200;
        }

        double capped = Math.min(catalystOutput, 120.0);
        double progress = (capped - 90.0) / 30.0;
        return 300 + (int) Math.round(progress * 100.0);
    }

    public static int getMediaPerMinute(double catalystOutput) {
        if (catalystOutput <= 0.0) {
            return 0;
        }
        if (catalystOutput <= 30.0) {
            return 2;
        }
        if (catalystOutput <= 60.0) {
            return 4;
        }
        if (catalystOutput <= 90.0) {
            return 6;
        }
        return 10;
    }

    public static double getEfficiency(ItemStack stack) {
        return getAssemblyOrEmpty(stack).getDouble("efficiency");
    }

    public static double getQuality(ItemStack stack) {
        return getAssemblyOrEmpty(stack).getDouble("quality");
    }

     public static PersistedStaffStats getPersistedStats(ItemStack stack) {
        if (!hasStats(stack)) {
            return PersistedStaffStats.EMPTY;
        }

        CompoundTag assembly = getAssemblyOrEmpty(stack);
        ComponentResult wrap = new ComponentResult(assembly.getDouble("gambit"), 0, 0, assembly.getDouble("wrap_attunement"), assembly.getDouble("wrap_efficiency"), 0);
        ComponentResult focus = new ComponentResult(assembly.getDouble("ambit"), 0, 0, assembly.getDouble("focus_attunement"), assembly.getDouble("focus_efficiency"), 0);
        ComponentResult catalyst = new ComponentResult(getCatalystOutput(stack), 0, 0, assembly.getDouble("catalyst_attunement"), assembly.getDouble("catalyst_efficiency"), 0);
        return new PersistedStaffStats(assembly.getDouble("core_attunement"), wrap, focus, catalyst);
    }

    public static @Nullable EfficiencyRating getQualityRating(ItemStack stack) {
        CompoundTag assembly = getAssemblyOrEmpty(stack);
        if (!assembly.contains("quality_rating")) {
            return null;
        }
        try {
            return EfficiencyRating.valueOf(assembly.getString("quality_rating"));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static CompoundTag getAssemblyOrEmpty(ItemStack stack) {
        if (stack.isEmpty()) {
            return new CompoundTag();
        }
        CompoundTag assembly = NBTHelper.getCompound(stack, TAG_ASSEMBLY_ROOT);
        return assembly != null ? assembly : new CompoundTag();
    }

    private static String tagKey(StaffPartCategory category) {
        return category.name().toLowerCase(java.util.Locale.ROOT);
    }
}
