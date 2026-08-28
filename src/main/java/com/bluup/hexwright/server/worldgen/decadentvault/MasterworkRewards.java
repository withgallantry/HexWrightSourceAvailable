package com.bluup.hexwright.server.worldgen.decadentvault;

import com.bluup.hexwright.server.armour.ArmourPiece;
import com.bluup.hexwright.server.armour.ArmourSet;
import com.bluup.hexwright.server.armour.ArmourTier;
import com.bluup.hexwright.server.armour.HexwrightArmour;
import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import com.bluup.hexwright.server.staff_assembly.StaffCoreData;
import com.bluup.hexwright.server.vehicle.VehicleData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

final class MasterworkRewards {

    private static final Item[] STAFF_CORES = {
        HexwrightItems.AMETHYST_CORE, HexwrightItems.QUARTZ_CORE, HexwrightItems.SCRIBE_CORE,
        HexwrightItems.TRAVELLER_CORE, HexwrightItems.ECHO_CORE
    };

    private MasterworkRewards() {
    }

    static ItemStack roll(RandomSource random) {
        return switch (random.nextInt(3)) {
            case 0 -> staffCore(random);
            case 1 -> broom();
            default -> armourPiece(random);
        };
    }

    private static ItemStack staffCore(RandomSource random) {
        Item core = STAFF_CORES[random.nextInt(STAFF_CORES.length)];
        return StaffCoreData.create(core, PocketCasterData.Quality.MASTERWORK);
    }

    private static ItemStack broom() {
        ItemStack stack = new ItemStack(HexwrightItems.BROOM);
        CompoundTag data = stack.getOrCreateTagElement(VehicleData.ROOT_TAG);
        VehicleData.setQuality(data, PocketCasterData.Quality.MASTERWORK);
        return stack;
    }

    private static ItemStack armourPiece(RandomSource random) {
        ArmourSet[] sets = ArmourSet.values();
        ArmourPiece[] pieces = ArmourPiece.values();
        ArmourSet set = sets[random.nextInt(sets.length)];
        ArmourPiece piece = pieces[random.nextInt(pieces.length)];
        Item item = HexwrightArmour.piece(set, ArmourTier.forGrade(PocketCasterData.Quality.MASTERWORK), piece);
        return new ItemStack(item);
    }
}
