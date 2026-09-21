package com.bluup.hexwright.server.talisman;

import com.bluup.hexwright.server.hexpatterns.StoredHex;
import at.petrak.hexcasting.api.addldata.ADIotaHolder;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.EntityIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.NullIota;
import at.petrak.hexcasting.api.casting.iota.Vec3Iota;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class TalismanCasting {

    private static final double GAZE_REACH = 16.0;

    private static final float REPRIEVE_HEALTH = 1.0f;
    private static final int REPRIEVE_GRACE_TICKS = 40;

    private static int castDepth = 0;

    private TalismanCasting() {
    }

    public static boolean isCasting() {
        return castDepth > 0;
    }

    public static void onTrigger(ServerPlayer player, TalismanData.Trigger trigger,
                                 @Nullable Entity other, @Nullable Double magnitude) {
        onTrigger(player, trigger, other, magnitude, null);
    }

    public static void onTrigger(ServerPlayer player, TalismanData.Trigger trigger,
                                 @Nullable Entity other, @Nullable Double magnitude,
                                 @Nullable Vec3 position) {
        if (castDepth > 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        for (ItemStack stack : readyStacks(player, trigger)) {
            if (!(stack.getItem() instanceof TalismanItem)) {
                continue;
            }
            if (TalismanData.getTrigger(stack).orElse(null) != trigger) {
                continue;
            }
            if (!TalismanData.isArmed(stack)) {
                continue;
            }
            if (level.getGameTime() < TalismanData.getNextFire(stack)) {
                continue;
            }
            TalismanData.setNextFire(stack, level.getGameTime() + TalismanData.cooldownTicks(stack, trigger));
            fire(player, level, stack, other, magnitude, position);
        }
    }

    public static boolean onBrink(ServerPlayer player, @Nullable Entity source, double damage) {
        if (castDepth > 0) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        for (ItemStack stack : WornTalismans.getWorn(player)) {
            if (TalismanData.getTrigger(stack).orElse(null) != TalismanData.Trigger.BRINK) {
                continue;
            }
            if (!TalismanData.isArmed(stack)) {
                continue;
            }
            if (level.getGameTime() < TalismanData.getNextFire(stack)) {
                continue;
            }
            TalismanData.setNextFire(stack,
                level.getGameTime() + TalismanData.cooldownTicks(stack, TalismanData.Trigger.BRINK));

            player.setHealth(REPRIEVE_HEALTH);
            player.clearFire();
            player.invulnerableTime = Math.max(player.invulnerableTime, REPRIEVE_GRACE_TICKS);

            fire(player, level, stack, source, damage, null);

            if (player.getHealth() <= 0.0f) {
                return false;
            }
            announceReprieve(player, level);
            return true;
        }
        return false;
    }

    public static void onDied(ServerPlayer player) {
        for (ItemStack stack : WornTalismans.getWorn(player)) {
            resetReprieve(stack);
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            resetReprieve(player.getInventory().getItem(i));
        }
    }

    private static void resetReprieve(ItemStack stack) {
        if (stack.getItem() instanceof TalismanItem talisman
            && talisman.fixedTrigger() == TalismanData.Trigger.BRINK
            && TalismanData.getNextFire(stack) != 0L) {
            TalismanData.setNextFire(stack, 0L);
        }
    }

    private static void announceReprieve(ServerPlayer player, ServerLevel level) {
        level.playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
            player.getX(), player.getY(0.5), player.getZ(), 40, 0.4, 0.6, 0.4, 0.35);
    }

    private static List<ItemStack> readyStacks(ServerPlayer player, TalismanData.Trigger trigger) {
        if (trigger != TalismanData.Trigger.USE) {
            return WornTalismans.getWorn(player);
        }
        List<ItemStack> stacks = new ArrayList<>(12);
        for (int i = 0; i < 9; i++) {
            stacks.add(player.getInventory().getItem(i));
        }
        stacks.add(player.getOffhandItem());
        stacks.addAll(WornTalismans.getWorn(player));
        return stacks;
    }

    private static void fire(ServerPlayer player, ServerLevel level, ItemStack talisman,
                             @Nullable Entity other, @Nullable Double magnitude,
                             @Nullable Vec3 position) {
        ADIotaHolder holder = IXplatAbstractions.INSTANCE.findDataHolder(talisman);
        Iota iota = holder == null ? null : holder.readIota(level);
        List<Iota> hex = StoredHex.decode(iota);
        if (hex == null || hex.isEmpty()) {
            return;
        }

        TalismanData.Context context = TalismanData.getContext(talisman).orElse(TalismanData.Context.SELF);
        Iota seed = buildContextIota(player, context, other, magnitude, position);

        castDepth++;
        try {
            CastingImage seededImage = new CastingImage(
                List.of(seed),
                0,
                List.of(),
                false,
                0L,
                new CompoundTag()
            );
            CastingVM vm = new CastingVM(seededImage, new TalismanCastEnv(player, InteractionHand.MAIN_HAND));
            vm.queueExecuteAndWrapIotas(new ArrayList<>(hex), level);
        } catch (RuntimeException ignored) {
        } finally {
            castDepth--;
        }
    }

    private static Iota buildContextIota(ServerPlayer player, TalismanData.Context context,
                                         @Nullable Entity other, @Nullable Double magnitude,
                                         @Nullable Vec3 position) {
        return switch (context) {
            case SELF -> new EntityIota(player);
            case OTHER -> other != null ? new EntityIota(other) : new NullIota();
            case MAGNITUDE -> magnitude != null ? new DoubleIota(magnitude) : new NullIota();
            case GAZE -> gazeIota(player);
            case POSITION -> new Vec3Iota(position != null ? position : player.position());
        };
    }

    private static Iota gazeIota(ServerPlayer player) {
        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 look = player.getViewVector(1.0f);
        HitResult blockHit = player.pick(GAZE_REACH, 1.0f, false);
        Vec3 end = blockHit.getLocation();

        AABB search = player.getBoundingBox().expandTowards(look.scale(GAZE_REACH)).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
            player.level(), player, eye, end, search,
            entity -> !entity.isSpectator() && entity.isPickable());

        return entityHit == null ? new Vec3Iota(end) : new EntityIota(entityHit.getEntity());
    }

}
