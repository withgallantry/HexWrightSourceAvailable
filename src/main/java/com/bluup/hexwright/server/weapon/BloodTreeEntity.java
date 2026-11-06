package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.server.boss.Bosses;
import com.bluup.hexwright.server.staff_assembly.HexwrightEntities;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.Comparator;
import java.util.List;

public class BloodTreeEntity extends Entity implements GeoEntity {

    public static final double RADIUS = 12.0D;
    private static final double ZONE_FLOOR = 4.0D;
    private static final double ZONE_CEILING = 16.0D;

    public static final int GROW_TICKS = 50;
    public static final int WITHER_TICKS = 30;

    static final int SHORTEST_TICKS = 400;
    static final int LONGEST_TICKS = 2400;

    static final int LITRES_PER_SACK = 2;

    static final float SMALLEST_SIZE = 0.8F;
    static final float LARGEST_SIZE = 1.5F;

    private static final Vec3[] MOUTHS = {
        new Vec3(-1.456D, 3.938D, 2.165D),
        new Vec3(1.996D, 5.994D, 0.769D),
        new Vec3(-2.174D, 4.238D, 0.583D),
        new Vec3(0.583D, 5.023D, 2.872D),
        new Vec3(-0.465D, 4.852D, -1.629D),
    };
    public static final int SACK_COUNT = MOUTHS.length;
    private static final double SACK_HANG = 1.594D;

    private static final Vec3[] TIPS = {
        new Vec3(-2.783D, 3.150D, 2.699D),
        new Vec3(-1.005D, 3.618D, 2.335D),
        new Vec3(1.877D, 7.376D, 1.612D),
        new Vec3(2.055D, 5.592D, 1.140D),
        new Vec3(-2.828D, 4.295D, 2.070D),
        new Vec3(-1.758D, 4.680D, 0.507D),
        new Vec3(-0.232D, 4.382D, 4.083D),
        new Vec3(0.151D, 5.349D, 2.647D),
        new Vec3(-1.357D, 6.184D, -1.328D),
        new Vec3(0.080D, 4.948D, -1.476D),
        new Vec3(-0.401D, 6.989D, 1.659D),
        new Vec3(1.001D, 7.179D, 0.235D),
        new Vec3(-0.519D, 7.196D, -0.221D),
    };

    private static final double PLANT_AHEAD = 2.0D;

    private static final int HEARTBEAT_TICKS = 40;

    private static final int SLOW_AMPLIFIER = 1;
    private static final int SLOW_SWEEP_TICKS = 10;
    private static final int SLOW_TICKS = 30;

    private static final double REBORN_BELOW_SACK = 0.6D;
    private static final int REBORN_GRACE_TICKS = 40;
    private static final int REBORN_FALL_TICKS = 60;

    private static final ResourceLocation REBIRTH_PHASE = Hexwright.id("blood_tree_rebirth");

    private static final BlockParticleOption GORE =
        new BlockParticleOption(ParticleTypes.BLOCK, Blocks.NETHER_WART_BLOCK.defaultBlockState());
    private static final BlockParticleOption DRIP =
        new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.NETHER_WART_BLOCK.defaultBlockState());
    private static final DustParticleOptions BLOOD_MIST =
        new DustParticleOptions(new Vector3f(0.55F, 0.02F, 0.08F), 1.8F);

    private static final RawAnimation GROW = RawAnimation.begin().thenPlay("grow").thenLoop("idle");
    private static final RawAnimation STAND = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WITHER = RawAnimation.begin().thenPlayAndHold("wither");

    private static final EntityDataAccessor<Byte> DATA_SACKS =
        SynchedEntityData.defineId(BloodTreeEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Long> DATA_PLANTED_AT =
        SynchedEntityData.defineId(BloodTreeEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Integer> DATA_LIFE =
        SynchedEntityData.defineId(BloodTreeEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SIZE =
        SynchedEntityData.defineId(BloodTreeEntity.class, EntityDataSerializers.FLOAT);

    private final AnimatableInstanceCache animatableCache = GeckoLibUtil.createInstanceCache(this);

    @Nullable
    private ServerPlayer planter;

    private boolean forPlanterAlone;

    @Nullable
    private RawAnimation firstSeen;

    public BloodTreeEntity(EntityType<? extends BloodTreeEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    private BloodTreeEntity(ServerLevel level, ServerPlayer planter, boolean forPlanterAlone, Vec3 at, int life,
                            float size, int sacks) {
        this(HexwrightEntities.BLOOD_TREE, level);
        this.planter = planter;
        this.forPlanterAlone = forPlanterAlone;
        this.moveTo(at.x, at.y, at.z, planter.getYRot(), 0.0F);
        this.entityData.set(DATA_PLANTED_AT, level.getGameTime());
        this.entityData.set(DATA_LIFE, life);
        this.entityData.set(DATA_SIZE, size);
        this.entityData.set(DATA_SACKS, (byte) ((1 << sacks) - 1));
    }

    public static void register() {
        NourishmentParticles.register();
        ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(REBIRTH_PHASE, Event.DEFAULT_PHASE);
        ServerLivingEntityEvents.ALLOW_DEATH.register(REBIRTH_PHASE, (entity, source, damage) ->
            !(entity instanceof ServerPlayer player) || !rebirth(player, source));
    }


    @Nullable
    static BloodTreeEntity plant(ServerLevel level, ServerPlayer wielder, int blood, boolean forPlanterAlone) {
        if (blood < NourishmentItem.LITRE || wielder.isRemoved() || !wielder.isAlive() || wielder.level() != level) {
            return null;
        }
        Vec3 ahead = wielder.position().add(Vec3.directionFromRotation(0.0F, wielder.getYRot()).scale(PLANT_AHEAD));
        Vec3 at = Hailpiercer.onSurface(level, ahead);
        BlockPos ground = BlockPos.containing(at).below();
        if (level.getBlockState(ground).getCollisionShape(level, ground).isEmpty()) {
            wielder.displayClientMessage(Component.translatable("message.hexwright.nourishment.no_ground"), true);
            return null;
        }

        int life = lifeTicks(blood);
        float size = Mth.lerp(fill(blood), SMALLEST_SIZE, LARGEST_SIZE);
        int sacks = Math.min(SACK_COUNT, blood / (LITRES_PER_SACK * NourishmentItem.LITRE));
        BloodTreeEntity tree = new BloodTreeEntity(level, wielder, forPlanterAlone, at, life, size, sacks);
        level.addFreshEntity(tree);

        RandomSource random = level.getRandom();
        level.sendParticles(GORE, at.x, at.y + 0.2D, at.z, 40, 0.6D, 0.2D, 0.6D, 0.15D);
        level.sendParticles(BLOOD_MIST, at.x, at.y + 0.5D, at.z, 24, 0.8D, 0.4D, 0.8D, 0.0D);
        level.playSound(null, at.x, at.y, at.z, NourishmentSounds.PLANT.get(), SoundSource.PLAYERS,
            1.2F, 0.9F + random.nextFloat() * 0.2F);
        level.playSound(null, at.x, at.y, at.z, NourishmentSounds.GROW.get(), SoundSource.PLAYERS, 1.5F, 0.9F);
        level.playSound(null, at.x, at.y + 4.0D, at.z, NourishmentSounds.DARKEN.get(), SoundSource.PLAYERS,
            2.0F, 1.0F);
        HexwrightDebug.log(HexwrightDebug.WEAPON,
            "[weapon] blood tree from {} at {}: {} ml, {} ticks, size {}, {} sacks, for planter alone {}",
            wielder.getScoreboardName(), at, blood, life, size, sacks, forPlanterAlone);
        return tree;
    }

    static int lifeTicks(int blood) {
        return SHORTEST_TICKS + Math.round((LONGEST_TICKS - SHORTEST_TICKS) * fill(blood));
    }

    private static float fill(int blood) {
        return Mth.clamp((blood - NourishmentItem.LITRE) / (float) (NourishmentItem.CAPACITY - NourishmentItem.LITRE),
            0.0F, 1.0F);
    }


    @Override
    public void tick() {
        super.tick();
        int age = age();
        if (this.level().isClientSide) {
            if (age >= GROW_TICKS / 2 && age < witherAt()) {
                bleed();
            }
            return;
        }
        if (age >= lifeTicks()) {
            this.discard();
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        if (age == witherAt()) {
            level.playSound(null, getX(), getY(), getZ(), NourishmentSounds.WITHER.get(), SoundSource.PLAYERS,
                1.5F, 1.0F);
            level.sendParticles(GORE, getX(), getY() + 0.3D, getZ(), 30, 1.0D, 0.2D, 1.0D, 0.1D);
        } else if (age >= GROW_TICKS && age < witherAt() && (age - GROW_TICKS) % HEARTBEAT_TICKS == 0) {
            level.playSound(null, getX(), getY() + 2.0D, getZ(), NourishmentSounds.HEARTBEAT.get(),
                SoundSource.PLAYERS, 1.5F, 0.9F);
        }
        if (age < witherAt() && age % SLOW_SWEEP_TICKS == 0) {
            slow(level);
        }
    }

    private void slow(ServerLevel level) {
        if (this.planter == null) {
            return;
        }
        ServerPlayer current = level.getServer().getPlayerList().getPlayer(this.planter.getUUID());
        ServerPlayer planter = current != null ? current : this.planter;
        AABB ground = new AABB(getX() - RADIUS, getY() - ZONE_FLOOR, getZ() - RADIUS,
            getX() + RADIUS, getY() + ZONE_CEILING, getZ() + RADIUS);
        for (LivingEntity enemy : level.getEntitiesOfClass(LivingEntity.class, ground,
                candidate -> TeleportStrike.strikeable(planter, candidate) && !Bosses.is(candidate)
                    && !(candidate instanceof ServerPlayer player
                        && (!this.forPlanterAlone || !TeleportStrike.mayTouch(planter, player)))
                    && distanceOutside(candidate.position()) == 0.0D)) {
            enemy.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, SLOW_AMPLIFIER,
                true, false, true), planter);
        }
    }

    private void bleed() {
        RandomSource random = this.random;
        Level level = this.level();
        if (random.nextInt(4) != 0) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            Vec3 edge = Hailpiercer.onSurface(level,
                position().add(Math.cos(angle) * RADIUS, 0.0D, Math.sin(angle) * RADIUS));
            level.addParticle(NourishmentParticles.BLOOD_WISP, edge.x, edge.y + 0.2D, edge.z,
                random.nextGaussian() * 0.02D, 0.05D + random.nextDouble() * 0.04D, random.nextGaussian() * 0.02D);
        }
        for (int n = 0; n < SACK_COUNT; n++) {
            if (hasSack(n) && random.nextInt(14) == 0) {
                Vec3 bottom = sack(n);
                level.addParticle(DRIP, bottom.x, bottom.y, bottom.z, 0.0D, 0.0D, 0.0D);
            }
        }
        if (random.nextInt(4) == 0) {
            Vec3 tip = onTree(TIPS[random.nextInt(TIPS.length)]);
            level.addParticle(DRIP, tip.x, tip.y, tip.z, 0.0D, 0.0D, 0.0D);
        }
        if (random.nextInt(2) == 0) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double out = random.nextDouble() * RADIUS;
            level.addParticle(ParticleTypes.CRIMSON_SPORE, getX() + Math.cos(angle) * out,
                getY() + random.nextDouble() * 6.0D, getZ() + Math.sin(angle) * out, 0.0D, 0.0D, 0.0D);
        }
    }

    public int age() {
        return (int) (this.level().getGameTime() - this.entityData.get(DATA_PLANTED_AT));
    }

    public int lifeTicks() {
        return this.entityData.get(DATA_LIFE);
    }

    public float size() {
        return this.entityData.get(DATA_SIZE);
    }

    private int witherAt() {
        return lifeTicks() - WITHER_TICKS;
    }

    public float presence() {
        int age = age();
        return Mth.clamp(Math.min(age / (float) GROW_TICKS, (lifeTicks() - age) / (float) WITHER_TICKS), 0.0F, 1.0F);
    }

    public float heartbeat(float partialTick) {
        float grown = age() + partialTick - GROW_TICKS;
        if (grown < 0.0F) {
            return 0.0F;
        }
        float t = grown % HEARTBEAT_TICKS;
        return Math.max(pulse(t, 3.0F), 0.7F * pulse(t, 9.6F));
    }

    private static float pulse(float t, float at) {
        return Math.max(0.0F, 1.0F - Math.abs(t - at) / 3.0F);
    }

    public double distanceOutside(Vec3 at) {
        double across = Math.max(0.0D, Math.sqrt(Mth.square(at.x - getX()) + Mth.square(at.z - getZ())) - RADIUS);
        double up = at.y - getY();
        double beyond = up < -ZONE_FLOOR ? -ZONE_FLOOR - up : Math.max(0.0D, up - ZONE_CEILING);
        return Math.sqrt(across * across + beyond * beyond);
    }

    public boolean hasSack(int n) {
        return (this.entityData.get(DATA_SACKS) & (1 << n)) != 0;
    }

    private Vec3 sack(int n) {
        return onTree(MOUTHS[n]).subtract(0.0D, SACK_HANG, 0.0D);
    }

    public Vec3 somewhereOnIt(RandomSource random) {
        Vec3 tip = TIPS[random.nextInt(TIPS.length)];
        float pick = random.nextFloat();
        if (pick < 0.4F) {
            return onTree(tip);
        }
        if (pick < 0.8F) {
            return onTree(new Vec3(0.0D, tip.y * 0.55D, 0.0D).lerp(tip, 0.35D + random.nextDouble() * 0.65D));
        }
        double angle = random.nextDouble() * Math.PI * 2.0D;
        return onTree(new Vec3(Math.cos(angle) * 0.6D, 0.4D + random.nextDouble() * 3.5D,
            Math.sin(angle) * 0.6D));
    }

    private Vec3 onTree(Vec3 local) {
        return position().add(local.scale(size()).yRot((180.0F - getYRot()) * Mth.DEG_TO_RAD));
    }


    private static boolean rebirth(ServerPlayer player, DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        BloodTreeEntity tree = level.getEntitiesOfClass(BloodTreeEntity.class,
                player.getBoundingBox().inflate(RADIUS, ZONE_CEILING, RADIUS), candidate -> candidate.holds(player))
            .stream()
            .min(Comparator.comparingDouble(candidate -> candidate.distanceToSqr(player)))
            .orElse(null);
        if (tree == null) {
            return false;
        }
        Vec3 sack = tree.burst(level, tree.nearestSack(player.position()));
        reborn(level, player, sack);
        HexwrightDebug.log(HexwrightDebug.WEAPON, "[weapon] blood tree at {} refused {}'s death ({})",
            tree.position(), player.getScoreboardName(), source.getMsgId());
        return true;
    }

    private boolean holds(ServerPlayer player) {
        return !this.isRemoved() && age() < witherAt() && this.entityData.get(DATA_SACKS) != 0
            && distanceOutside(player.position()) == 0.0D
            && (!this.forPlanterAlone || this.planter != null && this.planter.getUUID().equals(player.getUUID()));
    }

    private int nearestSack(Vec3 to) {
        int nearest = -1;
        double best = Double.MAX_VALUE;
        for (int n = 0; n < SACK_COUNT; n++) {
            double distance = hasSack(n) ? sack(n).distanceToSqr(to) : Double.MAX_VALUE;
            if (distance < best) {
                best = distance;
                nearest = n;
            }
        }
        return nearest;
    }

    private Vec3 burst(ServerLevel level, int n) {
        this.entityData.set(DATA_SACKS, (byte) (this.entityData.get(DATA_SACKS) & ~(1 << n)));
        Vec3 bottom = sack(n);
        Vec3 middle = bottom.add(0.0D, 0.5D, 0.0D);
        level.sendParticles(GORE, middle.x, middle.y, middle.z, 50, 0.35D, 0.45D, 0.35D, 0.2D);
        level.sendParticles(BLOOD_MIST, middle.x, middle.y, middle.z, 30, 0.4D, 0.5D, 0.4D, 0.0D);
        level.sendParticles(DRIP, middle.x, middle.y, middle.z, 20, 0.4D, 0.3D, 0.4D, 0.0D);
        level.playSound(null, middle.x, middle.y, middle.z, NourishmentSounds.BURST.get(), SoundSource.PLAYERS,
            1.3F, 0.9F + level.getRandom().nextFloat() * 0.2F);
        return bottom;
    }

    private static void reborn(ServerLevel level, ServerPlayer player, Vec3 sack) {
        player.setHealth(player.getMaxHealth());
        player.clearFire();
        List<MobEffect> harmful = player.getActiveEffects().stream()
            .map(MobEffectInstance::getEffect)
            .filter(effect -> effect.getCategory() == MobEffectCategory.HARMFUL)
            .toList();
        harmful.forEach(player::removeEffect);
        player.invulnerableTime = Math.max(player.invulnerableTime, REBORN_GRACE_TICKS);

        Vec3 out = roomFor(level, player, sack.subtract(0.0D, REBORN_BELOW_SACK, 0.0D));
        player.stopRiding();
        player.teleportTo(out.x, out.y, out.z);
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
        player.resetFallDistance();
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, REBORN_FALL_TICKS, 0, false, false, true));

        level.playSound(null, out.x, out.y, out.z, NourishmentSounds.REBORN.get(), SoundSource.PLAYERS, 1.2F, 1.0F);
        player.displayClientMessage(Component.translatable("message.hexwright.nourishment.reborn"), true);
    }

    private static Vec3 roomFor(ServerLevel level, ServerPlayer player, Vec3 at) {
        for (int step = 0; step < 8; step++) {
            Vec3 feet = at.subtract(0.0D, step * 0.5D, 0.0D);
            if (level.noCollision(player, player.getBoundingBox().move(feet.subtract(player.position())))) {
                return feet;
            }
        }
        return player.position();
    }


    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "tree", 0, state -> state.setAndContinue(clip())));
    }

    private RawAnimation clip() {
        if (age() >= witherAt()) {
            return WITHER;
        }
        if (this.firstSeen == null) {
            this.firstSeen = age() < GROW_TICKS ? GROW : STAND;
        }
        return this.firstSeen;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.animatableCache;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_SACKS, (byte) 0);
        this.entityData.define(DATA_PLANTED_AT, 0L);
        this.entityData.define(DATA_LIFE, SHORTEST_TICKS);
        this.entityData.define(DATA_SIZE, 1.0F);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean ignoreExplosion() {
        return true;
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
    public void push(Entity other) {
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }
}
