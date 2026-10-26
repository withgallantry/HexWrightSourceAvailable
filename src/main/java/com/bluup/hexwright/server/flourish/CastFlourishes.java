package com.bluup.hexwright.server.flourish;

import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.pigment.FrozenPigment;
import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.common.flourish.Flourish;
import com.bluup.hexwright.inits.HexwrightNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class CastFlourishes {

    private static final int BURST = 4;

    private static final int RECHARGE_TICKS = 5;

    private static final double LINK = 2.0;

    private static final double MAX_EXTENT = 6.0;

    private static final double SEND_RADIUS = 64.0;

    private static final Map<ServerPlayer, Budget> BUDGETS = new WeakHashMap<>();

    private static final Map<ServerPlayer, List<Held>> HELD = new LinkedHashMap<>();

    private record Held(ServerLevel level, ParticleSpray spray, FrozenPigment pigment) {
    }

    private CastFlourishes() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(CastFlourishes::flush);
    }

    public static boolean replaces(CastingEnvironment env, ParticleSpray spray) {
        if (!(env.getCastingEntity() instanceof ServerPlayer caster) || !DeliberateCasts.isDeliberate(env)) {
            return false;
        }
        if (FlourishState.get(caster.server).flourish(caster.getUUID()) == null) {
            return false;
        }
        HELD.computeIfAbsent(caster, key -> new ArrayList<>()).add(new Held(env.getWorld(), spray, env.getPigment()));
        return true;
    }

    private static void flush(MinecraftServer server) {
        if (HELD.isEmpty()) {
            return;
        }
        long tick = server.getTickCount();
        FlourishState state = FlourishState.get(server);
        for (Map.Entry<ServerPlayer, List<Held>> entry : HELD.entrySet()) {
            ServerPlayer caster = entry.getKey();
            Flourish flourish = state.flourish(caster.getUUID());
            List<List<Held>> clusters = clusters(entry.getValue());
            Budget budget = BUDGETS.computeIfAbsent(caster, key -> new Budget());
            int shown = 0;
            for (List<Held> cluster : clusters) {
                if (flourish != null && budget.spend(tick)) {
                    send(caster, flourish, cluster);
                    shown++;
                } else {
                    for (Held held : cluster) {
                        held.spray().sprayParticles(held.level(), held.pigment());
                    }
                }
            }
            if (HexwrightDebug.on(HexwrightDebug.CASTING)) {
                HexwrightDebug.log(HexwrightDebug.CASTING, "[flourish] {}: {} sprays -> {} clusters, {} flourished",
                    caster.getGameProfile().getName(), entry.getValue().size(), clusters.size(), shown);
            }
        }
        HELD.clear();
    }

    private static void send(ServerPlayer caster, Flourish flourish, List<Held> cluster) {
        Held first = cluster.get(0);
        Vec3 centre;
        float extent;
        Vec3 axis;
        if (cluster.size() == 1) {
            centre = first.spray().getPos();
            extent = extentOf(first.spray());
            axis = axisOf(first.spray());
        } else {
            Vec3 sum = Vec3.ZERO;
            for (Held held : cluster) {
                sum = sum.add(held.spray().getPos());
            }
            centre = sum.scale(1.0 / cluster.size());
            double reach = 0.0;
            for (Held held : cluster) {
                reach = Math.max(reach, held.spray().getPos().distanceTo(centre));
            }
            extent = (float) Mth.clamp(reach + 0.5, 0.4, MAX_EXTENT);
            axis = Vec3.ZERO;
        }
        FlourishCast cast = new FlourishCast(flourish, centre, extent, axis, caster.getId(),
            first.level().random.nextInt(), first.pigment().serializeToNBT());
        HexwrightNetworking.sendFlourish(first.level(), cast, SEND_RADIUS);
    }

    private static List<List<Held>> clusters(List<Held> held) {
        int count = held.size();
        int[] parent = new int[count];
        Map<Long, List<Integer>> cells = new HashMap<>();
        for (int i = 0; i < count; i++) {
            parent[i] = i;
            Held spray = held.get(i);
            Vec3 pos = spray.spray().getPos();
            int cx = Mth.floor(pos.x / LINK);
            int cy = Mth.floor(pos.y / LINK);
            int cz = Mth.floor(pos.z / LINK);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        List<Integer> near = cells.get(BlockPos.asLong(cx + dx, cy + dy, cz + dz));
                        if (near == null) {
                            continue;
                        }
                        for (int j : near) {
                            Held other = held.get(j);
                            if (other.level() == spray.level()
                                && other.spray().getPos().distanceToSqr(pos) <= LINK * LINK) {
                                union(parent, i, j);
                            }
                        }
                    }
                }
            }
            cells.computeIfAbsent(BlockPos.asLong(cx, cy, cz), key -> new ArrayList<>()).add(i);
        }

        Map<Integer, List<Held>> byRoot = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            byRoot.computeIfAbsent(find(parent, i), key -> new ArrayList<>()).add(held.get(i));
        }
        List<List<Held>> clusters = new ArrayList<>();
        for (List<Held> cluster : byRoot.values()) {
            clusters.addAll(split(cluster));
        }
        clusters.sort(Comparator.comparingInt(List<Held>::size).reversed());
        return clusters;
    }

    private static List<List<Held>> split(List<Held> cluster) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (Held held : cluster) {
            Vec3 pos = held.spray().getPos();
            minX = Math.min(minX, pos.x);
            minY = Math.min(minY, pos.y);
            minZ = Math.min(minZ, pos.z);
            maxX = Math.max(maxX, pos.x);
            maxY = Math.max(maxY, pos.y);
            maxZ = Math.max(maxZ, pos.z);
        }
        if (Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ)) <= MAX_EXTENT * 2.0) {
            return List.of(cluster);
        }
        double piece = MAX_EXTENT * 2.0;
        Map<Long, List<Held>> pieces = new LinkedHashMap<>();
        for (Held held : cluster) {
            Vec3 pos = held.spray().getPos();
            pieces.computeIfAbsent(BlockPos.asLong(Mth.floor(pos.x / piece), Mth.floor(pos.y / piece),
                Mth.floor(pos.z / piece)), key -> new ArrayList<>()).add(held);
        }
        return new ArrayList<>(pieces.values());
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        parent[find(parent, a)] = find(parent, b);
    }

    private static float extentOf(ParticleSpray spray) {
        double reach = spray.getFuzziness() * 0.5;
        if (spray.getSpread() > 1.0) {
            reach = Math.max(reach, spray.getVel().length() * 0.75);
        }
        return (float) Mth.clamp(reach, 0.4, 3.0);
    }

    private static Vec3 axisOf(ParticleSpray spray) {
        Vec3 vel = spray.getVel();
        if (spray.getSpread() < 1.0 && vel.lengthSqr() > 1e-4) {
            return vel.normalize();
        }
        return Vec3.ZERO;
    }

    private static final class Budget {
        private double tokens = BURST;
        private long lastTick;

        boolean spend(long tick) {
            tokens = Math.min(BURST, tokens + (tick - lastTick) / (double) RECHARGE_TICKS);
            lastTick = tick;
            if (tokens < 1.0) {
                return false;
            }
            tokens -= 1.0;
            return true;
        }
    }
}
