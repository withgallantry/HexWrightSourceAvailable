package com.bluup.hexwright.client.weapon;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

@Environment(EnvType.CLIENT)
public final class FlipbookVfx implements GeoAnimatable {

    private static final String CONTROLLER = "flipbook";

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private RawAnimation animation;
    private String clip;

    int frame;

    FlipbookVfx(String clip) {
        this.clip = clip;
        this.animation = hold(clip);
    }

    void play(String clip) {
        if (!clip.equals(this.clip)) {
            this.clip = clip;
            this.animation = hold(clip);
        }
    }

    private static RawAnimation hold(String clip) {
        return RawAnimation.begin().thenPlayAndHold(clip);
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
