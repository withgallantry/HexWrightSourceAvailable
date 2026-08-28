package com.bluup.hexwright.server.portal;

import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.mixin.EntityLevelAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class PortalInteraction {

    private static final double REACH_SURVIVAL = 4.5;
    private static final double REACH_CREATIVE = 5.0;

    private static final double MENU_RANGE_SQ = 64.0;

    public static final int ATTACK_START = 0;
    public static final int ATTACK_CONTINUE = 1;
    public static final int ATTACK_STOP = 2;

    private static final int MINING_LAPSE_TICKS = 5;

    private static final int CREATIVE_DESTROY_DELAY = 5;

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private static final Map<UUID, Mining> MINING = new ConcurrentHashMap<>();

    private record Session(AbstractContainerMenu menu, UUID pairId, int side,
                           ResourceKey<Level> destDimension,
                           BlockPos target, Block block) {
    }

    private static final class Mining {

        private final UUID pairId;
        private final int side;
        private final ResourceKey<Level> dimension;
        private final BlockPos pos;
        private final long startTick;
        private long lastTick;
        private int lastStage = -1;

        Mining(UUID pairId, int side, ResourceKey<Level> dimension, BlockPos pos, long tick) {
            this.pairId = pairId;
            this.side = side;
            this.dimension = dimension;
            this.pos = pos;
            this.startTick = tick;
            this.lastTick = tick;
        }

        boolean sameTarget(UUID pairId, int side, ResourceKey<Level> dimension, BlockPos pos) {
            return this.pairId.equals(pairId) && this.side == side
                && this.dimension.equals(dimension) && this.pos.equals(pos);
        }
    }

    private record Fold(PortalPair pair, ServerLevel destLevel, Vec3 from, Vec3 to) {
    }

    private PortalInteraction() {
    }

    private static @Nullable Fold fold(ServerPlayer player, UUID pairId, int side) {
        if (side != 0 && side != 1 || player.isSpectator()) {
            return null;
        }
        ServerLevel level = player.serverLevel();
        PortalPair pair = PortalManager.get(level).byId(pairId);
        if (pair == null || !pair.isOpen(level.getGameTime())
            || !pair.sideIn(side, level.dimension())) {
            return null;
        }
        ServerLevel destLevel = level;
        var destDim = pair.dimensionOr(1 - side, level.dimension());
        if (!destDim.equals(level.dimension())) {
            destLevel = level.getServer().getLevel(destDim);
            if (destLevel == null) {
                return null;
            }
        }

        PortalWindow window = pair.window(side);
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        double reach = player.isCreative() ? REACH_CREATIVE : REACH_SURVIVAL;
        double tHit = window.rayHit(eye, look, reach);
        if (tHit < 0.0) {
            return null;
        }

        PortalTransform transform = pair.transformFrom(side);
        Vec3 exitDir = transform.applyDirection(look);
        Vec3 exitPoint = transform.apply(eye.add(look.scale(tHit)));
        return new Fold(pair, destLevel,
            exitPoint.add(exitDir.scale(0.005)),
            exitPoint.add(exitDir.scale(reach - tHit)));
    }

    public static void handleUse(ServerPlayer player, UUID pairId, int side) {
        Fold fold = fold(player, pairId, side);
        if (fold == null) {
            return;
        }
        ServerLevel destLevel = fold.destLevel();
        HitResult hit = resolveTarget(destLevel, player, fold.from(), fold.to());
        if (hit == null) {
            return;
        }

        for (InteractionHand hand : InteractionHand.values()) {
            InteractionResult result;
            if (hit instanceof EntityHitResult entityHit) {
                result = player.interactOn(entityHit.getEntity(), hand);
            } else {
                BlockHitResult blockHit = (BlockHitResult) hit;
                AbstractContainerMenu before = player.containerMenu;
                result = inDestination(player, destLevel, () -> player.gameMode.useItemOn(
                    player, destLevel, player.getItemInHand(hand), hand, blockHit));
                if (player.containerMenu != before && player.containerMenu != player.inventoryMenu) {
                    SESSIONS.put(player.getUUID(), new Session(
                        player.containerMenu, pairId, side, destLevel.dimension(), blockHit.getBlockPos(),
                        destLevel.getBlockState(blockHit.getBlockPos()).getBlock()));
                }
            }
            if (result.consumesAction()) {
                if (result.shouldSwing()) {
                    player.swing(hand, true);
                }
                break;
            }
        }
    }


    public static void handleAttack(ServerPlayer player, UUID pairId, int side, int action) {
        if (action == ATTACK_STOP) {
            abandonMining(player);
            return;
        }
        Fold fold = fold(player, pairId, side);
        if (fold == null) {
            abandonMining(player);
            return;
        }
        HitResult hit = resolveTarget(fold.destLevel(), player, fold.from(), fold.to());
        if (hit == null) {
            abandonMining(player);
            return;
        }
        if (hit instanceof EntityHitResult entityHit) {
            abandonMining(player);
            if (action == ATTACK_START) {
                player.attack(entityHit.getEntity());
            }
            return;
        }
        mine(player, fold, side, ((BlockHitResult) hit).getBlockPos(), action == ATTACK_START);
    }

    private static void mine(ServerPlayer player, Fold fold, int side, BlockPos pos, boolean pressed) {
        ServerLevel destLevel = fold.destLevel();
        BlockState state = destLevel.getBlockState(pos);
        if (state.isAir() || !destLevel.mayInteract(player, pos)) {
            abandonMining(player);
            return;
        }
        UUID id = player.getUUID();
        ResourceKey<Level> dimension = destLevel.dimension();
        long tick = destLevel.getGameTime();

        if (player.isCreative()) {
            Mining last = MINING.get(id);
            if (!pressed && last != null && tick - last.startTick < CREATIVE_DESTROY_DELAY) {
                return;
            }
            abandonMining(player);
            MINING.put(id, new Mining(fold.pair().id(), side, dimension, pos, tick));
            destroy(player, destLevel, pos);
            return;
        }

        float perTick = state.getDestroyProgress(player, destLevel, pos);
        Mining mining = MINING.get(id);
        if (pressed || mining == null
            || !mining.sameTarget(fold.pair().id(), side, dimension, pos)
            || tick - mining.lastTick > MINING_LAPSE_TICKS) {
            abandonMining(player);
            state.attack(destLevel, pos, player);
            mining = new Mining(fold.pair().id(), side, dimension, pos, tick);
            MINING.put(id, mining);
            if (perTick >= 1.0f) {
                MINING.remove(id);
                destroy(player, destLevel, pos);
                return;
            }
        }
        mining.lastTick = tick;

        float progress = perTick * (tick - mining.startTick + 1);
        int stage = (int) (progress * 10.0f);
        if (stage != mining.lastStage) {
            mining.lastStage = stage;
            sendProgress(player, destLevel, pos, stage);
        }
        if (progress >= 1.0f) {
            MINING.remove(id);
            sendProgress(player, destLevel, pos, -1);
            destroy(player, destLevel, pos);
        }
    }

    private static void destroy(ServerPlayer player, ServerLevel destLevel, BlockPos pos) {
        inDestination(player, destLevel, () -> player.gameMode.destroyBlock(pos));
    }

    private static <T> T inDestination(ServerPlayer player, ServerLevel destLevel, Supplier<T> interaction) {
        ServerLevel own = player.serverLevel();
        if (destLevel == own) {
            return interaction.get();
        }
        EntityLevelAccessor entity = (EntityLevelAccessor) player;
        entity.hexwright$setLevel(destLevel);
        player.gameMode.setLevel(destLevel);
        try {
            return interaction.get();
        } finally {
            entity.hexwright$setLevel(own);
            player.gameMode.setLevel(own);
        }
    }

    private static void sendProgress(ServerPlayer player, ServerLevel destLevel, BlockPos pos, int stage) {
        destLevel.destroyBlockProgress(player.getId(), pos, stage);
        if (destLevel.dimension().equals(player.level().dimension())) {
            player.connection.send(new ClientboundBlockDestructionPacket(player.getId(), pos, stage));
        } else {
            HexwrightNetworking.sendVaultRemoteDestroyProgress(
                player, destLevel.dimension().location(), player.getId(), pos, stage);
        }
    }

    private static void abandonMining(ServerPlayer player) {
        Mining mining = MINING.remove(player.getUUID());
        if (mining == null || mining.lastStage < 0 || player.getServer() == null) {
            return;
        }
        ServerLevel level = player.getServer().getLevel(mining.dimension);
        if (level != null) {
            sendProgress(player, level, mining.pos, -1);
        }
    }

    private static @Nullable HitResult resolveTarget(ServerLevel level, ServerPlayer player, Vec3 from, Vec3 to) {
        BlockHitResult blockHit = level.clip(new ClipContext(
            from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        double blockDistSq = blockHit.getType() == HitResult.Type.BLOCK
            ? blockHit.getLocation().distanceToSqr(from)
            : Double.MAX_VALUE;

        Vec3 entityRayEnd = blockDistSq == Double.MAX_VALUE ? to : blockHit.getLocation();
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, player, from, entityRayEnd,
            new AABB(from, entityRayEnd).inflate(1.0),
            entity -> entity != player && entity.isAlive() && entity.isPickable());
        if (entityHit != null) {
            return entityHit;
        }
        return blockHit.getType() == HitResult.Type.BLOCK ? blockHit : null;
    }

    public static boolean menuStillValid(ServerPlayer player, AbstractContainerMenu menu, boolean vanillaValid) {
        if (vanillaValid) {
            return true;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return false;
        }
        if (session.menu() != menu) {
            SESSIONS.remove(player.getUUID());
            return false;
        }
        ServerLevel level = player.serverLevel();
        PortalPair pair = PortalManager.get(level).byId(session.pairId());
        if (pair == null || !pair.isOpen(level.getGameTime())
            || !pair.sideIn(session.side(), level.dimension())) {
            SESSIONS.remove(player.getUUID());
            return false;
        }
        PortalWindow window = pair.window(session.side());
        Vec3 eye = player.getEyePosition();
        if (window.distanceSqToRect(eye) > MENU_RANGE_SQ) {
            return false;
        }
        ServerLevel destLevel = session.destDimension().equals(level.dimension())
            ? level
            : level.getServer().getLevel(session.destDimension());
        if (destLevel == null || !destLevel.getBlockState(session.target()).is(session.block())) {
            SESSIONS.remove(player.getUUID());
            return false;
        }
        Vec3 foldedEye = pair.transformFrom(session.side()).apply(eye);
        return foldedEye.distanceToSqr(Vec3.atCenterOf(session.target())) <= MENU_RANGE_SQ;
    }

    public static void forget(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
        MINING.remove(player.getUUID());
    }

    public static void forgetPair(UUID pairId) {
        SESSIONS.values().removeIf(session -> session.pairId().equals(pairId));
        MINING.values().removeIf(mining -> mining.pairId.equals(pairId));
    }
}
