package com.bluup.hexwright.mixin;

import com.lowdragmc.photon.client.gameobject.particle.TileParticle;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = TileParticle.class, remap = false)
public interface TileParticleAccessor {

    @Accessor("localX")
    float hexwright$getLocalX();

    @Accessor("localY")
    float hexwright$getLocalY();

    @Accessor("localZ")
    float hexwright$getLocalZ();

    @Accessor("velocityX")
    float hexwright$getVelocityX();

    @Accessor("velocityY")
    float hexwright$getVelocityY();

    @Accessor("velocityZ")
    float hexwright$getVelocityZ();

    @Accessor("velocityX")
    void hexwright$setVelocityX(float value);

    @Accessor("velocityY")
    void hexwright$setVelocityY(float value);

    @Accessor("velocityZ")
    void hexwright$setVelocityZ(float value);

    @Accessor("initialSize")
    Vector3f hexwright$getInitialSize();

    @Accessor("initialTransform")
    Matrix4f hexwright$getInitialTransform();

    @Accessor("initialTransformInverse")
    Matrix4f hexwright$getInitialTransformInverse();
}
