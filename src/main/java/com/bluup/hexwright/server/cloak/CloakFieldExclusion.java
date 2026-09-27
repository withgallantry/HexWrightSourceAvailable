package com.bluup.hexwright.server.cloak;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public final class CloakFieldExclusion {

    private static final double SET_DOWN_MARGIN = 2.0;
    private static final int SET_DOWN_SEARCH = 48;

    private CloakFieldExclusion() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(CloakFieldExclusion::expelTrespassers);
    }

    public static boolean excludes(Level level, Vec3 point, @Nullable Entity who) {
        return excluding(level, point, who) != null;
    }

    private static @Nullable CloakField excluding(Level level, Vec3 point, @Nullable Entity who) {
        for (CloakField field : CloakFieldStore.fieldsIn(level)) {
            if (field.encloses(point) && !field.admits(who)) {
                return field;
            }
        }
        return null;
    }

    public static void pruneBlast(ServerLevel level, List<BlockPos> positions, @Nullable Entity igniter) {
        Collection<CloakField> fields = CloakFieldStore.fieldsIn(level);
        if (fields.isEmpty()) {
            return;
        }
        positions.removeIf(pos -> {
            for (CloakField field : fields) {
                if (field.encloses(Vec3.atCenterOf(pos)) && !field.admits(igniter)) {
                    return true;
                }
            }
            return false;
        });
    }

    private static void expelTrespassers(ServerLevel level) {
        if (level.players().isEmpty() || CloakFieldStore.fieldsIn(level).isEmpty()) {
            return;
        }
        for (ServerPlayer player : List.copyOf(level.players())) {
            if (player.isSpectator() || player.isCreative()) {
                continue;
            }
            CloakField field = excluding(level, player.getBoundingBox().getCenter(), player);
            if (field != null) {
                expel(level, player, field);
            }
        }
    }

    private static void expel(ServerLevel level, ServerPlayer player, CloakField field) {
        Vec3 c = field.center();
        Vec3 out = new Vec3(player.getX() - c.x, 0.0, player.getZ() - c.z);
        out = out.lengthSqr() < 1.0e-4 ? new Vec3(1.0, 0.0, 0.0) : out.normalize();
        double reach = field.radius() + SET_DOWN_MARGIN;
        double x = c.x + out.x * reach;
        double z = c.z + out.z * reach;
        double y = standingY(level, player, x, z);

        player.stopRiding();
        player.teleportTo(x, y, z);
        player.resetFallDistance();
        player.displayClientMessage(Component.translatable("message.hexwright.cloak.turned_away"), true);
    }

    private static double standingY(ServerLevel level, ServerPlayer player, double x, double z) {
        int from = Mth.floor(player.getY());
        AABB body = player.getBoundingBox().move(-player.getX(), -player.getY(), -player.getZ());
        for (int step = 0; step <= SET_DOWN_SEARCH * 2; step++) {
            int y = from + ((step & 1) == 0 ? step / 2 : -(step + 1) / 2);
            if (y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
                continue;
            }
            BlockPos feet = BlockPos.containing(x, y, z);
            if (level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
                && level.noCollision(player, body.move(x, y, z))) {
                return y;
            }
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
    }
}
