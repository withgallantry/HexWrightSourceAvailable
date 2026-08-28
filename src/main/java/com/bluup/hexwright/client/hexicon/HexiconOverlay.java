package com.bluup.hexwright.client.hexicon;

import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.server.hexicon.HexiconData;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

public final class HexiconOverlay {
    private static final long DOUBLE_TAP_WINDOW_MS = 250L;
    private static final float SELECTOR_LERP = 0.35f;

    private static final int SLOT_COUNT = HexiconData.SLOTS_PER_BAR;
    private static final int SLOT_SIZE = 20;
    private static final int SLOT_GAP = 2;
    private static final int SLOT_STEP = SLOT_SIZE + SLOT_GAP;
    private static final int BAR_WIDTH = SLOT_COUNT * SLOT_SIZE + (SLOT_COUNT - 1) * SLOT_GAP;
    private static final int SLOT_ICON_SIZE = 14;
    private static final int TITLE_ICON_SIZE = 10;
    private static final int SLOT_LABEL_OFFSET = 13;
    private static final int CHAPTER_LABEL_OFFSET = 24;

    private static boolean active;
    private static boolean wasKeyDown;
    private static boolean suppressOverlayUntilRelease;
    private static long lastKeyPressMs;

    private static int selectedSlotIndex;
    private static int targetSlotIndex;
    private static float visualSelectorX;

    private static int barIndex;

    private HexiconOverlay() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void onClientTick(Minecraft client, boolean keyDown) {
        Pair<InteractionHand, ItemStack> held = resolveHeld(client);
        if (held == null || client.screen != null) {
            reset();
            suppressOverlayUntilRelease = false;
            wasKeyDown = keyDown;
            return;
        }

        if (keyDown && !wasKeyDown) {
            long now = Util.getMillis();
            boolean isDoubleTap = now - lastKeyPressMs <= DOUBLE_TAP_WINDOW_MS;
            lastKeyPressMs = now;

            if (isDoubleTap) {
                suppressOverlayUntilRelease = true;
                active = false;
                openEditorForHeld(held);
            } else {
                active = true;
                barIndex = HexiconData.getSelectedBar(held.getSecond());
                selectedSlotIndex = HexiconData.getSelectedSlot(held.getSecond());
                targetSlotIndex = selectedSlotIndex;
                visualSelectorX = slotX(selectedSlotIndex);
            }
        } else if (!keyDown && wasKeyDown) {
            reset();
            suppressOverlayUntilRelease = false;
        }

        if (!keyDown) {
            active = false;
            wasKeyDown = keyDown;
            return;
        }

        if (suppressOverlayUntilRelease) {
            wasKeyDown = keyDown;
            return;
        }

        visualSelectorX = Mth.lerp(SELECTOR_LERP, visualSelectorX, slotX(targetSlotIndex));
        wasKeyDown = true;
    }

    public static void onHudRender(GuiGraphics graphics, float partialTick) {
        if (!active) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        Pair<InteractionHand, ItemStack> held = resolveHeld(client);
        if (held == null || client.screen != null) {
            reset();
            return;
        }

        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int barLeft = (screenWidth - BAR_WIDTH) / 2;
        int barTop = screenHeight - 24;
        ItemStack stack = held.getSecond();

        graphics.fill(barLeft - 3, barTop - 3, barLeft + BAR_WIDTH + 3, barTop + SLOT_SIZE + 3, 0xAA1A1328);
        graphics.fill(barLeft - 2, barTop - 2, barLeft + BAR_WIDTH + 2, barTop + SLOT_SIZE + 2, 0xCC241B3A);

        for (int i = 0; i < SLOT_COUNT; i++) {
            int x = barLeft + i * SLOT_STEP;
            int y = barTop;
            boolean slotBound = HexiconData.getCachedSlotBound(stack, barIndex, i);
            HexiconData.SpellDisplay slotDisplay = HexiconData.getCachedSpellDisplay(stack, barIndex, i);
            int fill = i == targetSlotIndex ? 0xAA5A4D8A : (slotBound ? 0x88343A56 : 0x88302044);
            graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, fill);

            if (slotDisplay.icon() != null) {
                int ix = x + (SLOT_SIZE - SLOT_ICON_SIZE) / 2;
                int iy = y + (SLOT_SIZE - SLOT_ICON_SIZE) / 2;
                new ResourceTexture(slotDisplay.icon()).draw(graphics, 0, 0, ix, iy, SLOT_ICON_SIZE, SLOT_ICON_SIZE);
            } else {
                graphics.drawCenteredString(client.font, String.valueOf(i + 1), x + SLOT_SIZE / 2, y + 6, 0xE8E4FF);
            }
        }

        int selectorX = barLeft + Math.round(visualSelectorX);
        graphics.fill(selectorX - 1, barTop - 1, selectorX + SLOT_SIZE + 1, barTop, 0xFFDAB7FF);
        graphics.fill(selectorX - 1, barTop + SLOT_SIZE, selectorX + SLOT_SIZE + 1, barTop + SLOT_SIZE + 1, 0xFFDAB7FF);
        graphics.fill(selectorX - 1, barTop, selectorX, barTop + SLOT_SIZE, 0xFFDAB7FF);
        graphics.fill(selectorX + SLOT_SIZE, barTop, selectorX + SLOT_SIZE + 1, barTop + SLOT_SIZE, 0xFFDAB7FF);

        boolean selectedBound = HexiconData.getCachedSlotBound(stack, barIndex, targetSlotIndex);
        String chapterName = HexiconData.getChapterName(stack, barIndex);
        ResourceLocation chapterIcon = HexiconData.getChapterIcon(stack, barIndex);
        int chapterLabelY = barTop - CHAPTER_LABEL_OFFSET;
        if (chapterIcon != null) {
            int titleIconX = screenWidth / 2 - client.font.width(chapterName) / 2 - TITLE_ICON_SIZE - 4;
            new ResourceTexture(chapterIcon).draw(graphics, 0, 0, titleIconX, chapterLabelY - 1, TITLE_ICON_SIZE, TITLE_ICON_SIZE);
        }
        graphics.drawCenteredString(client.font, chapterName, screenWidth / 2, chapterLabelY, 0xD7D1F2);

        graphics.drawCenteredString(client.font,
            Component.translatable(
                selectedBound ? "overlay.hexwright.hexicon.slot_bound" : "overlay.hexwright.hexicon.slot_empty",
                targetSlotIndex + 1
            ),
            screenWidth / 2,
            barTop - SLOT_LABEL_OFFSET,
            selectedBound ? 0x9FE8FF : 0xA8A0B7
        );
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
        if (verticalDelta > 0) {
            barIndex = (barIndex + bars - 1) % bars;
        } else {
            barIndex = (barIndex + 1) % bars;
        }

        syncSelectionToServer();
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

        selectedSlotIndex = slotIndex;
        targetSlotIndex = slotIndex;
        syncSelectionToServer();
        return true;
    }

    private static void syncSelectionToServer() {
        Minecraft client = Minecraft.getInstance();
        Pair<InteractionHand, ItemStack> held = resolveHeld(client);
        if (held == null) {
            return;
        }

        ItemStack stack = held.getSecond();
        HexiconData.setSelectedBarAndSlot(stack, barIndex, selectedSlotIndex);

        UUID libraryId = HexiconData.getLibraryId(stack);
        HexwrightNetworking.sendHexiconSelection(held.getFirst(), barIndex, selectedSlotIndex, libraryId);
    }

    private static float slotX(int slotIndex) {
        return slotIndex * SLOT_STEP;
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

    private static void openEditorForHeld(Pair<InteractionHand, ItemStack> held) {
        HexwrightNetworking.sendHexiconOpenMenu(held.getFirst());
    }

    private static void reset() {
        active = false;
    }
}
