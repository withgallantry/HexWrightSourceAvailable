package com.bluup.hexwright.client.hexweave;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Size;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

@Environment(EnvType.CLIENT)
final class LoomFrame extends WidgetGroup {

    private static final float MARGIN = 0.05f;
    private static final float FILLS = 2f / 3f;

    private final WidgetGroup window;
    private final Size authored;
    private final Map<Widget, Anchor> anchors = new IdentityHashMap<>();

    LoomFrame(WidgetGroup window) {
        super(0, 0, 0, 0);
        this.window = window;
        this.authored = window.getSize();
        Set<Widget> holdsCanvas = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Widget at = window.getWidgetsByType(LoomCanvasWidget.class).get(0); at != window; at = at.getParent()) {
            holdsCanvas.add(at);
        }
        record(window, holdsCanvas);
        addWidget(window);
    }

    private void record(WidgetGroup group, Set<Widget> holdsCanvas) {
        Size space = group.getSize();
        for (Widget child : group.widgets) {
            boolean grows = holdsCanvas.contains(child);
            anchors.put(child, new Anchor(child.getSelfPositionX(), child.getSelfPositionY(), child.getSizeWidth(),
                child.getSizeHeight(),
                Follow.of(grows, child.getSelfPositionX(), child.getSizeWidth(), space.width),
                Follow.of(grows, child.getSelfPositionY(), child.getSizeHeight(), space.height)));
            if (child instanceof WidgetGroup inner) {
                record(inner, holdsCanvas);
            }
        }
    }

    @Override
    public void onScreenSizeUpdate(int screenWidth, int screenHeight) {
        setSize(screenWidth, screenHeight);
        int width = Math.round(screenWidth * (1 - 2 * MARGIN));
        int height = Math.round(screenHeight * (1 - 2 * MARGIN));
        window.setSelfPosition((screenWidth - width) / 2, (screenHeight - height) / 2);
        window.setSize(width, height);
        stretch(window, width - authored.width, height - authored.height);
        super.onScreenSizeUpdate(screenWidth, screenHeight);
    }

    private void stretch(WidgetGroup group, int extraWidth, int extraHeight) {
        for (Widget child : group.widgets) {
            Anchor anchor = anchors.get(child);
            if (anchor == null) {
                continue;
            }
            int width = Math.max(0, anchor.width + anchor.horizontal.grow(extraWidth));
            int height = Math.max(0, anchor.height + anchor.vertical.grow(extraHeight));
            child.setSelfPosition(anchor.x + anchor.horizontal.shift(extraWidth), anchor.y + anchor.vertical.shift(extraHeight));
            child.setSize(width, height);
            if (child instanceof WidgetGroup inner) {
                stretch(inner, width - anchor.width, height - anchor.height);
            }
        }
    }

    private record Anchor(int x, int y, int width, int height, Follow horizontal, Follow vertical) {
    }

    private enum Follow {
        START,
        CENTRE,
        END,
        GROW;

        static Follow of(boolean holdsCanvas, int start, int length, int space) {
            if (holdsCanvas || length >= space * FILLS) {
                return GROW;
            }
            int before = start;
            int after = space - start - length;
            if (Math.abs(before - after) <= 1) {
                return CENTRE;
            }
            return after < before ? END : START;
        }

        int shift(int extra) {
            return switch (this) {
                case START, GROW -> 0;
                case CENTRE -> extra / 2;
                case END -> extra;
            };
        }

        int grow(int extra) {
            return this == GROW ? extra : 0;
        }
    }
}
