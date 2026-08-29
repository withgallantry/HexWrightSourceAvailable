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

import java.util.List;

final class MasterworkRewards {

    private static final Item[] STAFF_CORES = {
        HexwrightItems.AMETHYST_CORE, HexwrightItems.QUARTZ_CORE, HexwrightItems.SCRIBE_CORE,
        HexwrightItems.TRAVELLER_CORE, HexwrightItems.ECHO_CORE
    };

    private MasterworkRewards() {
    }

    static ItemStack staffCore(RandomSource random) {
        Item core = STAFF_CORES[random.nextInt(STAFF_CORES.length)];
        return StaffCoreData.create(core, PocketCasterData.Quality.MASTERWORK);
    }

    static ItemStack broom(RandomSource random) {
        ItemStack stack = new ItemStack(HexwrightItems.BROOM);
        CompoundTag data = stack.getOrCreateTagElement(VehicleData.ROOT_TAG);
        VehicleData.setQuality(data, PocketCasterData.Quality.MASTERWORK);
        return stack;
    }

    static List<ItemStack> armourSet(RandomSource random) {
        ArmourSet[] sets = ArmourSet.values();
        ArmourSet set = sets[random.nextInt(sets.length)];
        ArmourTier tier = ArmourTier.forGrade(PocketCasterData.Quality.MASTERWORK);
        return List.of(
            new ItemStack(HexwrightArmour.piece(set, tier, ArmourPiece.HELMET)),
            new ItemStack(HexwrightArmour.piece(set, tier, ArmourPiece.CHESTPLATE)),
            new ItemStack(HexwrightArmour.piece(set, tier, ArmourPiece.LEGGINGS)),
            new ItemStack(HexwrightArmour.piece(set, tier, ArmourPiece.BOOTS))
        );
    }
}
