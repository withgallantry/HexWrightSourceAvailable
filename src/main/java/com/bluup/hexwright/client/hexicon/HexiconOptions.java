package com.bluup.hexwright.client.hexicon;

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

public final class HexiconOptions {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "hexwright-hexicon.json";

    private static final class Data {
        boolean wheelSounds = true;
    }

    private static Data data = new Data();

    private HexiconOptions() {
    }

    public static boolean wheelSounds() {
        return data.wheelSounds;
    }

    public static OptionInstance<Boolean> wheelSoundsOption() {
        return OptionInstance.createBoolean(
            "options.hexwright.hexicon_sounds",
            OptionInstance.cachedConstantTooltip(
                Component.translatable("options.hexwright.hexicon_sounds.tooltip")),
            data.wheelSounds,
            value -> {
                data.wheelSounds = value;
                save();
            });
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
            Hexwright.LOGGER.warn("Could not read {}; keeping Hexicon defaults", FILE_NAME, e);
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
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
