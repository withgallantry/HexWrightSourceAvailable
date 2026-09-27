package com.bluup.hexwright.server.worldgen.decadentvault;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.common.aspects.AspectMappings;
import com.bluup.hexwright.common.staff_assembly.StaffPart;
import com.bluup.hexwright.common.staff_assembly.StaffPartCategory;
import com.bluup.hexwright.common.staff_assembly.StaffParts;
import com.bluup.hexwright.common.staff_assembly.calc.ComponentResult;
import com.bluup.hexwright.common.staff_assembly.calc.EfficiencyRating;
import com.bluup.hexwright.common.staff_assembly.calc.StaffCalculationResult;
import com.bluup.hexwright.common.staff_assembly.calc.StaffCalculator;
import com.bluup.hexwright.server.armour.ArmourPiece;
import com.bluup.hexwright.server.armour.ArmourSet;
import com.bluup.hexwright.server.armour.ArmourTier;
import com.bluup.hexwright.server.armour.HexwrightArmour;
import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import com.bluup.hexwright.server.staff_assembly.StaffAssemblyData;
import com.bluup.hexwright.server.staff_assembly.StaffCoreData;
import com.bluup.hexwright.server.vehicle.VehicleData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MasterworkRewards {

    private static final Item[] STAFF_CORES = {
        HexwrightItems.AMETHYST_CORE, HexwrightItems.QUARTZ_CORE, HexwrightItems.SCRIBE_CORE,
        HexwrightItems.TRAVELLER_CORE, HexwrightItems.ECHO_CORE
    };

    private static final int STAFF_ATTEMPTS = 20;

    private static final int STAFF_PICKS = 60;

    private static final double PERFECT_FIT = 0.999;

    private MasterworkRewards() {
    }

    public static ItemStack staff(RandomSource random) {
        ItemStack core = staffCore(random);
        List<List<Item>> fits = new ArrayList<>();
        for (int component = 0; component < 3; component++) {
            fits.add(perfectFits(core, component));
        }
        for (int attempt = 0; attempt < STAFF_ATTEMPTS; attempt++) {
            List<List<ItemStack>> build = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
            StaffCalculationResult result = null;
            for (int pick = 0; pick < STAFF_PICKS; pick++) {
                int component = random.nextInt(3);
                List<Item> pool = fits.get(component);
                List<ItemStack> slots = build.get(component);
                if (pool.isEmpty() || slots.size() >= StaffCalculator.MAX_ITEMS_PER_COMPONENT) {
                    continue;
                }
                slots.add(new ItemStack(pool.get(random.nextInt(pool.size()))));
                StaffCalculationResult trial = calculate(core, build);
                if (trial.craftable()) {
                    result = trial;
                } else {
                    slots.remove(slots.size() - 1);
                }
            }
            if (result != null && result.qualityRating() == EfficiencyRating.MASTERWORK) {
                return assemble(core, result, random);
            }
        }
        Hexwright.LOGGER.warn("No Masterwork staff build found from {} aspect mappings", AspectMappings.mappedItems().size());
        return ItemStack.EMPTY;
    }

    private static ItemStack assemble(ItemStack core, StaffCalculationResult result, RandomSource random) {
        List<StaffPart> models = StaffParts.options(StaffPartCategory.MODEL).stream()
            .filter(model -> StaffParts.isUnlocked(model, result.qualityRating()))
            .toList();
        ItemStack staff = new ItemStack(HexwrightItems.CONFIGURABLE_STAFF);
        StaffAssemblyData.setPart(staff, StaffPartCategory.MODEL, models.get(random.nextInt(models.size())).id());
        StaffAssemblyData.setStats(staff, result);
        StaffAssemblyData.setCoreItem(staff, core);
        return staff;
    }

    private static List<Item> perfectFits(ItemStack core, int component) {
        List<Item> fits = new ArrayList<>();
        for (Item item : AspectMappings.mappedItems().stream()
            .sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()))
            .toList()) {
            List<List<ItemStack>> build = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
            build.get(component).add(new ItemStack(item));
            if (part(calculate(core, build), component).efficiency() >= PERFECT_FIT) {
                fits.add(item);
            }
        }
        return fits;
    }

    private static StaffCalculationResult calculate(ItemStack core, List<List<ItemStack>> build) {
        return StaffCalculator.calculate(core, build.get(0), build.get(1), build.get(2));
    }

    private static ComponentResult part(StaffCalculationResult result, int component) {
        return switch (component) {
            case 0 -> result.wrap();
            case 1 -> result.focus();
            default -> result.catalyst();
        };
    }

    static ItemStack staffCore(RandomSource random) {
        Item core = STAFF_CORES[random.nextInt(STAFF_CORES.length)];
        return StaffCoreData.create(core, PocketCasterData.Quality.MASTERWORK);
    }

    public static ItemStack broom(RandomSource random) {
        ItemStack stack = new ItemStack(HexwrightItems.BROOM);
        CompoundTag data = stack.getOrCreateTagElement(VehicleData.ROOT_TAG);
        VehicleData.setQuality(data, PocketCasterData.Quality.MASTERWORK);
        return stack;
    }

    public static ItemStack armourPiece(RandomSource random) {
        ArmourSet[] sets = ArmourSet.values();
        ArmourPiece[] pieces = ArmourPiece.values();
        return new ItemStack(HexwrightArmour.piece(sets[random.nextInt(sets.length)],
            ArmourTier.forGrade(PocketCasterData.Quality.MASTERWORK), pieces[random.nextInt(pieces.length)]));
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
