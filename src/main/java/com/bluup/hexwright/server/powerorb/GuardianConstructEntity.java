package com.bluup.hexwright.server.powerorb;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.core.animation.RawAnimation;

public class GuardianConstructEntity extends OrbConstructEntity {

    public static final float MAX_HEALTH = 80.0F;

    private enum Clip implements OrbConstructEntity.Clip {
        STRIKE_1("attack1", 35, BLEND_TICKS + 13, 1, PowerOrbSounds.GUARDIAN_STRIKE_1),
        STRIKE_2("attack2", 30, BLEND_TICKS + 10, BLEND_TICKS + 7, PowerOrbSounds.GUARDIAN_STRIKE_2),
        STRIKE_3("attack3", 34, BLEND_TICKS + 13, BLEND_TICKS + 7, PowerOrbSounds.GUARDIAN_PIERCE);

        private static final Clip[] BY_ORDINAL = values();

        private final RawAnimation animation;
        private final int length;
        private final int hitTick;
        private final int soundTick;
        private final PowerOrbSounds sound;

        Clip(String clip, int length, int hitTick, int soundTick, PowerOrbSounds sound) {
            this.animation = RawAnimation.begin().thenPlay(clip);
            this.length = length;
            this.hitTick = hitTick;
            this.soundTick = soundTick;
            this.sound = sound;
        }

        @Override
        public RawAnimation animation() {
            return this.animation;
        }

        @Override
        public int length() {
            return this.length;
        }
    }

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation GUARD = RawAnimation.begin().thenPlay("shield_ready").thenLoop("shield_loop");

    private static final EntityDataAccessor<Integer> DATA_WEARER =
        SynchedEntityData.defineId(GuardianConstructEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_GUARDING =
        SynchedEntityData.defineId(GuardianConstructEntity.class, EntityDataSerializers.BOOLEAN);

    public static final float GUARD_ARC_DEGREES = 60.0F;
    private static final double GUARD_ARC_COS = Math.cos(Math.toRadians(GUARD_ARC_DEGREES));
    private static final double GUARD_RANGE = 16.0D;
    private static final int GUARD_LINGER = 15;
    private static final float GUARD_TURN = 30.0F;
    private static final float IDLE_TURN = 20.0F;
    private static final double PROJECTILE_WATCH = 10.0D;
    private static final double PROJECTILE_MISS = 1.5D;
    private static final int PROJECTILE_LEAD_TICKS = 10;
    private static final int RECENT_ATTACK_TICKS = 40;

    public static final float STRIKE_DAMAGE = 6.0F;
    private static final double STRIKE_REACH = 1.0D;
    private static final double STRIKE_RADIUS = 3.0D;
    private static final int COMBO_OPENS = 15;
    private static final int COMBO_CLOSES = 25;
    private static final int PIERCE_RECOVERY = 45 - 34;
    private static final float STRIKE_TURN = 40.0F;

    private int guardLinger;
    private float guardYaw;
    private int strikeCooldown;
    private boolean absorbing;

    public GuardianConstructEntity(EntityType<? extends GuardianConstructEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, MAX_HEALTH)
            .add(Attributes.MOVEMENT_SPEED, 0.0D)
            .add(Attributes.ATTACK_DAMAGE, STRIKE_DAMAGE)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
            .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Nullable
    public static GuardianConstructEntity summon(ServerLevel level, Player owner, Vec3 at, float health) {
        GuardianConstructEntity guardian = create(PowerOrbEntities.GUARDIAN_CONSTRUCT, level, owner,
            owner.position(), health);
        if (guardian == null) {
            return null;
        }
        guardian.entityData.set(DATA_WEARER, owner.getId());
        if (!level.addFreshEntity(guardian)) {
            return null;
        }
        level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), PowerOrbSounds.GUARDIAN_SUMMON.get(),
            guardian.getSoundSource(), 0.8F, 1.0F);
        guardian.shimmer(level);
        return guardian;
    }

    public static boolean shields(LivingEntity victim, DamageSource source, float amount) {
        return amount > 0.0F
            && !victim.level().isClientSide()
            && victim instanceof Player wearer
            && ConstructOrbPower.GUARDIAN.construct(wearer) instanceof GuardianConstructEntity guardian
            && guardian.block(wearer, source, amount);
    }

    public static void wearerSwung(ServerPlayer wearer) {
        if (ConstructOrbPower.GUARDIAN.construct(wearer) instanceof GuardianConstructEntity guardian) {
            guardian.swing(wearer);
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_WEARER, -1);
        this.entityData.define(DATA_GUARDING, false);
    }

    @Nullable
    public Entity wearer() {
        return this.level().getEntity(this.entityData.get(DATA_WEARER));
    }

    public boolean isGuarding() {
        return this.entityData.get(DATA_GUARDING);
    }

    private void setGuarding(boolean guarding) {
        if (guarding && !isGuarding()) {
            this.playSound(PowerOrbSounds.GUARDIAN_GUARD.get(), 0.8F, 1.0F);
        }
        this.entityData.set(DATA_GUARDING, guarding);
    }

    @Override
    protected ConstructOrbPower orbPower() {
        return ConstructOrbPower.GUARDIAN;
    }

    @Nullable
    @Override
    protected OrbConstructEntity.Clip clipByOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < Clip.BY_ORDINAL.length ? Clip.BY_ORDINAL[ordinal] : null;
    }

    @Override
    protected RawAnimation idleAnimation() {
        return isGuarding() ? GUARD : IDLE;
    }

    @Override
    protected RawAnimation walkAnimation() {
        return isGuarding() ? GUARD : WALK;
    }


    @Override
    public void tick() {
        super.tick();
        Player owner = owner();
        if (!this.level().isClientSide() && owner != null) {
            this.setPos(owner.getX(), owner.getY(), owner.getZ());
            this.setDeltaMovement(Vec3.ZERO);
        }
    }

    @Override
    protected float tickHeadTurn(float bodyRot, float walked) {
        this.yBodyRot = this.getYRot();
        this.yHeadRot = this.getYRot();
        return walked;
    }

    private void turnTo(float yaw, float maxStep) {
        float turned = Mth.approachDegrees(this.getYRot(), yaw, maxStep);
        this.setYRot(turned);
        this.yBodyRot = turned;
        this.yHeadRot = turned;
    }

    private static float yawTowards(Vec3 from, Vec3 to) {
        return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90.0F;
    }


    @Override
    protected void tickCooldowns() {
        if (this.strikeCooldown > 0) {
            this.strikeCooldown--;
        }
    }

    @Override
    protected void act(Player owner, @Nullable LivingEntity target) {
        Vec3 threat = threatTo(owner);
        if (threat != null) {
            this.guardLinger = GUARD_LINGER;
            this.guardYaw = yawTowards(owner.position(), threat);
        } else if (this.guardLinger > 0) {
            this.guardLinger--;
        }
        setGuarding(this.guardLinger > 0);
        if (isGuarding()) {
            turnTo(this.guardYaw, GUARD_TURN);
        } else {
            turnTo(owner.getYRot(), IDLE_TURN);
        }
    }

    @Nullable
    private Vec3 threatTo(Player owner) {
        Projectile incoming = incomingProjectile(owner);
        if (incoming != null) {
            return incoming.position();
        }
        Mob nearest = null;
        double best = GUARD_RANGE * GUARD_RANGE;
        for (Mob mob : this.level().getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(GUARD_RANGE),
            mob -> mob.isAlive() && mob.getTarget() == owner)) {
            double distance = mob.distanceToSqr(owner);
            if (distance < best) {
                best = distance;
                nearest = mob;
            }
        }
        if (nearest != null) {
            return nearest.position();
        }
        LivingEntity attacker = owner.getLastHurtByMob();
        if (attacker != null && attacker.isAlive() && owner.tickCount - owner.getLastHurtByMobTimestamp() < RECENT_ATTACK_TICKS
            && attacker.distanceToSqr(owner) < GUARD_RANGE * GUARD_RANGE) {
            return attacker.position();
        }
        return null;
    }

    @Nullable
    private Projectile incomingProjectile(Player owner) {
        Vec3 centre = owner.getBoundingBox().getCenter();
        Projectile soonest = null;
        double soonestTicks = PROJECTILE_LEAD_TICKS;
        for (Projectile projectile : this.level().getEntitiesOfClass(Projectile.class,
            owner.getBoundingBox().inflate(PROJECTILE_WATCH), projectile -> isHostile(projectile, owner))) {
            Vec3 velocity = projectile.getDeltaMovement();
            double speed = velocity.lengthSqr();
            if (speed < 1.0E-4D) {
                continue;
            }
            Vec3 toward = centre.subtract(projectile.position());
            double ticks = toward.dot(velocity) / speed;
            if (ticks <= 0.0D || ticks > soonestTicks) {
                continue;
            }
            if (toward.subtract(velocity.scale(ticks)).lengthSqr() <= PROJECTILE_MISS * PROJECTILE_MISS) {
                soonestTicks = ticks;
                soonest = projectile;
            }
        }
        return soonest;
    }

    private boolean isHostile(Projectile projectile, Player owner) {
        Entity shooter = projectile.getOwner();
        return projectile.isAlive()
            && shooter != owner
            && shooter != this
            && (shooter == null || !owner.isAlliedTo(shooter) && !this.isAlliedTo(shooter));
    }

    private boolean block(Player wearer, DamageSource source, float amount) {
        if (!isGuarding() || !this.isAlive() || source.is(DamageTypeTags.BYPASSES_SHIELD)
            || source.getDirectEntity() instanceof AbstractArrow arrow && arrow.getPierceLevel() > 0) {
            return false;
        }
        Vec3 from = source.getSourcePosition();
        if (from == null) {
            return false;
        }
        Vec3 toward = new Vec3(from.x - wearer.getX(), 0.0D, from.z - wearer.getZ());
        if (toward.lengthSqr() < 1.0E-4D
            || toward.normalize().dot(Vec3.directionFromRotation(0.0F, this.getYRot())) < GUARD_ARC_COS) {
            return false;
        }
        this.absorbing = true;
        try {
            this.hurt(source, amount);
        } finally {
            this.absorbing = false;
        }
        if (!source.is(DamageTypeTags.IS_PROJECTILE) && source.getDirectEntity() instanceof LivingEntity attacker) {
            attacker.knockback(0.5D, wearer.getX() - attacker.getX(), wearer.getZ() - attacker.getZ());
        }
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.absorbing && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        return super.hurt(source, amount);
    }


    private void swing(Player wearer) {
        if (!this.isAlive()) {
            return;
        }
        Clip next;
        if (playing() instanceof Clip strike) {
            int tick = playingTick();
            if (strike == Clip.STRIKE_3 || tick < COMBO_OPENS) {
                return;
            }
            next = tick <= COMBO_CLOSES ? Clip.BY_ORDINAL[strike.ordinal() + 1] : Clip.STRIKE_1;
        } else if (this.strikeCooldown > 0) {
            return;
        } else {
            next = Clip.STRIKE_1;
        }
        LivingEntity struck = wearer.getLastHurtMob();
        if (struck != null && wearer.getLastHurtMobTimestamp() == wearer.tickCount
            && !(struck instanceof Player) && !this.isAlliedTo(struck)) {
            this.setTarget(struck);
        }
        this.guardLinger = 0;
        setGuarding(false);
        turnTo(wearer.getYRot(), STRIKE_TURN);
        begin(next);
    }

    @Override
    protected void tickAction(OrbConstructEntity.Clip clip, int tick) {
        if (!(clip instanceof Clip strike) || !(this.level() instanceof ServerLevel level)) {
            return;
        }
        Player wearer = owner();
        if (wearer == null) {
            return;
        }
        if (tick <= strike.hitTick) {
            turnTo(wearer.getYRot(), STRIKE_TURN);
        }
        if (tick == strike.soundTick) {
            this.playSound(strike.sound.get(), 0.8F, 1.0F);
        }
        if (tick == strike.hitTick) {
            cut(level, wearer);
        }
    }

    @Override
    protected void finishAction(OrbConstructEntity.Clip clip) {
        if (clip == Clip.STRIKE_3) {
            this.strikeCooldown = PIERCE_RECOVERY;
        }
    }

    private void cut(ServerLevel level, Player wearer) {
        Vec3 forward = Vec3.directionFromRotation(0.0F, this.getYRot());
        Vec3 centre = wearer.position().add(forward.scale(STRIKE_REACH));
        DamageSource source = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
            .getHolderOrThrow(DamageTypes.MOB_ATTACK), this, wearer);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
            new AABB(centre, centre).inflate(STRIKE_RADIUS),
            entity -> isEnemy(entity) && entity.position().subtract(centre).horizontalDistanceSqr()
                <= STRIKE_RADIUS * STRIKE_RADIUS)) {
            if (victim.hurt(source, STRIKE_DAMAGE)) {
                level.sendParticles(dust(), victim.getX(), victim.getY() + victim.getBbHeight() * 0.5D,
                    victim.getZ(), 12, 0.3D, 0.5D, 0.3D, 0.0D);
            }
        }
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, centre.x, centre.y + 1.0D, centre.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }


    private void shimmer(ServerLevel level) {
        level.sendParticles(dust(), this.getX(), this.getY() + 2.0D, this.getZ(), 20, 1.3D, 1.2D, 1.3D, 0.0D);
        level.sendParticles(new DustParticleOptions(new Vector3f(0.812F, 0.941F, 1.0F), 1.0F),
            this.getX(), this.getY() + 2.0D, this.getZ(), 20, 1.3D, 1.2D, 1.3D, 0.0D);
    }

    private static DustParticleOptions dust() {
        return new DustParticleOptions(new Vector3f(0.2F, 1.0F, 0.867F), 1.0F);
    }

    @Override
    protected void dismissEffects(ServerLevel level) {
        shimmer(level);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE,
            this.getSoundSource(), 0.7F, 1.2F);
    }


    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return false;
    }

    @Override
    public boolean ignoreExplosion() {
        return true;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return this.getBoundingBox().inflate(2.5D, 0.0D, 2.5D).expandTowards(0.0D, 3.5D, 0.0D);
    }


    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return PowerOrbSounds.GUARDIAN_BLOCK.get();
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SHIELD_BREAK;
    }
}
