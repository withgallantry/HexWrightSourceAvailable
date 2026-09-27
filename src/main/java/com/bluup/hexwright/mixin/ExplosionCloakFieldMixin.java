package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.cloak.CloakFieldWear;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Explosion.class)
public abstract class ExplosionCloakFieldMixin {

    @Shadow
    @Final
    private Level level;

    @Shadow
    @Final
    private double x;

    @Shadow
    @Final
    private double y;

    @Shadow
    @Final
    private double z;

    @Shadow
    public float radius;

    @Shadow
    @Nullable
    public abstract LivingEntity getIndirectSourceEntity();

    @Inject(method = "explode", at = @At("HEAD"))
    private void hexwright$wearCloakField(CallbackInfo ci) {
        if (this.level instanceof ServerLevel server) {
            CloakFieldWear.blast(server, new Vec3(this.x, this.y, this.z), this.radius, this.getIndirectSourceEntity());
        }
    }
}
