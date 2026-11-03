package com.bluup.hexwright.compat.create;

import com.bluup.hexwright.server.worldgen.AreaWard;
import com.simibubi.create.api.contraption.BlockMovementChecks;

final class CreateMovementChecks {

    private CreateMovementChecks() {
    }

    static void register() {
        BlockMovementChecks.registerMovementAllowedCheck((state, level, pos) -> AreaWard.fixedLoot(level, pos)
            ? BlockMovementChecks.CheckResult.FAIL
            : BlockMovementChecks.CheckResult.PASS);
    }
}
