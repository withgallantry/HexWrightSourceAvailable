package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.server.boss.Bosses;
import com.bluup.hexwright.server.powerorb.OrbConstructEntity;
import com.bluup.hexwright.server.staff_assembly.HexwrightEntities;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class RaiseDead {

    public static final int MAX_THRALLS = 3;

    private RaiseDead() {
    }

    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
            if (!(source.getEntity() instanceof ServerPlayer killer)) {
                return;
            }
            ItemStack sword = killer.getMainHandItem();
            if (!(sword.getItem() instanceof NecromancerSwordItem item)) {
                return;
            }
            if (raisable(victim)) {
                raise(killer, victim, item.thrallHex(sword));
            }
        });
    }

    private static boolean raisable(LivingEntity victim) {
        return victim instanceof Mob && !(victim instanceof OrbConstructEntity) && !Bosses.is(victim);
    }

    private static void raise(ServerPlayer master, LivingEntity victim, @Nullable CompoundTag hex) {
        ServerLevel level = master.serverLevel();
        if (victim.level() != level) {
            return;
        }
        List<RisenThrallEntity> held = new ArrayList<>(level.getEntities(HexwrightEntities.RISEN_THRALL,
            thrall -> thrall.isAlive() && master.getUUID().equals(thrall.getOwnerUUID())));
        held.sort(Comparator.comparingInt(thrall -> thrall.tickCount));
        for (int i = MAX_THRALLS - 1; i < held.size(); i++) {
            held.get(i).dismiss();
        }
        RisenThrallEntity thrall = RisenThrallEntity.raise(level, master, victim.position());
        if (thrall != null && hex != null) {
            WeaponHexCasting.castOnTarget(master, hex, thrall, thrall.position());
        }
    }
}
