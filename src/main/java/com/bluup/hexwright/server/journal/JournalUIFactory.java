package com.bluup.hexwright.server.journal;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.mixin.WidgetHoverTextureAccessor;
import com.bluup.hexwright.server.item.JournalItem;
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
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

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

    private static final String INVESTIGATIONS_SECTION_ID = "investigations_pages";
    private static final String LORE_SECTION_ID = "lore_pages";
    private static final String INVESTIGATIONS_TAB_ID = "invest_tab_container";
    private static final String LORE_TAB_ID = "lore_tab_container";

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
        bindLore(root, entityPlayer);
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
        Widget investigations = root.getFirstWidgetById("^" + INVESTIGATIONS_SECTION_ID + "$");
        Widget lore = root.getFirstWidgetById("^" + LORE_SECTION_ID + "$");
        if (investigations == null || lore == null) {
            Hexwright.LOGGER.warn("journal.ui is missing '{}' or '{}'; the book cannot change section",
                INVESTIGATIONS_SECTION_ID, LORE_SECTION_ID);
            return;
        }

        Tab investigationsTab = Tab.of(root, INVESTIGATIONS_TAB_ID);
        Tab loreTab = Tab.of(root, LORE_TAB_ID);

        Consumer<Boolean> open = loreOpen -> {
            showSection(investigations, !loreOpen);
            showSection(lore, loreOpen);
            investigationsTab.setSelected(!loreOpen);
            loreTab.setSelected(loreOpen);
        };

        investigationsTab.onPress(player, () -> open.accept(false));
        loreTab.onPress(player, () -> open.accept(true));

        open.accept(false);
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
