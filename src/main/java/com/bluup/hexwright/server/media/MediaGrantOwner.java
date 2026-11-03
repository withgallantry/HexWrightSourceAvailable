package com.bluup.hexwright.server.media;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

public interface MediaGrantOwner {

    default @Nullable BlockPos hexwright$grantPos() {
        return null;
    }

    default @Nullable Entity hexwright$grantEntity() {
        return null;
    }
}
