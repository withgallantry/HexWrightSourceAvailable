package com.bluup.hexwright.mixin;

import com.bluup.hexwright.client.armour.PassagePortalVisualClient;
import com.bluup.hexwright.client.portal.PortalViewRenderer;
import com.bluup.hexwright.client.portal.PortalFold;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraPortalMixin {

    @Inject(method = "setup", at = @At("TAIL"))
    private void hexwright$foldThroughPortal(BlockGetter level, Entity entity, boolean detached,
                                             boolean mirrored, float partialTick, CallbackInfo ci) {
        Camera self = (Camera) (Object) this;
        Vec3 passage = PassagePortalVisualClient.cameraOffset(entity);
        if (passage != null) {
            ((CameraAccessor) self).hexwright$setPosition(self.getPosition().add(passage));
        }
        PortalFold transform = PortalViewRenderer.cameraTransform();
        if (transform == null) {
            PortalViewRenderer.recordUnfoldedCamera(self);
            com.bluup.hexwright.client.portal.ArrivalTrace.frame(self, entity, partialTick);
            return;
        }
        CameraAccessor access = (CameraAccessor) self;
        access.hexwright$setPosition(transform.apply(self.getPosition()));
        access.hexwright$setRotation(
            Mth.wrapDegrees(self.getYRot() + transform.yawDeltaDegrees()), self.getXRot());
    }
}
