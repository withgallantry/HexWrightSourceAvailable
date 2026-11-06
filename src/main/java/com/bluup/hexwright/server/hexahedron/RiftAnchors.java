package com.bluup.hexwright.server.hexahedron;

import at.petrak.hexcasting.api.addldata.ADMediaHolder;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.mixin.ItemEntityAgeAccessor;
import com.bluup.hexwright.server.boss.BossDamage;
import com.bluup.hexwright.server.boss.CameraShake;
import com.bluup.hexwright.server.maintenance.PlanarMaintenance;
import com.bluup.hexwright.server.sound.HexwrightSoundEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class RiftAnchors {

    public static final TagKey<Item> OFFERINGS = TagKey.create(Registries.ITEM, Hexwright.id("rift_offerings"));

    private static final int RISE_TICKS = 40;
    private static final int DESPAWN_AGE = 6000;
    public static final double CATCH_RADIUS = 6.0;
    private static final double CATCH_BELOW = 8.0;
    private static final double CATCH_ABOVE = 24.0;
    private static final int GATHER_INTERVAL = 4;
    private static final double SHOVE_RADIUS = 14.0;
    private static final double SHAKE_RADIUS = 160.0;
    private static final double SEEN_RADIUS = 256.0;
    private static final double TOLD_RADIUS = 48.0;
    private static final String RISING_TAG = "hexwright_rift_offering";

    private record Rising(ResourceKey<Level> level, UUID hexahedron, int anchor, long media, long since,
                          @Nullable UUID giver) {
    }

    private static final Map<UUID, Rising> RISING = new HashMap<>();

    private RiftAnchors() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(RiftAnchors::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> RISING.clear());
    }

    public static boolean feed(ServerLevel level, Hexahedron hexahedron, int index, long media) {
        RiftAnchor anchor = hexahedron.anchors().get(index);
        if (anchor.fallen()) {
            return false;
        }
        if (anchor.groundY() == RiftAnchor.UNKNOWN) {
            Vec3 hung = hexahedron.anchorAt(index);
            anchor = anchor.onGround(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(hung.x), Mth.floor(hung.z)));
        }
        HexahedronStore.update(level, hexahedron.withAnchor(index, anchor.fed(media)));
        return true;
    }

    private static void tick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (HexahedronStore.isEmpty(level)) {
                continue;
            }
            long now = level.getGameTime();
            for (Hexahedron hexahedron : HexahedronStore.in(level)) {
                if (now == hexahedron.burstAt()) {
                    burst(level, hexahedron);
                }
                if (hexahedron.emergence(now) < 1.0f) {
                    continue;
                }
                Hexahedron tended = hexahedron;
                for (int i = 0; i < RiftAnchor.COUNT; i++) {
                    tended = tend(level, tended, i, now);
                }
                if (tended != hexahedron) {
                    HexahedronStore.update(level, tended);
                }
            }
        }
        lift(server);
    }

    private static Hexahedron tend(ServerLevel level, Hexahedron hexahedron, int index, long now) {
        RiftAnchor anchor = hexahedron.anchors().get(index);
        Vec3 hung = hexahedron.anchorAt(index);
        if (anchor.fallen()) {
            if (!anchor.landed() && now >= anchor.impactAt()) {
                land(level, hexahedron, index, anchor);
                return hexahedron.withAnchor(index, anchor.landing());
            }
            if (anchor.landed() && now < anchor.impactAt() + RiftAnchor.DISSOLVE_TICKS && now % 2 == 0) {
                Vec3 lying = new Vec3(hung.x, anchor.groundY() + RiftAnchor.HALF_LENGTH * 0.4, hung.z);
                double across = RiftAnchor.RADIUS * 1.5;
                double up = RiftAnchor.HALF_LENGTH * 0.5;
                spray(level, lying, ParticleTypes.END_ROD, 6, across, up, across, 0.08);
                spray(level, lying, ParticleTypes.ELECTRIC_SPARK, 4, across, up, across, 0.3);
            }
            return hexahedron;
        }
        if (anchor.media() >= RiftAnchor.MEDIA_NEEDED && anchor.groundY() != RiftAnchor.UNKNOWN) {
            return hexahedron.withAnchor(index, tear(level, hexahedron, index, anchor, now));
        }
        int x = Mth.floor(hung.x);
        int z = Mth.floor(hung.z);
        if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) {
            return hexahedron;
        }
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        RiftAnchor tended = ground == anchor.groundY() ? anchor : anchor.onGround(ground);
        if (now % GATHER_INTERVAL == 0) {
            gather(level, hexahedron, index, tended, hung, now);
        }
        return tended == anchor ? hexahedron : hexahedron.withAnchor(index, tended);
    }


    private static void gather(ServerLevel level, Hexahedron hexahedron, int index, RiftAnchor anchor, Vec3 hung, long now) {
        long wanted = RiftAnchor.MEDIA_NEEDED - anchor.media() - onTheWay(hexahedron.id(), index);
        if (wanted <= 0) {
            return;
        }
        AABB catchment = new AABB(hung.x - CATCH_RADIUS, anchor.groundY() - CATCH_BELOW, hung.z - CATCH_RADIUS,
            hung.x + CATCH_RADIUS, anchor.groundY() + CATCH_ABOVE, hung.z + CATCH_RADIUS);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, catchment, RiftAnchors::offered)) {
            ItemStack stack = item.getItem();
            long each = mediaOf(stack) / stack.getCount();
            if (each <= 0) {
                continue;
            }
            int taken = (int) Math.min(stack.getCount(), (wanted + each - 1) / each);
            UUID giver = item.getOwner() instanceof ServerPlayer thrower ? thrower.getUUID() : null;
            ItemEntity offering = item;
            if (taken < stack.getCount()) {
                item.setItem(stack.copyWithCount(stack.getCount() - taken));
                offering = new ItemEntity(level, item.getX(), item.getY(), item.getZ(), stack.copyWithCount(taken), 0.0, 0.0, 0.0);
                level.addFreshEntity(offering);
            }
            offering.addTag(RISING_TAG);
            offering.setNoGravity(true);
            offering.setNeverPickUp();
            ((ItemEntityAgeAccessor) offering).hexwright$setAge(DESPAWN_AGE - RISE_TICKS - 5);
            RISING.put(offering.getUUID(), new Rising(level.dimension(), hexahedron.id(), index, each * taken, now, giver));
            level.playSound(null, offering.getX(), offering.getY(), offering.getZ(), HexahedronSounds.OFFERING.get(),
                SoundSource.AMBIENT, 1.5f, 0.9f + level.getRandom().nextFloat() * 0.2f);
            wanted -= each * taken;
            if (wanted <= 0) {
                return;
            }
        }
    }

    private static boolean offered(ItemEntity item) {
        return item.isAlive() && !item.getTags().contains(RISING_TAG) && item.getItem().is(OFFERINGS);
    }

    private static long mediaOf(ItemStack stack) {
        ADMediaHolder holder = IXplatAbstractions.INSTANCE.findMediaHolder(stack);
        return holder == null ? 0L : Math.max(holder.getMedia(), 0L);
    }

    private static long onTheWay(UUID hexahedron, int anchor) {
        long media = 0L;
        for (Rising rising : RISING.values()) {
            if (rising.hexahedron().equals(hexahedron) && rising.anchor() == anchor) {
                media += rising.media();
            }
        }
        return media;
    }

    private static void lift(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Rising>> entries = RISING.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Rising> entry = entries.next();
            Rising rising = entry.getValue();
            ServerLevel level = server.getLevel(rising.level());
            Entity entity = level == null ? null : level.getEntity(entry.getKey());
            Hexahedron hexahedron = level == null ? null : find(level, rising.hexahedron());
            if (!(entity instanceof ItemEntity offering) || !offering.isAlive() || hexahedron == null) {
                entries.remove();
                continue;
            }
            long age = level.getGameTime() - rising.since();
            if (age >= RISE_TICKS) {
                long media = Math.min(rising.media(), mediaOf(offering.getItem()));
                Vec3 at = offering.position();
                offering.discard();
                entries.remove();
                taken(level, hexahedron, rising.anchor(), media, at, rising.giver());
                continue;
            }
            Vec3 hung = hexahedron.anchorAt(rising.anchor());
            offering.setDeltaMovement((hung.x - offering.getX()) * 0.06, 0.08 + 0.01 * age, (hung.z - offering.getZ()) * 0.06);
            offering.hasImpulse = true;
            if (age % 2 == 0) {
                level.sendParticles(ParticleTypes.END_ROD, offering.getX(), offering.getY() + 0.2, offering.getZ(), 1,
                    0.05, 0.05, 0.05, 0.0);
            }
        }
    }

    private static void taken(ServerLevel level, Hexahedron hexahedron, int index, long media, Vec3 at, @Nullable UUID giver) {
        RiftAnchor anchor = hexahedron.anchors().get(index);
        if (anchor.fallen() || media <= 0) {
            return;
        }
        RiftAnchor fed = anchor.fed(media);
        HexahedronStore.update(level, hexahedron.withAnchor(index, fed));
        if (giver != null) {
            PlanarMaintenance.fed(level.getServer(), hexahedron.id(), giver);
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.2, at.z, 10, 0.25, 0.25, 0.25, 0.15);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 0.2, at.z, 4, 0.2, 0.2, 0.2, 0.05);
        Component progress = Component.translatable("message.hexwright.hexahedron.anchor_fed",
            Mth.floor(fed.fill() * 100.0f)).withStyle(ChatFormatting.AQUA);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(at) < TOLD_RADIUS * TOLD_RADIUS) {
                player.displayClientMessage(progress, true);
            }
        }
        HexwrightDebug.log(HexwrightDebug.HEXAHEDRON, "[anchor] {} #{} took {} dust, now {}%", hexahedron.id(), index,
            media / (double) MediaConstants.DUST_UNIT, Mth.floor(fed.fill() * 100.0f));
    }


    private static RiftAnchor tear(ServerLevel level, Hexahedron hexahedron, int index, RiftAnchor anchor, long now) {
        Vec3 hung = hexahedron.anchorAt(index);
        level.playSound(null, hung.x, hung.y, hung.z, HexahedronSounds.ANCHOR_TEAR.get(), SoundSource.AMBIENT, 24.0f, 1.0f);
        long down = hexahedron.anchors().stream().filter(RiftAnchor::fallen).count() + 1;
        announce(level, hexahedron, Component.translatable("message.hexwright.hexahedron.anchor_torn", down, RiftAnchor.COUNT));
        HexwrightDebug.log(HexwrightDebug.HEXAHEDRON, "[anchor] {} #{} tore free over {} {}, ground {}", hexahedron.id(), index,
            Mth.floor(hung.x), Mth.floor(hung.z), anchor.groundY());
        return anchor.tearing(now, anchor.groundY());
    }

    private static void land(ServerLevel level, Hexahedron hexahedron, int index, RiftAnchor anchor) {
        Vec3 hung = hexahedron.anchorAt(index);
        Vec3 at = new Vec3(hung.x, anchor.groundY(), hung.z);
        level.playSound(null, at.x, at.y + 2.0, at.z, HexahedronSounds.ANCHOR_IMPACT.get(), SoundSource.AMBIENT, 32.0f, 0.85f);
        level.playSound(null, at.x, at.y + 2.0, at.z, HexwrightSoundEvents.bindstoneClaim(), SoundSource.AMBIENT, 4.0f, 0.75f);
        CameraShake.shake(level, at, SHAKE_RADIUS, 7.0f);
        BlockState ground = level.getBlockState(BlockPos.containing(at).below());
        if (!ground.isAir()) {
            spray(level, at.add(0.0, 0.5, 0.0), new BlockParticleOption(ParticleTypes.BLOCK, ground), 300, 8.0, 0.6, 8.0, 0.6);
        }
        spray(level, at.add(0.0, 2.0, 0.0), ParticleTypes.EXPLOSION_EMITTER, 5, 6.0, 1.0, 6.0, 0.0);
        spray(level, at.add(0.0, 4.0, 0.0), ParticleTypes.FLASH, 1, 0.0, 0.0, 0.0, 0.0);
        spray(level, at.add(0.0, 3.0, 0.0), ParticleTypes.END_ROD, 80, 4.0, 2.0, 4.0, 0.4);
        AABB under = new AABB(at, at).inflate(SHOVE_RADIUS, 8.0, SHOVE_RADIUS);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, under)) {
            if (!player.isSpectator()) {
                BossDamage.knock(player, player.position().subtract(at), 1.8, 0.7);
            }
        }
        for (ServerPlayer player : level.players()) {
            if (hexahedron.covers(player.position())) {
                player.playNotifySound(HexahedronSounds.LURCH.get(), SoundSource.AMBIENT, 1.0f, 0.6f);
            }
        }
        boolean last = true;
        for (int i = 0; i < RiftAnchor.COUNT; i++) {
            last &= i == index || hexahedron.anchors().get(i).landed();
        }
        if (last) {
            announce(level, hexahedron, Component.translatable("message.hexwright.hexahedron.anchors_down"));
        }
        HexwrightDebug.log(HexwrightDebug.HEXAHEDRON, "[anchor] {} #{} landed at {}{}", hexahedron.id(), index,
            BlockPos.containing(at).toShortString(), last ? ", the last" : "");
    }

    private static void burst(ServerLevel level, Hexahedron hexahedron) {
        for (ServerPlayer player : level.players()) {
            if (hexahedron.covers(player.position())) {
                player.playNotifySound(HexahedronSounds.BURST.get(), SoundSource.AMBIENT, 1.0f, 1.0f);
                CameraShake.kick(player, 2.0f);
            }
        }
        HexwrightDebug.log(HexwrightDebug.HEXAHEDRON, "[anchor] {} burst", hexahedron.id());
    }

    private static void spray(ServerLevel level, Vec3 at, ParticleOptions particle, int count,
                              double dx, double dy, double dz, double speed) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(at) < SEEN_RADIUS * SEEN_RADIUS) {
                level.sendParticles(player, particle, true, at.x, at.y, at.z, count, dx, dy, dz, speed);
            }
        }
    }

    private static void announce(ServerLevel level, Hexahedron hexahedron, Component message) {
        Component styled = message.copy().withStyle(ChatFormatting.DARK_PURPLE);
        for (ServerPlayer player : level.players()) {
            if (hexahedron.covers(player.position())) {
                player.sendSystemMessage(styled);
            }
        }
    }

    private static @Nullable Hexahedron find(ServerLevel level, UUID id) {
        for (Hexahedron hexahedron : HexahedronStore.in(level)) {
            if (hexahedron.id().equals(id)) {
                return hexahedron;
            }
        }
        return null;
    }
}
