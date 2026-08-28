package com.bluup.hexwright.server.vault;

import com.bluup.hexwright.Hexwright;
import net.minecraft.world.level.block.Rotation;

public final class VaultCottageBuild extends VaultSchematicBuild {

    public VaultCottageBuild() {
        super(Hexwright.id("vault/cottage"), Rotation.COUNTERCLOCKWISE_90,
            39, 29, 12, 4, 49, 64, 14);
    }

    @Override
    public String id() {
        return "cottage";
    }
}
