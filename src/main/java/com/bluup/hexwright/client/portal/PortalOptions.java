package com.bluup.hexwright.client.portal;

import com.bluup.hexwright.Hexwright;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PortalOptions {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "hexwright-portals.json";

    private static final class Data {
        boolean portalsThroughPortals = true;
    }

    private static Data data = new Data();

    private PortalOptions() {
    }

    public static boolean portalsThroughPortals() {
        return data.portalsThroughPortals;
    }

    public static void setPortalsThroughPortals(boolean value) {
        if (data.portalsThroughPortals != value) {
            data.portalsThroughPortals = value;
            save();
        }
    }

    public static OptionInstance<Boolean> portalsThroughPortalsOption() {
        return OptionInstance.createBoolean(
            "options.hexwright.portals_through_portals",
            OptionInstance.cachedConstantTooltip(
                Component.translatable("options.hexwright.portals_through_portals.tooltip")),
            portalsThroughPortals(),
            PortalOptions::setPortalsThroughPortals);
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static void load() {
        Path path = configPath();
        if (!Files.exists(path)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Data parsed = GSON.fromJson(reader, Data.class);
            if (parsed != null) {
                data = parsed;
            }
        } catch (IOException | JsonSyntaxException e) {
            Hexwright.LOGGER.warn("Could not read {}; keeping portal defaults", FILE_NAME, e);
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
