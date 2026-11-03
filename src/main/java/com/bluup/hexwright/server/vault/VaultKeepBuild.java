package com.bluup.hexwright.server.vault;

import com.bluup.hexwright.Hexwright;
import net.minecraft.world.level.block.Rotation;

public final class VaultKeepBuild extends VaultSchematicBuild {

    public VaultKeepBuild() {
        super(Hexwright.id("vault/medieval_keep"), Rotation.CLOCKWISE_180,
            33, 34, 16, 0, 57, 64, 14);
    }

    @Override
    public String id() {
        return "medieval_keep";
    }
}
