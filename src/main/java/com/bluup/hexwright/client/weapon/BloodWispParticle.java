package com.bluup.hexwright.client.weapon;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import org.joml.Vector3d;

@Environment(EnvType.CLIENT)
public class BloodWispParticle extends NoRenderParticle {

    private static final int MOTES_PER_TICK = 3;
    private static final double DRAG = 0.95D;
    private static final double LIFT = 0.004D;
    private static final float TIGHTENING = 2.0F;
    private static final float SWELL_TICKS = 4.0F;
    private static final float THINNEST = 0.3F;

    private final SpriteSet sprites;
    private final Vector3d velocity;
    private final Vector3d axis;
    private final float spin;
    private final float width;

    BloodWispParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz,
                      SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.lifetime = 24 + this.random.nextInt(16);
        this.velocity = new Vector3d(dx, dy, dz);
        this.axis = this.velocity.cross(this.random.nextGaussian(), this.random.nextGaussian(),
            this.random.nextGaussian(), new Vector3d());
        if (this.axis.lengthSquared() < 1.0E-8D) {
            this.axis.set(0.0D, 1.0D, 0.0D);
        }
        this.axis.normalize();
        this.spin = (0.1F + this.random.nextFloat() * 0.16F) * (this.random.nextBoolean() ? 1.0F : -1.0F);
        this.width = 0.07F + this.random.nextFloat() * 0.04F;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        float life = this.age / (float) this.lifetime;
        this.velocity.rotateAxis(this.spin * (1.0F + TIGHTENING * life), this.axis.x, this.axis.y, this.axis.z)
            .mul(DRAG);
        this.velocity.y += LIFT;
        this.setPos(this.x + this.velocity.x, this.y + this.velocity.y, this.z + this.velocity.z);

        float size = this.width * Math.min(1.0F, this.age / SWELL_TICKS) * Mth.lerp(life, 1.0F, THINNEST);
        for (int n = 1; n <= MOTES_PER_TICK; n++) {
            float along = n / (float) MOTES_PER_TICK;
            Minecraft.getInstance().particleEngine.add(new Mote(this.level, Mth.lerp(along, this.xo, this.x),
                Mth.lerp(along, this.yo, this.y), Mth.lerp(along, this.zo, this.z), this.sprites, size));
        }
    }

    static final class Mote extends TextureSheetParticle {

        private static final float HOT_RED = 1.0F;
        private static final float HOT_GREEN = 0.16F;
        private static final float HOT_BLUE = 0.22F;
        private static final float COLD_RED = 0.42F;
        private static final float COLD_GREEN = 0.0F;
        private static final float COLD_BLUE = 0.05F;
        private static final float ALPHA = 0.9F;

        private final SpriteSet sprites;
        private final float size;

        Mote(ClientLevel level, double x, double y, double z, SpriteSet sprites, float size) {
            super(level, x, y, z);
            this.sprites = sprites;
            this.size = size;
            this.quadSize = size;
            this.lifetime = 14 + this.random.nextInt(10);
            this.hasPhysics = false;
            this.friction = 0.9F;
            this.xd = this.random.nextGaussian() * 0.004D;
            this.yd = 0.006D;
            this.zd = this.random.nextGaussian() * 0.004D;
            this.setColor(HOT_RED, HOT_GREEN, HOT_BLUE);
            this.setAlpha(ALPHA);
            this.setSpriteFromAge(sprites);
        }

        @Override
        public void tick() {
            super.tick();
            if (this.removed) {
                return;
            }
            float life = this.age / (float) this.lifetime;
            this.setSpriteFromAge(this.sprites);
            this.quadSize = this.size * (1.0F - life * life);
            this.setColor(Mth.lerp(life, HOT_RED, COLD_RED), Mth.lerp(life, HOT_GREEN, COLD_GREEN),
                Mth.lerp(life, HOT_BLUE, COLD_BLUE));
            this.setAlpha(ALPHA * (1.0F - life));
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        protected int getLightColor(float partialTick) {
            return 0xF000F0;
        }
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new BloodWispParticle(level, x, y, z, dx, dy, dz, this.sprites);
        }
    }
}
