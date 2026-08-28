package com.bluup.hexwright.server.staff_assembly;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.iota.EntityIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.casting.iota.Vec3Iota;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.common.lib.HexAttributes;
import com.bluup.hexwright.common.staff_assembly.calc.CoreData;
import com.bluup.hexwright.common.staff_assembly.calc.CoreRegistry;
import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class StaffPowers {
    public static final String MINOR_AMPLIFY_POWER_ID = "minor_amplify";
    public static final String AREA_CAST_POWER_ID = "area_cast";
    public static final String BEAM_CAST_POWER_ID = "beam_cast";
    public static final String HEXICON_POWER_ID = "built_in_hexicon";

    private StaffPowers() {
    }

    public static void bindFromWrite(CastingEnvironment env, Iota datum) {
        ItemStack staff = getCastingStaff(env);
        if (staff.isEmpty() || !hasEntityListBindingCore(staff)) {
            return;
        }

        List<HexPattern> patterns = decodePatternPayload(datum);
        if (patterns == null) {
            return;
        }

        StaffAssemblyData.setAreaCastPatterns(staff, patterns);

        if (env.getCaster() != null) {
            env.getCaster().displayClientMessage(
                Component.translatable("message.hexwright.core.area_cast_bound", patterns.size())
                    .withStyle(ChatFormatting.AQUA),
                true
            );
        }
    }

    public static void executeTick(ServerPlayer player, ItemStack staff) {
        AABB scanBox = areaScanBox(player, staff);
        executeTickWithBuckets(player, staff, buildEntityBuckets(player.serverLevel(), scanBox, player), scanBox);
    }

    public static AABB areaScanBox(ServerPlayer player, ItemStack staff) {
        Vec3 centre = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
        return new AABB(centre, centre).inflate(areaScanHalfExtent(player, staff));
    }

    public static final double MIN_AREA_WIDTH = 4.0;

    public static double maxAreaWidth(ServerPlayer player, ItemStack staff) {
        ItemStack coreItem = StaffAssemblyData.getCoreItem(staff);
        PocketCasterData.Quality quality = StaffCoreData.getQuality(coreItem);
        double liveAmbit = player.getAttributeValue(HexAttributes.AMBIT_RADIUS);
        return Math.max(MIN_AREA_WIDTH, liveAmbit * StaffCoreData.gradeFraction(quality));
    }

    public static double areaWidth(ServerPlayer player, ItemStack staff) {
        double max = maxAreaWidth(player, staff);
        double tuned = StaffAssemblyData.getAreaWidth(staff);
        if (tuned <= 0.0) {
            return max;
        }
        return Math.min(Math.max(tuned, MIN_AREA_WIDTH), max);
    }

    public static double areaScanHalfExtent(ServerPlayer player, ItemStack staff) {
        return areaWidth(player, staff) * 0.5;
    }

    public static void executeTickWithBuckets(ServerPlayer player, ItemStack staff, ListIota entityBuckets, @Nullable AABB rangeBox) {
        executeWithSeed(player, StaffAssemblyData.getAreaCastPatterns(staff), entityBuckets, rangeBox);
    }

    public static void executeWithSeed(ServerPlayer player, List<HexPattern> patterns, Iota seed, @Nullable AABB rangeBox) {
        if (patterns.isEmpty()) {
            return;
        }

        List<Iota> executable = patterns.stream().map(PatternIota::new).collect(Collectors.toList());
        if (executable.isEmpty()) {
            return;
        }

        try {
            CastingImage seededImage = new CastingImage().copy(
                List.of(seed),
                0,
                List.of(),
                false,
                0L,
                new CompoundTag()
            );

            StaffPowerCastEnv env = new StaffPowerCastEnv(player, InteractionHand.MAIN_HAND);
            if (rangeBox != null) {
                env.addExtension(new AreaCastRangeComponent(rangeBox));
            }

            CastingVM vm = new CastingVM(seededImage, env);
            vm.queueExecuteAndWrapIotas(executable, player.serverLevel());
        } catch (RuntimeException ignored) {
        }
    }

    public static boolean hasAreaCastCore(ItemStack staff) {
        ItemStack coreItem = StaffAssemblyData.getCoreItem(staff);
        Optional<CoreData> coreData = CoreRegistry.lookup(coreItem.getItem());
        return coreData.map(data -> AREA_CAST_POWER_ID.equals(data.powerId())).orElse(false);
    }

    public static boolean hasEntityListBindingCore(ItemStack staff) {
        ItemStack coreItem = StaffAssemblyData.getCoreItem(staff);
        Optional<CoreData> coreData = CoreRegistry.lookup(coreItem.getItem());
        return coreData.map(data -> AREA_CAST_POWER_ID.equals(data.powerId()) || BEAM_CAST_POWER_ID.equals(data.powerId())).orElse(false);
    }

    public static boolean hasHexiconCore(ItemStack staff) {
        ItemStack coreItem = StaffAssemblyData.getCoreItem(staff);
        Optional<CoreData> coreData = CoreRegistry.lookup(coreItem.getItem());
        return coreData.map(data -> HEXICON_POWER_ID.equals(data.powerId())).orElse(false);
    }

    public static boolean hasAmethystCore(ItemStack staff) {
        ItemStack coreItem = StaffAssemblyData.getCoreItem(staff);
        Optional<CoreData> coreData = CoreRegistry.lookup(coreItem.getItem());
        return coreData.map(data -> MINOR_AMPLIFY_POWER_ID.equals(data.powerId())).orElse(false);
    }

    public static ItemStack getCastingStaff(CastingEnvironment env) {
        if (!(env instanceof at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv)) {
            return ItemStack.EMPTY;
        }
        var living = env.getCastingEntity();
        ItemStack held = living.getItemInHand(env.getCastingHand());
        return held.is(HexwrightItems.CONFIGURABLE_STAFF) ? held : ItemStack.EMPTY;
    }

    private static List<HexPattern> decodePatternPayload(Iota datum) {
        if (datum instanceof PatternIota patternIota) {
            return List.of(patternIota.getPattern());
        }

        if (!(datum instanceof ListIota listIota)) {
            return null;
        }

        List<HexPattern> out = new ArrayList<>();
        for (Iota entry : listIota.getList()) {
            if (!(entry instanceof PatternIota patternIota)) {
                return null;
            }
            out.add(patternIota.getPattern());
        }

        return out;
    }

    public static ListIota buildEntityBuckets(Level level, AABB scanBox, @Nullable Entity excludeFromPlayers) {
        List<Iota> mobs = level.getEntitiesOfClass(Mob.class, scanBox, mob -> inScan(scanBox, mob) && mob.isAlive() && !(mob instanceof Animal))
            .stream()
            .map(EntityIota::new)
            .collect(Collectors.toList());

        List<Iota> animals = level.getEntitiesOfClass(Animal.class, scanBox, animal -> inScan(scanBox, animal) && animal.isAlive())
            .stream()
            .map(EntityIota::new)
            .collect(Collectors.toList());

        List<Iota> items = level.getEntitiesOfClass(ItemEntity.class, scanBox, item -> inScan(scanBox, item) && item.isAlive())
            .stream()
            .map(EntityIota::new)
            .collect(Collectors.toList());

        List<Iota> players = level.getEntitiesOfClass(Player.class, scanBox, player -> inScan(scanBox, player) && player.isAlive() && !player.isSpectator() && player != excludeFromPlayers)
            .stream()
            .map(EntityIota::new)
            .collect(Collectors.toList());

        return new ListIota(List.of(
            new ListIota(mobs),
            new ListIota(animals),
            new ListIota(items),
            new ListIota(players)
        ));
    }

    public static ListIota buildImpactPayload(Level level, Vec3 impact, AABB scanBox, Collection<Entity> excluded) {
        List<Iota> caught = level.getEntities((Entity) null, scanBox, entity ->
                inScan(scanBox, entity)
                    && entity.isAlive()
                    && !excluded.contains(entity)
                    && !(entity instanceof Player player && player.isSpectator()))
            .stream()
            .map(EntityIota::new)
            .collect(Collectors.toList());

        return new ListIota(List.of(
            new Vec3Iota(impact),
            new ListIota(caught)
        ));
    }

    private static boolean inScan(AABB scanBox, Entity entity) {
        return scanBox.contains(entity.position());
    }
}
