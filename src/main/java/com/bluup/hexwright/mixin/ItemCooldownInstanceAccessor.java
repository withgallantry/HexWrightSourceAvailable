package com.bluup.hexwright.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.item.ItemCooldowns$CooldownInstance")
public interface ItemCooldownInstanceAccessor {

    @Accessor("startTime")
    int hexwright$startTime();

    @Accessor("endTime")
    int hexwright$endTime();
}
