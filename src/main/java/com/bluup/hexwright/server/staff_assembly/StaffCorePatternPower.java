package com.bluup.hexwright.server.staff_assembly;

import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.env.PlayerBasedCastEnv;
import com.bluup.hexwright.common.staff_assembly.calc.CoreData;
import com.bluup.hexwright.common.staff_assembly.calc.CoreRegistry;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.item.HexwrightItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public final class StaffCorePatternPower {
    public static final String FREE_GREATER_TELEPORT_POWER_ID = "traveller_greater_teleport";

    private StaffCorePatternPower() {
    }

    public static void onTravellerTeleportCast(CastingEnvironment env, Entity teleportee, Vec3 from) {
        if (from == null || !(teleportee instanceof ServerPlayer player) || env.getCastingEntity() != teleportee) {
            return;
        }

        if (findTravellerStaff(env).isEmpty()) {
            return;
        }

        Vec3 to = teleportee.position();
        if (from.distanceToSqr(to) < 0.01) {
            return;
        }

        HexwrightNetworking.sendStaffTravellerWarp(player, from, to);
    }

    public static boolean travellerBenefitsApply(CastingEnvironment env, Entity teleportee) {
        if (teleportee == null || findTravellerStaff(env).isEmpty()) {
            return false;
        }

        return teleportee == env.getCastingEntity() || teleportee instanceof ItemEntity;
    }

    public static SpellAction.Result discountGreaterTeleport(CastingEnvironment env,
                                                             SpellAction.Result result,
                                                             Entity teleportee) {
        if (result == null || result.getCost() <= 0L || !travellerBenefitsApply(env, teleportee)) {
            return result;
        }

        ItemStack staff = findTravellerStaff(env);
        double discount = StaffCoreData.gradeFraction(StaffCoreData.getQuality(StaffAssemblyData.getCoreItem(staff)));
        long cost = result.getCost();
        long discounted = Math.max(0L, cost - Math.round(cost * discount));
        if (discounted == cost) {
            return result;
        }
        return new SpellAction.Result(result.getEffect(), discounted, result.getParticles(), result.getOpCount());
    }

    private static ItemStack findTravellerStaff(CastingEnvironment env) {
        if (!(env instanceof PlayerBasedCastEnv)) {
            return ItemStack.EMPTY;
        }
        LivingEntity caster = env.getCastingEntity();
        for (InteractionHand hand : new InteractionHand[] {env.getCastingHand(), env.getOtherHand()}) {
            ItemStack held = caster.getItemInHand(hand);
            if (hasTravellerCorePower(held)) {
                return held;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean hasTravellerCorePower(ItemStack staff) {
        if (!staff.is(HexwrightItems.CONFIGURABLE_STAFF)) {
            return false;
        }
        ItemStack coreItem = StaffAssemblyData.getCoreItem(staff);
        Optional<CoreData> coreData = CoreRegistry.lookup(coreItem.getItem());
        return coreData.map(data -> FREE_GREATER_TELEPORT_POWER_ID.equals(data.powerId())).orElse(false);
    }
}
