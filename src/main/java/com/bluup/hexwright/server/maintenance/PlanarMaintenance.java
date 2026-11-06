package com.bluup.hexwright.server.maintenance;

import com.bluup.hexwright.HexwrightDebug;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class PlanarMaintenance extends SavedData {

    public static final int WORKSHOPS_BEFORE_STUDY = 30;
    public static final float STUDY_CHANCE = 0.15F;

    private static final String STORAGE_ID = "hexwright_planar_maintenance";
    private static final String TAG_WORKSHOPS = "Workshops";
    private static final String TAG_ENGAGED_AT = "EngagedAt";
    private static final String TAG_STUDY_FORCED = "StudyForced";
    private static final String TAG_AWAITED_WORKSHOP = "AwaitedWorkshop";
    private static final String TAG_NEXT_KEYSTONE_AT = "NextKeystoneAt";
    private static final String TAG_KEYSTONE = "Keystone";
    private static final String TAG_KEYSTONE_OPENED_AT = "KeystoneOpenedAt";
    private static final String TAG_KEYSTONES = "Keystones";
    private static final String TAG_FEEDERS = "Feeders";

    private int workshops;
    private long engagedAt = -1L;
    private boolean studyForced;
    private int awaitedWorkshop = -1;
    private long nextKeystoneAt = -1L;
    private @Nullable UUID keystone;
    private long keystoneOpenedAt;
    private int keystones;
    private final Set<UUID> feeders = new HashSet<>();

    private PlanarMaintenance() {
    }

    static PlanarMaintenance get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PlanarMaintenance::load, PlanarMaintenance::new,
            STORAGE_ID);
    }

    public static void workshopCompleted(MinecraftServer server) {
        PlanarMaintenance state = get(server);
        state.workshops++;
        state.setDirty();
        HexwrightDebug.log(HexwrightDebug.MAINTENANCE, "[maintenance] workshop {} completed", state.workshops);
    }

    public static boolean rollsStudy(MinecraftServer server, RandomSource random) {
        PlanarMaintenance state = get(server);
        if (state.studyForced) {
            state.studyForced = false;
            state.setDirty();
            return true;
        }
        return state.engagedAt < 0L && state.workshops >= WORKSHOPS_BEFORE_STUDY && random.nextFloat() < STUDY_CHANCE;
    }

    public static boolean engaged(MinecraftServer server) {
        return get(server).engagedAt >= 0L;
    }

    public static boolean engage(MinecraftServer server, int workshop) {
        PlanarMaintenance state = get(server);
        if (state.engagedAt >= 0L) {
            return false;
        }
        state.engagedAt = server.overworld().getGameTime();
        state.awaitedWorkshop = workshop;
        state.nextKeystoneAt = workshop < 0 ? System.currentTimeMillis() + PlanarIncursion.FIRST_KEYSTONE_MILLIS : -1L;
        state.keystones = 0;
        state.feeders.clear();
        state.setDirty();
        return true;
    }

    public static void workshopClosed(MinecraftServer server, int workshop) {
        PlanarMaintenance state = get(server);
        if (state.engagedAt < 0L || workshop != state.awaitedWorkshop) {
            return;
        }
        state.awaitedWorkshop = -1;
        state.nextKeystoneAt = System.currentTimeMillis() + PlanarIncursion.FIRST_KEYSTONE_MILLIS;
        state.setDirty();
        HexwrightDebug.log(HexwrightDebug.MAINTENANCE, "[maintenance] workshop {} closed; the first Keystone is coming",
            workshop);
    }

    public static void release(MinecraftServer server) {
        PlanarMaintenance state = get(server);
        state.engagedAt = -1L;
        state.awaitedWorkshop = -1;
        state.nextKeystoneAt = -1L;
        state.keystone = null;
        state.feeders.clear();
        state.setDirty();
    }

    public static void fed(MinecraftServer server, UUID hexahedron, UUID player) {
        PlanarMaintenance state = get(server);
        if (hexahedron.equals(state.keystone) && state.feeders.add(player)) {
            state.setDirty();
        }
    }

    static Set<UUID> feedersOf(MinecraftServer server, UUID keystone) {
        PlanarMaintenance state = get(server);
        return keystone.equals(state.keystone) ? Set.copyOf(state.feeders) : Set.of();
    }

    int workshops() {
        return this.workshops;
    }

    void setWorkshops(int workshops) {
        this.workshops = workshops;
        setDirty();
    }

    long engagedAt() {
        return this.engagedAt;
    }

    boolean studyForced() {
        return this.studyForced;
    }

    void forceStudy() {
        this.studyForced = true;
        setDirty();
    }

    int awaitedWorkshop() {
        return this.awaitedWorkshop;
    }

    long nextKeystoneAt() {
        return this.nextKeystoneAt;
    }

    void scheduleKeystone(long at) {
        this.nextKeystoneAt = at;
        setDirty();
    }

    @Nullable UUID keystone() {
        return this.keystone;
    }

    long keystoneOpenedAt() {
        return this.keystoneOpenedAt;
    }

    int keystones() {
        return this.keystones;
    }

    void keystoneOpened(UUID keystone, long at) {
        this.keystone = keystone;
        this.keystoneOpenedAt = at;
        this.nextKeystoneAt = -1L;
        this.keystones++;
        setDirty();
    }

    void keystoneFilled(long nextAt) {
        this.keystone = null;
        this.nextKeystoneAt = nextAt;
        setDirty();
    }

    private static PlanarMaintenance load(CompoundTag tag) {
        PlanarMaintenance state = new PlanarMaintenance();
        state.workshops = tag.getInt(TAG_WORKSHOPS);
        state.engagedAt = tag.contains(TAG_ENGAGED_AT) ? tag.getLong(TAG_ENGAGED_AT) : -1L;
        state.studyForced = tag.getBoolean(TAG_STUDY_FORCED);
        state.awaitedWorkshop = tag.contains(TAG_AWAITED_WORKSHOP) ? tag.getInt(TAG_AWAITED_WORKSHOP) : -1;
        state.nextKeystoneAt = tag.contains(TAG_NEXT_KEYSTONE_AT) ? tag.getLong(TAG_NEXT_KEYSTONE_AT) : -1L;
        state.keystone = tag.hasUUID(TAG_KEYSTONE) ? tag.getUUID(TAG_KEYSTONE) : null;
        state.keystoneOpenedAt = tag.getLong(TAG_KEYSTONE_OPENED_AT);
        state.keystones = tag.getInt(TAG_KEYSTONES);
        for (Tag feeder : tag.getList(TAG_FEEDERS, Tag.TAG_INT_ARRAY)) {
            state.feeders.add(NbtUtils.loadUUID(feeder));
        }
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt(TAG_WORKSHOPS, this.workshops);
        tag.putLong(TAG_ENGAGED_AT, this.engagedAt);
        tag.putBoolean(TAG_STUDY_FORCED, this.studyForced);
        tag.putInt(TAG_AWAITED_WORKSHOP, this.awaitedWorkshop);
        tag.putLong(TAG_NEXT_KEYSTONE_AT, this.nextKeystoneAt);
        if (this.keystone != null) {
            tag.putUUID(TAG_KEYSTONE, this.keystone);
        }
        tag.putLong(TAG_KEYSTONE_OPENED_AT, this.keystoneOpenedAt);
        tag.putInt(TAG_KEYSTONES, this.keystones);
        ListTag feeders = new ListTag();
        this.feeders.forEach(feeder -> feeders.add(NbtUtils.createUUID(feeder)));
        tag.put(TAG_FEEDERS, feeders);
        return tag;
    }
}
