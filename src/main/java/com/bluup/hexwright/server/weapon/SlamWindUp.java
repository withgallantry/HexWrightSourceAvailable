package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.Hexwright;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class SlamWindUp {

    private record Pending(long dueTick, Runnable blow) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    private SlamWindUp() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(SlamWindUp::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
    }

    public static void schedule(MinecraftServer server, int delayTicks, Runnable blow) {
        PENDING.add(new Pending(server.getTickCount() + delayTicks, blow));
    }

    private static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }

        long now = server.getTickCount();
        Iterator<Pending> pending = PENDING.iterator();
        while (pending.hasNext()) {
            Pending entry = pending.next();
            if (entry.dueTick() > now) {
                continue;
            }
            pending.remove();
            try {
                entry.blow().run();
            } catch (RuntimeException e) {
                Hexwright.LOGGER.error("Scheduled weapon blow failed", e);
            }
        }
    }
}
