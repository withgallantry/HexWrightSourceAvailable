package com.bluup.hexwright.client.armour;

import com.bluup.hexwright.Hexwright;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.util.RenderUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public final class PassagePortalVisualClient {

    private static final int LEAVE_TICKS = 31;
    private static final int JOIN_TICKS = 33;

    private static final int JOIN_SETTLED_TICKS = 23;

    private static final int ARRIVAL_GRACE_TICKS = 5;

    private static final double ARRIVAL_RADIUS_SQR = 0.5 * 0.5;

    private static final double DEPARTURE_RADIUS_SQR = 1.5 * 1.5;

    private static final float FADE_OUT_START = 17.0f;
    private static final float FADE_TICKS = 5.0f;
    private static final int FADE_RGB = 0x0A0014;

    private static final int PARTICLE_INTERVAL = 10;

    private static final List<Portal> LIVE = new ArrayList<>();

    private static boolean warned;

    private PassagePortalVisualClient() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(PassagePortalVisualClient::onClientTick);
        WorldRenderEvents.AFTER_ENTITIES.register(PassagePortalVisualClient::render);
        HudRenderCallback.EVENT.register(PassagePortalVisualClient::onHudRender);
    }

    public static void handlePortal(UUID traveller, boolean arriving, Vec3 at, float yaw) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Portal portal = new Portal(level, traveller, arriving, at, yaw);
        if (arriving) {
            for (Portal leave : LIVE) {
                if (!leave.arriving && leave.traveller.equals(traveller)) {
                    portal.fadesIn |= leave.holding();
                    leave.departed = true;
                }
            }
        }
        LIVE.add(portal);
        portal.tick();
    }

    public static boolean hidesPlayer(Entity entity) {
        if (LIVE.isEmpty() || !(entity instanceof Player)) {
            return false;
        }
        for (Portal portal : LIVE) {
            if (portal.traveller.equals(entity.getUUID())
                && (portal.arriving ? portal.standingIn() : portal.holding())) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static Portal localLeave() {
        Minecraft mc = Minecraft.getInstance();
        if (LIVE.isEmpty() || mc.player == null) {
            return null;
        }
        for (Portal portal : LIVE) {
            if (!portal.arriving && portal.holding() && portal.traveller.equals(mc.player.getUUID())) {
                return portal;
            }
        }
        return null;
    }

    @Nullable
    public static Vec3 cameraOffset(Entity entity) {
        if (entity != Minecraft.getInstance().player) {
            return null;
        }
        Portal leave = localLeave();
        return leave != null ? leave.headOffset : null;
    }

    public static boolean freezesInput(Player player) {
        return player == Minecraft.getInstance().player && localLeave() != null;
    }

    public static void onHudRender(GuiGraphics graphics, float partialTick) {
        float alpha = 0.0f;
        Portal leave = localLeave();
        if (leave != null) {
            alpha = Mth.clamp((leave.age + partialTick - FADE_OUT_START) / FADE_TICKS, 0.0f, 1.0f);
        } else {
            Minecraft mc = Minecraft.getInstance();
            for (Portal portal : LIVE) {
                if (portal.arriving && portal.fadesIn && mc.player != null
                    && portal.traveller.equals(mc.player.getUUID())) {
                    alpha = Math.max(alpha, 1.0f - Mth.clamp((portal.age + partialTick) / FADE_TICKS, 0.0f, 1.0f));
                }
            }
        }
        if (alpha <= 0.0f) {
            return;
        }
        int argb = ((int) (alpha * 255.0f) << 24) | FADE_RGB;
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), argb);
    }

    private static void onClientTick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null) {
            LIVE.clear();
            return;
        }
        Iterator<Portal> portals = LIVE.iterator();
        while (portals.hasNext()) {
            Portal portal = portals.next();
            if (portal.level != level || ++portal.age > portal.life) {
                portals.remove();
                continue;
            }
            portal.tick();
        }
    }

    private static void render(WorldRenderContext context) {
        if (LIVE.isEmpty() || context.consumers() == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        PoseStack pose = context.matrixStack();
        Vec3 camera = context.camera().getPosition();
        boolean firstPerson = !context.camera().isDetached();

        for (Portal portal : LIVE) {
            if (portal.level != level) {
                continue;
            }
            boolean ownView = firstPerson && mc.player != null && portal.traveller.equals(mc.player.getUUID());
            boolean standIn = !ownView && (!portal.arriving || portal.standingIn());
            AbstractClientPlayer traveller = level.getPlayerByUUID(portal.traveller) instanceof AbstractClientPlayer p
                ? p : null;
            portal.vfx.showStandIn = standIn && traveller == null;
            portal.vfx.light = LevelRenderer.getLightColor(level, BlockPos.containing(portal.at.add(0.0, 0.5, 0.0)));

            pose.pushPose();
            try {
                pose.translate(portal.at.x - camera.x, portal.at.y - camera.y, portal.at.z - camera.z);
                pose.mulPose(Axis.YP.rotationDegrees(180.0f - portal.yaw));
                portal.renderer.render(pose, portal.vfx, context.consumers(), null, null, portal.vfx.light);
                if (!portal.arriving) {
                    portal.headOffset = headOffset(portal);
                }
                if (standIn && traveller != null) {
                    renderTraveller(pose, portal, traveller, context);
                }
            } catch (Exception e) {
                if (!warned) {
                    warned = true;
                    Hexwright.LOGGER.error("Failed to draw a Cape of Passage portal", e);
                }
            } finally {
                PassageStandInPose.active = null;
                pose.popPose();
            }
        }
    }

    private static void renderTraveller(PoseStack pose, Portal portal, AbstractClientPlayer traveller,
                                        WorldRenderContext context) {
        GeoModel<PassagePortalVfx> rig = portal.renderer.getGeoModel();
        GeoBone body = rig.getBone(PassageStandInPose.BODY).orElse(null);
        if (body == null
            || Math.min(body.getScaleX(), Math.min(body.getScaleY(), body.getScaleZ())) < 0.01f) {
            return;
        }
        float partialTick = context.tickDelta();
        float bodyYaw = Mth.rotLerp(partialTick, traveller.yBodyRotO, traveller.yBodyRot);

        pose.pushPose();
        try {
            RenderUtils.prepMatrixForBone(pose, body);
            pose.mulPose(Axis.YP.rotationDegrees(bodyYaw - 180.0f));
            PassageStandInPose.active = PassageStandInPose.capture(rig);
            EntityRenderer<? super AbstractClientPlayer> renderer =
                Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(traveller);
            renderer.render(traveller, traveller.getYRot(), partialTick, pose, context.consumers(),
                portal.vfx.light);
        } finally {
            PassageStandInPose.active = null;
            pose.popPose();
        }
    }

    @Nullable
    private static Vec3 headOffset(Portal portal) {
        GeoBone body = portal.renderer.getGeoModel().getBone(PassageStandInPose.BODY).orElse(null);
        if (body == null) {
            return null;
        }
        return new Vec3(-body.getPosX() / 16.0, body.getPosY() / 16.0, body.getPosZ() / 16.0)
            .yRot((180.0f - portal.yaw) * Mth.DEG_TO_RAD);
    }

    private static final class Portal {
        private final ClientLevel level;
        private final UUID traveller;
        private final boolean arriving;
        private final Vec3 at;
        private final float yaw;
        private final int life;
        private final PassagePortalVfx vfx;
        private final PassagePortalRenderer renderer;
        private int age;
        private boolean arrived;
        private boolean steppedOff;
        private boolean departed;
        @Nullable
        private Vec3 headOffset;
        private boolean fadesIn;

        private Portal(ClientLevel level, UUID traveller, boolean arriving, Vec3 at, float yaw) {
            this.level = level;
            this.traveller = traveller;
            this.arriving = arriving;
            this.at = at;
            this.yaw = yaw;
            this.life = arriving ? JOIN_TICKS : LEAVE_TICKS;
            this.vfx = new PassagePortalVfx(arriving, skinOf(traveller));
            this.renderer = new PassagePortalRenderer(new PassagePortalModel());
        }

        private boolean standingIn() {
            return !steppedOff && age < JOIN_SETTLED_TICKS;
        }

        private boolean holding() {
            return !arriving && !departed;
        }

        private void tick() {
            playCues();
            if (age % PARTICLE_INTERVAL == 0) {
                spray();
            }
            if (!arriving) {
                Player player = level.getPlayerByUUID(traveller);
                if (player != null && player != Minecraft.getInstance().player
                    && player.position().distanceToSqr(at) > DEPARTURE_RADIUS_SQR) {
                    departed = true;
                }
                return;
            }
            if (steppedOff) {
                return;
            }
            Player player = level.getPlayerByUUID(traveller);
            boolean onArrival = player != null && player.position().distanceToSqr(at) < ARRIVAL_RADIUS_SQR;
            if (onArrival) {
                arrived = true;
            } else if (arrived || age > ARRIVAL_GRACE_TICKS) {
                steppedOff = true;
            }
        }

        private void playCues() {
            int step = arriving ? 18 : 9;
            int move = arriving ? 11 : 18;
            if (age == 0) {
                play(SoundEvents.ILLUSIONER_PREPARE_MIRROR);
            }
            if (age == move) {
                play(SoundEvents.ILLUSIONER_MIRROR_MOVE);
            }
            if (age == step) {
                play(SoundEvents.GRASS_STEP);
            }
        }

        private void play(SoundEvent sound) {
            level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.PLAYERS, 1.0f, 1.0f, false);
        }

        private void spray() {
            double rad = yaw * Mth.DEG_TO_RAD;
            double forwardX = -Math.sin(rad);
            double forwardZ = Math.cos(rad);
            double along = arriving ? -1.25 : 1.5;
            double height = arriving ? 1.45 : 0.1;
            double x = at.x + forwardX * along;
            double z = at.z + forwardZ * along;
            for (int i = 0; i < 16; i++) {
                level.addParticle(ParticleTypes.PORTAL,
                    x + (level.random.nextDouble() - 0.5) * 1.2,
                    at.y + height + (level.random.nextDouble() - 0.5) * 1.2,
                    z + (level.random.nextDouble() - 0.5) * 1.2,
                    0.0, 0.0, 0.0);
            }
        }

        private static ResourceLocation skinOf(UUID traveller) {
            var connection = Minecraft.getInstance().getConnection();
            PlayerInfo info = connection != null ? connection.getPlayerInfo(traveller) : null;
            return info != null ? info.getSkinLocation() : DefaultPlayerSkin.getDefaultSkin(traveller);
        }
    }
}
