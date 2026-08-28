package com.bluup.hexwright.client.weapon;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.weapon.AnimatedWeapon;
import com.bluup.hexwright.server.weapon.WeaponSlash;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class WeaponSlashVisualClient {

    private static final List<Armed> ARMED = new ArrayList<>();

    private static final List<Live> LIVE = new ArrayList<>();

    private static final float FAN_HEIGHT = 1.5f;

    private static boolean warned;

    private WeaponSlashVisualClient() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(WeaponSlashVisualClient::onClientTick);
        WorldRenderEvents.AFTER_ENTITIES.register(WeaponSlashVisualClient::render);
    }

    public static void onSwingStarted(AbstractClientPlayer player, String clipName, float clipSpeed) {
        Item item = player.getMainHandItem().getItem();
        if (!(item instanceof AnimatedWeapon weapon)) {
            return;
        }
        WeaponSlash slash = weapon.slashFor(clipName);
        if (slash == null) {
            return;
        }

        ARMED.removeIf(armed -> armed.player == player);
        ARMED.add(new Armed(player, slash, clipSpeed));
    }

    public static void onSwingStopped(AbstractClientPlayer player) {
        ARMED.removeIf(armed -> armed.player == player);
    }

    private static void onClientTick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null) {
            ARMED.clear();
            LIVE.clear();
            return;
        }

        Iterator<Armed> arming = ARMED.iterator();
        while (arming.hasNext()) {
            Armed armed = arming.next();
            if (armed.player.isRemoved() || armed.player.level() != level) {
                arming.remove();
                continue;
            }
            if (armed.delay-- <= 0) {
                arming.remove();
                fire(level, armed);
            }
        }

        Iterator<Live> living = LIVE.iterator();
        while (living.hasNext()) {
            Live live = living.next();
            if (live.level != level || ++live.age > live.life) {
                living.remove();
                continue;
            }
            live.vfx.setAge(live.age);
        }
    }

    private static void fire(ClientLevel level, Armed armed) {
        WeaponSlash slash = armed.slash;
        float yaw = armed.player.getYRot();
        float pitch = armed.player.getXRot();
        double forwardX = -Mth.sin(yaw * Mth.DEG_TO_RAD);
        double forwardZ = Mth.cos(yaw * Mth.DEG_TO_RAD);

        Vec3 feet = armed.player.position();
        LIVE.add(new Live(level,
            feet.x + forwardX * slash.forward(),
            feet.y + slash.height(),
            feet.z + forwardZ * slash.forward(),
            yaw, pitch, slash));
    }

    private static void render(WorldRenderContext context) {
        if (LIVE.isEmpty()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || context.consumers() == null) {
            return;
        }

        PoseStack pose = context.matrixStack();
        Vec3 camera = context.camera().getPosition();

        for (Live live : LIVE) {
            if (live.level != level) {
                continue;
            }
            pose.pushPose();
            try {
                pose.translate(live.x - camera.x, live.y - camera.y, live.z - camera.z);
                pose.translate(0.0f, FAN_HEIGHT * live.scale, 0.0f);
                pose.mulPose(Axis.YP.rotationDegrees(180.0f - live.yaw));
                pose.mulPose(Axis.XP.rotationDegrees(-live.pitch));
                pose.translate(0.0f, -FAN_HEIGHT * live.scale, 0.0f);
                pose.scale(live.scale, live.scale, live.scale);
                live.renderer.render(pose, live.vfx, context.consumers(), null, null,
                    LightTexture.FULL_BRIGHT);
            } catch (Exception e) {
                if (!warned) {
                    warned = true;
                    Hexwright.LOGGER.error("Failed to draw the weapon slash effect", e);
                }
            } finally {
                pose.popPose();
            }
        }
    }

    private static final class Armed {
        private final AbstractClientPlayer player;
        private final WeaponSlash slash;
        private int delay;

        private Armed(AbstractClientPlayer player, WeaponSlash slash, float clipSpeed) {
            this.player = player;
            this.slash = slash;
            this.delay = Math.round(slash.leadTicks() / clipSpeed);
        }
    }

    private static final class Live {
        private final ClientLevel level;
        private final double x;
        private final double y;
        private final double z;
        private final float yaw;
        private final float pitch;
        private final float scale;
        private final int life;
        private final SlashVfx vfx;
        private final SlashVfxRenderer renderer;
        private int age;

        private Live(ClientLevel level, double x, double y, double z, float yaw, float pitch,
                     WeaponSlash slash) {
            this.level = level;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.scale = slash.scale();
            this.life = slash.lifeTicks();
            this.vfx = new SlashVfx(slash.clip());
            this.renderer = new SlashVfxRenderer(new SlashVfxModel(slash.style()));
        }
    }
}
