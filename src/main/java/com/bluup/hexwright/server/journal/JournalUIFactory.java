package com.bluup.hexwright.server.journal;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.client.ldlib.widget.StaffItemDisplayWidget;
import com.bluup.hexwright.mixin.WidgetHoverTextureAccessor;
import com.bluup.hexwright.server.item.JournalItem;
import com.bluup.hexwright.server.menu.MenuWidgets;
import com.bluup.hexwright.server.menu.MenuWidgets.SilentButtonWidget;
import com.bluup.hexwright.server.menu.UiTemplates;
import com.bluup.hexwright.server.sound.HexwrightSoundEvents;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurableWidget;
import com.lowdragmc.lowdraglib.gui.factory.UIFactory;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextBoxWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextTextureWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;

public final class JournalUIFactory extends UIFactory<JournalUIFactory.Holder> {

    public static final JournalUIFactory INSTANCE = new JournalUIFactory();

    private static final String TEMPLATE = "journal";

    private static final String LIST_ID = "investigations_list";
    private static final String ROW_ID = "investigation_item";
    private static final String ROW_ICON_ID = "investigation_icon";
    private static final String ROW_TITLE_ID = "investigation_title";
    private static final String ROW_DESC_ID = "investigation_desc";
    private static final String COMPLETED_ID = "completed";
    private static final String DETAIL_IMAGE_ID = "investigation_image";
    private static final String DETAIL_TITLE_ID = "investigation_info_title";
    private static final String DETAIL_DESC_ID = "investigation_info_desc";

    private static final String LORE_LIST_ID = "lore_items_container";
    private static final String LORE_ROW_ID = "lore_list_item";
    private static final String LORE_ROW_CAP_ID = "lore_cap";
    private static final String LORE_ROW_TITLE_ID = "lore_list_title";
    private static final String LORE_ROW_TYPE_ID = "lore_list_type";
    private static final String LORE_PAGE_ID = "lore_info";
    private static final String LORE_TITLE_ID = "lore_entry_title";
    private static final String LORE_IMAGE_ID = "lore_entry_image";
    private static final String LORE_TEXT_ID = "lore_entry_text";

    private static final String ARTIFACT_LIST_ID = "artifact_items_container";
    private static final String ARTIFACT_ROW_ID = "artifact_list_item";
    private static final String ARTIFACT_ROW_ITEM_ID = "artifact_item";
    private static final String ARTIFACT_MAIN_ITEM_ID = "artifact_item_main";
    private static final String ARTIFACT_TITLE_ID = "artifact_title";
    private static final String ARTIFACT_INFO_ID = "artifact_info";
    private static final String ARTIFACT_LOCATIONS_ID = "artifact_locations";
    private static final String ARTIFACT_LOCATION_ROW_ID = "found_in_item";
    private static final String ARTIFACT_LOCATION_TEXT_ID = "lost_location";

    private static final String INVESTIGATIONS_SECTION_ID = "investigations_pages";
    private static final String LORE_SECTION_ID = "lore_pages";
    private static final String ARTIFACT_SECTION_ID = "item_pages";
    private static final String INVESTIGATIONS_TAB_ID = "invest_tab_container";
    private static final String LORE_TAB_ID = "lore_tab_container";
    private static final String ARTIFACT_TAB_ID = "artifact_tab_container";

    private static final int CELL_GAP = 2;

    private static final int TAB_CLOSED_TUCK = 4;

    private static final int ROW_GAP = 6;

    private static final int ROW_BORDER_INSET = 1;

    private JournalUIFactory() {
        super(Hexwright.id("journal_ui"));
    }

    public boolean openForHand(ServerPlayer player, InteractionHand hand) {
        return openUI(new Holder(player, hand), player);
    }

    @Override
    protected ModularUI createUITemplate(Holder holder, Player entityPlayer) {
        if (!(entityPlayer.getItemInHand(holder.hand).getItem() instanceof JournalItem)) {
            return null;
        }
        var template = UiTemplates.load(TEMPLATE);
        if (template == null) {
            return null;
        }
        WidgetGroup root = template.get();
        if (root == null) {
            return null;
        }

        bindInvestigations(root, entityPlayer);
        bindLore(sectionOrRoot(root, LORE_SECTION_ID), entityPlayer);
        bindArtifacts(sectionOrRoot(root, ARTIFACT_SECTION_ID), entityPlayer);
        bindSections(root, entityPlayer);
        return new ModularUI(root, holder, entityPlayer);
    }

    private static void bindInvestigations(WidgetGroup root, Player player) {
        Widget listWidget = root.getFirstWidgetById("^" + LIST_ID + "$");
        if (!(listWidget instanceof WidgetGroup list)) {
            Hexwright.LOGGER.warn("journal.ui is missing its '{}' group", LIST_ID);
            return;
        }
        Widget rowWidget = list.getFirstWidgetById("^" + ROW_ID + "$");
        if (!(rowWidget instanceof WidgetGroup rowTemplate)) {
            Hexwright.LOGGER.warn("journal.ui is missing its '{}' row template", ROW_ID);
            return;
        }

        CompoundTag rowTag = ((IConfigurableWidget) rowTemplate).serializeWrapper();
        int rowX = rowTemplate.getSelfPositionX();
        int rowHeight = rowTemplate.getSizeHeight();
        int rowStride = rowHeight + ROW_GAP;
        list.removeWidget(rowTemplate);

        RowSelection selection = RowSelection.of(rowTemplate);

        DetailPage detail = DetailPage.of(root);
        Set<String> completed = InvestigationProgress.completedFor(player);
        List<Investigation> visible = Investigations.visible(completed);

        List<Runnable> selectors = new ArrayList<>(visible.size());
        for (int index = 0; index < visible.size(); index++) {
            Investigation investigation = visible.get(index);

            IConfigurableWidget wrapper = IConfigurableWidget.deserializeWrapper(rowTag);
            if (wrapper == null || !(wrapper.widget() instanceof WidgetGroup row)) {
                continue;
            }
            row.setId(ROW_ID + "_" + investigation.id());
            row.setSelfPosition(rowX, ROW_BORDER_INSET + index * rowStride);

            boolean rowComplete = completed.contains(investigation.id());
            bindRow(row, investigation, rowComplete);

            Runnable select = () -> {
                selection.select(row);
                detail.show(investigation, rowComplete);
            };
            selectors.add(select);

            row.addWidget(new SilentButtonWidget(0, 0, row.getSizeWidth(), row.getSizeHeight(), clickData -> {
                player.playSound(HexwrightSoundEvents.journalClick(), 1.0F, 1.0F);
                select.run();
            }).setClientSideWidget());

            list.addWidget(row);
        }

        if (!selectors.isEmpty()) {
            list.addWidget(new WidgetGroup(rowX, ROW_BORDER_INSET + (selectors.size() - 1) * rowStride + rowHeight,
                1, ROW_BORDER_INSET));
        }

        if (!selectors.isEmpty()) {
            selectors.get(0).run();
        } else {
            detail.clear();
        }

        if (list instanceof DraggableScrollableWidgetGroup scrollable) {
            scrollable.computeMax();
        }
    }

    private static void bindRow(WidgetGroup row, Investigation investigation, boolean complete) {
        if (row.getFirstWidgetById("^" + ROW_TITLE_ID + "$") instanceof TextTextureWidget title) {
            title.setClientSideWidget();
            title.setText(investigation.titleComponent());
        }
        if (row.getFirstWidgetById("^" + ROW_DESC_ID + "$") instanceof TextTextureWidget description) {
            description.setClientSideWidget();
            description.setText(investigation.descriptionComponent());
        }
        if (row.getFirstWidgetById("^" + ROW_ICON_ID + "$") instanceof ImageWidget icon) {
            applyArt(icon, iconFor(investigation, complete));
        }
        Widget stamp = row.getFirstWidgetById("^" + COMPLETED_ID + "$");
        if (stamp != null) {
            stamp.setVisible(complete);
        }
        row.setHoverTooltips(investigation.titleComponent());
    }

    private static @Nullable InvestigationIcon iconFor(Investigation investigation, boolean complete) {
        Investigations.Content content = Investigations.get();
        if (complete && !content.completedIcon().isEmpty()) {
            InvestigationIcon done = content.icons().get(content.completedIcon());
            if (done != null) {
                return done;
            }
        }
        return content.icons().get(investigation.icon());
    }

    private static void applyArt(ImageWidget widget, @Nullable InvestigationIcon art) {
        if (art != null) {
            widget.setImage(art.toTexture());
        }
    }


    private static void bindSections(WidgetGroup root, Player player) {
        String[][] ids = {
            {INVESTIGATIONS_SECTION_ID, INVESTIGATIONS_TAB_ID},
            {LORE_SECTION_ID, LORE_TAB_ID},
            {ARTIFACT_SECTION_ID, ARTIFACT_TAB_ID},
        };
        List<Widget> sections = new ArrayList<>(ids.length);
        List<Tab> tabs = new ArrayList<>(ids.length);
        for (String[] pair : ids) {
            Widget section = root.getFirstWidgetById("^" + pair[0] + "$");
            if (section == null) {
                Hexwright.LOGGER.warn("journal.ui is missing section '{}'; its bookmark does nothing", pair[0]);
                continue;
            }
            sections.add(section);
            tabs.add(Tab.of(root, pair[1]));
        }
        if (sections.isEmpty()) {
            return;
        }

        java.util.function.IntConsumer open = selected -> {
            for (int i = 0; i < sections.size(); i++) {
                showSection(sections.get(i), i == selected);
                tabs.get(i).setSelected(i == selected);
            }
        };
        for (int i = 0; i < sections.size(); i++) {
            int index = i;
            tabs.get(i).onPress(player, () -> open.accept(index));
        }
        open.accept(0);
    }

    private static WidgetGroup sectionOrRoot(WidgetGroup root, String sectionId) {
        return root.getFirstWidgetById("^" + sectionId + "$") instanceof WidgetGroup section ? section : root;
    }

    private static void showSection(Widget section, boolean shown) {
        section.setVisible(shown);
        section.setActive(shown);
    }

    private record Tab(@Nullable WidgetGroup container, int restingX) {

        static Tab of(WidgetGroup root, String id) {
            WidgetGroup container = root.getFirstWidgetById("^" + id + "$") instanceof WidgetGroup found
                ? found : null;
            if (container == null) {
                Hexwright.LOGGER.warn("journal.ui has no '{}' bookmark", id);
            }
            return new Tab(container, container == null ? 0 : container.getSelfPositionX());
        }

        void onPress(Player player, Runnable action) {
            if (container == null) {
                return;
            }
            container.addWidget(new SilentButtonWidget(0, 0, container.getSizeWidth(), container.getSizeHeight(),
                clickData -> {
                    player.playSound(HexwrightSoundEvents.journalClick(), 1.0F, 1.0F);
                    action.run();
                }).setClientSideWidget());
        }

        void setSelected(boolean selected) {
            if (container != null) {
                container.setSelfPosition(restingX - (selected ? 0 : TAB_CLOSED_TUCK),
                    container.getSelfPositionY());
            }
        }
    }


    private static void bindLore(WidgetGroup root, Player player) {
        Widget listWidget = root.getFirstWidgetById("^" + LORE_LIST_ID + "$");
        if (!(listWidget instanceof WidgetGroup list)) {
            Hexwright.LOGGER.warn("journal.ui is missing its '{}' group", LORE_LIST_ID);
            return;
        }
        Widget rowWidget = list.getFirstWidgetById("^" + LORE_ROW_ID + "$");
        if (!(rowWidget instanceof WidgetGroup rowTemplate)) {
            Hexwright.LOGGER.warn("journal.ui is missing its '{}' row template", LORE_ROW_ID);
            return;
        }

        CompoundTag rowTag = ((IConfigurableWidget) rowTemplate).serializeWrapper();
        int rowX = rowTemplate.getSelfPositionX();
        int rowTop = rowTemplate.getSelfPositionY();
        int rowHeight = rowTemplate.getSizeHeight();
        int rowStride = rowHeight + ROW_GAP;
        list.removeWidget(rowTemplate);

        RowSelection selection = RowSelection.of(rowTemplate);
        LorePage page = LorePage.of(root);
        List<LoreEntry> visible = LoreEntries.visible(InvestigationProgress.completedFor(player));

        List<Runnable> selectors = new ArrayList<>(visible.size());
        for (int index = 0; index < visible.size(); index++) {
            LoreEntry entry = visible.get(index);

            IConfigurableWidget wrapper = IConfigurableWidget.deserializeWrapper(rowTag);
            if (wrapper == null || !(wrapper.widget() instanceof WidgetGroup row)) {
                continue;
            }
            row.setId(LORE_ROW_ID + "_" + entry.id());
            row.setSelfPosition(rowX, rowTop + index * rowStride);
            bindLoreRow(row, entry);

            Runnable select = () -> {
                selection.select(row);
                if (page != null) {
                    page.show(entry);
                }
            };
            selectors.add(select);

            row.addWidget(new SilentButtonWidget(0, 0, row.getSizeWidth(), row.getSizeHeight(), clickData -> {
                player.playSound(HexwrightSoundEvents.journalClick(), 1.0F, 1.0F);
                select.run();
            }).setClientSideWidget());

            list.addWidget(row);
        }

        if (!selectors.isEmpty()) {
            list.addWidget(new WidgetGroup(rowX, rowTop + (selectors.size() - 1) * rowStride + rowHeight,
                1, ROW_BORDER_INSET));
            selectors.get(0).run();
        } else if (page != null) {
            page.clear();
        }

        if (list instanceof DraggableScrollableWidgetGroup scrollable) {
            scrollable.computeMax();
        }
    }

    private static void bindLoreRow(WidgetGroup row, LoreEntry entry) {
        writeRowText(row, LORE_ROW_CAP_ID, entry.capComponent());
        writeRowText(row, LORE_ROW_TITLE_ID, entry.titleComponent());
        writeRowText(row, LORE_ROW_TYPE_ID, entry.typeComponent());
        row.setHoverTooltips(entry.titleComponent());
    }

    private static void writeRowText(WidgetGroup row, String id, Component text) {
        Widget widget = row.getFirstWidgetById("^" + id + "$");
        if (widget instanceof TextTextureWidget) {
            widget.setClientSideWidget();
        }
        writeText(widget, text);
    }

    private static void writeText(@Nullable Widget widget, Component text) {
        if (widget instanceof TextBoxWidget box) {
            String escaped = text.getString().replace("%", "%%");
            box.setContent(escaped.isEmpty() ? List.of() : List.of(escaped));
        } else if (widget instanceof TextTextureWidget texture) {
            texture.setText(text);
        }
    }

    private static final class LorePage {

        private final @Nullable DraggableScrollableWidgetGroup scroll;
        private final WidgetGroup holder;
        private final List<Element> stack;
        private final @Nullable Widget title;
        private final @Nullable Widget imageFrame;
        private final @Nullable ImageWidget image;
        private final @Nullable Widget textFrame;
        private final @Nullable TextBoxWidget text;

        private record Element(Widget widget, int x, int gapBefore) {
        }

        private LorePage(@Nullable DraggableScrollableWidgetGroup scroll, WidgetGroup holder, List<Element> stack,
                         @Nullable Widget title, @Nullable Widget imageFrame, @Nullable ImageWidget image,
                         @Nullable Widget textFrame, @Nullable TextBoxWidget text) {
            this.scroll = scroll;
            this.holder = holder;
            this.stack = stack;
            this.title = title;
            this.imageFrame = imageFrame;
            this.image = image;
            this.textFrame = textFrame;
            this.text = text;
        }

        static @Nullable LorePage of(WidgetGroup root) {
            if (!(root.getFirstWidgetById("^" + LORE_PAGE_ID + "$") instanceof WidgetGroup page)) {
                Hexwright.LOGGER.warn("journal.ui is missing its '{}' page", LORE_PAGE_ID);
                return null;
            }
            WidgetGroup container = page;
            DraggableScrollableWidgetGroup scroll = null;
            for (Widget child : page.widgets) {
                if (child instanceof DraggableScrollableWidgetGroup found) {
                    scroll = found;
                    container = found;
                    break;
                }
            }

            List<Widget> authored = new ArrayList<>(container.widgets);
            authored.sort(Comparator.comparingInt(Widget::getSelfPositionY));
            if (authored.isEmpty()) {
                Hexwright.LOGGER.warn("journal.ui's '{}' page has nothing on it", LORE_PAGE_ID);
                return null;
            }

            List<Element> stack = new ArrayList<>(authored.size());
            Widget previous = null;
            for (Widget widget : authored) {
                int gap = previous == null ? 0
                    : Math.max(0, widget.getSelfPositionY() - (previous.getSelfPositionY() + previous.getSizeHeight()));
                stack.add(new Element(widget, widget.getSelfPositionX(), gap));
                previous = widget;
            }

            WidgetGroup holder = new WidgetGroup(0, 0, container.getSizeWidth(), container.getSizeHeight());
            holder.setBackground(IGuiTexture.EMPTY);
            container.clearAllWidgets();
            container.addWidget(holder);
            for (Element element : stack) {
                holder.addWidget(element.widget());
                element.widget().setVisible(true);
                element.widget().setActive(true);
            }

            Widget title = holder.getFirstWidgetById("^" + LORE_TITLE_ID + "$");
            if (title instanceof TextTextureWidget) {
                title.setClientSideWidget();
            }
            Widget imageFrame = holder.getFirstWidgetById("^" + LORE_IMAGE_ID + "$");
            Widget textFrame = holder.getFirstWidgetById("^" + LORE_TEXT_ID + "$");

            return new LorePage(scroll, holder, stack, title, imageFrame, imageOf(imageFrame),
                textFrame, textOf(textFrame));
        }

        private static @Nullable ImageWidget imageOf(@Nullable Widget frame) {
            if (frame instanceof ImageWidget direct) {
                return direct;
            }
            if (frame instanceof WidgetGroup group) {
                for (Widget child : group.widgets) {
                    if (child instanceof ImageWidget found) {
                        return found;
                    }
                }
            }
            return null;
        }

        private static @Nullable TextBoxWidget textOf(@Nullable Widget frame) {
            if (frame instanceof TextBoxWidget direct) {
                return direct;
            }
            if (!(frame instanceof WidgetGroup group)) {
                return null;
            }
            for (Widget child : group.widgets) {
                if (child instanceof TextBoxWidget found) {
                    return found;
                }
            }
            TextBoxWidget made = new TextBoxWidget(0, 0, group.getSizeWidth(), List.of());
            group.addWidget(made);
            return made;
        }

        void show(LoreEntry entry) {
            writeText(title, entry.titleComponent());
            if (text != null) {
                text.setContent(entry.bodyLines());
            }
            InvestigationIcon art = entry.image().isEmpty() ? null
                : LoreEntries.get().images().get(entry.image());
            if (image != null && art != null) {
                image.setImage(art.toTexture());
            }
            showFrame(art != null);
            layout();
        }

        void clear() {
            writeText(title, Component.empty());
            if (text != null) {
                text.setContent(List.of());
            }
            showFrame(false);
            layout();
        }

        private void showFrame(boolean shown) {
            if (imageFrame != null) {
                imageFrame.setVisible(shown);
                imageFrame.setActive(shown);
            }
        }

        private void layout() {
            if (scroll != null) {
                scroll.setScrollYOffset(0);
            }
            if (textFrame != null && text != null) {
                textFrame.setSizeHeight(text.getSizeHeight());
            }

            int y = 0;
            boolean first = true;
            for (Element element : stack) {
                Widget widget = element.widget();
                if (!widget.isVisible()) {
                    continue;
                }
                if (!first) {
                    y += element.gapBefore();
                }
                first = false;
                widget.setSelfPosition(element.x(), y);
                y += widget.getSizeHeight();
            }

            holder.setSizeHeight(y);
            if (scroll != null) {
                scroll.computeMax();
            }
        }
    }


    private static void bindArtifacts(WidgetGroup section, Player player) {
        Widget listWidget = section.getFirstWidgetById("^" + ARTIFACT_LIST_ID + "$");
        if (!(listWidget instanceof WidgetGroup list)) {
            Hexwright.LOGGER.warn("journal.ui is missing its '{}' group", ARTIFACT_LIST_ID);
            return;
        }
        Widget cellWidget = list.getFirstWidgetById("^" + ARTIFACT_ROW_ID + "$");
        if (!(cellWidget instanceof WidgetGroup cellTemplate)) {
            Hexwright.LOGGER.warn("journal.ui is missing its '{}' cell template", ARTIFACT_ROW_ID);
            return;
        }

        CompoundTag cellTag = ((IConfigurableWidget) cellTemplate).serializeWrapper();
        int left = cellTemplate.getSelfPositionX();
        int top = cellTemplate.getSelfPositionY();
        int cellWidth = cellTemplate.getSizeWidth();
        int cellHeight = cellTemplate.getSizeHeight();
        int columns = Math.max(1, (list.getSizeWidth() - left + CELL_GAP) / (cellWidth + CELL_GAP));
        list.removeWidget(cellTemplate);

        RowSelection selection = RowSelection.of(cellTemplate);
        ArtifactPage page = ArtifactPage.of(section, player);
        List<Artifact> all = Artifacts.all();
        Collection<String> foundIds = InvestigationProgress.artifactsFor(player);

        List<Runnable> selectors = new ArrayList<>(all.size());
        int bottom = 0;
        for (int index = 0; index < all.size(); index++) {
            Artifact artifact = all.get(index);

            IConfigurableWidget wrapper = IConfigurableWidget.deserializeWrapper(cellTag);
            if (wrapper == null || !(wrapper.widget() instanceof WidgetGroup cell)) {
                continue;
            }
            cell.setId(ARTIFACT_ROW_ID + "_" + artifact.id());
            int y = top + (index / columns) * (cellHeight + CELL_GAP);
            cell.setSelfPosition(left + (index % columns) * (cellWidth + CELL_GAP), y);
            bottom = Math.max(bottom, y + cellHeight);

            if (!foundIds.contains(artifact.id())) {
                showItem(cell, ARTIFACT_ROW_ITEM_ID, () -> ItemStack.EMPTY);
                cell.setHoverTooltips(Component.translatable("journal.hexwright.artifact.undiscovered"));
                list.addWidget(cell);
                continue;
            }

            ItemStack stack = artifact.displayStack();
            showItem(cell, ARTIFACT_ROW_ITEM_ID, () -> stack);

            Runnable select = () -> {
                selection.select(cell);
                page.show(artifact);
            };
            selectors.add(select);

            cell.addWidget(new SilentButtonWidget(0, 0, cell.getSizeWidth(), cell.getSizeHeight(), clickData -> {
                player.playSound(HexwrightSoundEvents.journalClick(), 1.0F, 1.0F);
                select.run();
            }).setClientSideWidget());

            list.addWidget(cell);
        }

        if (bottom > 0) {
            list.addWidget(new WidgetGroup(left, bottom, 1, ROW_BORDER_INSET));
        }
        if (!selectors.isEmpty()) {
            selectors.get(0).run();
        } else {
            page.clear();
        }

        if (list instanceof DraggableScrollableWidgetGroup scrollable) {
            scrollable.computeMax();
        }
    }

    private static void showItem(WidgetGroup parent, String id, java.util.function.Supplier<ItemStack> stack) {
        showItem(parent, id, stack, true);
    }

    private static void showItem(WidgetGroup parent, String id, java.util.function.Supplier<ItemStack> stack,
                                 boolean tooltip) {
        Widget template = parent.getFirstWidgetById("^" + id + "$");
        if (template == null) {
            return;
        }
        StaffItemDisplayWidget display = MenuWidgets.replaceInPlace(template,
            (x, y) -> new StaffItemDisplayWidget(x + 1, y + 1, 16, 16, stack, tooltip));
        if (display != null) {
            display.setClientSideWidget();
        }
    }

    private static final class ArtifactPage {

        private final Player player;
        private final AtomicReference<ItemStack> mainStack = new AtomicReference<>(ItemStack.EMPTY);
        private final @Nullable Widget title;
        private final @Nullable TextBoxWidget info;
        private final @Nullable DraggableScrollableWidgetGroup infoScroll;
        private final @Nullable WidgetGroup locations;
        private final @Nullable CompoundTag locationTag;
        private final int locationLeft;
        private final int locationTop;
        private final int locationRows;
        private final int locationStrideX;
        private final int locationStrideY;
        private final List<Widget> shownLocations = new ArrayList<>();

        private ArtifactPage(Player player, WidgetGroup section) {
            this.player = player;
            showItem(section, ARTIFACT_MAIN_ITEM_ID, mainStack::get, false);

            title = section.getFirstWidgetById("^" + ARTIFACT_TITLE_ID + "$");
            if (title instanceof TextTextureWidget) {
                title.setClientSideWidget();
            }

            info = section.getFirstWidgetById("^" + ARTIFACT_INFO_ID + "$") instanceof TextBoxWidget box ? box : null;
            infoScroll = info != null && info.getParent() instanceof DraggableScrollableWidgetGroup scroll ? scroll : null;

            locations = section.getFirstWidgetById("^" + ARTIFACT_LOCATIONS_ID + "$") instanceof WidgetGroup group
                ? group : null;
            List<WidgetGroup> cells = new ArrayList<>();
            if (locations != null) {
                for (Widget child : new ArrayList<>(locations.widgets)) {
                    if (child instanceof WidgetGroup row && ARTIFACT_LOCATION_ROW_ID.equals(row.getId())) {
                        cells.add(row);
                    }
                }
            }
            if (!cells.isEmpty()) {
                WidgetGroup first = cells.get(0);
                locationTag = ((IConfigurableWidget) first).serializeWrapper();
                Set<Integer> xs = new TreeSet<>();
                Set<Integer> ys = new TreeSet<>();
                for (WidgetGroup row : cells) {
                    xs.add(row.getSelfPositionX());
                    ys.add(row.getSelfPositionY());
                }
                List<Integer> columnXs = new ArrayList<>(xs);
                List<Integer> rowYs = new ArrayList<>(ys);
                locationLeft = columnXs.get(0);
                locationTop = rowYs.get(0);
                locationRows = rowYs.size();
                locationStrideX = columnXs.size() > 1
                    ? (columnXs.get(columnXs.size() - 1) - locationLeft) / (columnXs.size() - 1)
                    : first.getSizeWidth() + CELL_GAP;
                locationStrideY = rowYs.size() > 1
                    ? (rowYs.get(rowYs.size() - 1) - locationTop) / (rowYs.size() - 1)
                    : first.getSizeHeight() + CELL_GAP;
                for (WidgetGroup row : cells) {
                    locations.removeWidget(row);
                }
            } else {
                if (locations != null) {
                    Hexwright.LOGGER.warn("journal.ui's '{}' has no '{}' template", ARTIFACT_LOCATIONS_ID,
                        ARTIFACT_LOCATION_ROW_ID);
                }
                locationTag = null;
                locationLeft = locationTop = 0;
                locationRows = 1;
                locationStrideX = locationStrideY = 0;
            }
        }

        static ArtifactPage of(WidgetGroup section, Player player) {
            return new ArtifactPage(player, section);
        }

        void show(Artifact artifact) {
            ItemStack stack = artifact.displayStack();
            mainStack.set(stack);
            writeText(title, artifact.titleComponent());
            setInfo(player.level().isClientSide && !stack.isEmpty() ? tooltipBody(stack) : List.of());
            setLocations(artifact.locationComponents());
        }

        void clear() {
            mainStack.set(ItemStack.EMPTY);
            writeText(title, Component.empty());
            setInfo(List.of());
            setLocations(List.of());
        }

        private void setInfo(List<String> lines) {
            if (info == null) {
                return;
            }
            if (infoScroll != null) {
                infoScroll.setScrollYOffset(0);
            }
            info.setContent(lines);
            if (infoScroll != null) {
                infoScroll.computeMax();
            }
        }

        private void setLocations(List<Component> places) {
            if (locations == null || locationTag == null) {
                return;
            }
            for (Widget shown : shownLocations) {
                locations.removeWidget(shown);
            }
            shownLocations.clear();

            int rows = Math.max(1, locationRows);
            for (int index = 0; index < places.size(); index++) {
                IConfigurableWidget wrapper = IConfigurableWidget.deserializeWrapper(locationTag);
                if (wrapper == null || !(wrapper.widget() instanceof WidgetGroup row)) {
                    continue;
                }
                row.setId(ARTIFACT_LOCATION_ROW_ID + "_" + index);
                row.setSelfPosition(
                    locationLeft + (index / rows) * locationStrideX,
                    locationTop + (index % rows) * locationStrideY);
                Widget text = row.getFirstWidgetById("^" + ARTIFACT_LOCATION_TEXT_ID + "$");
                if (text instanceof TextTextureWidget) {
                    text.setClientSideWidget();
                }
                writeText(text, places.get(index));
                row.setHoverTooltips(places.get(index));
                locations.addWidget(row);
                shownLocations.add(row);
            }
        }

        private List<String> tooltipBody(ItemStack stack) {
            List<Component> lines = new ArrayList<>();
            try {
                stack.getItem().appendHoverText(stack, player.level(), lines, TooltipFlag.Default.NORMAL);
            } catch (Throwable t) {
                Hexwright.LOGGER.error("Tooltip for journal artifact '{}' threw", stack.getItem(), t);
                return List.of();
            }
            List<String> out = new ArrayList<>(lines.size());
            for (Component component : lines) {
                String line = flatten(component).replace("%", "%%");
                out.add(line.isEmpty() ? " " : line);
            }
            return out;
        }

        private static String flatten(Component component) {
            StringBuilder out = new StringBuilder();
            component.visit((style, text) -> {
                if (!text.isEmpty()) {
                    String codes = styleCodes(style);
                    if (!codes.isEmpty()) {
                        out.append(ChatFormatting.RESET).append(codes);
                    }
                    out.append(text);
                    if (!codes.isEmpty()) {
                        out.append(ChatFormatting.RESET);
                    }
                }
                return Optional.empty();
            }, Style.EMPTY);
            return out.toString();
        }

        private static String styleCodes(Style style) {
            StringBuilder codes = new StringBuilder();
            ChatFormatting ink = parchmentInk(style.getColor());
            if (ink != null) {
                codes.append(ink);
            }
            if (style.isBold()) {
                codes.append(ChatFormatting.BOLD);
            }
            if (style.isItalic()) {
                codes.append(ChatFormatting.ITALIC);
            }
            if (style.isUnderlined()) {
                codes.append(ChatFormatting.UNDERLINE);
            }
            if (style.isStrikethrough()) {
                codes.append(ChatFormatting.STRIKETHROUGH);
            }
            if (style.isObfuscated()) {
                codes.append(ChatFormatting.OBFUSCATED);
            }
            return codes.toString();
        }

        private static @Nullable ChatFormatting parchmentInk(@Nullable net.minecraft.network.chat.TextColor color) {
            if (color == null) {
                return null;
            }
            ChatFormatting named = ChatFormatting.getByName(color.serialize());
            if (named == null) {
                named = nearestFormatting(color.getValue());
            }
            return switch (named) {
                case WHITE, GRAY -> null;
                case YELLOW -> ChatFormatting.GOLD;
                case GREEN -> ChatFormatting.DARK_GREEN;
                case AQUA -> ChatFormatting.DARK_AQUA;
                case BLUE -> ChatFormatting.DARK_BLUE;
                case RED -> ChatFormatting.DARK_RED;
                case LIGHT_PURPLE -> ChatFormatting.DARK_PURPLE;
                default -> named;
            };
        }

        private static ChatFormatting nearestFormatting(int rgb) {
            ChatFormatting best = ChatFormatting.WHITE;
            long bestDistance = Long.MAX_VALUE;
            for (ChatFormatting candidate : ChatFormatting.values()) {
                Integer value = candidate.getColor();
                if (value == null) {
                    continue;
                }
                long dr = ((rgb >> 16) & 0xFF) - ((value >> 16) & 0xFF);
                long dg = ((rgb >> 8) & 0xFF) - ((value >> 8) & 0xFF);
                long db = (rgb & 0xFF) - (value & 0xFF);
                long distance = dr * dr + dg * dg + db * db;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = candidate;
                }
            }
            return best;
        }
    }

    private static final class RowSelection {

        private final @Nullable IGuiTexture focusTexture;
        private @Nullable Widget selected;
        private @Nullable IGuiTexture selectedBackground;

        private RowSelection(@Nullable IGuiTexture focusTexture) {
            this.focusTexture = focusTexture;
        }

        static RowSelection of(WidgetGroup rowTemplate) {
            return new RowSelection(((WidgetHoverTextureAccessor) rowTemplate).hexwright$getHoverTexture());
        }

        void select(Widget row) {
            if (focusTexture == null || selected == row) {
                return;
            }
            if (selected != null) {
                selected.setBackground(selectedBackground == null ? IGuiTexture.EMPTY : selectedBackground);
            }
            selected = row;
            selectedBackground = row.getBackgroundTexture();
            row.setBackground(focusTexture);
        }
    }

    private record DetailPage(ImageWidget image, Widget title, TextBoxWidget description,
                             @Nullable Widget completedStamp) {

        static DetailPage of(WidgetGroup root) {
            ImageWidget image = root.getFirstWidgetById("^" + DETAIL_IMAGE_ID + "$") instanceof ImageWidget found
                ? found : null;
            Widget title = root.getFirstWidgetById("^" + DETAIL_TITLE_ID + "$");
            TextBoxWidget description = root.getFirstWidgetById("^" + DETAIL_DESC_ID + "$") instanceof TextBoxWidget found
                ? found : null;
            Widget completedStamp = root.getFirstWidgetById("^" + COMPLETED_ID + "$");

            if (title instanceof TextTextureWidget) {
                title.setClientSideWidget();
            }
            return new DetailPage(image, title, description, completedStamp);
        }

        void show(Investigation investigation, boolean complete) {
            writeText(title, investigation.detailTitleComponent());
            if (description != null) {
                description.setContent(investigation.detailLines());
            }
            if (image != null) {
                applyArt(image, Investigations.get().images().get(investigation.detail().image()));
            }
            if (completedStamp != null) {
                completedStamp.setVisible(complete);
            }
        }

        void clear() {
            writeText(title, Component.empty());
            if (description != null) {
                description.setContent(List.of());
            }
            if (completedStamp != null) {
                completedStamp.setVisible(false);
            }
        }
    }

    @Override
    @Environment(EnvType.CLIENT)
    protected Holder readHolderFromSyncData(FriendlyByteBuf syncData) {
        Player player = Minecraft.getInstance().player;
        return player == null ? null : new Holder(player, syncData.readEnum(InteractionHand.class));
    }

    @Override
    protected void writeHolderToSyncData(FriendlyByteBuf syncData, Holder holder) {
        syncData.writeEnum(holder.hand);
    }

    public static final class Holder implements IUIHolder {
        private final Player player;
        private final InteractionHand hand;

        private Holder(Player player, InteractionHand hand) {
            this.player = player;
            this.hand = hand;
        }

        @Override
        public ModularUI createUI(Player entityPlayer) {
            return null;
        }

        @Override
        public boolean isInvalid() {
            return !(player.getItemInHand(hand).getItem() instanceof JournalItem);
        }

        @Override
        public boolean isRemote() {
            return player.level().isClientSide;
        }

        @Override
        public void markAsDirty() {
        }
    }
}
