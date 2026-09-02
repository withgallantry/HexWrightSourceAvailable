package com.bluup.hexwright.client.portal;

import com.bluup.hexwright.Hexwright;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.Codec;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.OptionEnum;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class PortalOptions {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "hexwright-portals.json";

    public enum PortalViews implements OptionEnum {
        SHIMMER(0, "options.hexwright.portal_views.shimmer"),
        PANES(1, "options.hexwright.portal_views.panes"),
        FULL(2, "options.hexwright.portal_views.full");

        private static final PortalViews[] BY_ID = values();

        private final int id;
        private final String key;

        PortalViews(int id, String key) {
            this.id = id;
            this.key = key;
        }

        @Override
        public int getId() {
            return id;
        }

        @Override
        public String getKey() {
            return key;
        }

        static PortalViews byId(int id) {
            return BY_ID[Math.floorMod(id, BY_ID.length)];
        }
    }

    private static final class Data {
        Boolean portalsThroughPortals;

        PortalViews portalViews;
    }

    private static Data data = new Data();

    private PortalOptions() {
    }

    public static PortalViews views() {
        PortalViews chosen = data.portalViews;
        return chosen == null ? PortalViews.FULL : chosen;
    }

    public static void setViews(PortalViews value) {
        if (data.portalViews == value) {
            return;
        }
        data.portalViews = value;
        if (value == PortalViews.SHIMMER) {
            PortalViewRenderer.destroyAllTargets();
        }
        save();
    }

    public static OptionInstance<PortalViews> portalViewsOption() {
        return new OptionInstance<>(
            "options.hexwright.portal_views",
            OptionInstance.cachedConstantTooltip(
                Component.translatable("options.hexwright.portal_views.tooltip")),
            OptionInstance.forOptionEnum(),
            new OptionInstance.Enum<>(List.of(PortalViews.values()),
                Codec.INT.xmap(PortalViews::byId, PortalViews::getId)),
            views(),
            PortalOptions::setViews);
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static void load() {
        Path path = configPath();
        boolean existed = Files.exists(path);
        if (existed) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                Data parsed = GSON.fromJson(reader, Data.class);
                if (parsed != null) {
                    data = parsed;
                }
            } catch (IOException | JsonSyntaxException e) {
                Hexwright.LOGGER.warn("Could not read {}; keeping portal defaults", FILE_NAME, e);
            }
        }

        boolean rewrite = !existed;
        if (data.portalViews == null) {
            data.portalViews = Boolean.FALSE.equals(data.portalsThroughPortals)
                ? PortalViews.PANES : PortalViews.FULL;
            rewrite = true;
        }
        if (data.portalsThroughPortals != null) {
            data.portalsThroughPortals = null;
            rewrite = true;
        }
        if (rewrite) {
            save();
        }
    }

    private static void save() {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            Hexwright.LOGGER.warn("Could not write {}; the setting will not survive this session",
                FILE_NAME, e);
        }
    }
}
