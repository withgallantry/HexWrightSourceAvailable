package com.bluup.hexwright.client.staff_assembly;

import at.petrak.hexcasting.api.HexAPI;
import at.petrak.hexcasting.api.casting.eval.ResolvedPattern;
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType;
import at.petrak.hexcasting.api.casting.math.HexCoord;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.client.gui.GuiSpellcasting;
import at.petrak.hexcasting.client.render.RenderLib;
import at.petrak.hexcasting.common.lib.HexSounds;
import at.petrak.hexcasting.common.msgs.MsgNewSpellPatternC2S;
import at.petrak.hexcasting.xplat.IClientXplatAbstractions;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.client.spellcasting.PatternDrawBatch;
import com.bluup.hexwright.mixin.GuiSpellcastingAccessor;
import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.staff_assembly.StaffGreatSpellData;
import com.bluup.hexwright.server.staff_assembly.StaffPowers;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class AmethystGreatSpellOverlay {
    private static final int ICON_SIZE = 14;
    private static final int ICON_GAP = 4;
    private static final int PANEL_PAD = 4;
    private static final int PANEL_MARGIN = 8;
    private static final int MAX_ROWS = 20;

    private static final int SEARCH_MIN_SPELLS = 10;
    private static final int SEARCH_WIDTH = 90;
    private static final int SEARCH_HEIGHT = 14;

    private static final int GHOST_ALPHA = 0x99000000;

    private AmethystGreatSpellOverlay() {
    }

    public record Picked(int index, HexPattern pattern) {
    }

    public static EditBox createSearch(Font font, int screenWidth, String previousValue) {
        EditBox search = new EditBox(font, screenWidth - PANEL_MARGIN - SEARCH_WIDTH, PANEL_MARGIN,
            SEARCH_WIDTH, SEARCH_HEIGHT, Component.translatable("gui.hexwright.amethyst_core.search"));
        search.setHint(Component.translatable("gui.hexwright.amethyst_core.search")
            .withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(previousValue);
        search.visible = false;
        return search;
    }

    public static void render(GuiGraphics graphics, GuiSpellcasting spellGui, int mouseX, int mouseY,
                              InteractionHand handOpenedWith, @Nullable HexPattern carried, EditBox search,
                              float partialTick) {
        List<StaffGreatSpellData.LearnedGreatSpell> learned = learned(handOpenedWith);
        search.visible = learned.size() > SEARCH_MIN_SPELLS;
        if (learned.isEmpty()) {
            return;
        }

        if (carried != null) {
            drawCarried(graphics, spellGui, carried, mouseX, mouseY);
        }

        if (search.visible) {
            search.render(graphics, mouseX, mouseY, partialTick);
        }

        Layout layout = layout(learned, search, spellGui.width, spellGui.height);
        if (layout.slots().isEmpty()) {
            return;
        }

        graphics.fill(layout.left() - 1, layout.top() - 1, layout.left() + layout.width() + 1,
            layout.top() + layout.height() + 1, 0xAA1A1328);
        graphics.fill(layout.left(), layout.top(), layout.left() + layout.width(), layout.top() + layout.height(),
            0xCC241B3A);

        for (Slot slot : layout.slots()) {
            ItemStack displayScroll = learned.get(slot.index()).toDisplayScroll();
            drawScaledItem(graphics, displayScroll, slot.x(), slot.y(), ICON_SIZE);
            if (contains(mouseX, mouseY, slot.x(), slot.y(), ICON_SIZE, ICON_SIZE)) {
                Minecraft client = Minecraft.getInstance();
                List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(client, displayScroll));
                lines.add(Component.translatable("tooltip.hexwright.amethyst_core.chooser_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
                graphics.renderTooltip(client.font, lines, Optional.empty(), mouseX, mouseY);
            }
        }
    }

    public static @Nullable Picked pick(double mouseX, double mouseY, GuiSpellcasting spellGui,
                                        InteractionHand handOpenedWith, EditBox search) {
        List<StaffGreatSpellData.LearnedGreatSpell> learned = learned(handOpenedWith);
        for (Slot slot : layout(learned, search, spellGui.width, spellGui.height).slots()) {
            if (contains(mouseX, mouseY, slot.x(), slot.y(), ICON_SIZE, ICON_SIZE)) {
                return new Picked(slot.index(), learned.get(slot.index()).toPatternIota().getPattern());
            }
        }
        return null;
    }

    public static boolean place(GuiSpellcasting spellGui, InteractionHand handOpenedWith, HexPattern carried,
                                double mouseX, double mouseY) {
        HexCoord origin = originUnderCursor(spellGui, carried, mouseX, mouseY);
        List<HexCoord> positions = carried.positions(origin);
        GuiSpellcastingAccessor accessor = (GuiSpellcastingAccessor) (Object) spellGui;
        Set<HexCoord> usedSpots = accessor.hexwright$getUsedSpots();
        if (positions.stream().anyMatch(usedSpots::contains)) {
            return false;
        }

        HexPattern placed = new HexPattern(carried.getStartDir(), new ArrayList<>(carried.getAngles()));
        List<ResolvedPattern> patterns = accessor.hexwright$getPatterns();
        patterns.add(new ResolvedPattern(placed, origin, ResolvedPatternType.UNRESOLVED));
        usedSpots.addAll(positions);

        IClientXplatAbstractions.INSTANCE.sendPacketToServer(
            new MsgNewSpellPatternC2S(handOpenedWith, placed, patterns));
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(HexSounds.ADD_TO_PATTERN, 1f, 0.25f));
        return true;
    }

    private static void drawCarried(GuiGraphics graphics, GuiSpellcasting spellGui, HexPattern carried,
                                    int mouseX, int mouseY) {
        HexCoord origin = originUnderCursor(spellGui, carried, mouseX, mouseY);
        List<HexCoord> positions = carried.positions(origin);
        Set<HexCoord> usedSpots = ((GuiSpellcastingAccessor) (Object) spellGui).hexwright$getUsedSpots();
        boolean blocked = positions.stream().anyMatch(usedSpots::contains);

        List<Vec2> points = new ArrayList<>(positions.size());
        for (HexCoord position : positions) {
            points.add(spellGui.coordToPx(position));
        }

        int start = blocked ? ResolvedPatternType.ERRORED.getColor() : 0x64C8FF;
        int end = blocked ? ResolvedPatternType.ERRORED.getFadeColor() : 0xFECBE6;
        PatternDrawBatch.drawPattern(graphics.pose().last().pose(), points, RenderLib.findDupIndices(positions),
            true, (start & 0xFFFFFF) | GHOST_ALPHA, (end & 0xFFFFFF) | GHOST_ALPHA, blocked ? 0.9f : 0.1f,
            RenderLib.DEFAULT_READABILITY_OFFSET, 1f, 0);
        PatternDrawBatch.flush();
        RenderSystem.disableBlend();
    }

    private static HexCoord originUnderCursor(GuiSpellcasting spellGui, HexPattern pattern,
                                              double mouseX, double mouseY) {
        List<HexCoord> positions = pattern.positions();
        int q = 0;
        int r = 0;
        for (HexCoord position : positions) {
            q += position.getQ();
            r += position.getR();
        }
        HexCoord centre = new HexCoord(Math.round((float) q / positions.size()),
            Math.round((float) r / positions.size()));
        return spellGui.pxToCoord(new Vec2((float) mouseX, (float) mouseY)).minus(centre);
    }

    private record Slot(int index, int x, int y) {
    }

    private record Layout(int left, int top, int width, int height, List<Slot> slots) {
    }

    private static Layout layout(List<StaffGreatSpellData.LearnedGreatSpell> learned, EditBox search,
                                 int screenWidth, int screenHeight) {
        String query = search.visible ? search.getValue().trim().toLowerCase(Locale.ROOT) : "";
        List<Integer> matching = new ArrayList<>();
        for (int i = 0; i < learned.size(); i++) {
            if (query.isEmpty() || matches(learned.get(i), query)) {
                matching.add(i);
            }
        }

        int count = matching.size();
        int cell = ICON_SIZE + ICON_GAP;
        int top = search.visible ? PANEL_MARGIN + SEARCH_HEIGHT + ICON_GAP : PANEL_MARGIN;
        int fitRows = (screenHeight - top - PANEL_MARGIN - PANEL_PAD * 2 + ICON_GAP) / cell;
        int maxRows = Math.max(1, Math.min(MAX_ROWS, fitRows));
        int columns = Math.max(1, (count + maxRows - 1) / maxRows);
        int rows = Math.max(1, (count + columns - 1) / columns);

        int width = PANEL_PAD * 2 + columns * ICON_SIZE + (columns - 1) * ICON_GAP;
        int height = PANEL_PAD * 2 + rows * ICON_SIZE + (rows - 1) * ICON_GAP;
        int left = screenWidth - width - PANEL_MARGIN;
        if (!search.visible) {
            top = Math.max(PANEL_MARGIN, (screenHeight - height) / 2);
        }

        List<Slot> slots = new ArrayList<>(count);
        for (int n = 0; n < count; n++) {
            int x = left + PANEL_PAD + (n / rows) * cell;
            int y = top + PANEL_PAD + (n % rows) * cell;
            slots.add(new Slot(matching.get(n), x, y));
        }
        return new Layout(left, top, width, height, slots);
    }

    private static boolean matches(StaffGreatSpellData.LearnedGreatSpell spell, String query) {
        ResourceLocation opId = ResourceLocation.tryParse(spell.opId());
        if (opId == null) {
            return spell.opId().contains(query);
        }
        String name = I18n.get(HexAPI.instance().getActionI18nKey(
            ResourceKey.create(IXplatAbstractions.INSTANCE.getActionRegistry().key(), opId)));
        return name.toLowerCase(Locale.ROOT).contains(query) || spell.opId().contains(query);
    }

    private static List<StaffGreatSpellData.LearnedGreatSpell> learned(InteractionHand handOpenedWith) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) {
            return List.of();
        }

        ItemStack held = client.player.getItemInHand(handOpenedWith);
        if (!held.is(HexwrightItems.CONFIGURABLE_STAFF) || !StaffPowers.hasAmethystCore(held)) {
            return List.of();
        }
        return StaffGreatSpellData.getLearned(held);
    }

    private static void drawScaledItem(GuiGraphics graphics, ItemStack stack, int x, int y, int targetSize) {
        float scale = targetSize / 16.0f;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
    }

    private static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
