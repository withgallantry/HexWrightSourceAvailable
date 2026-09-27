package com.bluup.hexwright.client.cloak;

import com.bluup.hexwright.server.cloak.CloakField;
import com.bluup.hexwright.server.cloak.CloakFieldStore;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class ClientCloakFields {

    private static final long RISE_NANOS = 3_500_000_000L;

    private static List<CloakField> fields = List.of();
    private static ClientLevel boundLevel;
    private static boolean synced;
    private static final Map<UUID, Long> risingSince = new HashMap<>();

    private ClientCloakFields() {
    }

    public static void register() {
        CloakFieldStore.setClientFields(ClientCloakFields::fields);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != boundLevel) {
                boundLevel = client.level;
                fields = List.of();
                synced = false;
                risingSince.clear();
            }
            long now = System.nanoTime();
            risingSince.values().removeIf(since -> now - since >= RISE_NANOS);
        });
    }

    public static void set(List<CloakField> received) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == boundLevel && synced) {
            Set<UUID> standing = fields.stream().map(CloakField::id).collect(Collectors.toSet());
            long now = System.nanoTime();
            for (CloakField field : received) {
                if (!standing.contains(field.id())) {
                    risingSince.put(field.id(), now);
                }
            }
        } else {
            risingSince.clear();
        }
        boundLevel = level;
        synced = true;
        fields = List.copyOf(received);
    }

    public static List<CloakField> fields() {
        return fields;
    }

    static float rise(CloakField field, long now) {
        Long since = risingSince.get(field.id());
        return since == null ? 1.0f : Math.min(1.0f, (float) (now - since) / RISE_NANOS);
    }
}
