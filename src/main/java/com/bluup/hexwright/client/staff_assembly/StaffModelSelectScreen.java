package com.bluup.hexwright.client.staff_assembly;

import com.bluup.hexwright.common.staff_assembly.StaffPart;
import com.bluup.hexwright.common.staff_assembly.StaffPartCategory;
import com.bluup.hexwright.common.staff_assembly.StaffParts;
import com.bluup.hexwright.common.staff_assembly.calc.EfficiencyRating;
import com.bluup.hexwright.server.menu.StaffAssemblyMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

public final class StaffModelSelectScreen extends Screen {

    private enum Tab {
        ALL(null),
        MASTERWORK(EfficiencyRating.MASTERWORK),
        EXQUISITE(EfficiencyRating.EXQUISITE),
        FINE(EfficiencyRating.FINE),
        SOUND(EfficiencyRating.SOUND);

        final @Nullable EfficiencyRating requiredQuality;

        Tab(@Nullable EfficiencyRating requiredQuality) {
            this.requiredQuality = requiredQuality;
        }

        String label() {
            return this.requiredQuality != null
                ? this.requiredQuality.label().getString()
                : Component.translatable("gui.hexwright.staff_selection.filter.all").getString();
        }
    }

    private static final int COLOR_PANEL        = 0xFF1B1F26;
    private static final int COLOR_PANEL_BORDER = 0xFF45505C;
    private static final int COLOR_SLOT         = 0xFF0E1014;
    private static final int COLOR_TEXT         = 0xC8D7FF;
    private static final int COLOR_TEXT_DIM     = 0xFF8895AA;
    private static final int COLOR_DIM_OVERLAY  = 0xA0000000;
    private static final int COLOR_TAB_ACTIVE   = 0xFF3A6B3A;
    private static final int COLOR_TAB_INACTIVE = 0xFF2A2E36;
    private static final int COLOR_SELECTED_BORDER = 0xFFE8C547;

    private static final int IMAGE_WIDTH  = StaffAssemblyMenu.IMAGE_WIDTH;
    private static final int IMAGE_HEIGHT = StaffAssemblyMenu.IMAGE_HEIGHT;
    private static final int PANEL_PADDING = 8;

    private static final int TAB_Y      = 22;
    private static final int TAB_HEIGHT = 16;
    private static final int TAB_GAP    = 4;

    private static final int GRID_COLUMNS = 5;
    private static final int CELL_SIZE    = 52;
    private static final int CELL_GAP     = 4;
    private static final int GRID_TOP     = TAB_Y + TAB_HEIGHT + 8;
    private static final int ICON_SIZE    = 32;

    private final Screen parent;
    private final @Nullable StaffPart selected;
    private final EfficiencyRating currentQuality;
    private final Consumer<StaffPart> onSelect;

    private Tab activeTab = Tab.ALL;
    private int leftPos;
    private int topPos;
    private int tabWidth;
    private List<StaffPart> visibleModels = List.of();

    public StaffModelSelectScreen(Screen parent, @Nullable StaffPart selected, EfficiencyRating currentQuality, Consumer<StaffPart> onSelect) {
        super(Component.translatable("gui.hexwright.staff_selection.title"));
        this.parent = parent;
        this.selected = selected;
        this.currentQuality = currentQuality;
        this.onSelect = onSelect;
    }

    @Override
    protected void init() {
        this.leftPos = (this.width - IMAGE_WIDTH) / 2;
        this.topPos = (this.height - IMAGE_HEIGHT) / 2;
        this.tabWidth = (IMAGE_WIDTH - PANEL_PADDING * 2 - TAB_GAP * (Tab.values().length - 1)) / Tab.values().length;
        selectTab(Tab.ALL);
    }

    private void selectTab(Tab tab) {
        this.activeTab = tab;
        List<StaffPart> all = StaffParts.options(StaffPartCategory.MODEL);
        this.visibleModels = tab.requiredQuality == null
            ? all
            : all.stream().filter(part -> part.requiredQuality() == tab.requiredQuality).toList();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        graphics.fill(this.leftPos, this.topPos, this.leftPos + IMAGE_WIDTH, this.topPos + IMAGE_HEIGHT, COLOR_PANEL);
        graphics.fill(this.leftPos, this.topPos, this.leftPos + IMAGE_WIDTH, this.topPos + 1, COLOR_PANEL_BORDER);
        graphics.fill(this.leftPos, this.topPos + IMAGE_HEIGHT - 1, this.leftPos + IMAGE_WIDTH, this.topPos + IMAGE_HEIGHT, COLOR_PANEL_BORDER);
        graphics.fill(this.leftPos, this.topPos, this.leftPos + 1, this.topPos + IMAGE_HEIGHT, COLOR_PANEL_BORDER);
        graphics.fill(this.leftPos + IMAGE_WIDTH - 1, this.topPos, this.leftPos + IMAGE_WIDTH, this.topPos + IMAGE_HEIGHT, COLOR_PANEL_BORDER);

        graphics.drawCenteredString(this.font, this.title, this.leftPos + IMAGE_WIDTH / 2, this.topPos + 6, COLOR_TEXT);

        renderTabs(graphics);
        renderGrid(graphics, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private record Cell(int x, int y) {
    }

    private Cell tabCell(int index) {
        return new Cell(this.leftPos + PANEL_PADDING + index * (this.tabWidth + TAB_GAP), this.topPos + TAB_Y);
    }

    private Cell modelCell(int index) {
        int gridWidth = GRID_COLUMNS * CELL_SIZE + (GRID_COLUMNS - 1) * CELL_GAP;
        int gridX = this.leftPos + (IMAGE_WIDTH - gridWidth) / 2;
        int gridY = this.topPos + GRID_TOP;
        int col = index % GRID_COLUMNS;
        int row = index / GRID_COLUMNS;
        return new Cell(gridX + col * (CELL_SIZE + CELL_GAP), gridY + row * (CELL_SIZE + CELL_GAP));
    }

    private void renderTabs(GuiGraphics graphics) {
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Tab tab = tabs[i];
            Cell cell = tabCell(i);
            int tx = cell.x();
            int ty = cell.y();

            int backgroundColor = tab == this.activeTab ? COLOR_TAB_ACTIVE : COLOR_TAB_INACTIVE;
            graphics.fill(tx - 1, ty - 1, tx + this.tabWidth + 1, ty + TAB_HEIGHT + 1, COLOR_PANEL_BORDER);
            graphics.fill(tx, ty, tx + this.tabWidth, ty + TAB_HEIGHT, backgroundColor);

            String label = tab.label();
            int labelX = tx + (this.tabWidth - this.font.width(label)) / 2;
            int labelY = ty + (TAB_HEIGHT - this.font.lineHeight) / 2;
            graphics.drawString(this.font, label, labelX, labelY, COLOR_TEXT, false);
        }
    }

    private void renderGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        StaffPart hovered = null;

        for (int i = 0; i < this.visibleModels.size(); i++) {
            StaffPart part = this.visibleModels.get(i);
            Cell cell = modelCell(i);
            int cx = cell.x();
            int cy = cell.y();

            boolean unlocked = StaffParts.isUnlocked(part, this.currentQuality);
            boolean isSelected = this.selected != null && this.selected.id().equals(part.id());

            int borderColor = isSelected ? COLOR_SELECTED_BORDER : COLOR_PANEL_BORDER;
            graphics.fill(cx - 1, cy - 1, cx + CELL_SIZE + 1, cy + CELL_SIZE + 1, borderColor);
            graphics.fill(cx, cy, cx + CELL_SIZE, cy + CELL_SIZE, COLOR_SLOT);

            int iconX = cx + (CELL_SIZE - ICON_SIZE) / 2;
            int iconY = cy + 4;
            StaffInfoPanelRenderer.renderLargeItem(graphics, part.icon(), iconX, iconY, ICON_SIZE);

            String name = part.displayName().getString();
            int nameColor = unlocked ? COLOR_TEXT : COLOR_TEXT_DIM;
            drawScaledCenteredString(graphics, name, cx + CELL_SIZE / 2, cy + ICON_SIZE + 8, nameColor, 0.6f, CELL_SIZE - 4);

            if (!unlocked) {
                graphics.fill(cx, cy, cx + CELL_SIZE, cy + CELL_SIZE, COLOR_DIM_OVERLAY);
            }

            if (mouseX >= cx && mouseX < cx + CELL_SIZE && mouseY >= cy && mouseY < cy + CELL_SIZE) {
                hovered = part;
            }
        }

        if (hovered != null) {
            Component tooltip = StaffParts.isUnlocked(hovered, this.currentQuality)
                ? hovered.displayName()
                : Component.translatable("gui.hexwright.staff_selection.locked",
                    hovered.displayName(), hovered.requiredQuality().label());
            graphics.renderTooltip(this.font, tooltip, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            Cell cell = tabCell(i);
            int tx = cell.x();
            int ty = cell.y();
            if (mouseX >= tx && mouseX < tx + this.tabWidth && mouseY >= ty && mouseY < ty + TAB_HEIGHT) {
                selectTab(tabs[i]);
                return true;
            }
        }

        for (int i = 0; i < this.visibleModels.size(); i++) {
            StaffPart part = this.visibleModels.get(i);
            Cell cell = modelCell(i);
            int cx = cell.x();
            int cy = cell.y();

            if (mouseX >= cx && mouseX < cx + CELL_SIZE && mouseY >= cy && mouseY < cy + CELL_SIZE) {
                if (StaffParts.isUnlocked(part, this.currentQuality)) {
                    selectModel(part);
                }
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void selectModel(StaffPart part) {
        this.onSelect.accept(part);
        this.onClose();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawScaledCenteredString(GuiGraphics graphics, String text, int centerX, int y, int color, float scale, int maxWidth) {
        scale = StaffInfoPanelRenderer.effectiveScale(scale);
        int unscaledMaxWidth = (int) (maxWidth / scale);
        String truncated = this.font.plainSubstrByWidth(text, unscaledMaxWidth);
        int textWidth = this.font.width(truncated);

        graphics.pose().pushPose();
        graphics.pose().translate(centerX - (textWidth * scale) / 2f, y, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(this.font, truncated, 0, 0, color, false);
        graphics.pose().popPose();
    }
}
