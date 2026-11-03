package com.bluup.hexwright.server.combat;

import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.damagesource.DamageSource;

public final class DamagePolicy {

    public static final double NORMAL_PROJECTILE_SPEED = 4.0D;

    private DamagePolicy() {
    }

    public static float normaliseProjectile(DamageSource source, float amount) {
        if (!(source.getDirectEntity() instanceof Projectile projectile)) {
            return amount;
        }
        double speed = projectile.getDeltaMovement().length();
        if (speed <= NORMAL_PROJECTILE_SPEED) {
            return amount;
        }
        return (float) (amount * (NORMAL_PROJECTILE_SPEED / speed));
    }

    public static final class Window {

        private static final int WINDOW_TICKS = 20;

        private boolean open;
        private long start;
        private float spent;

        public float remaining(long now, float perSecondCap) {
            if (!this.open || now < this.start || now - this.start >= WINDOW_TICKS) {
                this.open = true;
                this.start = now;
                this.spent = 0.0F;
            }
            return Math.max(0.0F, perSecondCap - this.spent);
        }

        public void spend(float healthLost) {
            if (healthLost > 0.0F) {
                this.spent += healthLost;
            }
        }
    }
}
