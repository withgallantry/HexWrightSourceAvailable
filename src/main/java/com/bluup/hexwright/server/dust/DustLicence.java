package com.bluup.hexwright.server.dust;

import com.bluup.hexwright.server.powerorb.PowerOrbPower;
import com.bluup.hexwright.server.powerorb.PowerOrbSlot;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

public final class DustLicence {

    private DustLicence() {
    }

    public static boolean licensed(@Nullable LivingEntity caster) {
        return !PowerOrbSlot.worn(caster, PowerOrbPower.DUST).isEmpty();
    }
}
