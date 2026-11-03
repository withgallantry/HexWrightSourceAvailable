package com.bluup.hexwright.compat.ears;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.server.armour.HexwrightArmourItem;
import com.unascribed.ears.api.EarsFeatureType;
import com.unascribed.ears.api.iface.EarsInhibitor;
import com.unascribed.ears.api.registry.EarsInhibitorRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;

@Environment(EnvType.CLIENT)
public final class EarsChestInhibitor implements EarsInhibitor {

    private EarsChestInhibitor() {
    }

    static void register() {
        EarsInhibitorRegistry.register(Hexwright.MOD_ID, new EarsChestInhibitor());
        HexwrightDebug.log(HexwrightDebug.RENDER,
            "[ears] Ears detected; chest feature will be inhibited for Hexwright's own chestplates");
    }

    @Override
    public boolean shouldInhibit(EarsFeatureType type, Object peer) {
        return type == EarsFeatureType.CHEST
            && peer instanceof LivingEntity wearer
            && isCustomModelChestplate(wearer.getItemBySlot(EquipmentSlot.CHEST).getItem());
    }

    private static boolean isCustomModelChestplate(Item item) {
        return item instanceof HexwrightArmourItem armour
            && armour.getType() == ArmorItem.Type.CHESTPLATE;
    }
}
