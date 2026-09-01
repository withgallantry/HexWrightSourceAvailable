package com.bluup.hexwright.server.remnant;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.common.remnant.Remnant;
import com.bluup.hexwright.common.remnant.RemnantSnapshot;
import com.bluup.hexwright.common.remnant.RemnantType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RemnantExtraction {

    private static final int DEATH_WINDOW_TICKS = 20;

    private static final String CLAIM_TAG = "hexwright_reaped";

    private static final Map<ResourceLocation, RemnantType> BOSS_REMNANTS = new HashMap<>();

    static {
        BOSS_REMNANTS.put(Hexwright.id("quartz_golem"), RemnantType.SHARDSKIN);
        BOSS_REMNANTS.put(Hexwright.id("ancient_golem"), RemnantType.ADAMANT);
        BOSS_REMNANTS.put(Hexwright.id("wither_storm"), RemnantType.RIFT);
        BOSS_REMNANTS.put(new ResourceLocation("minecraft", "wither"), RemnantType.BLIGHT);
        BOSS_REMNANTS.put(new ResourceLocation("minecraft", "ender_dragon"), RemnantType.ASCENDANT);
        BOSS_REMNANTS.put(new ResourceLocation("minecraft", "warden"), RemnantType.TREMOR);
        BOSS_REMNANTS.put(new ResourceLocation("minecraft", "elder_guardian"), RemnantType.INTERDICT);
    }

    private RemnantExtraction() {
    }

    public enum Refusal {
        LIVING,
        COLD,
        CLAIMED,
        BARREN
    }

    public static @Nullable Refusal refuse(LivingEntity victim) {
        if (!victim.isDeadOrDying()) {
            return Refusal.LIVING;
        }
        if (victim.deathTime > DEATH_WINDOW_TICKS) {
            return Refusal.COLD;
        }
        if (victim.getTags().contains(CLAIM_TAG)) {
            return Refusal.CLAIMED;
        }
        return null;
    }

    public static List<Remnant> readAll(LivingEntity victim) {
        List<Remnant> found = new ArrayList<>();
        for (RemnantType type : RemnantType.values()) {
            double drams = type.dramsFrom(victim);
            if (drams > 0.0) {
                found.add(new Remnant(type, drams));
            }
        }
        RemnantType boss = bossRemnant(victim);
        if (boss != null) {
            found.add(new Remnant(boss, RemnantType.BOSS_DRAMS * RemnantType.stature(victim)));
        }
        return found;
    }

    private static @Nullable RemnantType bossRemnant(LivingEntity victim) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType());
        return BOSS_REMNANTS.get(id);
    }

    public static @Nullable RemnantSnapshot reap(LivingEntity victim, long gameTime) {
        if (refuse(victim) != null) {
            return null;
        }
        List<Remnant> found = readAll(victim);
        if (found.isEmpty()) {
            return null;
        }
        victim.addTag(CLAIM_TAG);
        return new RemnantSnapshot(found, gameTime, UUID.randomUUID());
    }

    public static boolean isBoss(EntityType<?> type) {
        return BOSS_REMNANTS.containsKey(BuiltInRegistries.ENTITY_TYPE.getKey(type));
    }
}
