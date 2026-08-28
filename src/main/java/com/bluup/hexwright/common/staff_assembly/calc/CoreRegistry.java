package com.bluup.hexwright.common.staff_assembly.calc;

import net.minecraft.world.item.Item;

import com.bluup.hexwright.server.item.HexwrightItems;

import static com.bluup.hexwright.server.staff_assembly.StaffCorePatternPower.FREE_GREATER_TELEPORT_POWER_ID;
import static com.bluup.hexwright.server.staff_assembly.StaffPowers.AREA_CAST_POWER_ID;
import static com.bluup.hexwright.server.staff_assembly.StaffPowers.BEAM_CAST_POWER_ID;
import static com.bluup.hexwright.server.staff_assembly.StaffPowers.HEXICON_POWER_ID;
import static com.bluup.hexwright.server.staff_assembly.StaffPowers.MINOR_AMPLIFY_POWER_ID;

import java.util.Map;
import java.util.Optional;

public final class CoreRegistry {
    private static final Map<Item, CoreData> CORES = Map.ofEntries(
        entry(HexwrightItems.AMETHYST_CORE, "amethyst_core", MINOR_AMPLIFY_POWER_ID, 20),
        entry(HexwrightItems.QUARTZ_CORE, "quartz_core", AREA_CAST_POWER_ID, 24),
        entry(HexwrightItems.SCRIBE_CORE, "scribe_core", HEXICON_POWER_ID, 28),
        entry(HexwrightItems.TRAVELLER_CORE, "traveller_core", FREE_GREATER_TELEPORT_POWER_ID, 30),
        entry(HexwrightItems.ECHO_CORE, "echo_core", BEAM_CAST_POWER_ID, 35)
    );

    private CoreRegistry() {
    }

    public static Optional<CoreData> lookup(Item item) {
        return Optional.ofNullable(CORES.get(item));
    }

    private static Map.Entry<Item, CoreData> entry(Item item, String id, String powerId, double attunementCost) {
        return Map.entry(item, new CoreData(id, powerId, attunementCost));
    }
}
