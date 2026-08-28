package com.bluup.hexwright.server.sound;

import com.bluup.hexwright.Hexwright;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public final class HexwrightSoundEvents {
    private static final ResourceLocation STAFF_CORE_PROJECTILE_FIRE_ID = Hexwright.id("staff_core_projectile_fire");
    private static final ResourceLocation PORTAL_OPEN_CLOSE_ID = Hexwright.id("portal_open_close");
    private static final ResourceLocation BINDSTONE_NOTE_ID = Hexwright.id("bindstone_note");
    private static final ResourceLocation QUARTZ_GOLEM_ARMSLAM_ID = Hexwright.id("quartz_golem_armslam");
    private static final ResourceLocation QUARTZ_GOLEM_STOMP_ID = Hexwright.id("quartz_golem_stomp");
    private static final ResourceLocation QUARTZ_GOLEM_SHARDGRAB_ID = Hexwright.id("quartz_golem_shardgrab");
    private static final ResourceLocation QUARTZ_GOLEM_SHARDOUT_ID = Hexwright.id("quartz_golem_shardout");
    private static final ResourceLocation QUARTZ_GOLEM_DEATH_ID = Hexwright.id("quartz_golem_death");
    private static final ResourceLocation JANUS_OPEN_ID = Hexwright.id("janus_open");
    private static final ResourceLocation VOID_TEAR_OPEN_ID = Hexwright.id("void_tear_open");
    private static final ResourceLocation RUINED_PORTAL_TELEPORT_ID = Hexwright.id("ruined_portal_teleport");
    private static final ResourceLocation RUINED_PORTAL_FLARE_ID = Hexwright.id("ruined_portal_flare");
    private static final ResourceLocation RESONANCE_TOWER_ACTIVATE_ID = Hexwright.id("resonance_tower_activate");
    private static final ResourceLocation AREA_CAST_ACTIVATE_ID = Hexwright.id("area_cast_activate");
    private static final ResourceLocation RECIPE_UNLOCK_ID = Hexwright.id("recipe_unlock");

    private static SoundEvent staffCoreProjectileFire = SoundEvents.BLAZE_SHOOT;
    private static SoundEvent portalOpenClose = SoundEvents.PORTAL_TRIGGER;
    private static SoundEvent bindstoneNote = SoundEvents.NOTE_BLOCK_CHIME.value();
    private static SoundEvent quartzGolemArmslam = SoundEvents.IRON_GOLEM_ATTACK;
    private static SoundEvent quartzGolemStomp = SoundEvents.GENERIC_EXPLODE;
    private static SoundEvent quartzGolemShardgrab = SoundEvents.IRON_GOLEM_ATTACK;
    private static SoundEvent quartzGolemShardout = SoundEvents.AMETHYST_BLOCK_BREAK;
    private static SoundEvent quartzGolemDeath = SoundEvents.IRON_GOLEM_DEATH;
    private static SoundEvent janusOpen = SoundEvents.PORTAL_TRIGGER;
    private static SoundEvent voidTearOpen = SoundEvents.PORTAL_TRIGGER;
    private static SoundEvent ruinedPortalTeleport = SoundEvents.PORTAL_TRAVEL;
    private static SoundEvent ruinedPortalFlare = SoundEvents.PORTAL_TRIGGER;
    private static SoundEvent resonanceTowerActivate = SoundEvents.BEACON_ACTIVATE;
    private static SoundEvent areaCastActivate = SoundEvents.AMETHYST_BLOCK_CHIME;
    private static SoundEvent recipeUnlock = SoundEvents.ENCHANTMENT_TABLE_USE;
    private static boolean registered;

    private HexwrightSoundEvents() {
    }

    public static void register() {
        if (registered) {
            return;
        }

        staffCoreProjectileFire = registerOrReuse(STAFF_CORE_PROJECTILE_FIRE_ID, staffCoreProjectileFire);
        portalOpenClose = registerOrReuse(PORTAL_OPEN_CLOSE_ID, portalOpenClose);
        bindstoneNote = registerOrReuse(BINDSTONE_NOTE_ID, bindstoneNote);
        quartzGolemArmslam = registerOrReuse(QUARTZ_GOLEM_ARMSLAM_ID, quartzGolemArmslam);
        quartzGolemStomp = registerOrReuse(QUARTZ_GOLEM_STOMP_ID, quartzGolemStomp);
        quartzGolemShardgrab = registerOrReuse(QUARTZ_GOLEM_SHARDGRAB_ID, quartzGolemShardgrab);
        quartzGolemShardout = registerOrReuse(QUARTZ_GOLEM_SHARDOUT_ID, quartzGolemShardout);
        quartzGolemDeath = registerOrReuse(QUARTZ_GOLEM_DEATH_ID, quartzGolemDeath);
        janusOpen = registerOrReuse(JANUS_OPEN_ID, janusOpen);
        voidTearOpen = registerOrReuse(VOID_TEAR_OPEN_ID, voidTearOpen);
        ruinedPortalTeleport = registerOrReuse(RUINED_PORTAL_TELEPORT_ID, ruinedPortalTeleport);
        ruinedPortalFlare = registerOrReuse(RUINED_PORTAL_FLARE_ID, ruinedPortalFlare);
        resonanceTowerActivate = registerOrReuse(RESONANCE_TOWER_ACTIVATE_ID, resonanceTowerActivate);
        areaCastActivate = registerOrReuse(AREA_CAST_ACTIVATE_ID, areaCastActivate);
        recipeUnlock = registerOrReuse(RECIPE_UNLOCK_ID, recipeUnlock);
        com.bluup.hexwright.server.boss.ancient.AncientSounds.register();
        com.bluup.hexwright.server.boss.storm.WitherStormSounds.register();
        registered = true;
    }

    private static SoundEvent registerOrReuse(ResourceLocation id, SoundEvent fallback) {
        SoundEvent existing = BuiltInRegistries.SOUND_EVENT.get(id);
        if (existing != null) {
            return existing;
        }

        try {
            return Registry.register(
                BuiltInRegistries.SOUND_EVENT,
                id,
                SoundEvent.createVariableRangeEvent(id)
            );
        } catch (IllegalStateException ex) {
            Hexwright.LOGGER.error("Failed to register {} before registry freeze; using fallback sound.", id, ex);
            return fallback;
        }
    }

    public static SoundEvent staffCoreProjectileFire() {
        return staffCoreProjectileFire;
    }

    public static SoundEvent portalOpenClose() {
        return portalOpenClose;
    }

    public static SoundEvent bindstoneNote() {
        return bindstoneNote;
    }

    public static SoundEvent quartzGolemArmslam() {
        return quartzGolemArmslam;
    }

    public static SoundEvent quartzGolemStomp() {
        return quartzGolemStomp;
    }

    public static SoundEvent quartzGolemShardgrab() {
        return quartzGolemShardgrab;
    }

    public static SoundEvent quartzGolemShardout() {
        return quartzGolemShardout;
    }

    public static SoundEvent quartzGolemDeath() {
        return quartzGolemDeath;
    }

    public static SoundEvent janusOpen() {
        return janusOpen;
    }

    public static SoundEvent voidTearOpen() {
        return voidTearOpen;
    }

    public static SoundEvent ruinedPortalFlare() {
        return ruinedPortalFlare;
    }

    public static SoundEvent ruinedPortalTeleport() {
        return ruinedPortalTeleport;
    }

    public static SoundEvent resonanceTowerActivate() {
        return resonanceTowerActivate;
    }

    public static SoundEvent areaCastActivate() {
        return areaCastActivate;
    }

    public static SoundEvent recipeUnlock() {
        return recipeUnlock;
    }
}