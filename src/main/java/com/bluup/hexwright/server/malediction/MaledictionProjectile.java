package com.bluup.hexwright.server.malediction;

import at.petrak.hexcasting.common.particles.ConjureParticleOptions;
import com.bluup.hexwright.common.malediction.MaledictionFinish;
import com.bluup.hexwright.common.malediction.MaledictionForm;
import com.bluup.hexwright.common.malediction.MaledictionSigil;
import com.bluup.hexwright.server.path.Polyline;
import com.bluup.hexwright.server.staff_assembly.HexwrightEntities;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class MaledictionProjectile extends Projectile {

    private static final EntityDataAccessor<Byte> FINISH =
        SynchedEntityData.defineId(MaledictionProjectile.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> FORM =
        SynchedEntityData.defineId(MaledictionProjectile.class, EntityDataSerializers.BYTE);

    private static final double SEEKER_TURN = 0.18;
    public static final double THROW_BURST = 1.5;
    private static final double TRAIL_SPARKS_PER_BLOCK = 3.0;

    private enum Mode { STRAIGHT, SEEK, PATH }

    private Mode mode = Mode.STRAIGHT;
    private MaledictionForm form = MaledictionForm.BOLT;
    private MaledictionFinish finish = MaledictionFinish.SEARING;
    private float damage;
    private double speed;
    private int maxTicks;
    private double burst;
    private @Nullable Polyline path;
    private double travelled;
    private @Nullable UUID prey;
    private @Nullable Vec3 trailFrom;

    public MaledictionProjectile(EntityType<? extends MaledictionProjectile> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    private MaledictionProjectile(ServerLevel level, ServerPlayer caster, MaledictionForm form, MaledictionFinish finish,
                                  float damage, double speed, int maxTicks, Vec3 start) {
        this(HexwrightEntities.MALEDICTION_PROJECTILE, level);
        setOwner(caster);
        this.form = form;
        this.finish = finish;
        this.damage = damage;
        this.speed = speed;
        this.maxTicks = maxTicks;
        this.entityData.set(FINISH, (byte) finish.ordinal());
        this.entityData.set(FORM, (byte) form.ordinal());
        setPos(start);
    }

    public static void straight(ServerLevel level, ServerPlayer caster, MaledictionForm form, MaledictionFinish finish,
                                float damage, Vec3 start, Vec3 direction, double speed, double range) {
        MaledictionProjectile projectile = new MaledictionProjectile(level, caster, form, finish, damage, speed,
            (int) Math.ceil(range / speed), start);
        projectile.setDeltaMovement(direction.normalize().scale(speed));
        level.addFreshEntity(projectile);
    }

    public static void seek(ServerLevel level, ServerPlayer caster, MaledictionFinish finish, float damage, Vec3 start,
                            Entity prey, double speed, int maxTicks) {
        MaledictionProjectile projectile = new MaledictionProjectile(level, caster, MaledictionForm.SEEKER, finish, damage,
            speed, maxTicks, start);
        projectile.mode = Mode.SEEK;
        projectile.prey = prey.getUUID();
        projectile.setDeltaMovement(prey.getBoundingBox().getCenter().subtract(start).normalize().scale(speed));
        level.addFreshEntity(projectile);
    }

    public static void along(ServerLevel level, ServerPlayer caster, MaledictionForm form, MaledictionFinish finish,
                             float damage, Polyline path, double speed, double burst) {
        MaledictionProjectile projectile = new MaledictionProjectile(level, caster, form, finish, damage, speed,
            (int) Math.ceil(path.length() / speed) + 1, path.at(0.0));
        projectile.mode = Mode.PATH;
        projectile.path = path;
        projectile.burst = burst;
        level.addFreshEntity(projectile);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(FINISH, (byte) 0);
        this.entityData.define(FORM, (byte) 0);
    }

    public MaledictionForm form() {
        return MaledictionForm.values()[Math.floorMod(entityData.get(FORM), MaledictionForm.values().length)];
    }

    public MaledictionFinish shownFinish() {
        return MaledictionFinish.values()[Math.floorMod(entityData.get(FINISH), MaledictionFinish.values().length)];
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            trail();
            return;
        }
        if (!(getOwner() instanceof ServerPlayer caster) || !caster.isAlive() || tickCount > maxTicks) {
            land(position(), null);
            return;
        }

        Vec3 from = position();
        Vec3 to = next(from);
        boolean pathEnds = mode == Mode.PATH && path != null && travelled >= path.length();
        HitResult blockHit = level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        if (blockHit.getType() != HitResult.Type.MISS) {
            to = blockHit.getLocation();
        }
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level(), this, from, to,
            getBoundingBox().expandTowards(to.subtract(from)).inflate(1.0), this::canHitEntity);
        if (entityHit != null) {
            Entity hit = entityHit.getEntity();
            Vec3 at = hit.getBoundingBox().clip(from, to).orElse(hit.getBoundingBox().getCenter());
            land(at, hit instanceof LivingEntity living ? living : null);
        } else if (blockHit.getType() != HitResult.Type.MISS || pathEnds) {
            land(to, null);
        } else {
            setDeltaMovement(to.subtract(from));
            setPos(to);
        }
    }

    private Vec3 next(Vec3 from) {
        if (mode == Mode.PATH && path != null) {
            travelled += speed;
            return path.at(travelled);
        }
        if (mode == Mode.SEEK && prey != null && level() instanceof ServerLevel level) {
            Entity target = level.getEntity(prey);
            if (target != null && target.isAlive()) {
                Vec3 want = target.getBoundingBox().getCenter().subtract(from).normalize();
                Vec3 have = getDeltaMovement().normalize();
                setDeltaMovement(have.lerp(want, SEEKER_TURN).normalize().scale(speed));
            }
        }
        return from.add(getDeltaMovement());
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        return getOwner() instanceof ServerPlayer caster && MaledictionStrike.mayHarm(caster, target);
    }

    private void land(Vec3 at, @Nullable LivingEntity hit) {
        if (getOwner() instanceof ServerPlayer caster && level() instanceof ServerLevel level && caster.isAlive()) {
            if (burst > 0.0) {
                AABB area = new AABB(at, at).inflate(burst);
                for (LivingEntity caught : level.getEntitiesOfClass(LivingEntity.class, area,
                    entity -> entity.getBoundingBox().getCenter().distanceToSqr(at) <= burst * burst || entity == hit)) {
                    MaledictionStrike.strike(caster, caught, damage, finish, this, at);
                }
                MaledictionFx.burst(level, at, finish, 24, burst * 0.5);
                MaledictionFx.sigil(level, MaledictionSigil.Kind.BURST, finish, at, (float) burst);
                MaledictionFx.sound(level, at, SoundEvents.FIRECHARGE_USE, 0.6F, 1.5F);
            } else if (hit != null) {
                MaledictionStrike.strike(caster, hit, damage, finish, this, at);
                MaledictionFx.sigil(level, MaledictionSigil.Kind.IMPACT, finish, at,
                    form == MaledictionForm.VOLLEY ? 0.5F : 0.8F);
                MaledictionFx.sound(level, at, SoundEvents.AMETHYST_BLOCK_BREAK, 0.8F, 1.3F);
            } else {
                MaledictionFx.burst(level, at, finish, 6, 0.15);
                MaledictionFx.sigil(level, MaledictionSigil.Kind.IMPACT, finish, at, 0.4F);
            }
        }
        discard();
    }

    private void trail() {
        Vec3 now = position();
        Vec3 from = trailFrom == null ? now : trailFrom;
        trailFrom = now;
        ConjureParticleOptions spark = new ConjureParticleOptions(shownFinish().colour());
        int steps = Math.max(1, (int) Math.ceil(from.distanceTo(now) * TRAIL_SPARKS_PER_BLOCK));
        for (int i = 1; i <= steps; i++) {
            Vec3 at = from.lerp(now, i / (double) steps);
            level().addParticle(spark, at.x, at.y, at.z, 0.0, 0.0, 0.0);
        }
    }
}
