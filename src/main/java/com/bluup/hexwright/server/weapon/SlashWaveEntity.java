package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.server.staff_assembly.HexwrightEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class SlashWaveEntity extends Entity {

    private static final double SPEED = 1.45D;

    public static final int LIFE_TICKS = 11;

    private static final float DAMAGE = 5.0F;

    private static final double KNOCKBACK = 0.35D;

    private static final double LAUNCH_HEIGHT = 1.3D;

    private static final double CUT_WIDTH = 1.8D;
    private static final double CUT_HEIGHT = 0.9D;
    private static final double CUT_DEPTH = 0.8D;

    private static final EntityDataAccessor<Byte> DATA_STYLE =
        SynchedEntityData.defineId(SlashWaveEntity.class, EntityDataSerializers.BYTE);

    private static final String TAG_OWNER = "Owner";
    private static final String TAG_AGE = "Age";

    @Nullable
    private UUID ownerUuid;

    private final Set<Integer> struck = new HashSet<>();

    private final List<LivingEntity> cut = new ArrayList<>();

    @Nullable
    private CompoundTag hex;

    private int age;

    public SlashWaveEntity(EntityType<? extends SlashWaveEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public static void launch(ServerLevel level, ServerPlayer player, SlashStyle style,
                              @Nullable CompoundTag hex) {
        SlashWaveEntity wave = new SlashWaveEntity(HexwrightEntities.SLASH_WAVE, level);
        wave.ownerUuid = player.getUUID();
        wave.hex = hex;
        wave.setStyle(style);
        wave.setYRot(player.getYRot());
        wave.setXRot(player.getXRot());
        wave.yRotO = wave.getYRot();
        wave.xRotO = wave.getXRot();

        Vec3 heading = wave.heading();
        Vec3 from = player.position().add(0.0D, LAUNCH_HEIGHT, 0.0D);
        wave.setPos(from.x, from.y, from.z);
        wave.setDeltaMovement(heading.scale(SPEED));
        level.addFreshEntity(wave);

        level.playSound(null, from.x, from.y, from.z,
            SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7F, 1.4F);
    }

    private Vec3 heading() {
        float yaw = this.getYRot() * Mth.DEG_TO_RAD;
        float pitch = this.getXRot() * Mth.DEG_TO_RAD;
        float cosPitch = Mth.cos(pitch);
        return new Vec3(-Mth.sin(yaw) * cosPitch, -Mth.sin(pitch), Mth.cos(yaw) * cosPitch);
    }

    @Override
    public void tick() {
        super.tick();

        Vec3 from = this.position();
        Vec3 to = from.add(heading().scale(SPEED));

        this.age++;
        if (this.level() instanceof ServerLevel serverLevel) {
            cut(serverLevel, from, to);
            HitResult wall = this.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
            if (wall.getType() != HitResult.Type.MISS || this.age >= LIFE_TICKS) {
                expire(serverLevel);
                return;
            }
        }

        this.setPos(to.x, to.y, to.z);
    }

    private void cut(ServerLevel level, Vec3 from, Vec3 to) {
        Entity owner = this.ownerUuid == null ? null : level.getEntity(this.ownerUuid);
        AABB swept = new AABB(from, to).inflate(CUT_WIDTH, CUT_HEIGHT, CUT_DEPTH);
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, swept,
            victim -> victim.isAlive() && victim != owner && !victim.isSpectator()
                && !this.struck.contains(victim.getId()));
        if (victims.isEmpty()) {
            return;
        }

        DamageSource damage = owner instanceof Player player
            ? level.damageSources().playerAttack(player)
            : level.damageSources().generic();
        Vec3 push = heading().scale(KNOCKBACK);
        for (LivingEntity victim : victims) {
            this.struck.add(victim.getId());
            this.cut.add(victim);
            victim.hurt(damage, DAMAGE);
            victim.push(push.x, KNOCKBACK * 0.4D, push.z);
            if (victim instanceof ServerPlayer hit) {
                hit.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(hit));
            } else {
                victim.hurtMarked = true;
            }
        }
    }

    private void expire(ServerLevel level) {
        castOnCut(level);
        this.discard();
    }

    private void castOnCut(ServerLevel level) {
        if (this.hex == null || this.cut.isEmpty() || this.ownerUuid == null) {
            return;
        }
        if (!(level.getEntity(this.ownerUuid) instanceof ServerPlayer wielder)) {
            return;
        }
        WeaponHexCasting.castOnCaught(wielder, this.hex, this.cut, this.position());
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_STYLE, (byte) SlashStyle.PALE.ordinal());
    }

    private void setStyle(SlashStyle style) {
        this.entityData.set(DATA_STYLE, (byte) style.ordinal());
    }

    public SlashStyle style() {
        return SlashStyle.byOrdinal(this.entityData.get(DATA_STYLE));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.age = tag.getInt(TAG_AGE);
        this.ownerUuid = tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt(TAG_AGE, this.age);
        if (this.ownerUuid != null) {
            tag.putUUID(TAG_OWNER, this.ownerUuid);
        }
    }

    public int age() {
        return this.age;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
