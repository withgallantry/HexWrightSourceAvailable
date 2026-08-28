package com.bluup.hexwright.server.menu;

import com.bluup.hexwright.Hexwright;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;

public final class HexwrightMenus {
    public static final MenuType<StaffAssemblyMenu> STAFF_ASSEMBLY_MENU = new MenuType<>(
        StaffAssemblyMenu::new,
        FeatureFlags.VANILLA_SET
    );

    private HexwrightMenus() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.MENU, Hexwright.id("staff_assembly"), STAFF_ASSEMBLY_MENU);
    }
}
