package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.media.MediaGrantHolder;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Entity.class)
public abstract class EntityMediaGrantMixin implements MediaGrantHolder {

    @Unique
    private long hexwright$mediaGrant;

    @Override
    public long hexwright$mediaGrant() {
        return this.hexwright$mediaGrant;
    }

    @Override
    public void hexwright$setMediaGrant(long media) {
        this.hexwright$mediaGrant = Math.max(0L, media);
    }
}
