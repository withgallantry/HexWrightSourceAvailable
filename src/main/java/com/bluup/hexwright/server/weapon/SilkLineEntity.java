package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.server.boss.Bosses;
import com.bluup.hexwright.server.effect.HexwrightEffects;
import com.bluup.hexwright.server.staff_assembly.HexwrightEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class SilkLineEntity extends Projectile {

    public static final double REACH = 24.0D;

    public static final int WEB_TICKS = 60;

    private static final float SPEED = 3.0F;
    private static final int FLIGHT_TICKS = (int) Math.ceil(REACH / SPEED);

    private static final float STING_DAMAGE = 2.0F;

    private static final double REEL_SPEED = 1.1D;
    private static final double REEL_EASE = 0.6D;
    private static final double ARRIVE_DISTANCE = 1.6D;
    private static final double LEDGE_ARRIVE_DISTANCE = 0.6D;
    private static final int MAX_REEL_TICKS = 40;
    private static final double LEDGE_HOP = 0.42D;
    private static final double LEDGE_CARRY = 0.3D;

    private static final EntityDataAccessor<Boolean> DATA_ANCHORED =
        SynchedEntityData.defineId(SilkLineEntity.class, EntityDataSerializers.BOOLEAN);

    private int flightTicks;
    private int reelTicks;

    private Vec3 reelTarget = Vec3.ZERO;
    private boolean ontoLedge;
    private Vec3 overTheLip = Vec3.ZERO;

    public SilkLineEntity(EntityType<? extends SilkLineEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    public static void shoot(ServerLevel level, ServerPlayer archer) {
        SilkLineEntity silk = new SilkLineEntity(HexwrightEntities.SILK_LINE, level);
        silk.setOwner(archer);
        silk.setPos(archer.getX(), archer.getEyeY() - 0.1D, archer.getZ());
        silk.shootFromRotation(archer, archer.getXRot(), archer.getYRot(), 0.0F, SPEED, 0.0F);
        level.addFreshEntity(silk);
        level.playSound(null, archer.getX(), archer.getY(), archer.getZ(), SoundEvents.FISHING_BOBBER_THROW,
            SoundSource.PLAYERS, 0.8F, 1.6F);
        level.playSound(null, archer.getX(), archer.getY(), archer.getZ(), SoundEvents.SPIDER_AMBIENT,
            SoundSource.PLAYERS, 0.35F, 1.5F);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_ANCHORED, false);
    }

    public boolean isAnchored() {
        return this.entityData.get(DATA_ANCHORED);
    }

    @Override
    public void tick() {
        super.tick();
        if (isAnchored()) {
            if (!this.level().isClientSide()) {
                reel();
            }
            return;
        }
        if (!this.level().isClientSide() && ++this.flightTicks > FLIGHT_TICKS) {
            this.discard();
            return;
        }

        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS) {
            this.onHit(hit);
        }
        if (hit.getType() == HitResult.Type.BLOCK) {
            this.setPos(hit.getLocation());
            this.setDeltaMovement(Vec3.ZERO);
        } else if (!this.isRemoved()) {
            Vec3 motion = this.getDeltaMovement();
            this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);
            ProjectileUtil.rotateTowardsMovement(this, 1.0F);
        }
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        Entity owner = this.getOwner();
        return super.canHitEntity(target) && target != owner && target instanceof LivingEntity
            && !target.isSpectator() && (owner == null || !target.isAlliedTo(owner));
    }


    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(this.level() instanceof ServerLevel level) || !(result.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        Entity owner = this.getOwner();
        if (owner instanceof Player archer) {
            SpiderBowItem.remember(archer, victim);
        }
        victim.hurt(this.damageSources().thrown(this, owner), STING_DAMAGE);
        if (!Bosses.is(victim)) {
            victim.addEffect(new MobEffectInstance(HexwrightEffects.WEBBED, WEB_TICKS, 0, false, true, true), owner);
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBWEB.defaultBlockState()),
            victim.getX(), victim.getY(0.5D), victim.getZ(), 20, victim.getBbWidth() * 0.4D,
            victim.getBbHeight() * 0.3D, victim.getBbWidth() * 0.4D, 0.0D);
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.SPIDER_STEP,
            SoundSource.PLAYERS, 1.0F, 0.8F);
        this.discard();
    }


    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 at = result.getLocation();
        this.entityData.set(DATA_ANCHORED, true);
        aim(level, at, result.getDirection(), result.getBlockPos());
        if (this.getOwner() instanceof Player archer) {
            SpiderBowItem.remember(archer, result.getBlockPos());
        }
        level.playSound(null, at.x, at.y, at.z, SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS, 1.0F, 1.3F);
    }

    private void aim(ServerLevel level, Vec3 at, Direction face, BlockPos struck) {
        if (face == Direction.UP) {
            this.ontoLedge = true;
            this.reelTarget = at;
        } else if (face.getAxis().isHorizontal() && standable(level, struck)) {
            this.ontoLedge = true;
            this.reelTarget = new Vec3(at.x, struck.getY() + 1.0D, at.z);
            this.overTheLip = Vec3.atLowerCornerOf(face.getOpposite().getNormal());
        } else {
            this.ontoLedge = false;
            this.reelTarget = at;
        }
    }

    private static boolean standable(ServerLevel level, BlockPos struck) {
        return level.getBlockState(struck.above()).getCollisionShape(level, struck.above()).isEmpty()
            && level.getBlockState(struck.above(2)).getCollisionShape(level, struck.above(2)).isEmpty();
    }

    private void reel() {
        if (!(this.getOwner() instanceof ServerPlayer archer) || archer.isRemoved() || !archer.isAlive()
            || archer.isSpectator() || archer.isPassenger() || archer.level() != this.level()) {
            this.discard();
            return;
        }
        Vec3 held = this.ontoLedge
            ? archer.position()
            : archer.position().add(0.0D, archer.getBbHeight() * 0.5D, 0.0D);
        Vec3 toAnchor = this.reelTarget.subtract(held);
        double distance = toAnchor.length();
        double arrival = this.ontoLedge ? LEDGE_ARRIVE_DISTANCE : ARRIVE_DISTANCE;
        if (distance < arrival || ++this.reelTicks > MAX_REEL_TICKS) {
            if (this.ontoLedge) {
                Vec3 over = this.overTheLip.lengthSqr() > 0.0D
                    ? this.overTheLip
                    : new Vec3(toAnchor.x, 0.0D, toAnchor.z).normalize();
                archer.setDeltaMovement(over.x * LEDGE_CARRY, LEDGE_HOP, over.z * LEDGE_CARRY);
                archer.connection.send(new ClientboundSetEntityMotionPacket(archer));
            }
            archer.resetFallDistance();
            this.level().playSound(null, archer.getX(), archer.getY(), archer.getZ(),
                SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.8F, 1.3F);
            this.discard();
            return;
        }
        archer.setDeltaMovement(toAnchor.scale(Math.min(REEL_SPEED, distance * REEL_EASE) / distance));
        archer.resetFallDistance();
        archer.connection.send(new ClientboundSetEntityMotionPacket(archer));
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
