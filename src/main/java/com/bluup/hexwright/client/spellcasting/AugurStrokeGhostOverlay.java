package com.bluup.hexwright.client.spellcasting;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.math.HexAngle;
import at.petrak.hexcasting.api.casting.math.HexCoord;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.mod.HexTags;
import at.petrak.hexcasting.api.utils.HexUtils;
import at.petrak.hexcasting.client.gui.GuiSpellcasting;
import at.petrak.hexcasting.client.render.RenderLib;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.mixin.GuiSpellcastingAccessor;
import com.bluup.hexwright.server.armour.ArmourPowerToggle;
import com.bluup.hexwright.server.armour.ArmourSet;
import com.bluup.hexwright.server.armour.ArmourTier;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec2;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AugurStrokeGhostOverlay {
    private static final float OUTER_FRAC = 0.33f;
    private static final float INNER_FRAC = 0.23f;

    private static final float OUTER_R = 0.73f;
    private static final float OUTER_G = 0.45f;
    private static final float OUTER_B = 1.0f;
    private static final float OUTER_A = 0.58f;

    private static final float INNER_R = 0.82f;
    private static final float INNER_G = 0.62f;
    private static final float INNER_B = 1.0f;
    private static final float INNER_A = 0.96f;

    private static final int NO_GUIDANCE = 0;
    private static final int UNLIMITED_GUIDANCE = Integer.MAX_VALUE;
    private static final int SAMPLE_NAME_LIMIT = 2;

    private static TrieCache trieCache;
    private static Handles handles;
    private static boolean handlesResolved;
    private static HintCache hintCache;

    private AugurStrokeGhostOverlay() {
    }

    public static void render(GuiGraphics graphics, GuiSpellcasting spellGui) {
        OverlayState state = overlayState(spellGui);
        if (state.hints().isEmpty()) {
            return;
        }

        float hexSize = spellGui.hexSize();
        var mat = graphics.pose().last().pose();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        for (Hint hint : state.hints()) {
            Vec2 px = spellGui.coordToPx(hint.coord());
            RenderLib.drawSpot(mat, px, hexSize * OUTER_FRAC, OUTER_R, OUTER_G, OUTER_B, OUTER_A);
            RenderLib.drawSpot(mat, px, hexSize * INNER_FRAC, INNER_R, INNER_G, INNER_B, INNER_A);
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();

        if (state.banner() != null) {
            drawTopBanner(graphics, spellGui, state.banner());
        }
    }

    private static OverlayState overlayState(GuiSpellcasting spellGui) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            hintCache = null;
            return OverlayState.EMPTY;
        }

        int budget = stepsFromAugurChest(client.player);
        if (budget == NO_GUIDANCE) {
            hintCache = null;
            return OverlayState.EMPTY;
        }

        Handles h = handles();
        if (h == null) {
            hintCache = null;
            return OverlayState.EMPTY;
        }

        DrawState state = drawStateOf(h, spellGui);
        if (state == null) {
            hintCache = null;
            return OverlayState.EMPTY;
        }

        Set<HexCoord> usedSpots = usedSpotsOf(spellGui);
        if (usedSpots == null) {
            hintCache = null;
            return OverlayState.EMPTY;
        }

        if (budget != UNLIMITED_GUIDANCE && state.strokesDrawn() >= budget) {
            hintCache = null;
            return OverlayState.EMPTY;
        }

        HintCache cached = hintCache;
        if (cached != null && cached.matches(state, usedSpots.size(), budget)) {
            return new OverlayState(cached.hints(), cached.banner());
        }

        TrieNode root = trieRoot();
        if (root == null || root.children().isEmpty()) {
            hintCache = null;
            return OverlayState.EMPTY;
        }

        TrieNode node = state.wip() == null ? root : descend(root, state.wip().anglesSignature());
        if (node == null) {
            hintCache = null;
            return OverlayState.EMPTY;
        }

        List<Hint> hints = nextHints(state, node, usedSpots);
        Component banner = bannerFor(node);
        hintCache = HintCache.of(state, usedSpots.size(), budget, hints, banner);
        return new OverlayState(hints, banner);
    }

    private static List<Hint> nextHints(DrawState state, TrieNode node, Set<HexCoord> usedSpots) {
        List<Hint> hints = new ArrayList<>();
        long[] drawnEdges = state.drawnEdges();

        for (HexDir dir : HexDir.values()) {
            HexAngle angle = null;
            if (state.inDir() != null) {
                angle = dir.minus(state.inDir());
                if (angle == HexAngle.BACK) {
                    continue;
                }
            }

            HexCoord to = state.current().plus(dir);
            if (usedSpots.contains(to)) {
                continue;
            }

            long edge = edgeKey(state.current(), to);
            if (Arrays.binarySearch(drawnEdges, edge) >= 0) {
                continue;
            }

            TrieNode next = angle == null ? node : node.children().get(angleChar(angle));
            if (next == null) {
                continue;
            }

            hints.add(new Hint(to));
        }

        return hints;
    }

    private static int stepsFromAugurChest(LivingEntity wearer) {
        ArmourTier tier = ArmourPowerToggle.activeTier(wearer, ArmourSet.AUGUR);
        if (tier == null) {
            return NO_GUIDANCE;
        }

        return switch (tier) {
            case IRON -> 4;
            case GOLDEN -> 6;
            case DIAMOND -> 8;
            case NETHERITE -> UNLIMITED_GUIDANCE;
        };
    }

    private static @Nullable Component bannerFor(TrieNode node) {
        if (node.terminalCount() <= 0) {
            return null;
        }

        List<Component> names = new ArrayList<>();
        for (String key : node.samples()) {
            names.add(Component.translatable(key));
        }

        if (names.isEmpty()) {
            return null;
        }
        if (node.terminalCount() == 1 || names.size() == 1) {
            return Component.translatable("overlay.hexwright.augur_future.single", names.get(0));
        }

        int extra = Math.max(0, node.terminalCount() - names.size());
        if (extra > 0) {
            return Component.translatable("overlay.hexwright.augur_future.multi_extra",
                names.get(0), names.get(1), extra);
        }
        return Component.translatable("overlay.hexwright.augur_future.multi", names.get(0), names.get(1));
    }

    private static void drawTopBanner(GuiGraphics graphics, GuiSpellcasting spellGui, Component text) {
        Minecraft client = Minecraft.getInstance();
        int textWidth = client.font.width(text);
        int pad = 4;
        int h = 12;
        int left = (spellGui.width - textWidth) / 2 - pad;
        int top = 6;
        int right = left + textWidth + pad * 2;
        int bottom = top + h;

        graphics.fill(left, top, right, bottom, 0xA0201636);
        graphics.fill(left, top, right, top + 1, 0xCC8E6DFF);
        graphics.drawCenteredString(client.font, text, spellGui.width / 2, top + 2, 0xFFE9DDFF);
    }


    private static long coordKey(HexCoord coord) {
        return ((long) (coord.getQ() + 0x8000) << 16) | (coord.getR() + 0x8000);
    }

    private static long edgeKey(HexCoord a, HexCoord b) {
        long ka = coordKey(a);
        long kb = coordKey(b);
        return ka < kb ? (ka << 32) | kb : (kb << 32) | ka;
    }

    private static long[] edgesOf(HexPattern pattern, HexCoord start) {
        List<HexCoord> positions = pattern.positions(start);
        long[] edges = new long[Math.max(0, positions.size() - 1)];
        for (int i = 0; i < edges.length; i++) {
            edges[i] = edgeKey(positions.get(i), positions.get(i + 1));
        }
        Arrays.sort(edges);
        return edges;
    }


    private static @Nullable TrieNode trieRoot() {
        Registry<ActionRegistryEntry> registry = IXplatAbstractions.INSTANCE.getActionRegistry();
        int size = registry.size();

        TrieCache cache = trieCache;
        if (cache != null && cache.registrySize() == size) {
            return cache.root();
        }

        TrieNode root = buildTrie(registry);
        trieCache = new TrieCache(size, root);
        return root;
    }

    private static TrieNode buildTrie(Registry<ActionRegistryEntry> registry) {
        TrieNode root = new TrieNode();

        for (ResourceKey<ActionRegistryEntry> key : registry.registryKeySet()) {
            ActionRegistryEntry entry = registry.get(key);
            if (entry == null || HexUtils.isOfTag(registry, key, HexTags.Actions.PER_WORLD_PATTERN)) {
                continue;
            }

            String sig = entry.prototype().anglesSignature();
            TrieNode node = root;
            for (int i = 0; i < sig.length(); i++) {
                node = node.children().computeIfAbsent(sig.charAt(i), ignored -> new TrieNode());
            }
            node.terminal = true;
            node.directTerminalKeys().add(actionTranslationKey(key));
        }

        summarizeTrie(root);

        return root;
    }

    private static String actionTranslationKey(ResourceKey<ActionRegistryEntry> key) {
        return "hexcasting.action." + key.location();
    }

    private static Summary summarizeTrie(TrieNode node) {
        int count = node.directTerminalKeys().size();
        List<String> samples = new ArrayList<>();
        appendSamples(samples, node.directTerminalKeys());

        for (TrieNode child : node.children().values()) {
            Summary childSummary = summarizeTrie(child);
            count += childSummary.count();
            appendSamples(samples, childSummary.samples());
        }

        node.terminalCount = count;
        node.samples = List.copyOf(samples);
        return new Summary(count, node.samples);
    }

    private static void appendSamples(List<String> out, List<String> in) {
        for (String key : in) {
            if (out.size() >= SAMPLE_NAME_LIMIT) {
                return;
            }
            if (!out.contains(key)) {
                out.add(key);
            }
        }
    }

    private static @Nullable TrieNode descend(TrieNode root, String signature) {
        TrieNode node = root;
        for (int i = 0; i < signature.length(); i++) {
            node = node.children().get(signature.charAt(i));
            if (node == null) {
                return null;
            }
        }
        return node;
    }

    private static char angleChar(HexAngle angle) {
        return switch (angle) {
            case FORWARD -> 'w';
            case RIGHT -> 'e';
            case RIGHT_BACK -> 'd';
            case BACK -> 's';
            case LEFT_BACK -> 'a';
            case LEFT -> 'q';
        };
    }


    private static @Nullable Handles handles() {
        if (handlesResolved) {
            return handles;
        }
        handlesResolved = true;

        try {
            Field drawState = GuiSpellcasting.class.getDeclaredField("drawState");
            drawState.setAccessible(true);

            ClassLoader loader = GuiSpellcasting.class.getClassLoader();
            Class<?> drawing = Class.forName(
                "at.petrak.hexcasting.client.gui.GuiSpellcasting$PatternDrawState$Drawing", false, loader);
            Class<?> justStarted = Class.forName(
                "at.petrak.hexcasting.client.gui.GuiSpellcasting$PatternDrawState$JustStarted", false, loader);

            handles = new Handles(
                drawState,
                drawing,
                drawing.getMethod("getStart"),
                drawing.getMethod("getCurrent"),
                drawing.getMethod("getWipPattern"),
                justStarted,
                justStarted.getMethod("getStart"));
        } catch (ReflectiveOperationException ignored) {
            handles = null;
        }
        return handles;
    }

    private static @Nullable DrawState drawStateOf(Handles h, GuiSpellcasting spellGui) {
        Object raw;
        try {
            raw = h.drawState().get(spellGui);
        } catch (IllegalAccessException ignored) {
            return null;
        }
        if (raw == null) {
            return null;
        }

        try {
            if (h.drawing().isInstance(raw)) {
                HexCoord start = (HexCoord) h.drawingStart().invoke(raw);
                HexCoord current = (HexCoord) h.drawingCurrent().invoke(raw);
                HexPattern wip = (HexPattern) h.drawingWip().invoke(raw);

                List<HexAngle> angles = List.copyOf(wip.getAngles());
                HexPattern snapshot = new HexPattern(wip.getStartDir(), new ArrayList<>(angles));
                return new DrawState(current, snapshot.finalDir(), snapshot, angles, edgesOf(snapshot, start),
                    angles.size() + 1);
            }
            if (h.justStarted().isInstance(raw)) {
                HexCoord start = (HexCoord) h.justStartedStart().invoke(raw);
                return new DrawState(start, null, null, List.of(), new long[0], 0);
            }
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            return null;
        }
        return null;
    }

    private static @Nullable Set<HexCoord> usedSpotsOf(GuiSpellcasting spellGui) {
        return ((GuiSpellcastingAccessor) (Object) spellGui).hexwright$getUsedSpots();
    }


    private record TrieCache(int registrySize, TrieNode root) {
    }

    private record Handles(Field drawState, Class<?> drawing, Method drawingStart,
                           Method drawingCurrent, Method drawingWip, Class<?> justStarted,
                           Method justStartedStart) {
    }

    private record Hint(HexCoord coord) {
    }

    private record OverlayState(List<Hint> hints, @Nullable Component banner) {
        private static final OverlayState EMPTY = new OverlayState(List.of(), null);
    }

    private record DrawState(HexCoord current, @Nullable HexDir inDir, @Nullable HexPattern wip,
                             List<HexAngle> angles, long[] drawnEdges, int strokesDrawn) {
    }

    private record HintCache(HexCoord current, @Nullable HexDir startDir, List<HexAngle> angles,
                             int usedSpotCount, int budget, List<Hint> hints, @Nullable Component banner) {

        static HintCache of(DrawState state, int usedSpotCount, int budget, List<Hint> hints,
                            @Nullable Component banner) {
            HexDir startDir = state.wip() == null ? null : state.wip().getStartDir();
            return new HintCache(state.current(), startDir, state.angles(), usedSpotCount, budget, hints, banner);
        }

        boolean matches(DrawState state, int otherUsedSpotCount, int otherBudget) {
            HexDir otherStartDir = state.wip() == null ? null : state.wip().getStartDir();
            return usedSpotCount == otherUsedSpotCount
                && budget == otherBudget
                && current.equals(state.current())
                && startDir == otherStartDir
                && angles.equals(state.angles());
        }
    }

    private static final class TrieNode {
        private final Map<Character, TrieNode> children = new HashMap<>();
        private boolean terminal;
        private final List<String> directTerminalKeys = new ArrayList<>();
        private int terminalCount;
        private List<String> samples = List.of();

        private Map<Character, TrieNode> children() {
            return children;
        }

        private List<String> directTerminalKeys() {
            return directTerminalKeys;
        }

        private int terminalCount() {
            return terminalCount;
        }

        private List<String> samples() {
            return samples;
        }
    }

    private record Summary(int count, List<String> samples) {
    }
}
