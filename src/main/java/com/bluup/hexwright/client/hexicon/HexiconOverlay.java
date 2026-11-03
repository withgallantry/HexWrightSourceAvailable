package com.bluup.hexwright.client.hexicon;

import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.hexicon.HexiconData;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.mojang.datafixers.util.Pair;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public final class HexiconOverlay {
    private static final long DOUBLE_TAP_WINDOW_MS = 250L;
    private static final long OPEN_ANIMATION_MS = 110L;

    private static final int SLOT_COUNT = HexiconData.SLOTS_PER_BAR;
    private static final int RING_HALF = 58;
    private static final int SLOT_SIZE = 22;
    private static final int SLOT_ICON_SIZE = 16;
    private static final int PANEL_HALF = RING_HALF + SLOT_SIZE / 2 + 7;
    private static final double CURSOR_RADIUS = RING_HALF;
    private static final int DEADZONE = 14;
    private static final double GAP_DEGREES = 20;
    private static final double SECTOR_DEGREES = (360 - GAP_DEGREES) / SLOT_COUNT;
    private static final double HYSTERESIS_DEGREES = 4;
    private static final double EXIT_CONE_DEGREES = 35;
    private static final int CENTER_ICON_SIZE = 28;
    private static final int CENTER_TEXT_WIDTH = RING_HALF * 2 - SLOT_SIZE - 4;

    private static final int CHAPTER_CELL = 20;
    private static final int CHAPTER_GAP = 3;
    private static final int CHAPTER_STEP = CHAPTER_CELL + CHAPTER_GAP;
    private static final int CHAPTER_PADDING = 4;
    private static final int CHAPTER_BAR_GAP = 10;
    private static final int CHAPTER_ICON_SIZE = 14;
    private static final int CHAPTER_HYSTERESIS = 5;
    private static final int TITLE_ICON_SIZE = 10;

    private static final double CURSOR_SPEED = 1.5;
    private static final double SWITCH_PUSH = 42.0;
    private static final double PUSH_DECAY_PER_SECOND = 90.0;
    private static final int PUSH_LEAN = 8;
    private static final int PUSH_METER_WIDTH = 40;
    private static final float HIGHLIGHT_SPEED = 22f;

    private static final int COLOR_PANEL = 0xC0140F22;
    private static final int COLOR_FRAME = 0xFF3E3260;
    private static final int COLOR_FRAME_FOCUSED = 0xFF8F78C8;
    private static final int COLOR_SLOT_BOUND = 0xC02A2240;
    private static final int COLOR_SLOT_EMPTY = 0x70201A2E;
    private static final int COLOR_SLOT_BORDER = 0xFF3A3052;
    private static final int COLOR_ACCENT = 0xFFDAB7FF;
    private static final int COLOR_ACCENT_DIM = 0x80DAB7FF;
    private static final int COLOR_GLOW = 0x30DAB7FF;
    private static final int COLOR_CURSOR = 0xE0FFFFFF;
    private static final int COLOR_TEXT = 0xFFE8E4FF;
    private static final int COLOR_TEXT_DIM = 0xFF8F87A8;
    private static final int COLOR_TEXT_BOUND = 0xFF9FE8FF;

    private static final int[][] SLOT_OFFSETS = new int[SLOT_COUNT][2];
    private static final double[] SLOT_ANGLES = new double[SLOT_COUNT];

    static {
        for (int i = 0; i < SLOT_COUNT; i++) {
            double degrees = -90 + i * SECTOR_DEGREES + (i >= SLOT_COUNT / 2 + 1 ? GAP_DEGREES : 0);
            double angle = Math.toRadians(degrees);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double toEdge = RING_HALF / Math.max(Math.abs(cos), Math.abs(sin));
            SLOT_OFFSETS[i][0] = (int) Math.round(cos * toEdge);
            SLOT_OFFSETS[i][1] = (int) Math.round(sin * toEdge);
            SLOT_ANGLES[i] = angle;
        }
    }

    private enum Section { RING, CHAPTERS }

    private static boolean active;
    private static boolean wasKeyDown;
    private static long lastKeyPressMs;
    private static int lastNumberKey = -1;
    private static long lastNumberKeyMs;
    private static int slotBeforeNumberTap;

    private static Section section = Section.RING;
    private static double cursorX;
    private static double cursorY;
    private static double chapterCursorX;
    private static double push;
    private static int chapter;
    private static int slot;
    private static int slotBeforePush;
    private static int chapterBeforePush;

    private static long openedAtMs;
    private static long lastInputMs;
    private static long lastRenderMs;
    private static float highlightX;
    private static float highlightY;
    private static float chapterHighlightX;

    private HexiconOverlay() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void onClientTick(Minecraft client, boolean keyDown) {
        Pair<InteractionHand, ItemStack> held = resolveHeld(client);
        if (held == null || client.screen != null) {
            active = false;
            wasKeyDown = keyDown;
            return;
        }

        if (keyDown && !wasKeyDown) {
            long now = Util.getMillis();
            boolean isDoubleTap = now - lastKeyPressMs <= DOUBLE_TAP_WINDOW_MS;
            lastKeyPressMs = now;

            if (isDoubleTap) {
                active = false;
                HexwrightNetworking.sendHexiconOpenMenu(held.getFirst());
            } else {
                open(held.getSecond());
            }
        } else if (!keyDown && wasKeyDown && active) {
            commit(held);
            active = false;
        }

        wasKeyDown = keyDown;
    }

    private static void open(ItemStack stack) {
        active = true;
        section = Section.RING;
        cursorX = 0;
        cursorY = 0;
        push = 0;
        chapter = HexiconData.getSelectedBar(stack);
        slot = HexiconData.getSelectedSlot(stack);
        lastNumberKey = -1;
        highlightX = SLOT_OFFSETS[slot][0];
        highlightY = SLOT_OFFSETS[slot][1];
        chapterHighlightX = chapterCellLeft(chapter, HexiconData.getAvailableBars(stack));
        openedAtMs = Util.getMillis();
        lastInputMs = openedAtMs;
        lastRenderMs = openedAtMs;
    }

    private static void commit(Pair<InteractionHand, ItemStack> held) {
        ItemStack stack = held.getSecond();
        if (chapter == HexiconData.getSelectedBar(stack) && slot == HexiconData.getSelectedSlot(stack)) {
            return;
        }
        HexiconData.setSelectedBarAndSlot(stack, chapter, slot);
        HexwrightNetworking.sendHexiconSelection(held.getFirst(), chapter, slot, HexiconData.getLibraryId(stack));
    }

    public static void accumulateDrag(double rawDx, double rawDy) {
        Minecraft client = Minecraft.getInstance();
        Pair<InteractionHand, ItemStack> held = resolveHeld(client);
        if (held == null) {
            return;
        }

        double scale = CURSOR_SPEED / client.getWindow().getGuiScale();
        double dx = rawDx * scale;
        double dy = rawDy * scale;

        long now = Util.getMillis();
        double seconds = Math.min(0.1, (now - lastInputMs) / 1000.0);
        lastInputMs = now;
        push = Math.max(0, push - PUSH_DECAY_PER_SECOND * seconds);

        int bars = HexiconData.getAvailableBars(held.getSecond());
        if (section == Section.RING) {
            moveInRing(dx, dy, bars);
        } else {
            moveInChapters(dx, dy, bars);
        }
    }

    private static void moveInRing(double dx, double dy, int bars) {
        double x = cursorX + dx;
        double y = cursorY + dy;
        double radius = Math.hypot(x, y);
        boolean pushing = false;
        if (radius > CURSOR_RADIUS) {
            double downward = Math.toDegrees(Math.abs(Math.atan2(x, y)));
            if (bars > 1 && downward <= EXIT_CONE_DEGREES && dy > Math.abs(dx)) {
                if (push == 0) {
                    slotBeforePush = slot;
                }
                push += radius - CURSOR_RADIUS;
                pushing = true;
            }
            x *= CURSOR_RADIUS / radius;
            y *= CURSOR_RADIUS / radius;
        } else if (dy < 0) {
            push = Math.max(0, push + dy);
        }
        cursorX = x;
        cursorY = y;

        if (push >= SWITCH_PUSH) {
            slot = slotBeforePush;
            section = Section.CHAPTERS;
            push = 0;
            chapterCursorX = chapterCellLeft(chapter, bars) + CHAPTER_CELL / 2.0;
            playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 0.5f);
            return;
        }

        if (!pushing && Math.hypot(cursorX, cursorY) >= DEADZONE) {
            hover(Math.atan2(cursorY, cursorX));
        }
    }

    private static void hover(double angle) {
        if (angularDistance(angle, SLOT_ANGLES[slot]) <= Math.toRadians(SECTOR_DEGREES / 2 + HYSTERESIS_DEGREES)) {
            return;
        }
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (angularDistance(angle, SLOT_ANGLES[i]) <= Math.toRadians(SECTOR_DEGREES / 2)) {
                slot = i;
                playSound(SoundEvents.AMETHYST_BLOCK_HIT, 1.6f, 0.3f);
                return;
            }
        }
    }

    private static double angularDistance(double a, double b) {
        return Math.abs(Math.IEEEremainder(a - b, Math.PI * 2));
    }

    private static void moveInChapters(double dx, double dy, int bars) {
        boolean upward = dy < 0 && -dy > Math.abs(dx);
        if (upward) {
            if (push == 0) {
                chapterBeforePush = chapter;
            }
            push -= dy;
        } else if (dy > 0) {
            push = Math.max(0, push - dy);
        }

        if (push >= SWITCH_PUSH) {
            chapter = chapterBeforePush;
            section = Section.RING;
            push = 0;
            cursorX = 0;
            cursorY = 0;
            playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.5f, 0.5f);
            return;
        }

        if (upward) {
            return;
        }

        double half = chapterBarWidth(bars) / 2.0;
        chapterCursorX = Mth.clamp(chapterCursorX + dx, -half, half);

        double currentLeft = chapterCellLeft(chapter, bars);
        if (chapterCursorX >= currentLeft - CHAPTER_HYSTERESIS && chapterCursorX <= currentLeft + CHAPTER_CELL + CHAPTER_HYSTERESIS) {
            return;
        }
        int hovered = Mth.clamp((int) Math.floor((chapterCursorX + half) / CHAPTER_STEP), 0, bars - 1);
        if (hovered != chapter) {
            chapter = hovered;
            playSound(SoundEvents.AMETHYST_BLOCK_HIT, 1.3f, 0.3f);
        }
    }

    public static boolean onMouseButton(int button, int action) {
        if (!active) {
            return false;
        }
        if (action == GLFW.GLFW_PRESS && (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
            Pair<InteractionHand, ItemStack> held = resolveHeld(Minecraft.getInstance());
            if (held != null && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                commit(held);
            }
            active = false;
        }
        return true;
    }

    public static boolean onMouseScroll(double verticalDelta) {
        if (!active || verticalDelta == 0.0) {
            return false;
        }

        Pair<InteractionHand, ItemStack> held = resolveHeld(Minecraft.getInstance());
        if (held == null) {
            return false;
        }

        int bars = HexiconData.getAvailableBars(held.getSecond());
        chapter = verticalDelta > 0 ? (chapter + bars - 1) % bars : (chapter + 1) % bars;
        chapterBeforePush = chapter;
        chapterCursorX = chapterCellLeft(chapter, bars) + CHAPTER_CELL / 2.0;
        playSound(SoundEvents.AMETHYST_BLOCK_HIT, 1.3f, 0.3f);
        return true;
    }

    public static boolean onNumberKeyPress(int keyCode, int action) {
        if (!active || action != GLFW.GLFW_PRESS) {
            return false;
        }

        int slotIndex = mapKeyToSlot(keyCode);
        if (slotIndex < 0) {
            return false;
        }

        long now = Util.getMillis();
        boolean doubleTap = slotIndex == lastNumberKey && now - lastNumberKeyMs <= DOUBLE_TAP_WINDOW_MS;
        lastNumberKey = doubleTap ? -1 : slotIndex;
        lastNumberKeyMs = now;

        if (doubleTap) {
            Pair<InteractionHand, ItemStack> held = resolveHeld(Minecraft.getInstance());
            int bars = held == null ? 1 : HexiconData.getAvailableBars(held.getSecond());
            if (slotIndex < bars) {
                slot = slotBeforeNumberTap;
                slotBeforePush = slot;
                chapter = slotIndex;
                chapterBeforePush = chapter;
                chapterCursorX = chapterCellLeft(chapter, bars) + CHAPTER_CELL / 2.0;
                playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 0.5f);
            }
            return true;
        }

        slotBeforeNumberTap = slot;
        slot = slotIndex;
        slotBeforePush = slotIndex;
        cursorX = 0;
        cursorY = 0;
        playSound(SoundEvents.AMETHYST_BLOCK_HIT, 1.6f, 0.3f);
        return true;
    }

    public static void onHudRender(GuiGraphics graphics, float partialTick) {
        if (!active) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        Pair<InteractionHand, ItemStack> held = resolveHeld(client);
        if (held == null || client.screen != null) {
            return;
        }

        ItemStack stack = held.getSecond();
        int bars = HexiconData.getAvailableBars(stack);
        Font font = client.font;

        long now = Util.getMillis();
        float follow = 1f - (float) Math.exp(-HIGHLIGHT_SPEED * Math.min(0.1f, (now - lastRenderMs) / 1000f));
        lastRenderMs = now;
        highlightX += (SLOT_OFFSETS[slot][0] - highlightX) * follow;
        highlightY += (SLOT_OFFSETS[slot][1] - highlightY) * follow;
        chapterHighlightX += (chapterCellLeft(chapter, bars) - chapterHighlightX) * follow;

        float opened = Mth.clamp((now - openedAtMs) / (float) OPEN_ANIMATION_MS, 0f, 1f);
        float popScale = 0.9f + 0.1f * (1f - (1f - opened) * (1f - opened));

        int lean = (int) Math.round(Math.min(1.0, push / SWITCH_PUSH) * PUSH_LEAN);
        int ringShift = section == Section.CHAPTERS ? lean : 0;
        int barShift = section == Section.RING ? -lean : 0;

        graphics.pose().pushPose();
        graphics.pose().translate(client.getWindow().getGuiScaledWidth() / 2f, client.getWindow().getGuiScaledHeight() / 2f, 0);
        graphics.pose().scale(popScale, popScale, 1f);

        renderRing(graphics, font, stack, ringShift);
        if (bars > 1) {
            renderChapterBar(graphics, stack, bars, PANEL_HALF + CHAPTER_BAR_GAP + barShift);
        }

        graphics.pose().popPose();
    }

    private static void renderRing(GuiGraphics graphics, Font font, ItemStack stack, int shiftY) {
        boolean focused = section == Section.RING;
        int top = -PANEL_HALF + shiftY;
        graphics.fill(-PANEL_HALF, top, PANEL_HALF, top + PANEL_HALF * 2, COLOR_PANEL);
        graphics.renderOutline(-PANEL_HALF, top, PANEL_HALF * 2, PANEL_HALF * 2, focused ? COLOR_FRAME_FOCUSED : COLOR_FRAME);

        renderChapterTitle(graphics, font, stack, top - 13);

        boolean showsCommitted = chapter == HexiconData.getSelectedBar(stack);
        int committedSlot = HexiconData.getSelectedSlot(stack);
        for (int i = 0; i < SLOT_COUNT; i++) {
            int x = SLOT_OFFSETS[i][0] - SLOT_SIZE / 2;
            int y = SLOT_OFFSETS[i][1] - SLOT_SIZE / 2 + shiftY;
            boolean bound = HexiconData.getCachedSlotBound(stack, chapter, i);
            graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, bound ? COLOR_SLOT_BOUND : COLOR_SLOT_EMPTY);
            graphics.renderOutline(x, y, SLOT_SIZE, SLOT_SIZE, COLOR_SLOT_BORDER);

            ResourceLocation icon = HexiconData.getCachedSpellDisplay(stack, chapter, i).icon();
            if (bound && icon != null) {
                int inset = (SLOT_SIZE - SLOT_ICON_SIZE) / 2;
                new ResourceTexture(icon).draw(graphics, 0, 0, x + inset, y + inset, SLOT_ICON_SIZE, SLOT_ICON_SIZE);
            } else {
                graphics.drawCenteredString(font, String.valueOf(i + 1), x + SLOT_SIZE / 2, y + 7, bound ? COLOR_TEXT : COLOR_TEXT_DIM);
            }

            if (showsCommitted && i == committedSlot) {
                graphics.fill(x + SLOT_SIZE - 4, y + 2, x + SLOT_SIZE - 2, y + 4, COLOR_ACCENT);
            }
        }

        int hx = Math.round(highlightX) - SLOT_SIZE / 2 - 2;
        int hy = Math.round(highlightY) - SLOT_SIZE / 2 - 2 + shiftY;
        graphics.fill(hx, hy, hx + SLOT_SIZE + 4, hy + SLOT_SIZE + 4, COLOR_GLOW);
        graphics.renderOutline(hx, hy, SLOT_SIZE + 4, SLOT_SIZE + 4, focused ? COLOR_ACCENT : COLOR_ACCENT_DIM);
        graphics.renderOutline(hx + 1, hy + 1, SLOT_SIZE + 2, SLOT_SIZE + 2, focused ? COLOR_ACCENT : COLOR_ACCENT_DIM);

        renderCentre(graphics, font, stack, shiftY);

        if (focused) {
            if (Math.hypot(cursorX, cursorY) >= DEADZONE) {
                int cx = (int) Math.round(cursorX);
                int cy = (int) Math.round(cursorY) + shiftY;
                graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, COLOR_CURSOR);
            }
            renderPushMeter(graphics, PANEL_HALF + shiftY - 3);
        }
    }

    private static void renderCentre(GuiGraphics graphics, Font font, ItemStack stack, int shiftY) {
        boolean bound = HexiconData.getCachedSlotBound(stack, chapter, slot);
        HexiconData.SpellDisplay display = HexiconData.getCachedSpellDisplay(stack, chapter, slot);

        int iconTop = -CENTER_ICON_SIZE / 2 - 10 + shiftY;
        if (bound && display.icon() != null) {
            new ResourceTexture(display.icon()).draw(graphics, 0, 0, -CENTER_ICON_SIZE / 2, iconTop, CENTER_ICON_SIZE, CENTER_ICON_SIZE);
        } else {
            graphics.pose().pushPose();
            graphics.pose().translate(0, iconTop + CENTER_ICON_SIZE / 2f - 7, 0);
            graphics.pose().scale(2f, 2f, 1f);
            graphics.drawCenteredString(font, String.valueOf(slot + 1), 0, 0, bound ? COLOR_TEXT : COLOR_TEXT_DIM);
            graphics.pose().popPose();
        }

        String name = bound ? display.name() : Component.translatable("overlay.hexwright.hexicon.empty_page").getString();
        graphics.drawCenteredString(font, fitWidth(font, name, CENTER_TEXT_WIDTH), 0, 12 + shiftY, bound ? COLOR_TEXT_BOUND : COLOR_TEXT_DIM);
        graphics.drawCenteredString(font, Component.translatable("overlay.hexwright.hexicon.page", slot + 1), 0, 23 + shiftY, COLOR_TEXT_DIM);
    }

    private static void renderChapterTitle(GuiGraphics graphics, Font font, ItemStack stack, int y) {
        String chapterName = fitWidth(font, HexiconData.getChapterName(stack, chapter), PANEL_HALF * 2 - TITLE_ICON_SIZE - 4);
        ResourceLocation chapterIcon = HexiconData.getChapterIcon(stack, chapter);
        int nameWidth = font.width(chapterName);
        int left = -(nameWidth + (chapterIcon == null ? 0 : TITLE_ICON_SIZE + 4)) / 2;
        if (chapterIcon != null) {
            new ResourceTexture(chapterIcon).draw(graphics, 0, 0, left, y - 1, TITLE_ICON_SIZE, TITLE_ICON_SIZE);
            left += TITLE_ICON_SIZE + 4;
        }
        graphics.drawString(font, chapterName, left, y, COLOR_TEXT);
    }

    private static void renderChapterBar(GuiGraphics graphics, ItemStack stack, int bars, int top) {
        boolean focused = section == Section.CHAPTERS;
        int half = chapterBarWidth(bars) / 2;
        int frameLeft = -half - CHAPTER_PADDING;
        int frameWidth = half * 2 + CHAPTER_PADDING * 2;
        int frameHeight = CHAPTER_CELL + CHAPTER_PADDING * 2;
        graphics.fill(frameLeft, top, frameLeft + frameWidth, top + frameHeight, COLOR_PANEL);
        graphics.renderOutline(frameLeft, top, frameWidth, frameHeight, focused ? COLOR_FRAME_FOCUSED : COLOR_FRAME);

        int cellTop = top + CHAPTER_PADDING;
        int committedChapter = HexiconData.getSelectedBar(stack);
        Font font = Minecraft.getInstance().font;
        for (int i = 0; i < bars; i++) {
            int x = chapterCellLeft(i, bars);
            graphics.fill(x, cellTop, x + CHAPTER_CELL, cellTop + CHAPTER_CELL, COLOR_SLOT_BOUND);
            graphics.renderOutline(x, cellTop, CHAPTER_CELL, CHAPTER_CELL, COLOR_SLOT_BORDER);

            ResourceLocation icon = HexiconData.getChapterIcon(stack, i);
            if (icon != null) {
                int inset = (CHAPTER_CELL - CHAPTER_ICON_SIZE) / 2;
                new ResourceTexture(icon).draw(graphics, 0, 0, x + inset, cellTop + inset, CHAPTER_ICON_SIZE, CHAPTER_ICON_SIZE);
            } else {
                graphics.drawCenteredString(font, String.valueOf(i + 1), x + CHAPTER_CELL / 2, cellTop + 6, COLOR_TEXT);
            }

            if (i == committedChapter) {
                graphics.fill(x + CHAPTER_CELL - 4, cellTop + 2, x + CHAPTER_CELL - 2, cellTop + 4, COLOR_ACCENT);
            }
        }

        int hx = Math.round(chapterHighlightX) - 2;
        int hy = cellTop - 2;
        int accent = focused ? COLOR_ACCENT : COLOR_ACCENT_DIM;
        graphics.fill(hx, hy, hx + CHAPTER_CELL + 4, hy + CHAPTER_CELL + 4, COLOR_GLOW);
        graphics.renderOutline(hx, hy, CHAPTER_CELL + 4, CHAPTER_CELL + 4, accent);
        graphics.renderOutline(hx + 1, hy + 1, CHAPTER_CELL + 2, CHAPTER_CELL + 2, accent);

        if (focused) {
            int cx = (int) Math.round(chapterCursorX);
            int caretY = top + frameHeight + 2;
            graphics.fill(cx - 1, caretY, cx + 2, caretY + 3, COLOR_CURSOR);
            renderPushMeter(graphics, top + 1);
        }
    }

    private static void renderPushMeter(GuiGraphics graphics, int y) {
        int width = (int) Math.round(Math.min(1.0, push / SWITCH_PUSH) * PUSH_METER_WIDTH);
        if (width <= 0) {
            return;
        }
        graphics.fill(-width / 2, y, width - width / 2, y + 2, COLOR_ACCENT);
    }

    private static int chapterBarWidth(int bars) {
        return bars * CHAPTER_CELL + (bars - 1) * CHAPTER_GAP;
    }

    private static int chapterCellLeft(int chapterIndex, int bars) {
        return -chapterBarWidth(bars) / 2 + chapterIndex * CHAPTER_STEP;
    }

    private static String fitWidth(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        return font.plainSubstrByWidth(text, width - font.width("...")) + "...";
    }

    private static void playSound(SoundEvent sound, float pitch, float volume) {
        if (!HexiconOptions.wheelSounds()) {
            return;
        }
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    private static int mapKeyToSlot(int keyCode) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_KP_1 -> 0;
            case GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_KP_2 -> 1;
            case GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_KP_3 -> 2;
            case GLFW.GLFW_KEY_4, GLFW.GLFW_KEY_KP_4 -> 3;
            case GLFW.GLFW_KEY_5, GLFW.GLFW_KEY_KP_5 -> 4;
            case GLFW.GLFW_KEY_6, GLFW.GLFW_KEY_KP_6 -> 5;
            case GLFW.GLFW_KEY_7, GLFW.GLFW_KEY_KP_7 -> 6;
            case GLFW.GLFW_KEY_8, GLFW.GLFW_KEY_KP_8 -> 7;
            case GLFW.GLFW_KEY_9, GLFW.GLFW_KEY_KP_9 -> 8;
            default -> -1;
        };
    }

    private static Pair<InteractionHand, ItemStack> resolveHeld(Minecraft client) {
        Player player = client.player;
        if (player == null) {
            return null;
        }
        return HexiconData.findHeldSpellbook(player);
    }
}
