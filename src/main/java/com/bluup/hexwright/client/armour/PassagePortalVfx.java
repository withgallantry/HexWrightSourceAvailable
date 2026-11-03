package com.bluup.hexwright.client.armour;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

@Environment(EnvType.CLIENT)
public final class PassagePortalVfx implements GeoAnimatable {

    private static final String CONTROLLER = "portal";

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final RawAnimation animation;

    final ResourceLocation skin;

    int light;

    boolean showStandIn = true;

    PassagePortalVfx(boolean arriving, ResourceLocation skin) {
        this.animation = RawAnimation.begin().thenPlay(arriving ? "join" : "leave");
        this.skin = skin;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, CONTROLLER, 0,
            state -> state.setAndContinue(this.animation)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    @Override
    public double getTick(Object object) {
        return 0;
    }
}
