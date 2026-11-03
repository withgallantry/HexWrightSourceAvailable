package com.bluup.hexwright.client.armour;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

@Environment(EnvType.CLIENT)
public final class PassageStandInPose {

    static final String BODY = "pbody_body";

    @Nullable
    public static PassageStandInPose active;

    private final Limb head;
    private final Limb rightArm;
    private final Limb leftArm;
    private final Limb rightLeg;
    private final Limb leftLeg;

    private PassageStandInPose(GeoModel<?> rig) {
        this.head = Limb.of(rig, "h_phead_head");
        this.rightArm = Limb.of(rig, "prarm_right_arm");
        this.leftArm = Limb.of(rig, "plarm_left_arm");
        this.rightLeg = Limb.of(rig, "prleg_right_leg");
        this.leftLeg = Limb.of(rig, "plleg_left_leg");
    }

    static PassageStandInPose capture(GeoModel<?> rig) {
        return new PassageStandInPose(rig);
    }

    public void apply(HumanoidModel<?> model) {
        model.body.resetPose();
        head.apply(model.head);
        model.hat.copyFrom(model.head);
        rightArm.apply(model.rightArm);
        leftArm.apply(model.leftArm);
        rightLeg.apply(model.rightLeg);
        leftLeg.apply(model.leftLeg);
    }

    private record Limb(float posX, float posY, float posZ, float rotX, float rotY, float rotZ) {
        private static final Limb REST = new Limb(0, 0, 0, 0, 0, 0);

        static Limb of(GeoModel<?> rig, String name) {
            GeoBone bone = rig.getBone(name).orElse(null);
            return bone == null ? REST : new Limb(bone.getPosX(), bone.getPosY(), bone.getPosZ(),
                bone.getRotX(), bone.getRotY(), bone.getRotZ());
        }

        void apply(ModelPart part) {
            part.resetPose();
            part.x += posX;
            part.y -= posY;
            part.z += posZ;
            part.xRot = -rotX;
            part.yRot = -rotY;
            part.zRot = rotZ;
        }
    }
}
