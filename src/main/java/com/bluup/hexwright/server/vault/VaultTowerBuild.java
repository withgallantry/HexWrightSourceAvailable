package com.bluup.hexwright.server.vault;

import com.bluup.hexwright.Hexwright;
import net.minecraft.world.level.block.Rotation;

public final class VaultTowerBuild extends VaultSchematicBuild {

    public VaultTowerBuild() {
        super(Hexwright.id("vault/white_tower"), Rotation.CLOCKWISE_180,
            33, 28, 20, 11, 63, 64, 14);
    }

    @Override
    public String id() {
        return "white_tower";
    }
}
