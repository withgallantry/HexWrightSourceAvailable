package com.bluup.hexwright.server.vault;

import com.bluup.hexwright.Hexwright;
import net.minecraft.world.level.block.Rotation;

public final class VaultStarterHouseBuild extends VaultSchematicBuild {

    public VaultStarterHouseBuild() {
        super(Hexwright.id("vault/starter_house"), Rotation.CLOCKWISE_180,
            52, 77, 26, 0, 62, 96, 10);
    }

    @Override
    public String id() {
        return "starter_house";
    }
}
