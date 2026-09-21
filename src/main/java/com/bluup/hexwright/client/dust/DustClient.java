package com.bluup.hexwright.client.dust;

import com.bluup.hexwright.server.dust.DustPacket;
import com.bluup.hexwright.server.dust.DustSupport;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class DustClient {

    private static final Map<Integer, DustController> CONTROLLERS = new java.util.HashMap<>();

    private DustClient() {
    }

    public static void register() {
        DustConstructRenderer.register();
        DustSupport.source(true, into -> {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null) {
                return;
            }
            for (DustController controller : CONTROLLERS.values()) {
                DustSupport.Surface surface = controller.surface(level);
                if (surface != null) {
                    into.add(surface);
                }
            }
        });
        ClientTickEvents.START_CLIENT_TICK.register(client -> DustSupport.invalidate(true));
        ClientTickEvents.END_CLIENT_TICK.register(DustClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(DustClient::clear));
        DustCommands.register();
    }

    public static void handle(DustPacket packet) {
        int entityId = packet.entityId();
        DustController controller = CONTROLLERS.get(entityId);
        if (packet.op() == DustPacket.OP_RELEASE) {
            if (controller != null) {
                controller.release();
            }
            return;
        }
        if (controller == null) {
            controller = new DustController(entityId);
            CONTROLLERS.put(entityId, controller);
        } else {
            controller.manifest();
        }
        switch (packet.op()) {
            case DustPacket.OP_STATE -> {
                controller.setMass(packet.mass(), packet.targetMass());
                controller.setFormation(packet.region(), packet.constrained());
            }
            case DustPacket.OP_MASS -> controller.setMass(packet.mass(), packet.targetMass());
            case DustPacket.OP_DIRECT -> controller.direct(packet.vector().x, packet.vector().y,
                packet.vector().z, packet.durationTicks());
            default -> {
            }
        }
    }

    private static void tick(Minecraft mc) {
        if (CONTROLLERS.isEmpty() || mc.isPaused()) {
            return;
        }
        ClientLevel level = mc.level;
        if (level == null) {
            clear();
            return;
        }
        Iterator<DustController> it = CONTROLLERS.values().iterator();
        while (it.hasNext()) {
            DustController controller = it.next();
            Entity caster = level.getEntity(controller.entityId);
            if (!controller.tick(mc, caster)) {
                controller.disposeConstructs();
                it.remove();
            }
        }
    }

    public static void onParticlesCleared() {
        for (DustController controller : CONTROLLERS.values()) {
            controller.forgetRuntime();
            controller.disposeConstructs();
        }
        CONTROLLERS.clear();
        DustSupport.clear(true);
    }

    static void collectConstructs(List<DustConstruct> into) {
        for (DustController controller : CONTROLLERS.values()) {
            controller.collectConstructs(into);
        }
    }

    private static void clear() {
        for (DustController controller : CONTROLLERS.values()) {
            controller.discard();
        }
        CONTROLLERS.clear();
        DustSupport.clear(true);
    }

    static List<String> describe() {
        List<String> lines = new ArrayList<>();
        lines.add("Effect asset hexwright:dust " + (DustController.isFxLoadable() ? "loaded." : "FAILED TO LOAD.")
            + " Quality " + DustConfig.quality + " (" + DustConfig.quality.population + ").");
        if (CONTROLLERS.isEmpty()) {
            lines.add("No dust manifest.");
        }
        for (DustController controller : CONTROLLERS.values()) {
            lines.add("Entity " + controller.entityId + ": " + controller.mode()
                + ", " + controller.describeState()
                + ", " + controller.liveGrains() + "/" + controller.targetGrains() + " grains"
                + String.format(", steering %.2f ms/tick", controller.steerMillis())
                + (DustConstructRenderer.available() ? "" : ", constructs off")
                + ", lod " + controller.lod()
                + (controller.culled() ? ", culled" : controller.hasRuntime() ? "" : ", no effect running"));
        }
        return lines;
    }
}
