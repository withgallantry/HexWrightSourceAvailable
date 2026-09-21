package com.bluup.hexwright.server.mob;

import com.bluup.hexwright.server.boss.BossDamage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.RawAnimation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RunestoneTitanEntity extends ConstructEntity {

    public enum Clip implements Action {
        NONE(null, 0),
        SWEEP_LEFT("melee_sweep_left", 55),
        SWEEP_RIGHT("melee_sweep_right", 55),
        SLAM_SWEEP_LEFT("melee_slam_sweep_left", 57),
        SLAM_SWEEP_RIGHT("melee_slam_sweep_right", 57),
        DOUBLE_SLAM("melee_slam_both", 84),
        SUMMON("summon", 69);

        private final RawAnimation animation;
        private final int length;

        Clip(@Nullable String clip, int length) {
            this.length = length;
            this.animation = clip == null ? null : RawAnimation.begin().thenPlay(clip);
        }

        @Nullable
        @Override
        public RawAnimation animation() {
            return this.animation;
        }

        @Override
        public int length() {
            return this.length;
        }

        private static final Clip[] BY_ID = values();
    }

    private static final double ATTACK_START_RANGE = 6.5D;
    private static final double LUNGE_BEYOND = 4.0D;
    private static final double LUNGE_STRENGTH = 0.35D;

    private static final double SWEEP_REACH = 6.5D;
    private static final double SWEEP_CONE = -0.2D;
    private static final double SWEEP_THROW_HORIZONTAL = 0.6D;
    private static final double SWEEP_THROW_VERTICAL = 0.5D;

    private static final double SLAM_FORWARD = 3.0D;
    private static final double SLAM_RADIUS = 4.5D;
    private static final float SLAM_DAMAGE_SCALE = 1.5F;

    private static final double DOUBLE_SLAM_RADIUS = 9.0D;
    private static final double DOUBLE_SLAM_HEIGHT = 4.0D;
    private static final float DOUBLE_SLAM_DAMAGE_SCALE = 2.0F;
    private static final double DOUBLE_SLAM_THROW_HORIZONTAL = 0.35D;
    private static final double DOUBLE_SLAM_THROW_VERTICAL = 0.8D;

    private static final int SWEEP_TRACK_UNTIL = 14;
    private static final int SWEEP_LUNGE_TICK = 17;
    private static final int[] SWEEP_HIT_TICKS = {18, 21, 24};
    private static final int SLAM_SWEEP_TRACK_UNTIL = 11;
    private static final int SLAM_SWEEP_LUNGE_TICK = 14;
    private static final int SLAM_SWEEP_SLAM_TICK = 16;
    private static final int[] SLAM_SWEEP_HIT_TICKS = {27, 30, 33};
    private static final int DOUBLE_SLAM_TRACK_UNTIL = 25;
    private static final int DOUBLE_SLAM_LUNGE_TICK = 31;
    private static final int DOUBLE_SLAM_TICK = 33;
    private static final int[] SUMMON_TICKS = {26, 29, 32};
    private static final int[] SUMMON_COUNTS = {2, 2, 1};

    private static final int SWEEP_COOLDOWN = 80;
    private static final int SLAM_SWEEP_COOLDOWN = 80;
    private static final int DOUBLE_SLAM_COOLDOWN = 120;
    private static final int SUMMON_COOLDOWN = 600;
    private static final double SUMMON_RANGE = 15.0D;
    private static final double SUMMON_SPREAD = 6.0D;
    private static final int MAX_MINIONS = 6;
    private static final double MINION_COUNT_RANGE = 24.0D;

    private static final int RECOVER_TICKS = 10;

    private static final int LEASH = 20;

    private static final int DEATH_TICKS = 74;

    private static final float PER_HIT_CAP = 40.0F;
    private static final float PER_SECOND_CAP = 80.0F;

    private static final String TAG_HOME = "Home";

    @Nullable
    private BlockPos home;
    private int recover;
    private int repath;
    private int sweepCooldown;
    private int slamSweepCooldown = SLAM_SWEEP_COOLDOWN / 2;
    private int doubleSlamCooldown = DOUBLE_SLAM_COOLDOWN / 2;
    private int summonCooldown = SUMMON_COOLDOWN / 3;
    private final Set<Player> struck = new HashSet<>();

    public RunestoneTitanEntity(EntityType<? extends RunestoneTitanEntity> type, Level level) {
        super(type, level, PER_HIT_CAP, PER_SECOND_CAP, 3.0D, 1.3D);
        this.xpReward = 40;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 200.0D)
            .add(Attributes.ARMOR, 10.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.22D)
            .add(Attributes.ATTACK_DAMAGE, 8.0D)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
            .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(4, new MoveTowardsRestrictionGoal(this, 1.0D));
    }

    public void setHome(BlockPos home) {
        this.home = home.immutable();
        this.restrictTo(this.home, LEASH);
    }

    @Override
    protected Action actionById(int id) {
        return id >= 0 && id < Clip.BY_ID.length ? Clip.BY_ID[id] : Clip.NONE;
    }

    private void finish() {
        end();
        this.recover = RECOVER_TICKS;
        this.struck.clear();
    }


    @Override
    protected void combatTick(@Nullable LivingEntity target) {
        if (this.sweepCooldown > 0) {
            this.sweepCooldown--;
        }
        if (this.slamSweepCooldown > 0) {
            this.slamSweepCooldown--;
        }
        if (this.doubleSlamCooldown > 0) {
            this.doubleSlamCooldown--;
        }
        if (this.summonCooldown > 0) {
            this.summonCooldown--;
        }
        switch ((Clip) action()) {
            case NONE -> choose(target);
            case SWEEP_LEFT, SWEEP_RIGHT -> tickSweep(target);
            case SLAM_SWEEP_LEFT, SLAM_SWEEP_RIGHT -> tickSlamSweep(target);
            case DOUBLE_SLAM -> tickDoubleSlam(target);
            case SUMMON -> tickSummon(target);
        }
    }

    private boolean leashed(LivingEntity target) {
        return this.home != null && target.blockPosition().distSqr(this.home) > (double) LEASH * LEASH;
    }

    private void choose(@Nullable LivingEntity target) {
        if (target == null) {
            return;
        }
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (this.recover > 0) {
            this.recover--;
            this.getNavigation().stop();
            return;
        }
        double distance = this.distanceTo(target);
        boolean sees = this.getSensing().hasLineOfSight(target);
        if (sees && this.summonCooldown <= 0 && distance <= SUMMON_RANGE && minions() < MAX_MINIONS
            && this.onGround()) {
            this.getNavigation().stop();
            this.summonCooldown = SUMMON_COOLDOWN;
            begin(Clip.SUMMON);
            return;
        }
        if (sees && distance <= ATTACK_START_RANGE) {
            Clip move = pickMove();
            if (move != null) {
                this.getNavigation().stop();
                begin(move);
                return;
            }
        }
        if (leashed(target)) {
            if (this.home != null && --this.repath <= 0) {
                this.repath = 10;
                this.getNavigation().moveTo(this.home.getX() + 0.5D, this.home.getY(), this.home.getZ() + 0.5D, 1.0D);
            }
            return;
        }
        if (distance > ATTACK_START_RANGE - 1.5D && --this.repath <= 0) {
            this.repath = 4 + this.getRandom().nextInt(4);
            this.getNavigation().moveTo(target, 1.0D);
        } else if (distance <= ATTACK_START_RANGE - 1.5D) {
            this.getNavigation().stop();
            face(target, 10.0F);
        }
    }

    @Nullable
    private Clip pickMove() {
        List<Clip> ready = new ArrayList<>(3);
        if (this.sweepCooldown <= 0) {
            ready.add(this.random.nextBoolean() ? Clip.SWEEP_LEFT : Clip.SWEEP_RIGHT);
        }
        if (this.slamSweepCooldown <= 0) {
            ready.add(this.random.nextBoolean() ? Clip.SLAM_SWEEP_LEFT : Clip.SLAM_SWEEP_RIGHT);
        }
        if (this.doubleSlamCooldown <= 0) {
            ready.add(Clip.DOUBLE_SLAM);
        }
        if (ready.isEmpty()) {
            return null;
        }
        Clip move = ready.get(this.random.nextInt(ready.size()));
        switch (move) {
            case SWEEP_LEFT, SWEEP_RIGHT -> this.sweepCooldown = SWEEP_COOLDOWN;
            case SLAM_SWEEP_LEFT, SLAM_SWEEP_RIGHT -> this.slamSweepCooldown = SLAM_SWEEP_COOLDOWN;
            default -> this.doubleSlamCooldown = DOUBLE_SLAM_COOLDOWN;
        }
        return move;
    }

    private void tickSweep(@Nullable LivingEntity target) {
        int tick = ++this.actionTick;
        this.getNavigation().stop();
        if (target != null && tick <= SWEEP_TRACK_UNTIL) {
            face(target, 10.0F);
        }
        if (tick >= SWEEP_LUNGE_TICK && tick < SWEEP_LUNGE_TICK + 3) {
            if (tick == SWEEP_LUNGE_TICK) {
                swingSounds();
            }
            if (target != null && this.distanceTo(target) > LUNGE_BEYOND) {
                lunge(LUNGE_STRENGTH);
            }
        }
        for (int hit : SWEEP_HIT_TICKS) {
            if (tick == hit) {
                sweep();
            }
        }
        if (tick >= action().length()) {
            finish();
        }
    }

    private void tickSlamSweep(@Nullable LivingEntity target) {
        int tick = ++this.actionTick;
        this.getNavigation().stop();
        if (target != null && tick <= SLAM_SWEEP_TRACK_UNTIL) {
            face(target, 10.0F);
        }
        if (tick >= SLAM_SWEEP_LUNGE_TICK && tick < SLAM_SWEEP_LUNGE_TICK + 3) {
            if (tick == SLAM_SWEEP_LUNGE_TICK) {
                this.playSound(SoundEvents.RAVAGER_ATTACK, 0.8F, 0.6F);
            }
            if (target != null && this.distanceTo(target) > LUNGE_BEYOND) {
                lunge(LUNGE_STRENGTH);
            }
        }
        if (tick == SLAM_SWEEP_SLAM_TICK) {
            Vec3 forward = Vec3.directionFromRotation(0.0F, this.getYRot());
            slam(this.position().add(forward.scale(SLAM_FORWARD)), SLAM_RADIUS, SLAM_DAMAGE_SCALE, 0.0D, 0.0D);
            this.struck.clear();
        }
        if (tick == SLAM_SWEEP_HIT_TICKS[0]) {
            swingSounds();
        }
        for (int hit : SLAM_SWEEP_HIT_TICKS) {
            if (tick == hit) {
                sweep();
            }
        }
        if (tick >= action().length()) {
            finish();
        }
    }

    private void tickDoubleSlam(@Nullable LivingEntity target) {
        int tick = ++this.actionTick;
        this.getNavigation().stop();
        if (target != null && tick <= DOUBLE_SLAM_TRACK_UNTIL) {
            face(target, 8.0F);
        }
        if (tick >= DOUBLE_SLAM_LUNGE_TICK && tick < DOUBLE_SLAM_LUNGE_TICK + 2) {
            if (tick == DOUBLE_SLAM_LUNGE_TICK) {
                this.playSound(SoundEvents.RAVAGER_ATTACK, 0.8F, 0.6F);
            }
            if (target != null && this.distanceTo(target) > LUNGE_BEYOND) {
                lunge(LUNGE_STRENGTH);
            }
        }
        if (tick == DOUBLE_SLAM_TICK) {
            slam(this.position(), DOUBLE_SLAM_RADIUS, DOUBLE_SLAM_DAMAGE_SCALE,
                DOUBLE_SLAM_THROW_HORIZONTAL, DOUBLE_SLAM_THROW_VERTICAL);
        }
        if (tick >= action().length()) {
            finish();
        }
    }

    private void tickSummon(@Nullable LivingEntity target) {
        int tick = ++this.actionTick;
        this.getNavigation().stop();
        if (tick == SUMMON_TICKS[0]) {
            this.playSound(SoundEvents.ANVIL_LAND, 0.8F, 0.5F);
            groundBurst(this.position(), 2.0D, 30);
        }
        for (int beat = 0; beat < SUMMON_TICKS.length; beat++) {
            if (tick == SUMMON_TICKS[beat] && this.level() instanceof ServerLevel level) {
                for (int count = 0; count < SUMMON_COUNTS[beat] && minions() < MAX_MINIONS; count++) {
                    Vec3 spot = summonSpot(level);
                    if (spot != null) {
                        FracturedConstructEntity.summonFor(this, level, spot, target);
                    }
                }
            }
        }
        if (tick >= Clip.SUMMON.length()) {
            finish();
        }
    }

    private void swingSounds() {
        this.playSound(SoundEvents.RAVAGER_ATTACK, 0.8F, 0.6F);
        this.playSound(SoundEvents.ENDER_DRAGON_FLAP, 0.75F, 0.7F);
    }

    private void sweep() {
        float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        for (Player victim : players(this.position(), SWEEP_REACH + this.getBbWidth() * 0.5D, DOUBLE_SLAM_HEIGHT)) {
            if (this.struck.contains(victim) || !inReach(victim, SWEEP_REACH, SWEEP_CONE)) {
                continue;
            }
            this.struck.add(victim);
            if (victim.hurt(this.damageSources().mobAttack(this), damage)) {
                this.doEnchantDamageEffects(this, victim);
                BossDamage.knock(victim, victim.position().subtract(this.position()),
                    SWEEP_THROW_HORIZONTAL, SWEEP_THROW_VERTICAL);
            }
        }
    }

    private void slam(Vec3 centre, double radius, float scale, double throwHorizontal, double throwVertical) {
        this.playSound(SoundEvents.ANVIL_LAND, 0.8F, 0.55F);
        this.playSound(SoundEvents.GENERIC_EXPLODE, 0.5F, 0.6F);
        groundBurst(centre, Math.min(radius * 0.5D, 3.0D), (int) (radius * 5));
        float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE) * scale;
        for (Player victim : players(centre, radius, DOUBLE_SLAM_HEIGHT)) {
            if (this.struck.contains(victim)) {
                continue;
            }
            this.struck.add(victim);
            if (victim.hurt(this.damageSources().mobAttack(this), damage)) {
                this.doEnchantDamageEffects(this, victim);
                if (throwVertical > 0.0D) {
                    BossDamage.knock(victim, victim.position().subtract(this.position()),
                        throwHorizontal, throwVertical);
                }
            }
        }
    }

    private List<Player> players(Vec3 centre, double radius, double height) {
        List<Player> found = new ArrayList<>();
        for (Player player : this.level().getEntitiesOfClass(Player.class,
            this.getBoundingBox().inflate(radius + 2.0D, height, radius + 2.0D),
            EntitySelector.NO_CREATIVE_OR_SPECTATOR.and(Entity::isAlive))) {
            double dx = player.getX() - centre.x;
            double dz = player.getZ() - centre.z;
            if (dx * dx + dz * dz <= radius * radius && Math.abs(player.getY() - centre.y) <= height) {
                found.add(player);
            }
        }
        return found;
    }

    private void groundBurst(Vec3 centre, double spread, int count) {
        if (this.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, centre.x, centre.y + 0.1D, centre.z,
                Math.max(6, count / 3), spread, 0.2D, spread, 0.04D);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE_TILES.defaultBlockState()),
                centre.x, centre.y + 0.1D, centre.z, count, spread, 0.1D, spread, 0.0D);
        }
    }

    private int minions() {
        return this.level().getEntitiesOfClass(FracturedConstructEntity.class,
            this.getBoundingBox().inflate(MINION_COUNT_RANGE), Entity::isAlive).size();
    }

    @Nullable
    private Vec3 summonSpot(ServerLevel level) {
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            double reach = 2.5D + this.random.nextDouble() * (SUMMON_SPREAD - 2.5D);
            BlockPos column = BlockPos.containing(this.getX() + Math.cos(angle) * reach, this.getY(),
                this.getZ() + Math.sin(angle) * reach);
            for (int dy = 1; dy >= -2; dy--) {
                BlockPos feet = column.above(dy);
                if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()
                    || !level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)) {
                    continue;
                }
                Vec3 standing = Vec3.atBottomCenterOf(feet);
                HitResult sight = level.clip(new ClipContext(this.position().add(0.0D, 1.0D, 0.0D),
                    standing.add(0.0D, 0.5D, 0.0D), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
                if (sight.getType() == HitResult.Type.MISS) {
                    return standing;
                }
            }
        }
        return null;
    }


    @Override
    public void playAmbientSound() {
        this.playSound(SoundEvents.RAVAGER_AMBIENT, 0.75F, 0.5F);
    }

    @Override
    protected void playHurtSound(DamageSource source) {
        this.playSound(SoundEvents.TURTLE_HURT, 0.8F, 0.5F);
        this.playSound(SoundEvents.DEEPSLATE_HIT, 1.0F, 0.7F);
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(SoundEvents.HOGLIN_STEP, 0.9F, 0.5F);
        this.playSound(SoundEvents.DEEPSLATE_STEP, 1.0F, 0.6F);
    }

    @Override
    protected int deathTicks() {
        return DEATH_TICKS;
    }

    @Override
    protected void deathBeat(ServerLevel level, int tick) {
        switch (tick) {
            case 2 -> {
                breakOff(level, -1.0D, 2.4D, 1.4D, 20, 0.4D);
                this.playSound(SoundEvents.RAVAGER_HURT, 0.6F, 0.5F + this.random.nextFloat() * 0.1F);
            }
            case 3 -> breakOff(level, -1.0D, 2.6D, 3.0D, 20, 0.4D);
            case 5 -> breakOff(level, -1.0D, 1.9D, 4.4D, 20, 0.4D);
            case 15, 35 -> breakOff(level, 1.0D, 0.8D, 0.4D, 20, 0.4D);
            case 16, 36 -> breakOff(level, 1.0D, 0.5D, 1.6D, 20, 0.4D);
            case 27 -> breakOff(level, 1.0D, 2.4D, 1.4D, 20, 0.4D);
            case 28 -> {
                breakOff(level, 1.0D, 2.6D, 3.0D, 20, 0.4D);
                this.playSound(SoundEvents.RAVAGER_HURT, 0.6F, 0.5F + this.random.nextFloat() * 0.1F);
            }
            case 29 -> breakOff(level, 1.0D, 1.9D, 4.4D, 20, 0.4D);
            case 40 -> {
                this.playSound(SoundEvents.HOGLIN_STEP, 1.0F, 0.5F + this.random.nextFloat() * 0.2F);
                groundBurst(this.position(), 0.8D, 15);
            }
            case 53 -> breakOff(level, 0.0D, 0.0D, 4.3D, 20, 0.5D);
            case 56 -> breakOff(level, 0.0D, 0.0D, 3.5D, 20, 0.6D);
            case 59 -> breakOff(level, 0.0D, 0.0D, 2.6D, 20, 0.6D);
            case 65 -> breakOff(level, 0.0D, 0.0D, 2.0D, 40, 1.2D);
            default -> {
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.home != null) {
            tag.put(TAG_HOME, NbtUtils.writeBlockPos(this.home));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_HOME)) {
            setHome(NbtUtils.readBlockPos(tag.getCompound(TAG_HOME)));
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

}
