package com.bluup.hexwright.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraAccessor {

    @Accessor("eyeHeight")
    float hexwright$getEyeHeight();

    @Accessor("eyeHeightOld")
    float hexwright$getOldEyeHeight();

    @Accessor("eyeHeight")
    void hexwright$setEyeHeight(float height);

    @Accessor("eyeHeightOld")
    void hexwright$setOldEyeHeight(float height);

    @Invoker("setPosition")
    void hexwright$setPosition(Vec3 position);

    @Invoker("setRotation")
    void hexwright$setRotation(float yRot, float xRot);
}
