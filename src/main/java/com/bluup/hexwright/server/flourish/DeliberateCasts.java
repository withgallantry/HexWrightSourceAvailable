package com.bluup.hexwright.server.flourish;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.env.PackagedItemCastEnv;
import at.petrak.hexcasting.api.casting.eval.env.StaffCastEnv;
import com.bluup.hexwright.server.pocketcaster.PocketCasterCastEnv;
import com.bluup.hexwright.server.staff_assembly.StaffPowerCastEnv;

import java.util.Set;

final class DeliberateCasts {

    private static final Set<String> ADDON_ENVIRONMENTS = Set.of(
        "miyucomics.hexical.features.charms.CharmCastEnv",
        "com.hakimen.hex_machina.common.hex.envs.GunEnv",
        "com.hakimen.hex_machina.common.hex.envs.BulletProjectileEnv"
    );

    private DeliberateCasts() {
    }

    static boolean isDeliberate(CastingEnvironment env) {
        if (env instanceof PocketCasterCastEnv pocket) {
            return pocket.deliberate();
        }
        Class<?> type = env.getClass();
        return type == StaffCastEnv.class
            || type == PackagedItemCastEnv.class
            || type == StaffPowerCastEnv.class
            || ADDON_ENVIRONMENTS.contains(type.getName());
    }
}
