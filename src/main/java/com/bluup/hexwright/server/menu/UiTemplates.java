package com.bluup.hexwright.server.menu;

import com.bluup.hexwright.Hexwright;
import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.lowdraglib.gui.editor.data.UIProject;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class UiTemplates {

    private record CachedTemplate(long lastModified, Supplier<WidgetGroup> supplier) {
    }

    private record CacheKey(String name, boolean renderThread) {
    }

    private static final Map<CacheKey, CachedTemplate> FILE_CACHE = new ConcurrentHashMap<>();
    private static final Map<CacheKey, Optional<Supplier<WidgetGroup>>> PACKAGED_CACHE = new ConcurrentHashMap<>();

    private UiTemplates() {
    }

    public static @Nullable Supplier<WidgetGroup> load(String name) {
        Supplier<WidgetGroup> fromLdlibDir = loadFromLdlibDir(new ResourceLocation("ldlib", name));
        if (fromLdlibDir != null) {
            return fromLdlibDir;
        }
        Supplier<WidgetGroup> fromOurDir = loadFromLdlibDir(Hexwright.id(name));
        if (fromOurDir != null) {
            return fromOurDir;
        }
        return PACKAGED_CACHE.computeIfAbsent(new CacheKey(name, LDLib.isRemote()),
            key -> Optional.ofNullable(loadPackaged(key.name()))).orElse(null);
    }

    private static @Nullable Supplier<WidgetGroup> loadPackaged(String name) {
        try (InputStream raw = UiTemplates.class.getResourceAsStream("/assets/hexwright/projects/ui/" + name + ".ui")) {
            if (raw != null) {
                Supplier<WidgetGroup> loaded = read(raw);
                if (loaded != null) {
                    return loaded;
                }
            }
        } catch (Throwable t) {
            Hexwright.LOGGER.error("Failed to load {}.ui from the classpath", name, t);
        }

        if (LDLib.isClient()) {
            Supplier<WidgetGroup> fromPacks = ClientPacks.load(name);
            if (fromPacks != null) {
                return fromPacks;
            }
        }

        Hexwright.LOGGER.error("Failed to load {}.ui from the LDLib directory, the classpath, or resource packs", name);
        return null;
    }

    private static @Nullable Supplier<WidgetGroup> loadFromLdlibDir(ResourceLocation id) {
        File file = new File(LDLib.getLDLibDir(), "assets/%s/projects/ui/%s.ui".formatted(id.getNamespace(), id.getPath()));
        if (!file.isFile()) {
            return null;
        }
        long modified = file.lastModified();
        CacheKey key = new CacheKey(id.toString(), LDLib.isRemote());
        CachedTemplate cached = FILE_CACHE.get(key);
        if (cached != null && cached.lastModified() == modified) {
            return cached.supplier();
        }
        Supplier<WidgetGroup> loaded = UIProject.loadUIFromFile(id);
        if (loaded == null) {
            return null;
        }
        FILE_CACHE.put(key, new CachedTemplate(modified, loaded));
        return loaded;
    }

    private static @Nullable Supplier<WidgetGroup> read(InputStream stream) throws Exception {
        try (DataInputStream input = new DataInputStream(stream)) {
            CompoundTag tag = NbtIo.read(input);
            return tag == null ? null : UIProject.loadUIFromTag(tag);
        }
    }

    private static final class ClientPacks {
        static @Nullable Supplier<WidgetGroup> load(String name) {
            try {
                var resource = Minecraft.getInstance().getResourceManager()
                    .getResource(Hexwright.id("projects/ui/" + name + ".ui"));
                if (resource.isPresent()) {
                    return read(resource.get().open());
                }
            } catch (Throwable t) {
                Hexwright.LOGGER.error("Failed to load {}.ui from resource packs", name, t);
            }
            return null;
        }
    }
}
