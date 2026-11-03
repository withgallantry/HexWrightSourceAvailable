package com.bluup.hexwright.server.journal;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.function.Supplier;

final class JournalFile<T> {

    private static final Gson GSON = new Gson();

    private static final long NO_OVERRIDE = -1L;

    private static final long STAT_INTERVAL_NANOS = 2_000_000_000L;

    private final String classpathPath;
    private final String overrideFileName;
    private final Function<JsonObject, T> parser;
    private final Supplier<T> empty;

    private T cached;
    private long cachedStamp = NO_OVERRIDE;
    private long lastStatNanos;

    JournalFile(String classpathPath, String overrideFileName, Function<JsonObject, T> parser, Supplier<T> empty) {
        this.classpathPath = classpathPath;
        this.overrideFileName = overrideFileName;
        this.parser = parser;
        this.empty = empty;
    }

    synchronized T get() {
        T current = cached;

        long now = System.nanoTime();
        if (current != null && now - lastStatNanos < STAT_INTERVAL_NANOS) {
            return current;
        }
        lastStatNanos = now;

        Path override = overridePath();
        long stamp = stampOf(override);
        if (current != null && stamp == cachedStamp) {
            return current;
        }

        T loaded = read(override, stamp != NO_OVERRIDE);
        cached = loaded;
        cachedStamp = stamp;
        return loaded;
    }

    private Path overridePath() {
        return FabricLoader.getInstance().getConfigDir().resolve(overrideFileName);
    }

    private long stampOf(Path path) {
        try {
            return Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : NO_OVERRIDE;
        } catch (Throwable t) {
            Hexwright.LOGGER.error("Failed to stat journal override at {}", path, t);
            return NO_OVERRIDE;
        }
    }

    private T read(Path override, boolean useOverride) {
        String source = useOverride ? override.toString() : classpathPath;
        try (Reader reader = openReader(override, useOverride)) {
            if (reader == null) {
                Hexwright.LOGGER.error("Journal content missing at {}; that section will be empty", classpathPath);
                return empty.get();
            }
            T parsed = parser.apply(GSON.fromJson(reader, JsonObject.class));
            HexwrightDebug.log(HexwrightDebug.CONTENT, "Loaded journal content from {}", source);
            return parsed;
        } catch (Throwable t) {
            Hexwright.LOGGER.error("Failed to read journal content from {}; that section will be empty", source, t);
            return empty.get();
        }
    }

    private Reader openReader(Path override, boolean useOverride) throws Exception {
        if (useOverride) {
            return Files.newBufferedReader(override, StandardCharsets.UTF_8);
        }
        InputStream stream = JournalFile.class.getResourceAsStream(classpathPath);
        return stream == null ? null : new InputStreamReader(stream, StandardCharsets.UTF_8);
    }
}
