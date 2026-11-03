package com.bluup.hexwright.server.dungeon;

import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.server.boss.BossArena;
import com.bluup.hexwright.server.boss.BossDamage;
import com.bluup.hexwright.server.boss.Bosses;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class DungeonDifficulty extends SavedData {

    public static final String BOSSES = "bosses";
    public static final String MOBS = "mobs";

    public enum Setting {
        BOSS_HEALTH(BOSSES, "health", 1.0D, 0.1D, 10.0D, "multiplies every boss's health"),
        BOSS_DAMAGE(BOSSES, "damage", 1.0D, 0.0D, 10.0D, "multiplies the blows every boss lands"),
        BOSS_PARTY_HEALTH(BOSSES, "party_health", 0.75D, 0.0D, 4.0D,
            "share of a boss's bar each player in its fight past the first adds"),
        BOSS_ESCALATION(BOSSES, "escalation", 0.15D, 0.0D, 1.0D,
            "share the Talus Knight, Magnus and the Corrupt Experiment add to their blows every 30s past a fight's first minute"),
        MOB_HEALTH(MOBS, "health", 1.0D, 0.1D, 10.0D, "multiplies every dungeon mob's health"),
        MOB_DAMAGE(MOBS, "damage", 1.0D, 0.0D, 10.0D, "multiplies the blows every dungeon mob lands"),
        MOB_PARTY_HEALTH(MOBS, "party_health", 0.5D, 0.0D, 4.0D,
            "share of a dungeon mob's bar each player in its instance past the first adds");

        private final String group;
        private final String key;
        private final double defaultValue;
        private final double min;
        private final double max;
        private final String description;

        Setting(String group, String key, double defaultValue, double min, double max, String description) {
            this.group = group;
            this.key = key;
            this.defaultValue = defaultValue;
            this.min = min;
            this.max = max;
            this.description = description;
        }

        public String group() {
            return this.group;
        }

        public String key() {
            return this.key;
        }

        public double defaultValue() {
            return this.defaultValue;
        }

        public double min() {
            return this.min;
        }

        public double max() {
            return this.max;
        }

        public String description() {
            return this.description;
        }

        public double value(@Nullable MinecraftServer server) {
            return server == null ? this.defaultValue : get(server).values.getOrDefault(this, this.defaultValue);
        }

        private String id() {
            return this.group + "." + this.key;
        }
    }

    private static final String STORAGE_ID = "hexwright_dungeon_difficulty";

    private static final UUID HEALTH = UUID.fromString("074e28de-2d3d-431b-a400-d1afd97aca87");

    private static final Map<Integer, Integer> PARTIES = new HashMap<>();

    private final Map<Setting, Double> values = new EnumMap<>(Setting.class);

    private DungeonDifficulty() {
    }

    private static DungeonDifficulty get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(DungeonDifficulty::load, DungeonDifficulty::new,
            STORAGE_ID);
    }

    static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof Mob mob && isGarrison(mob)) {
                sizeGarrison(level.getServer(), mob);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(DungeonDifficulty::countParties);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PARTIES.clear());
        DungeonDifficultyCommand.register();
    }

    static void set(MinecraftServer server, Setting setting, double value) {
        DungeonDifficulty difficulty = get(server);
        if (value == setting.defaultValue) {
            difficulty.values.remove(setting);
        } else {
            difficulty.values.put(setting, value);
        }
        difficulty.setDirty();
        if (setting.group.equals(MOBS)) {
            sizeGarrisons(server);
        }
    }

    static void reset(MinecraftServer server) {
        DungeonDifficulty difficulty = get(server);
        difficulty.values.clear();
        difficulty.setDirty();
        sizeGarrisons(server);
    }

    public static double bossHealth(@Nullable MinecraftServer server, int party) {
        return sized(server, Setting.BOSS_HEALTH, Setting.BOSS_PARTY_HEALTH, party);
    }

    private static double sized(@Nullable MinecraftServer server, Setting health, Setting perPlayer, int party) {
        return health.value(server) * (1.0D + perPlayer.value(server) * (party - 1));
    }

    public static boolean scaleHealth(LivingEntity entity, double scale) {
        AttributeInstance maxHealth = entity.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null || entity.isDeadOrDying()) {
            return false;
        }
        double wanted = scale - 1.0D;
        AttributeModifier had = maxHealth.getModifier(HEALTH);
        if (Math.abs((had == null ? 0.0D : had.getAmount()) - wanted) < 1.0E-6D) {
            return false;
        }
        float share = entity.getHealth() / entity.getMaxHealth();
        maxHealth.removeModifier(HEALTH);
        if (Math.abs(wanted) >= 1.0E-6D) {
            maxHealth.addPermanentModifier(new AttributeModifier(HEALTH, "Hexwright difficulty health", wanted,
                AttributeModifier.Operation.MULTIPLY_BASE));
        }
        entity.setHealth(share * entity.getMaxHealth());
        return true;
    }

    public static float garrisonBlow(Player victim, DamageSource source, float amount) {
        return source.getEntity() instanceof Mob mob && isGarrison(mob)
            ? amount * (float) Setting.MOB_DAMAGE.value(victim.getServer())
            : amount;
    }

    private static boolean isGarrison(Mob mob) {
        return mob.getType().getCategory() == MobCategory.MONSTER && DungeonDimension.isDungeonLevel(mob.level())
            && !Bosses.is(mob);
    }

    private static void sizeGarrison(MinecraftServer server, Mob mob) {
        DungeonInstance instance = DungeonInstances.at(server, mob.position());
        int party = instance == null ? 1 : PARTIES.getOrDefault(instance.id(), 1);
        scaleHealth(mob, sized(server, Setting.MOB_HEALTH, Setting.MOB_PARTY_HEALTH, party));
    }

    private static void sizeGarrisons(MinecraftServer server) {
        ServerLevel dungeons = DungeonDimension.level(server);
        if (dungeons == null) {
            return;
        }
        for (Entity entity : dungeons.getAllEntities()) {
            if (entity instanceof Mob mob && isGarrison(mob)) {
                sizeGarrison(server, mob);
            }
        }
    }

    private static void countParties(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        ServerLevel dungeons = DungeonDimension.level(server);
        if (dungeons == null) {
            return;
        }
        DungeonInstanceRegistry registry = DungeonInstanceRegistry.get(server);
        PARTIES.keySet().removeIf(id -> registry.byId(id) == null);
        for (DungeonInstance instance : registry.all()) {
            if (instance.state() != DungeonInstance.State.OPEN) {
                continue;
            }
            BoundingBox bounds = DungeonInstances.bounds(dungeons, instance);
            int party = 0;
            for (ServerPlayer player : dungeons.players()) {
                if (player.isAlive() && EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(player)
                    && bounds.isInside(player.blockPosition())) {
                    party++;
                }
            }
            if (party == 0 || Objects.equals(PARTIES.put(instance.id(), party), party)) {
                continue;
            }
            HexwrightDebug.log(HexwrightDebug.DUNGEON, "[dungeon] instance {} has {} player(s); sizing its garrison",
                instance.id(), party);
            for (Mob mob : dungeons.getEntitiesOfClass(Mob.class, AABB.of(bounds), DungeonDifficulty::isGarrison)) {
                sizeGarrison(server, mob);
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        this.values.forEach((setting, value) -> tag.putDouble(setting.id(), value));
        return tag;
    }

    private static DungeonDifficulty load(CompoundTag tag) {
        DungeonDifficulty difficulty = new DungeonDifficulty();
        for (Setting setting : Setting.values()) {
            if (tag.contains(setting.id(), Tag.TAG_DOUBLE)) {
                difficulty.values.put(setting, Mth.clamp(tag.getDouble(setting.id()), setting.min, setting.max));
            }
        }
        return difficulty;
    }
}
