package com.bluup.hexwright.client.staff_assembly;

import com.bluup.hexwright.common.staff_assembly.StaffPart;
import com.bluup.hexwright.common.staff_assembly.StaffPartCategory;
import com.bluup.hexwright.common.staff_assembly.StaffParts;
import com.bluup.hexwright.common.staff_assembly.calc.ComponentConfig;
import com.bluup.hexwright.common.staff_assembly.calc.ComponentResult;
import com.bluup.hexwright.common.staff_assembly.calc.PersistedStaffStats;
import com.bluup.hexwright.common.staff_assembly.calc.StaffCalculationResult;
import com.bluup.hexwright.common.staff_assembly.calc.StaffCalculator;
import com.bluup.hexwright.server.staff_assembly.StaffAssemblyData;
import com.bluup.hexwright.server.menu.StaffAssemblyMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.stream.Collectors;

import static com.bluup.hexwright.client.staff_assembly.StaffInfoPanelRenderer.*;

public final class StaffAssemblyScreen extends AbstractContainerScreen<StaffAssemblyMenu> {

    private static final ResourceLocation BACKGROUND_TEXTURE = new ResourceLocation("hexwright", "textures/gui/staff_assembly.png");

    private static final int COLOR_PANEL        = 0xFF1B1F26;
    private static final int COLOR_PANEL_BORDER = 0xFF45505C;
    private static final int COLOR_SLOT         = 0xFF0E1014;
    private static final int COLOR_TEXT         = 0xC8D7FF;
    private static final int COLOR_TEXT_DIM     = 0xFF8895AA;
    private static final int COLOR_DIM_OVERLAY  = 0xA0000000;
    private static final int COLOR_BAR_BG          = 0xFF0E1014;
    private static final int COLOR_BAR_STAT        = 0xFFCC6633;
    private static final int COLOR_BAR_ATTUNEMENT  = 0xFFCC6633;
    private static final int COLOR_BAR_ATTUNEMENT_OVERLOAD = 0xFFFF5555;
    private static final int COLOR_BAR_EFFICIENCY  = 0xFF55CC55;
    private static final int COLOR_BAD             = 0xFFFF5555;
    private static final int COLOR_BUTTON_ENABLED       = 0xFF3A6B3A;
    private static final int COLOR_BUTTON_DISABLED      = 0xFF2A2E36;
    private static final int COLOR_BUTTON_TEXT_DISABLED = 0xFF6E7A88;

    private static final int CORE_LABEL_Y      = StaffAssemblyMenu.CORE_SLOT_Y - 10;
    private static final int CORE_DECO_X       = StaffAssemblyMenu.CORE_SLOT_X + 18 + 4;
    private static final int SECTIONS_LABEL_Y  = StaffAssemblyMenu.SECTIONS_SLOTS_Y - 10;

    private static final int PANEL_PADDING       = 4;
    private static final int PREVIEW_SIZE        = 48;
    private static final float TITLE_SCALE       = 1.15f;
    private static final float DESCRIPTION_SCALE = 0.7f;
    private static final int DESCRIPTION_LINES   = 3;
    private static final float BAR_LABEL_SCALE   = 0.8f;
    private static final float OVERALL_STAT_SCALE = 0.7f;
    private static final float CAPACITY_LABEL_SCALE = 0.5f;
    private static final int SECTION_GAP         = 8;
    private static final int CRAFT_BUTTON_HEIGHT = 20;
    private static final int SELECT_BUTTON_HEIGHT = 14;

    private StaffCalculationResult liveResult = StaffCalculator.calculate(ItemStack.EMPTY, List.of(), List.of(), List.of());

    private StaffCalculationResult mergedResult = this.liveResult;

    private PersistedStaffStats persistedStats = PersistedStaffStats.EMPTY;

    private boolean hasPersistedStats;

    private int selectButtonX;
    private int selectButtonY;
    private int selectButtonWidth;

    private int craftButtonX;
    private int craftButtonY;
    private int craftButtonWidth;
    private boolean craftEnabled;

    private int qualityLineX;
    private int qualityLineY;
    private int qualityLineWidth;
    private int qualityLineHeight;

    private StaffPart pendingModel;
    private boolean pendingModelSynced;
    private boolean pendingModelDirty;

    private ItemStack syncedStaff = ItemStack.EMPTY;

    public StaffAssemblyScreen(StaffAssemblyMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth  = StaffAssemblyMenu.IMAGE_WIDTH;
        this.imageHeight = StaffAssemblyMenu.IMAGE_HEIGHT;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.liveResult = computeResult();
        ItemStack staffStack = this.menu.staff();
        this.hasPersistedStats = StaffAssemblyData.hasStats(staffStack);
        this.persistedStats = StaffAssemblyData.getPersistedStats(staffStack);
        this.mergedResult = StaffCalculator.mergeWithPersisted(this.liveResult, this.persistedStats);
        syncPendingModel();
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void syncPendingModel() {
        ItemStack staff = this.menu.staff();
        if (staff.isEmpty()) {
            this.pendingModel = null;
            this.pendingModelSynced = false;
            this.pendingModelDirty = false;
            this.syncedStaff = ItemStack.EMPTY;
            return;
        }

        boolean sameItem = ItemStack.isSameItem(staff, this.syncedStaff);
        boolean sameItemAndTags = ItemStack.isSameItemSameTags(staff, this.syncedStaff);
        boolean shouldSyncFromStaff = !this.pendingModelSynced
            || this.pendingModel == null
            || !sameItem
            || (!this.pendingModelDirty && !sameItemAndTags);

        if (shouldSyncFromStaff) {
            this.pendingModel = StaffParts.model(staff).orElse(null);
            this.pendingModelSynced = true;
            this.pendingModelDirty = false;
        }
        this.syncedStaff = staff.copy();
    }

    private StaffCalculationResult computeResult() {
        return this.menu.computeResult();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        graphics.blit(BACKGROUND_TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight, this.imageWidth, this.imageHeight);

        renderCoreDecorations(graphics, x, y);

        renderComponentBars(graphics, x, y);
    }

    private void renderComponentBars(GuiGraphics graphics, int x, int y) {
        renderBarPair(graphics, x + StaffAssemblyMenu.BINDING_X, y, this.mergedResult.wrap(), StaffCalculator.WRAP_CONFIG);
        renderBarPair(graphics, x + StaffAssemblyMenu.FOCUS_X, y, this.mergedResult.focus(), StaffCalculator.FOCUS_CONFIG);
        renderBarPair(graphics, x + StaffAssemblyMenu.CATALYST_X, y, this.mergedResult.catalyst(), StaffCalculator.CATALYST_CONFIG);
    }

    private void renderBarPair(GuiGraphics graphics, int x, int y, ComponentResult component, ComponentConfig config) {
        int width = StaffAssemblyMenu.SECTION_WIDTH;
        int height = StaffAssemblyMenu.BAR_HEIGHT;
        int capacityY = y + StaffAssemblyMenu.CAPACITY_BAR_Y;
        int labelY = y + StaffAssemblyMenu.CAPACITY_LABEL_Y;
        int barX = x - 1;

        double cap = config.usableValueCap();
        double current = component.statValue();
        boolean overCap = cap > 0 && current > cap;
        double scale = Math.max(cap, current);
        if (scale <= 0) {
            renderBar(graphics, barX, capacityY, width, height, 0, COLOR_BAR_STAT);
        } else if (overCap) {
            int capWidth = (int) Math.round(width * (cap / scale));
            graphics.fill(barX, capacityY, barX + width, capacityY + height, COLOR_BAR_BG);
            graphics.fill(barX, capacityY, barX + capWidth, capacityY + height, COLOR_BAR_STAT);
            graphics.fill(barX + capWidth, capacityY, barX + width, capacityY + height, COLOR_BAD);
        } else {
            renderBar(graphics, barX, capacityY, width, height, current / cap, COLOR_BAR_STAT);
        }

        String label = format(current) + "/" + format(cap) + (overCap ? " over cap" : "");
        drawScaledString(graphics, this.font, label, barX, labelY, overCap ? COLOR_BAD : COLOR_TEXT_DIM, CAPACITY_LABEL_SCALE);
    }

    private void renderCoreDecorations(GuiGraphics graphics, int x, int y) {
        List<StaffPart> options = StaffParts.options(StaffPartCategory.CORE);
        ItemStack coreItem = this.menu.slots.get(StaffAssemblyMenu.CORE_SLOT).getItem();

        for (int i = 0; i < options.size(); i++) {
            StaffPart option = options.get(i);
            int ix = x + CORE_DECO_X + i * 18;
            int iy = y + StaffAssemblyMenu.CORE_SLOT_Y;

            graphics.fill(ix - 1, iy - 1, ix + 17, iy + 17, COLOR_PANEL_BORDER);
            graphics.fill(ix,     iy,     ix + 16, iy + 16, COLOR_SLOT);

            graphics.renderItem(option.icon(), ix, iy);

            boolean matched = !coreItem.isEmpty() && coreItem.getItem() == option.icon().getItem();
            if (!matched) {
                graphics.fill(ix, iy, ix + 16, iy + 16, COLOR_DIM_OVERLAY);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, "Core", StaffAssemblyMenu.CORE_SLOT_X, CORE_LABEL_Y, COLOR_TEXT, false);

        graphics.drawString(this.font, "Binding",  StaffAssemblyMenu.BINDING_X,  SECTIONS_LABEL_Y, COLOR_TEXT, false);
        graphics.drawString(this.font, "Focus",    StaffAssemblyMenu.FOCUS_X,    SECTIONS_LABEL_Y, COLOR_TEXT, false);
        graphics.drawString(this.font, "Catalyst", StaffAssemblyMenu.CATALYST_X, SECTIONS_LABEL_Y, COLOR_TEXT, false);

        renderStaffInfo(graphics, StaffAssemblyMenu.RIGHT_PANEL_X, StaffAssemblyMenu.RIGHT_PANEL_Y);

        if (mouseX >= this.qualityLineX && mouseX < this.qualityLineX + this.qualityLineWidth
                && mouseY >= this.qualityLineY && mouseY < this.qualityLineY + this.qualityLineHeight) {
            graphics.renderTooltip(this.font, Component.translatable("gui.hexwright.staff_assembly.quality.tooltip"), mouseX, mouseY);
        }
    }

    private void renderStaffInfo(GuiGraphics graphics, int panelX, int panelY) {
        int textX = panelX + PANEL_PADDING;
        int textWidth = StaffAssemblyMenu.RIGHT_PANEL_WIDTH - PANEL_PADDING * 2;

        int previewX = panelX + (StaffAssemblyMenu.RIGHT_PANEL_WIDTH - PREVIEW_SIZE) / 2;
        int previewY = panelY;
        ItemStack previewStack = this.pendingModel != null ? this.pendingModel.icon() : this.menu.staff();
        renderLargeItem(graphics, previewStack, previewX, previewY, PREVIEW_SIZE);

        int textY = previewY + PREVIEW_SIZE + LINE_GAP;

        String name = this.pendingModel != null ? this.pendingModel.displayName().getString() : "No Model Selected";
        drawCenteredString(graphics, this.font, name, panelX, StaffAssemblyMenu.RIGHT_PANEL_WIDTH, textY, COLOR_TEXT, TITLE_SCALE);
        textY += (int) (this.font.lineHeight * TITLE_SCALE) + (LINE_GAP + 4);

        this.selectButtonX = panelX + PANEL_PADDING;
        this.selectButtonY = textY;
        this.selectButtonWidth = textWidth;
        renderSelectButton(graphics);
        textY = this.selectButtonY + SELECT_BUTTON_HEIGHT + LINE_GAP + 4;

        int descriptionLineHeight = (int) (this.font.lineHeight * DESCRIPTION_SCALE) + LINE_GAP;
        if (this.mergedResult.core().isEmpty()) {
            drawScaledString(graphics, this.font, "No Core selected", textX, textY, COLOR_BAD, DESCRIPTION_SCALE);
            drawScaledString(graphics, this.font, "staff will have no effect", textX, textY + descriptionLineHeight, COLOR_BAD, DESCRIPTION_SCALE);
        } else {
            int wrapWidth = (int) (textWidth / DESCRIPTION_SCALE);
            String descriptionText = this.mergedResult.core().get().description().stream()
                .map(Component::getString)
                .collect(Collectors.joining(" "));
            List<String> lines = wrapLines(this.font, descriptionText, wrapWidth);
            for (int i = 0; i < Math.min(lines.size(), DESCRIPTION_LINES); i++) {
                drawScaledString(graphics, this.font, lines.get(i), textX, textY + i * descriptionLineHeight, COLOR_TEXT_DIM, DESCRIPTION_SCALE);
            }
        }
        textY += descriptionLineHeight * DESCRIPTION_LINES;

        textY += SECTION_GAP;

        boolean liveChanged = this.liveResult.wrap().itemCount() > 0 || this.liveResult.focus().itemCount() > 0
            || this.liveResult.catalyst().itemCount() > 0 || this.liveResult.core().isPresent();
        boolean showDeltas = this.hasPersistedStats && liveChanged;

        textY += (int) (this.font.lineHeight * TITLE_SCALE) + LINE_GAP;

        textY = drawComponentLine(graphics, this.font, textX, textY, textWidth, BAR_LABEL_SCALE, StaffCalculator.WRAP_CONFIG, this.mergedResult.wrap(), this.liveResult.wrap(), this.persistedStats.wrap(), showDeltas);
        textY = drawComponentLine(graphics, this.font, textX, textY, textWidth, BAR_LABEL_SCALE, StaffCalculator.FOCUS_CONFIG, this.mergedResult.focus(), this.liveResult.focus(), this.persistedStats.focus(), showDeltas);
        textY = drawComponentLine(graphics, this.font, textX, textY, textWidth, BAR_LABEL_SCALE, StaffCalculator.CATALYST_CONFIG, this.mergedResult.catalyst(), this.liveResult.catalyst(), this.persistedStats.catalyst(), showDeltas);

        textY += SECTION_GAP;

        this.qualityLineX = textX;
        this.qualityLineY = textY;
        this.qualityLineWidth = textWidth;
        this.qualityLineHeight = (int) (this.font.lineHeight * BAR_LABEL_SCALE);

        double qualityDelta = showDeltas ? this.mergedResult.quality() - StaffAssemblyData.getQuality(this.menu.staff()) : 0;
        textY = drawLineWithDelta(graphics, this.font, textX, textY, textWidth, BAR_LABEL_SCALE,
            "Quality", this.mergedResult.qualityRating().label().getString() + " · " + percent(this.mergedResult.quality()),
            qualityDelta, showDeltas, true);
        textY += SECTION_GAP / 2;

        boolean overloaded = this.mergedResult.totalAttunement() > StaffCalculator.MAX_ATTUNEMENT;
        int attunementColor = overloaded ? COLOR_BAR_ATTUNEMENT_OVERLOAD : COLOR_BAR_ATTUNEMENT;
        double attunementFraction = this.mergedResult.totalAttunement() / StaffCalculator.MAX_ATTUNEMENT;
        double attunementDelta = showDeltas
            ? (this.mergedResult.totalAttunement() - StaffAssemblyData.getAttunement(this.menu.staff())) / StaffCalculator.MAX_ATTUNEMENT
            : 0;
        String attunementValue = format(this.mergedResult.totalAttunement()) + "/" + format(StaffCalculator.MAX_ATTUNEMENT) + " used";
        textY = drawLineWithDelta(graphics, this.font, textX, textY, textWidth, OVERALL_STAT_SCALE,
            "Attunement", attunementValue,
            attunementDelta, showDeltas, true);
        renderBar(graphics, textX, textY, textWidth, StaffAssemblyMenu.BAR_HEIGHT, clamp01(attunementFraction), attunementColor);
        textY += StaffAssemblyMenu.BAR_HEIGHT + SECTION_GAP / 2;

        double efficiencyDelta = showDeltas ? this.mergedResult.overallEfficiency() - StaffAssemblyData.getEfficiency(this.menu.staff()) : 0;
        String efficiencyValue = format(this.mergedResult.overallEfficiency() * 100) + "/100 fit";
        textY = drawLineWithDelta(graphics, this.font, textX, textY, textWidth, OVERALL_STAT_SCALE,
            "Efficiency", efficiencyValue,
            efficiencyDelta, showDeltas, true);
        renderBar(graphics, textX, textY, textWidth, StaffAssemblyMenu.BAR_HEIGHT, clamp01(this.mergedResult.overallEfficiency()), COLOR_BAR_EFFICIENCY);
        textY += StaffAssemblyMenu.BAR_HEIGHT + LINE_GAP;

        renderCraftButton(graphics, panelX, panelY);
    }

    private void renderCraftButton(GuiGraphics graphics, int panelX, int panelY) {
        this.craftButtonX = panelX + PANEL_PADDING;
        this.craftButtonY = panelY + StaffAssemblyMenu.RIGHT_PANEL_HEIGHT - CRAFT_BUTTON_HEIGHT - PANEL_PADDING;
        this.craftButtonWidth = StaffAssemblyMenu.RIGHT_PANEL_WIDTH - PANEL_PADDING * 2;

        this.craftEnabled = !this.menu.staff().isEmpty()
            && StaffCalculator.isCraftable(this.mergedResult);

        int backgroundColor = this.craftEnabled ? COLOR_BUTTON_ENABLED : COLOR_BUTTON_DISABLED;
        int textColor = this.craftEnabled ? COLOR_TEXT : COLOR_BUTTON_TEXT_DISABLED;

        graphics.fill(this.craftButtonX - 1, this.craftButtonY - 1, this.craftButtonX + this.craftButtonWidth + 1, this.craftButtonY + CRAFT_BUTTON_HEIGHT + 1, COLOR_PANEL_BORDER);
        graphics.fill(this.craftButtonX, this.craftButtonY, this.craftButtonX + this.craftButtonWidth, this.craftButtonY + CRAFT_BUTTON_HEIGHT, backgroundColor);

        String label = this.hasPersistedStats ? "Reforge" : "Craft";
        int labelX = this.craftButtonX + (this.craftButtonWidth - this.font.width(label)) / 2;
        int labelY = this.craftButtonY + (CRAFT_BUTTON_HEIGHT - this.font.lineHeight) / 2;
        graphics.drawString(this.font, label, labelX, labelY, textColor, false);
    }

    private void renderSelectButton(GuiGraphics graphics) {
        boolean enabled = !this.menu.staff().isEmpty();
        int backgroundColor = enabled ? COLOR_BUTTON_ENABLED : COLOR_BUTTON_DISABLED;
        int textColor = enabled ? COLOR_TEXT : COLOR_BUTTON_TEXT_DISABLED;

        graphics.fill(this.selectButtonX - 1, this.selectButtonY - 1, this.selectButtonX + this.selectButtonWidth + 1, this.selectButtonY + SELECT_BUTTON_HEIGHT + 1, COLOR_PANEL_BORDER);
        graphics.fill(this.selectButtonX, this.selectButtonY, this.selectButtonX + this.selectButtonWidth, this.selectButtonY + SELECT_BUTTON_HEIGHT, backgroundColor);

        String label = "Select";
        int labelX = this.selectButtonX + (this.selectButtonWidth - this.font.width(label)) / 2;
        int labelY = this.selectButtonY + (SELECT_BUTTON_HEIGHT - this.font.lineHeight) / 2;
        graphics.drawString(this.font, label, labelX, labelY, textColor, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !this.menu.staff().isEmpty() && isOverSelectButton(mouseX, mouseY)) {
            this.minecraft.setScreen(new StaffModelSelectScreen(this, this.pendingModel, this.mergedResult.qualityRating(), model -> {
                this.pendingModel = model;
                this.pendingModelDirty = true;
            }));
            return true;
        }
        if (button == 0 && this.craftEnabled && isOverCraftButton(mouseX, mouseY)) {
            craft();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isOverSelectButton(double mouseX, double mouseY) {
        int x = this.leftPos + this.selectButtonX;
        int y = this.topPos + this.selectButtonY;
        return mouseX >= x && mouseX < x + this.selectButtonWidth && mouseY >= y && mouseY < y + SELECT_BUTTON_HEIGHT;
    }

    private boolean isOverCraftButton(double mouseX, double mouseY) {
        int x = this.leftPos + this.craftButtonX;
        int y = this.topPos + this.craftButtonY;
        return mouseX >= x && mouseX < x + this.craftButtonWidth && mouseY >= y && mouseY < y + CRAFT_BUTTON_HEIGHT;
    }

    private void craft() {
        int modelSelection = 0;
        if (this.pendingModel != null) {
            String currentId = StaffAssemblyData.getPart(this.menu.staff(), StaffPartCategory.MODEL);
            if (!this.pendingModel.id().equals(currentId)) {
                int index = StaffParts.options(StaffPartCategory.MODEL).indexOf(this.pendingModel);
                if (index >= 0) {
                    modelSelection = index + 1;
                }
            }
        }
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, StaffAssemblyMenu.CRAFT_BUTTON_BASE + modelSelection);
        this.minecraft.player.closeContainer();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
