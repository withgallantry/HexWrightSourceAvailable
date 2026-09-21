package com.bluup.hexwright.server.staff_assembly;

import at.petrak.hexcasting.api.utils.NBTHelper;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.common.lib.hex.HexIotaTypes;
import at.petrak.hexcasting.common.lib.HexAttributes;
import com.bluup.hexwright.common.staff_assembly.StaffPartCategory;
import com.bluup.hexwright.common.staff_assembly.calc.ComponentResult;
import com.bluup.hexwright.common.staff_assembly.calc.EfficiencyRating;
import com.bluup.hexwright.common.staff_assembly.calc.PersistedStaffStats;
import com.bluup.hexwright.common.staff_assembly.calc.StaffCalculationResult;
import com.bluup.hexwright.common.staff_assembly.calc.StaffCalculator;
import com.bluup.hexwright.server.hexpatterns.StoredHex;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
    private static final String TAG_BOUND_HEX = "bound_hex";
    private static final String TAG_AREA_WIDTH = "area_width";
    private static final String TAG_MEDIA_RESERVE = "media_reserve";

    private static final UUID GRID_ZOOM_MODIFIER_ID = UUID.fromString("fda8cbf8-b585-4be9-8ece-aa5ba95dc581");
    private static final UUID AMBIT_MODIFIER_ID = UUID.fromString("1b5b45f1-3010-4f7e-bd7f-c45faa26d9a7");
    private static final UUID MEDIA_DISCOUNT_MODIFIER_ID = UUID.fromString("7e5a2b1d-6c3f-4a2e-9b8d-2f6e4c1a9d70");

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

        setAttributeModifiers(stack, result.wrap().output(), result.focus().output(), result.catalyst().output());
    }

    private static void setAttributeModifiers(ItemStack stack, double gambit, double ambit, double catalystOutput) {
        ListTag modifiers = new ListTag();
        modifiers.add(attributeModifierTag(HexAttributes.GRID_ZOOM, GRID_ZOOM_MODIFIER_ID, "Hexwright Staff Grid Zoom", gambit / 100.0, AttributeModifier.Operation.MULTIPLY_BASE));
        modifiers.add(attributeModifierTag(HexAttributes.AMBIT_RADIUS, AMBIT_MODIFIER_ID, "Hexwright Staff Ambit", ambit, AttributeModifier.Operation.ADDITION));
        modifiers.add(attributeModifierTag(HexAttributes.MEDIA_CONSUMPTION_MODIFIER, MEDIA_DISCOUNT_MODIFIER_ID, "Hexwright Staff Catalyst Discount", -getMediaDiscount(catalystOutput), AttributeModifier.Operation.ADDITION));
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

    public static void setBoundHex(ItemStack stack, @Nullable List<Iota> hex) {
        CompoundTag assembly = NBTHelper.getOrCreateCompound(stack, TAG_ASSEMBLY_ROOT);
        assembly.remove(TAG_AREA_CAST);
        if (hex == null || hex.isEmpty()) {
            assembly.remove(TAG_BOUND_HEX);
            return;
        }
        assembly.put(TAG_BOUND_HEX, IotaType.serialize(new ListIota(hex)));
    }

    public static @Nullable CompoundTag getBoundHexTag(ItemStack stack) {
        CompoundTag assembly = getAssemblyOrEmpty(stack);
        if (assembly.contains(TAG_BOUND_HEX, Tag.TAG_COMPOUND)) {
            return assembly.getCompound(TAG_BOUND_HEX);
        }
        if (!assembly.contains(TAG_AREA_CAST, Tag.TAG_LIST)) {
            return null;
        }

        List<Iota> patterns = new java.util.ArrayList<>();
        for (Tag entry : assembly.getList(TAG_AREA_CAST, Tag.TAG_COMPOUND)) {
            if (entry instanceof CompoundTag patternTag && HexPattern.isPattern(patternTag)) {
                patterns.add(new PatternIota(HexPattern.fromNBT(patternTag)));
            }
        }
        return patterns.isEmpty() ? null : IotaType.serialize(new ListIota(patterns));
    }

    public static List<Iota> getBoundHex(ItemStack stack, ServerLevel level) {
        return boundHexFromTag(getBoundHexTag(stack), level);
    }

    public static List<Iota> boundHexFromTag(@Nullable CompoundTag tag, ServerLevel level) {
        if (tag == null) {
            return List.of();
        }
        List<Iota> hex = StoredHex.decode(IotaType.deserialize(tag, level));
        return hex == null ? List.of() : hex;
    }

    public static int getBoundHexSize(ItemStack stack) {
        CompoundTag tag = getBoundHexTag(stack);
        if (tag == null) {
            return 0;
        }
        if (IotaType.getTypeFromTag(tag) != HexIotaTypes.LIST) {
            return 1;
        }
        return tag.getList(HexIotaTypes.KEY_DATA, Tag.TAG_COMPOUND).size();
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

    public static final double MAX_MEDIA_DISCOUNT = 0.25;

    private static final double MEDIA_DISCOUNT_EXPONENT = 2.0;

    public static double getMediaDiscount(ItemStack stack) {
        return getMediaDiscount(getCatalystOutput(stack));
    }

    public static void syncDerivedState(ItemStack stack) {
        if (!hasStats(stack)) {
            return;
        }

        setAttributeModifiers(stack, getGambit(stack), getAmbit(stack), getCatalystOutput(stack));
    }

    public static double getMediaDiscount(double catalystOutput) {
        double cap = StaffCalculator.CATALYST_CONFIG.maxOutput();
        if (catalystOutput <= 0.0 || cap <= 0.0) {
            return 0.0;
        }
        double progress = Math.min(catalystOutput / cap, 1.0);
        return MAX_MEDIA_DISCOUNT * Math.pow(progress, MEDIA_DISCOUNT_EXPONENT);
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
