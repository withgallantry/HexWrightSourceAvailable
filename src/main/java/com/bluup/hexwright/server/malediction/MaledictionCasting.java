package com.bluup.hexwright.server.malediction;

import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.iota.EntityIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.Vec3Iota;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadLocation;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import com.bluup.hexwright.common.malediction.MaledictionFinish;
import com.bluup.hexwright.common.malediction.MaledictionForm;
import com.bluup.hexwright.common.malediction.MaledictionSigil;
import com.bluup.hexwright.server.path.Path;
import com.bluup.hexwright.server.path.PathActions;
import com.bluup.hexwright.server.path.PathIota;
import com.bluup.hexwright.server.path.Polyline;
import com.bluup.hexwright.server.region.HexpanseBridge;
import com.bluup.hexwright.server.region.Region;
import com.bluup.hexwright.server.region.RegionAmbit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class MaledictionCasting {

    private static final double DEFAULT_RADIUS = 2.5;
    private static final double SNARE_RADIUS = 1.5;
    public static final double MAX_PATH_LENGTH = 96.0;
    private static final double BOLT_SPEED = 1.6;
    private static final double BOLT_RANGE = 64.0;
    private static final double PATH_SPEED = 1.2;
    private static final double SEEKER_SPEED = 0.6;
    private static final int SEEKER_TICKS = 200;
    private static final double LANCE_RANGE = 32.0;
    private static final int LANCE_PIERCE = 4;
    private static final double LANCE_CIRCLE_OUT = 1.3;
    private static final double GRASP_REACH = 3.5;
    private static final int VOLLEY_BOLTS = 5;
    private static final double VOLLEY_SPREAD = Math.toRadians(7.5);
    private static final double VOLLEY_RANGE = 32.0;
    private static final double CHAIN_REACH = 4.0;
    private static final int CHAIN_JUMPS = 4;
    private static final float CHAIN_FALLOFF = 0.7F;
    private static final double SWEEP_RADIUS = 1.0;
    private static final int SWEEP_PULSES = 3;
    private static final int SWEEP_INTERVAL = 20;

    @FunctionalInterface
    public interface Delivery {
        void deliver(ServerLevel level, ServerPlayer caster);
    }

    private MaledictionCasting() {
    }

    public static Delivery prepare(MaledictionForm form, MaledictionFinish finish, Iota target, int reverseIdx,
                                   @Nullable MobEffect status, CastingEnvironment env, ServerPlayer caster) {
        float damage = form.damage();
        return switch (form) {
            case BOLT -> {
                Vec3 aim = aim(target, reverseIdx, env, "hexwright.malediction_aim");
                Vec3 eye = caster.getEyePosition();
                Vec3 direction = direction(eye, aim, caster);
                yield (level, who) -> {
                    MaledictionProjectile.straight(level, who, form, finish, damage, who.getEyePosition(), direction,
                        BOLT_SPEED, BOLT_RANGE);
                    MaledictionFx.sound(level, eye, SoundEvents.ILLUSIONER_CAST_SPELL, 0.6F, 1.6F);
                };
            }
            case VOLLEY -> {
                Vec3 aim = aim(target, reverseIdx, env, "hexwright.malediction_aim");
                Vec3 direction = direction(caster.getEyePosition(), aim, caster);
                yield (level, who) -> volley(level, who, finish, damage, direction);
            }
            case LANCE -> {
                Vec3 aim = aim(target, reverseIdx, env, "hexwright.malediction_aim");
                Vec3 direction = direction(caster.getEyePosition(), aim, caster);
                yield (level, who) -> lance(level, who, finish, damage, direction);
            }
            case GRASP -> {
                LivingEntity prey = preyInSight(target, reverseIdx, env, caster);
                if (!withinReach(caster.getEyePosition(), prey)) {
                    throw new MishapBadLocation(prey.position(), "hexwright_malediction_out_of_reach");
                }
                yield (level, who) -> {
                    MaledictionStrike.strike(who, prey, damage, finish, null, who.getEyePosition());
                    MaledictionFx.sigil(level, MaledictionSigil.Kind.BIND, finish, prey.position(), prey.position(),
                        prey.getBbWidth(), prey);
                    MaledictionFx.sound(level, prey.position(), SoundEvents.EVOKER_FANGS_ATTACK, 0.8F, 1.2F);
                };
            }
            case DOOM -> {
                LivingEntity prey = preyInSight(target, reverseIdx, env, caster);
                yield (level, who) -> MaledictionDooms.doom(level, who, prey, finish, damage, status);
            }
            case SEEKER -> {
                Entity prey = prey(target, reverseIdx, env);
                yield (level, who) -> {
                    MaledictionProjectile.seek(level, who, finish, damage, who.getEyePosition(), prey, SEEKER_SPEED,
                        SEEKER_TICKS);
                    MaledictionFx.sound(level, who.getEyePosition(), SoundEvents.ILLUSIONER_CAST_SPELL, 0.6F, 1.2F);
                };
            }
            case CHAIN -> {
                LivingEntity prey = preyInSight(target, reverseIdx, env, caster);
                yield (level, who) -> chain(level, who, finish, damage, prey);
            }
            case ORBIT -> {
                Entity anchor = target instanceof EntityIota entityIota ? entityIota.getEntity() : null;
                Vec3 point = anchor != null ? anchor.position() : aim(target, reverseIdx, env, "hexwright.malediction_aim");
                if (anchor != null) {
                    env.assertEntityInRange(anchor);
                }
                yield (level, who) -> MaledictionOrbits.orbit(level, who, anchor, point, finish, damage);
            }
            case NOVA, FIELD, CONJUNCTION, SNARE -> {
                Region region = area(form, target, reverseIdx, env);
                float scaled = damage * MaledictionFlares.density(region);
                yield form == MaledictionForm.SNARE
                    ? (level, who) -> MaledictionFlares.snare(level, who, region, finish, scaled)
                    : (level, who) -> MaledictionFlares.flare(level, who, region, finish, scaled, 1, 0);
            }
            case ARC, THROW, THREAD -> {
                Polyline line = path(form, target, reverseIdx, env);
                double burst = form == MaledictionForm.THROW ? MaledictionProjectile.THROW_BURST : 0.0;
                yield (level, who) -> {
                    MaledictionProjectile.along(level, who, form, finish, damage, line, PATH_SPEED, burst);
                    MaledictionFx.sound(level, line.at(0.0), SoundEvents.ILLUSIONER_CAST_SPELL, 0.6F, 1.4F);
                };
            }
            case SWEEP -> {
                path(form, target, reverseIdx, env);
                Path path = ((PathIota) target).getPath();
                Region tube = PathActions.sweep(path, SWEEP_RADIUS);
                float scaled = damage * MaledictionFlares.density(tube);
                yield (level, who) -> MaledictionFlares.flare(level, who, tube, finish, scaled, SWEEP_PULSES, SWEEP_INTERVAL);
            }
        };
    }


    private static Vec3 aim(Iota target, int reverseIdx, CastingEnvironment env, String expected) {
        if (target instanceof Vec3Iota vec) {
            env.assertVecInRange(vec.getVec3());
            return vec.getVec3();
        }
        if (target instanceof EntityIota entityIota) {
            Entity entity = entityIota.getEntity();
            env.assertEntityInRange(entity);
            return entity.getBoundingBox().getCenter();
        }
        throw MishapInvalidIota.ofType(target, reverseIdx, expected);
    }

    private static LivingEntity prey(Iota target, int reverseIdx, CastingEnvironment env) {
        if (target instanceof EntityIota entityIota && entityIota.getEntity() instanceof LivingEntity living) {
            env.assertEntityInRange(living);
            return living;
        }
        throw MishapInvalidIota.ofType(target, reverseIdx, "hexwright.malediction_prey");
    }

    private static LivingEntity preyInSight(Iota target, int reverseIdx, CastingEnvironment env, ServerPlayer caster) {
        LivingEntity prey = prey(target, reverseIdx, env);
        if (!sees(caster, caster.getEyePosition(), prey)) {
            throw new MishapBadLocation(prey.position(), "hexwright_malediction_unseen");
        }
        return prey;
    }

    private static boolean withinReach(Vec3 eye, LivingEntity target) {
        AABB box = target.getBoundingBox();
        Vec3 nearest = new Vec3(Mth.clamp(eye.x, box.minX, box.maxX), Mth.clamp(eye.y, box.minY, box.maxY),
            Mth.clamp(eye.z, box.minZ, box.maxZ));
        return nearest.distanceToSqr(eye) <= GRASP_REACH * GRASP_REACH;
    }

    private static Vec3 direction(Vec3 eye, Vec3 aim, ServerPlayer caster) {
        Vec3 direction = aim.subtract(eye);
        return direction.lengthSqr() < 1.0e-6 ? caster.getLookAngle() : direction.normalize();
    }

    private static Region area(MaledictionForm form, Iota target, int reverseIdx, CastingEnvironment env) {
        Region region = HexpanseBridge.regionOf(target);
        if (region == null) {
            Vec3 at = aim(target, reverseIdx, env, "hexwright.malediction_area." + form.id());
            region = Region.sphere(at, form == MaledictionForm.SNARE ? SNARE_RADIUS : DEFAULT_RADIUS);
        } else {
            boolean allowed = switch (form) {
                case NOVA -> region instanceof Region.Sphere;
                case FIELD, SNARE -> isSingleShape(region);
                default -> true;
            };
            if (!allowed || region.isEmpty()) {
                throw MishapInvalidIota.ofType(target, reverseIdx, "hexwright.malediction_area." + form.id());
            }
        }
        RegionAmbit.assertVolumeReachable(env, region, target, reverseIdx);
        return region;
    }

    private static boolean isSingleShape(Region region) {
        if (region instanceof Region.Transform transform) {
            return isSingleShape(transform.source());
        }
        return region instanceof Region.Sphere || region instanceof Region.Box || region instanceof Region.Cylinder
            || region instanceof Region.Cone || region instanceof Region.Torus || region instanceof Region.Tube;
    }

    private static Polyline path(MaledictionForm form, Iota target, int reverseIdx, CastingEnvironment env) {
        if (!(target instanceof PathIota pathIota)) {
            throw MishapInvalidIota.ofType(target, reverseIdx, "hexwright.malediction_path." + form.id());
        }
        Path path = pathIota.getPath();
        boolean allowed = switch (form) {
            case ARC, THROW -> path instanceof Path.Segment || path instanceof Path.Arc;
            default -> true;
        };
        if (!allowed) {
            throw MishapInvalidIota.ofType(target, reverseIdx, "hexwright.malediction_path." + form.id());
        }
        if (path.length() > MAX_PATH_LENGTH) {
            throw MishapInvalidIota.of(target, reverseIdx, "hexwright.malediction_path_length", (int) MAX_PATH_LENGTH);
        }
        Polyline line = Polyline.of(path, Polyline.FINE_STEP);
        Vec3[] points = line.points();
        for (int i = 0; i < points.length; i += 4) {
            env.assertVecInRange(points[i]);
        }
        env.assertVecInRange(points[points.length - 1]);
        return line;
    }


    private static void volley(ServerLevel level, ServerPlayer caster, MaledictionFinish finish, float damage, Vec3 direction) {
        Vec3 across = direction.cross(new Vec3(0.0, 1.0, 0.0));
        if (across.lengthSqr() < 1.0e-6) {
            across = Vec3.directionFromRotation(0.0F, caster.getYRot() + 90.0F);
        }
        across = across.normalize();
        Vec3 eye = caster.getEyePosition();
        for (int i = 0; i < VOLLEY_BOLTS; i++) {
            double angle = (i - (VOLLEY_BOLTS - 1) / 2.0) * VOLLEY_SPREAD;
            Vec3 heading = direction.scale(Math.cos(angle)).add(across.scale(Math.sin(angle)));
            MaledictionProjectile.straight(level, caster, MaledictionForm.VOLLEY, finish, damage, eye, heading, BOLT_SPEED,
                VOLLEY_RANGE);
        }
        MaledictionFx.sound(level, eye, SoundEvents.ILLUSIONER_CAST_SPELL, 0.7F, 1.9F);
    }

    private static void lance(ServerLevel level, ServerPlayer caster, MaledictionFinish finish, float damage, Vec3 direction) {
        Vec3 from = caster.getEyePosition();
        Vec3 to = from.add(direction.scale(LANCE_RANGE));
        HitResult wall = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        if (wall.getType() != HitResult.Type.MISS) {
            to = wall.getLocation();
        }
        Vec3 end = to;
        List<LivingEntity> struck = new ArrayList<>();
        for (Entity entity : level.getEntities(caster, new AABB(from, end).inflate(1.0),
            entity -> MaledictionStrike.mayHarm(caster, entity)
                && entity.getBoundingBox().inflate(0.3).clip(from, end).isPresent())) {
            struck.add((LivingEntity) entity);
        }
        struck.sort(Comparator.comparingDouble(entity -> entity.distanceToSqr(from)));
        for (int i = 0; i < Math.min(LANCE_PIERCE, struck.size()); i++) {
            MaledictionStrike.strike(caster, struck.get(i), damage, finish, null, from);
        }
        MaledictionFx.sigil(level, MaledictionSigil.Kind.LANCE, finish, from.add(direction.scale(LANCE_CIRCLE_OUT)), end, 0.0F, null);
        MaledictionFx.sound(level, from, SoundEvents.TRIDENT_THROW, 0.8F, 1.5F);
    }

    private static void chain(ServerLevel level, ServerPlayer caster, MaledictionFinish finish, float damage,
                              LivingEntity first) {
        Set<LivingEntity> hit = new HashSet<>();
        LivingEntity current = first;
        Vec3 from = caster.getEyePosition();
        float blow = damage;
        for (int jump = 0; jump <= CHAIN_JUMPS && current != null; jump++) {
            Vec3 to = current.getBoundingBox().getCenter();
            MaledictionFx.sigil(level, MaledictionSigil.Kind.LIGHTNING, finish, from, to, 0.0F, null);
            MaledictionStrike.strike(caster, current, blow, finish, null, from);
            hit.add(current);
            from = to;
            blow *= CHAIN_FALLOFF;
            current = nextLink(level, caster, first, current, hit);
        }
        MaledictionFx.sound(level, first.position(), SoundEvents.LIGHTNING_BOLT_IMPACT, 0.4F, 1.8F);
    }

    private static @Nullable LivingEntity nextLink(ServerLevel level, ServerPlayer caster, LivingEntity first,
                                                   LivingEntity from, Set<LivingEntity> hit) {
        Vec3 at = from.getBoundingBox().getCenter();
        LivingEntity nearest = null;
        double best = CHAIN_REACH * CHAIN_REACH;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().inflate(CHAIN_REACH))) {
            if (hit.contains(candidate) || !MaledictionStrike.mayHarm(caster, candidate)
                || !(candidate instanceof Enemy || candidate.getType() == first.getType())) {
                continue;
            }
            double distance = candidate.getBoundingBox().getCenter().distanceToSqr(at);
            if (distance <= best && sees(from, at, candidate)) {
                best = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    private static boolean sees(Entity looker, Vec3 eye, Entity target) {
        Vec3 to = target.getBoundingBox().getCenter();
        return looker.level().clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, looker))
            .getType() == HitResult.Type.MISS;
    }
}
