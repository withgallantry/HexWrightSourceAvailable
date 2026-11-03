package com.bluup.hexwright.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Entity.class)
public interface EntityLevelAccessor {

    @Invoker("setLevel")
    void hexwright$setLevel(Level level);
}
