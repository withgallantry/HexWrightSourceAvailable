package com.bluup.hexwright.server.mob;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.RawAnimation;

public class FracturedConstructEntity extends ConstructEntity {

    public enum Clip implements Action {
        NONE(null, 0),
        MELEE_LONG("melee", 33),
        MELEE_SHORT("melee", 13),
        SUMMON("summon", 21);

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

    public static final int PIECES = 4;

    private static final double[][] LANDING = {
        {0.0D, 0.19D}, {0.0D, 2.19D}, {-1.27D, 1.0D}, {2.11D, 0.53D},
    };

    private static final double MELEE_START_RANGE = 2.5D;
    private static final double MELEE_HIT_RANGE = 2.3D;
    private static final double MELEE_CONE = 0.4D;
    private static final int LONG_LUNGE_TICK = 14;
    private static final int LONG_HIT_TICK = 16;
    private static final int LONG_RECOVER = 3;
    private static final int SHORT_LUNGE_TICK = 8;
    private static final int SHORT_HIT_TICK = 10;
    private static final int SHORT_RECOVER = 18;

    private static final int DEATH_TICKS = 28;

    private static final float PER_HIT_CAP = 8.0F;
    private static final float PER_SECOND_CAP = 16.0F;

    private static final EntityDataAccessor<Byte> DATA_PIECE =
        SynchedEntityData.defineId(FracturedConstructEntity.class, EntityDataSerializers.BYTE);

    private static final String TAG_PIECE = "Piece";
    private static final String TAG_FLEEING = "Fleeing";

    private boolean fleeing;
    private int recover;
    private int repath;

    public FracturedConstructEntity(EntityType<? extends FracturedConstructEntity> type, Level level) {
        super(type, level, PER_HIT_CAP, PER_SECOND_CAP, 0.35D, 0.4D);
        this.xpReward = 2;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 8.0D)
            .add(Attributes.ARMOR, 2.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.28D)
            .add(Attributes.ATTACK_DAMAGE, 3.0D)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
            .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_PIECE, (byte) 1);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Player.class, 12.0F, 1.0D, 1.3D,
            player -> this.fleeing && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(player)));
    }

    public int piece() {
        return Mth.clamp(this.entityData.get(DATA_PIECE), 1, PIECES);
    }

    private void setPiece(int piece) {
        this.entityData.set(DATA_PIECE, (byte) Mth.clamp(piece, 1, PIECES));
    }

    public boolean isFleeing() {
        return this.fleeing;
    }

    @Override
    protected boolean wantsCombat() {
        return !this.fleeing;
    }

    @Override
    protected Action actionById(int id) {
        return id >= 0 && id < Clip.BY_ID.length ? Clip.BY_ID[id] : Clip.NONE;
    }

    static void splitFrom(ServitorConstructEntity servitor, ServerLevel level) {
        float yaw = servitor.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 left = new Vec3(Mth.cos(yaw), 0.0D, Mth.sin(yaw));
        Vec3 back = new Vec3(Mth.sin(yaw), 0.0D, -Mth.cos(yaw));
        for (int piece = 1; piece <= PIECES; piece++) {
            FracturedConstructEntity block = HexwrightMobEntities.FRACTURED_CONSTRUCT.create(level);
            if (block == null) {
                continue;
            }
            double[] landing = LANDING[piece - 1];
            Vec3 spot = servitor.position().add(left.scale(landing[0])).add(back.scale(landing[1]));
            block.moveTo(spot.x, servitor.getY(), spot.z, servitor.getYRot(), 0.0F);
            if (!level.noCollision(block)) {
                block.moveTo(servitor.getX(), servitor.getY(), servitor.getZ(), servitor.getYRot(), 0.0F);
            }
            block.setPiece(piece);
            block.fleeing = piece >= 3;
            if (servitor.isPersistenceRequired()) {
                block.setPersistenceRequired();
            }
            block.finalizeSpawn(level, level.getCurrentDifficultyAt(block.blockPosition()),
                MobSpawnType.MOB_SUMMONED, null, null);
            block.begin(Clip.SUMMON);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState()),
                block.getX(), block.getY() + 0.1D, block.getZ(), 20, 0.5D, 0.2D, 0.5D, 0.0D);
            level.addFreshEntity(block);
        }
        servitor.playSound(SoundEvents.DEEPSLATE_BREAK, 0.9F, 0.8F);
    }

    static void summonFor(RunestoneTitanEntity titan, ServerLevel level, Vec3 spot, @Nullable LivingEntity target) {
        FracturedConstructEntity block = HexwrightMobEntities.FRACTURED_CONSTRUCT.create(level);
        if (block == null) {
            return;
        }
        block.moveTo(spot.x, spot.y, spot.z, titan.getRandom().nextFloat() * 360.0F, 0.0F);
        if (!level.noCollision(block)) {
            return;
        }
        block.setPiece(1 + titan.getRandom().nextInt(PIECES));
        if (titan.isPersistenceRequired()) {
            block.setPersistenceRequired();
        }
        block.finalizeSpawn(level, level.getCurrentDifficultyAt(block.blockPosition()),
            MobSpawnType.MOB_SUMMONED, null, null);
        block.begin(Clip.SUMMON);
        if (target != null) {
            block.setTarget(target);
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState()),
            block.getX(), block.getY() + 0.1D, block.getZ(), 20, 0.5D, 0.2D, 0.5D, 0.0D);
        block.playSound(SoundEvents.DEEPSLATE_BREAK, 0.9F, 0.8F);
        level.addFreshEntity(block);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        if (reason != MobSpawnType.MOB_SUMMONED && (tag == null || !tag.contains(TAG_PIECE))) {
            setPiece(1 + this.random.nextInt(PIECES));
        }
        return super.finalizeSpawn(level, difficulty, reason, data, tag);
    }


    @Override
    protected void combatTick(@Nullable LivingEntity target) {
        Clip clip = (Clip) action();
        if (clip == Clip.SUMMON) {
            this.getNavigation().stop();
            if (++this.actionTick >= Clip.SUMMON.length()) {
                end();
            }
            return;
        }
        if (clip == Clip.MELEE_LONG || clip == Clip.MELEE_SHORT) {
            tickMelee(clip, target);
            return;
        }
        if (target == null || this.fleeing) {
            return;
        }
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (this.recover > 0) {
            this.recover--;
            this.getNavigation().stop();
            return;
        }
        if (this.distanceTo(target) <= MELEE_START_RANGE && this.getSensing().hasLineOfSight(target)) {
            this.getNavigation().stop();
            begin(piece() == 1 ? Clip.MELEE_LONG : Clip.MELEE_SHORT);
            return;
        }
        if (--this.repath <= 0) {
            this.repath = 4 + this.getRandom().nextInt(4);
            this.getNavigation().moveTo(target, 1.0D);
        }
    }

    private void tickMelee(Clip clip, @Nullable LivingEntity target) {
        boolean slow = clip == Clip.MELEE_LONG;
        int tick = ++this.actionTick;
        this.getNavigation().stop();
        if (target != null && tick < (slow ? LONG_LUNGE_TICK : SHORT_LUNGE_TICK)) {
            face(target, 25.0F);
        }
        if (tick == (slow ? LONG_LUNGE_TICK : SHORT_LUNGE_TICK)) {
            this.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, 0.7F);
            if (target != null && this.distanceTo(target) > 2.0D) {
                lunge(0.35D);
            }
        }
        if (tick == (slow ? LONG_HIT_TICK : SHORT_HIT_TICK)) {
            this.playSound(SoundEvents.GOAT_RAM_IMPACT, 0.7F, 1.2F);
            if (target != null && inReach(target, MELEE_HIT_RANGE, MELEE_CONE)) {
                this.doHurtTarget(target);
            }
        }
        if (tick >= clip.length()) {
            end();
            this.recover = slow ? LONG_RECOVER : SHORT_RECOVER;
        }
    }


    @Override
    public void playAmbientSound() {
        this.playSound(SoundEvents.TURTLE_AMBIENT_LAND, 0.75F, 0.9F);
    }

    @Override
    protected void playHurtSound(DamageSource source) {
        this.playSound(SoundEvents.TURTLE_HURT, 0.5F, 0.6F);
        this.playSound(SoundEvents.DEEPSLATE_HIT, 1.0F, 1.1F);
    }

    @Override
    protected int deathTicks() {
        return DEATH_TICKS;
    }

    @Override
    protected void deathBeat(ServerLevel level, int tick) {
        if (tick == 26) {
            breakOff(level, 0.0D, 0.0D, 0.3D, 20, 0.35D);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte(TAG_PIECE, (byte) piece());
        tag.putBoolean(TAG_FLEEING, this.fleeing);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_PIECE)) {
            setPiece(tag.getByte(TAG_PIECE));
        }
        this.fleeing = tag.getBoolean(TAG_FLEEING);
    }
}
