package com.bluup.hexwright.server.hexicon;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.menu.UiTemplates;
import com.bluup.hexwright.inits.HexwrightNetworking;
import com.bluup.hexwright.mixin.WidgetHoverTextureAccessor;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurableWidget;
import com.lowdragmc.lowdraglib.gui.factory.UIFactory;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextTextureWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Locale;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class HexiconUIFactory extends UIFactory<HexiconUIFactory.Holder> {
    public static final HexiconUIFactory INSTANCE = new HexiconUIFactory();

    private static final String TAG_UI_PAYLOAD_ID = "hexwright_ui_payload_id";
    private static final String TAG_UI_DISPLAY_NAME = "hexwright_ui_display_name";
    private static final String TAG_UI_DISPLAY_ICON = "hexwright_ui_display_icon";
    private static final int ICON_TILE_SIZE = 22;
    private static final int ICON_PADDING = 3;
    private static final int ICON_GAP = 4;
    private static final int SLOT_ICON_PADDING = 2;
    private static final int ICONS_PANEL_FILL = 0x281A1A1A;
    private static final int ICONS_PANEL_BORDER = 0x609E8C68;
    private static final int PAGE_SELECTED_BORDER = 0xC0DAB7FF;
    private static final String ACTIVE_BUTTON_TEXTURE = "ldlib:textures/button-hovered.png";
    private static final String SPELL_ICON_DIR = "/assets/ldlib/textures/spell_icons";

    private static volatile List<ResourceLocation> SPELL_ICON_CACHE;

    private static final IGuiTexture ICON_TILE_TEXTURE = new GuiTextureGroup(
        new ColorRectTexture(0x18202020),
        new ColorBorderTexture(1, 0x507A6C50)
    );
    private static final IGuiTexture ICON_TILE_ACTIVE_TEXTURE = new GuiTextureGroup(
        new ResourceTexture(ACTIVE_BUTTON_TEXTURE),
        new ColorBorderTexture(1, 0xB0D8C486)
    );

    private HexiconUIFactory() {
        super(Hexwright.id("hexicon_ui"));
    }

    public boolean openForHand(ServerPlayer player, InteractionHand hand) {
        return openUI(Holder.forHand(player, hand), player);
    }

    @Override
    protected ModularUI createUITemplate(Holder holder, Player entityPlayer) {
        Supplier<WidgetGroup> template = UiTemplates.load("spell_book");
        if (template == null) {
            return null;
        }

        WidgetGroup root = template.get();
        if (root == null) {
            return null;
        }

        ItemStack stack = entityPlayer.getItemInHand(holder.hand);
        if (!HexiconData.isHexiconStack(stack)) {
            return null;
        }

        EditorState editorState = new EditorState(entityPlayer, holder.hand, stack);
        bindChapters(root, editorState);
        bindEditPanel(root, editorState);
        return new ModularUI(root, holder, entityPlayer);
    }

    @Override
    @Environment(EnvType.CLIENT)
    protected Holder readHolderFromSyncData(FriendlyByteBuf syncData) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        return Holder.forHand(player, syncData.readEnum(InteractionHand.class));
    }

    @Override
    protected void writeHolderToSyncData(FriendlyByteBuf syncData, Holder holder) {
        syncData.writeEnum(holder.hand);
    }

    private static void bindChapters(WidgetGroup root, EditorState editorState) {
        Widget templateWidget = root.getFirstWidgetById("^chapter$");
        if (!(templateWidget instanceof WidgetGroup chapterTemplate)) {
            Hexwright.LOGGER.error("spell_book.ui is missing a 'chapter' widget group template");
            return;
        }

        WidgetGroup chapterParent = chapterTemplate.getParent();
        if (chapterParent == null) {
            Hexwright.LOGGER.error("spell_book.ui 'chapter' template has no parent group");
            return;
        }

        int baseX = chapterTemplate.getSelfPositionX();
        int baseY = chapterTemplate.getSelfPositionY();
        int chapterStepY = chapterTemplate.getSizeHeight() + 2;

        chapterParent.removeWidget(chapterTemplate);

        int selectedChapter = editorState.selectedChapter();
        int selectedSlot = editorState.selectedSlot();
        int selectedAbsolute = HexiconData.getAbsoluteSlotIndex(selectedChapter, selectedSlot);
        UUID libraryId = editorState.libraryId();
        ServerLevel serverLevel = editorState.serverLevel();
        List<PageBinding> pageBindings = new ArrayList<>();
        editorState.chapterGroups().clear();

        int availableChapters = HexiconData.getAvailableBars(editorState.hexiconStack());

        for (int chapterIndex = 0; chapterIndex < availableChapters; chapterIndex++) {
            WidgetGroup chapterGroup = cloneWidgetGroup(chapterTemplate);
            if (chapterGroup == null) {
                continue;
            }

            chapterGroup.setId("chapter_" + chapterIndex);
            chapterGroup.setSelfPosition(baseX, baseY + chapterIndex * chapterStepY);
            applyChapterMetadata(chapterGroup, editorState.hexiconStack(), chapterIndex);
            final int clickedChapter = chapterIndex;
            bindChapterSelection(chapterGroup, () -> editorState.selectChapter(clickedChapter));
            ChapterFocusWidget focusWidget = createChapterFocus(chapterGroup);
            if (focusWidget != null) {
                editorState.chapterFocusWidgets().put(chapterIndex, focusWidget);
            }
            editorState.chapterGroups().put(chapterIndex, chapterGroup);
            collectPageBindings(chapterGroup, chapterIndex, pageBindings);

            chapterParent.addWidget(chapterGroup);
        }

        bindPageSlots(pageBindings, editorState, serverLevel, libraryId);
        editorState.applyChapterSelectionVisuals();
        editorState.applySlotSelectionVisuals();

        if (chapterParent instanceof DraggableScrollableWidgetGroup scrollable) {
            scrollable.computeMax();
        }
    }

    private static void applyChapterMetadata(WidgetGroup chapterGroup, ItemStack hexiconStack, int chapterIndex) {
        String chapterName = HexiconData.getChapterName(hexiconStack, chapterIndex);
        Widget chapterNameWidget = findWidgetByIdToken(chapterGroup, "chapter_name");
        setWidgetText(chapterNameWidget, chapterName);

        Widget chapterIconWidget = findWidgetByIdToken(chapterGroup, "chapter_icon");
        var icon = HexiconData.getChapterIcon(hexiconStack, chapterIndex);
        setWidgetTexture(chapterIconWidget, icon == null ? IGuiTexture.EMPTY : new ResourceTexture(icon));

    }

    private static void bindChapterSelection(WidgetGroup chapterGroup, Runnable onSelect) {
        Widget titleSelect = chapterGroup.getFirstWidgetById("^title_select$");
        if (titleSelect instanceof WidgetGroup titleSelectGroup) {
            addChapterHeaderClickZone(
                chapterGroup,
                titleSelectGroup.getSelfPositionX(),
                titleSelectGroup.getSelfPositionY(),
                Math.max(1, titleSelectGroup.getSizeWidth()),
                Math.max(1, titleSelectGroup.getSizeHeight()),
                onSelect
            );
            return;
        }

        ButtonWidget chapterButton = getChapterHeaderButton(chapterGroup);
        if (chapterButton == null) {
            Hexwright.LOGGER.error("spell_book.ui chapter is missing a selectable title button");
            return;
        }

        configureChapterHeaderClickZone(chapterGroup, chapterButton);
        addChapterHeaderClickZone(
            chapterGroup,
            chapterButton.getSelfPositionX(),
            chapterButton.getSelfPositionY(),
            Math.max(1, chapterButton.getSizeWidth()),
            Math.max(1, chapterButton.getSizeHeight()),
            onSelect
        );
    }

    private static void addChapterHeaderClickZone(WidgetGroup chapterGroup, int x, int y, int width, int height, Runnable onSelect) {
        SilentButtonWidget clickZone = new SilentButtonWidget(x, y, width, height, clickData -> onSelect.run());
        clickZone.setButtonTexture(IGuiTexture.EMPTY);
        clickZone.setHoverTexture(IGuiTexture.EMPTY);
        clickZone.setDrawBackgroundWhenHover(false);
        chapterGroup.addWidget(clickZone);
    }

    private static @org.jetbrains.annotations.Nullable ButtonWidget getChapterHeaderButton(WidgetGroup chapterGroup) {
        Widget titleSelect = chapterGroup.getFirstWidgetById("^title_select$");
        if (titleSelect instanceof ButtonWidget buttonWidget) {
            return buttonWidget;
        }
        if (titleSelect instanceof WidgetGroup titleSelectGroup) {
            ButtonWidget nested = findFirstButtonWidget(titleSelectGroup);
            if (nested != null) {
                return nested;
            }
        }
        return findFirstButtonWidget(chapterGroup);
    }

    private static void configureChapterHeaderClickZone(WidgetGroup chapterGroup, ButtonWidget chapterButton) {
        HeaderBounds bounds = getChapterHeaderBounds(chapterGroup, chapterButton);
        chapterButton.setSelfPosition(bounds.x(), bounds.y());
        chapterButton.setSize(bounds.width(), bounds.height());
    }

    private static @org.jetbrains.annotations.Nullable ChapterFocusWidget createChapterFocus(WidgetGroup chapterGroup) {
        IGuiTexture hoverTexture = ((WidgetHoverTextureAccessor) chapterGroup).hexwright$getHoverTexture();
        if (hoverTexture == null) {
            return null;
        }
        return new ChapterFocusWidget(chapterGroup.getSizeWidth(), chapterGroup.getSizeHeight(), hoverTexture);
    }

    private static HeaderBounds getChapterHeaderBounds(WidgetGroup chapterGroup, @org.jetbrains.annotations.Nullable Widget fallbackWidget) {
        int minX = fallbackWidget == null ? Integer.MAX_VALUE : fallbackWidget.getSelfPositionX();
        int minY = fallbackWidget == null ? Integer.MAX_VALUE : fallbackWidget.getSelfPositionY();
        int maxX = fallbackWidget == null ? Integer.MIN_VALUE : minX + Math.max(1, fallbackWidget.getSizeWidth());
        int maxY = fallbackWidget == null ? Integer.MIN_VALUE : minY + Math.max(1, fallbackWidget.getSizeHeight());

        Widget chapterNameWidget = findWidgetByIdToken(chapterGroup, "chapter_name");
        if (chapterNameWidget != null) {
            minX = Math.min(minX, chapterNameWidget.getSelfPositionX());
            minY = Math.min(minY, chapterNameWidget.getSelfPositionY());
            maxX = Math.max(maxX, chapterNameWidget.getSelfPositionX() + Math.max(1, chapterNameWidget.getSizeWidth()));
            maxY = Math.max(maxY, chapterNameWidget.getSelfPositionY() + Math.max(1, chapterNameWidget.getSizeHeight()));
        }

        Widget chapterIconWidget = findWidgetByIdToken(chapterGroup, "chapter_icon");
        if (chapterIconWidget != null) {
            minX = Math.min(minX, chapterIconWidget.getSelfPositionX());
            minY = Math.min(minY, chapterIconWidget.getSelfPositionY());
            maxX = Math.max(maxX, chapterIconWidget.getSelfPositionX() + Math.max(1, chapterIconWidget.getSizeWidth()));
            maxY = Math.max(maxY, chapterIconWidget.getSelfPositionY() + Math.max(1, chapterIconWidget.getSizeHeight()));
        }

        if (minX == Integer.MAX_VALUE || minY == Integer.MAX_VALUE || maxX == Integer.MIN_VALUE || maxY == Integer.MIN_VALUE) {
            minX = 0;
            minY = 0;
            maxX = 1;
            maxY = 1;
        }

        return new HeaderBounds(minX, minY, Math.max(1, maxX - minX), Math.max(1, maxY - minY));
    }

    private static void setChapterHeaderSelectionVisual(WidgetGroup chapterGroup, ChapterFocusWidget focusWidget, boolean selected) {
        if (focusWidget == null || focusWidget.attached == selected) {
            return;
        }

        focusWidget.attached = selected;
        if (selected) {
            chapterGroup.addWidget(0, focusWidget);
        } else {
            chapterGroup.removeWidget(focusWidget);
        }
    }

    private record HeaderBounds(int x, int y, int width, int height) {
    }

    private static final class ChapterFocusWidget extends Widget {
        private final IGuiTexture focusTexture;
        private boolean attached;

        ChapterFocusWidget(int width, int height, IGuiTexture focusTexture) {
            super(0, 0, width, height);
            this.focusTexture = focusTexture;
        }

        @Override
        public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
            focusTexture.draw(graphics, mouseX, mouseY, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
        }
    }

    private static void collectPageBindings(WidgetGroup chapterGroup, int chapterIndex, List<PageBinding> bindingsOut) {
        Widget pagesWidget = chapterGroup.getFirstWidgetById("^pages_container$|^pages$");
        WidgetGroup pagesGroup = pagesWidget instanceof WidgetGroup g ? g : chapterGroup;

        List<SlotWidget> slots = new ArrayList<>();
        collectSlotWidgets(pagesGroup, slots);
        if (slots.isEmpty()) {
            return;
        }

        int pagesThisChapter = Math.min(HexiconData.SLOTS_PER_BAR, slots.size());
        for (int i = 0; i < pagesThisChapter; i++) {
            int absoluteSlot = HexiconData.getAbsoluteSlotIndex(chapterIndex, i);
            bindingsOut.add(new PageBinding(absoluteSlot, chapterIndex + 1, i + 1, slots.get(i)));
        }
    }

    private static void bindPageSlots(List<PageBinding> pageBindings, EditorState editorState, ServerLevel serverLevel, UUID libraryId) {
        if (pageBindings.isEmpty()) {
            return;
        }

        HexiconPageContainer pageContainer = new HexiconPageContainer(pageBindings, serverLevel, libraryId, editorState.hexiconStack());
        for (int i = 0; i < pageBindings.size(); i++) {
            PageBinding binding = pageBindings.get(i);
            SelectableSlotWidget replacement = new SelectableSlotWidget(binding.slotWidget, () -> editorState.selectSpell(binding.chapterNumber - 1, binding.pageNumber - 1));
            replacement.setContainerSlot(pageContainer, i);
            replacement.setCanPutItems(true);
            replacement.setCanTakeItems(true);
            replacement.setLocationInfo(false, false);
            replacement.setBackgroundTexture(binding.slotWidget.getBackgroundTexture());
            WidgetGroup parent = binding.slotWidget.getParent();
            if (parent != null) {
                int insertIndex = parent.widgets.indexOf(binding.slotWidget);
                parent.removeWidget(binding.slotWidget);
                parent.addWidget(insertIndex < 0 ? parent.widgets.size() : insertIndex, replacement);
            }
            editorState.slotWidgets().put(binding.absoluteSlot, replacement);
        }
    }

    private static void collectSlotWidgets(Widget widget, List<SlotWidget> out) {
        if (widget instanceof SlotWidget slotWidget) {
            out.add(slotWidget);
            return;
        }
        if (widget instanceof WidgetGroup group) {
            for (Widget child : group.widgets) {
                collectSlotWidgets(child, out);
            }
        }
    }

    private static WidgetGroup cloneWidgetGroup(WidgetGroup template) {
        IConfigurableWidget configurable = (IConfigurableWidget) template;
        IConfigurableWidget clone = IConfigurableWidget.deserializeWrapper(configurable.serializeWrapper());
        if (clone == null || !(clone.widget() instanceof WidgetGroup cloneGroup)) {
            return null;
        }
        return cloneGroup;
    }

    private static void bindEditPanel(WidgetGroup root, EditorState editorState) {
        Widget editWidget = root.getFirstWidgetById("^spell_book_edit$");
        if (!(editWidget instanceof WidgetGroup editGroup)) {
            return;
        }

        editorState.bindEditWidgets(editGroup);
        editorState.refreshEditorPanel();
    }

    private static void bindSpellIconGrid(WidgetGroup editGroup, EditorState state) {
        Widget iconList = editGroup.getFirstWidgetById("^icon_select_list$");
        DraggableScrollableWidgetGroup iconGrid;
        if (iconList instanceof DraggableScrollableWidgetGroup scrollable) {
            iconGrid = scrollable;
        } else {
            iconGrid = findFirstScrollableGroup(editGroup);
        }

        if (iconGrid == null) {
            return;
        }

        iconGrid.clearAllWidgets();
        iconGrid.setBackground(new GuiTextureGroup(
            new ColorRectTexture(ICONS_PANEL_FILL),
            new ColorBorderTexture(1, ICONS_PANEL_BORDER)
        ));

        List<ResourceLocation> icons = findSpellIconTextures();
        if (icons.isEmpty()) {
            return;
        }

        int usableWidth = Math.max(ICON_TILE_SIZE, iconGrid.getSizeWidth() - ICON_GAP * 2);
        int columns = Math.max(1, (usableWidth + ICON_GAP) / (ICON_TILE_SIZE + ICON_GAP));

        for (int index = 0; index < icons.size(); index++) {
            ResourceLocation icon = icons.get(index);
            int column = index % columns;
            int row = index / columns;
            int x = ICON_GAP + column * (ICON_TILE_SIZE + ICON_GAP);
            int y = ICON_GAP + row * (ICON_TILE_SIZE + ICON_GAP);
            WidgetGroup tile = createIconTile(icon, x, y, state);
            iconGrid.addWidget(tile);
        }

        updateIconSelectionVisual(state);
        iconGrid.computeMax();
    }

    private static WidgetGroup createIconTile(ResourceLocation icon, int x, int y, EditorState state) {
        WidgetGroup tile = new WidgetGroup(x, y, ICON_TILE_SIZE, ICON_TILE_SIZE);
        String iconPath = icon.toString();
        String iconName = icon.getPath().substring(icon.getPath().lastIndexOf('/') + 1, icon.getPath().lastIndexOf('.'));

        ButtonWidget button = new ButtonWidget(0, 0, ICON_TILE_SIZE, ICON_TILE_SIZE,
            ICON_TILE_TEXTURE,
            clickData -> {
                state.selectedIcon = iconPath;
                updateIconSelectionVisual(state);
            }
        ).setHoverBorderTexture(1, 0xB0D8C486);
        if (!isUnnamedIcon(iconName)) {
            button.setHoverTooltips(capitalizeWords(iconName.replace('_', ' ')));
        }
        state.iconButtons.put(iconPath, button);

        ImageWidget image = new ImageWidget(
            ICON_PADDING,
            ICON_PADDING,
            ICON_TILE_SIZE - ICON_PADDING * 2,
            ICON_TILE_SIZE - ICON_PADDING * 2,
            new ResourceTexture(icon)
        );
        image.setClientSideWidget();

        tile.addWidget(button);
        tile.addWidget(image);
        return tile;
    }

    private static void updateIconSelectionVisual(EditorState state) {
        for (Map.Entry<String, ButtonWidget> entry : state.iconButtons.entrySet()) {
            boolean selected = Objects.equals(entry.getKey(), state.selectedIcon);
            entry.getValue().setButtonTexture(selected ? ICON_TILE_ACTIVE_TEXTURE : ICON_TILE_TEXTURE);
        }
    }

    private static DraggableScrollableWidgetGroup findFirstScrollableGroup(Widget widget) {
        if (widget instanceof DraggableScrollableWidgetGroup scrollable) {
            return scrollable;
        }
        if (widget instanceof WidgetGroup group) {
            for (Widget child : group.widgets) {
                DraggableScrollableWidgetGroup found = findFirstScrollableGroup(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static @org.jetbrains.annotations.Nullable TextFieldWidget getTextField(WidgetGroup root, String regex) {
        Widget widget = root.getFirstWidgetById(regex);
        return widget instanceof TextFieldWidget textField ? textField : null;
    }

    private static @org.jetbrains.annotations.Nullable LabelWidget getLabel(WidgetGroup root, String regex) {
        Widget widget = root.getFirstWidgetById(regex);
        return widget instanceof LabelWidget label ? label : null;
    }

    private static @org.jetbrains.annotations.Nullable ButtonWidget getButton(WidgetGroup root, String regex) {
        Widget widget = root.getFirstWidgetById(regex);
        return widget instanceof ButtonWidget button ? button : null;
    }

    private static @org.jetbrains.annotations.Nullable ButtonWidget findFirstButtonWidget(Widget widget) {
        if (widget instanceof ButtonWidget buttonWidget) {
            return buttonWidget;
        }
        if (widget instanceof WidgetGroup group) {
            for (Widget child : group.widgets) {
                ButtonWidget found = findFirstButtonWidget(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static @org.jetbrains.annotations.Nullable Widget findWidgetByIdToken(Widget widget, String token) {
        if (widget == null || token == null || token.isBlank()) {
            return null;
        }
        String normalizedToken = token.toLowerCase(Locale.ROOT);
        String id = widget.getId();
        if (id != null && id.toLowerCase(Locale.ROOT).contains(normalizedToken)) {
            return widget;
        }
        if (widget instanceof WidgetGroup group) {
            for (Widget child : group.widgets) {
                Widget found = findWidgetByIdToken(child, token);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void updateChapterPreview(WidgetGroup editGroup, ItemStack hexiconStack, int chapterIndex) {
        Widget chapterName = findWidgetByIdToken(editGroup, "chapter_name");
        setWidgetText(chapterName, HexiconData.getChapterName(hexiconStack, chapterIndex));

        Widget chapterIconWidget = findWidgetByIdToken(editGroup, "chapter_icon");
        ResourceLocation icon = HexiconData.getChapterIcon(hexiconStack, chapterIndex);
        setWidgetTexture(chapterIconWidget, icon == null ? IGuiTexture.EMPTY : new ResourceTexture(icon));
    }

    private static String normalizeName(String input, String fallback) {
        String trimmed = input == null ? "" : input.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static boolean isUnnamedIcon(String iconName) {
        return iconName != null && iconName.toLowerCase(Locale.ROOT).startsWith("set1_");
    }

    private static String capitalizeWords(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        StringBuilder result = new StringBuilder(input.length());
        boolean capitalizeNext = true;
        for (char c : input.toCharArray()) {
            if (Character.isWhitespace(c)) {
                capitalizeNext = true;
                result.append(c);
            } else {
                result.append(capitalizeNext ? Character.toUpperCase(c) : c);
                capitalizeNext = false;
            }
        }
        return result.toString();
    }

    private static void setWidgetText(Widget widget, String value) {
        if (widget == null) {
            return;
        }
        if (widget instanceof TextFieldWidget textFieldWidget) {
            textFieldWidget.setCurrentString(value);
        } else if (widget instanceof LabelWidget labelWidget) {
            labelWidget.setText(value);
        } else if (widget instanceof TextTextureWidget textTextureWidget) {
            textTextureWidget.setText(Component.literal(value));
        } else if (widget instanceof ImageWidget imageWidget && imageWidget.getImage() instanceof TextTexture textTexture) {
            textTexture.updateText(value);
        } else {
            setWidgetTexture(widget, new TextTexture(value));
        }
    }

    private static void setWidgetTexture(Widget widget, IGuiTexture texture) {
        if (widget == null || texture == null) {
            return;
        }
        if (widget instanceof ImageWidget imageWidget) {
            imageWidget.setImage(texture);
            return;
        }
        if (widget instanceof ButtonWidget buttonWidget) {
            buttonWidget.setButtonTexture(texture);
            return;
        }
        if (widget instanceof SlotWidget slotWidget) {
            slotWidget.setBackgroundTexture(texture);
            return;
        }
        if (widget instanceof DraggableScrollableWidgetGroup scrollable) {
            scrollable.setBackground(texture);
            return;
        }
        widget.setBackground(texture);
    }

    private static ResourceLocation parseIcon(String rawIcon) {
        if (rawIcon == null || rawIcon.isBlank()) {
            return null;
        }
        try {
            return new ResourceLocation(rawIcon);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static List<ResourceLocation> findSpellIconTextures() {
        List<ResourceLocation> cached = SPELL_ICON_CACHE;
        if (cached != null) {
            return cached;
        }

        List<ResourceLocation> icons = List.of();
        try {
            URL dir = HexiconUIFactory.class.getResource(SPELL_ICON_DIR);
            if (dir == null) {
                Hexwright.LOGGER.error("No spell icons packaged at {}", SPELL_ICON_DIR);
            } else {
                URI uri = dir.toURI();
                if ("jar".equals(uri.getScheme())) {
                    FileSystem fs;
                    boolean owned = false;
                    try {
                        fs = FileSystems.newFileSystem(uri, Map.of());
                        owned = true;
                    } catch (FileSystemAlreadyExistsException existing) {
                        fs = FileSystems.getFileSystem(uri);
                    }
                    try {
                        icons = listIconsIn(fs.getPath(SPELL_ICON_DIR));
                    } finally {
                        if (owned) {
                            fs.close();
                        }
                    }
                } else {
                    icons = listIconsIn(Paths.get(uri));
                }
            }
        } catch (Throwable t) {
            Hexwright.LOGGER.error("Failed to list packaged spell icons", t);
        }

        SPELL_ICON_CACHE = icons;
        return icons;
    }

    private static List<ResourceLocation> listIconsIn(Path dir) throws IOException {
        try (Stream<Path> entries = Files.list(dir)) {
            return entries
                .map(path -> path.getFileName().toString())
                .filter(name -> name.endsWith(".png"))
                .sorted()
                .map(name -> new ResourceLocation("ldlib", "textures/spell_icons/" + name))
                .toList();
        }
    }

    private static final class EditorState {
        private final Player player;
        private final InteractionHand hand;
        private final ItemStack hexiconStack;
        private final ServerLevel serverLevel;
        private final UUID libraryId;
        private final Map<Integer, WidgetGroup> chapterGroups = new HashMap<>();
        private final Map<Integer, ChapterFocusWidget> chapterFocusWidgets = new HashMap<>();
        private final Map<Integer, SelectableSlotWidget> slotWidgets = new HashMap<>();
        private final Map<String, ButtonWidget> iconButtons = new HashMap<>();
        private WidgetGroup editGroup;
        private TextFieldWidget editName;
        private Widget editLabel;
        private ButtonWidget applyButton;
        private String selectedIcon;
        private int selectedChapter;
        private int selectedSlot;
        private boolean editingChapter;

        private EditorState(Player player, InteractionHand hand, ItemStack hexiconStack) {
            this.player = player;
            this.hand = hand;
            this.hexiconStack = hexiconStack;
            this.selectedChapter = HexiconData.getSelectedBar(hexiconStack);
            this.selectedSlot = HexiconData.getSelectedSlot(hexiconStack);
            this.editingChapter = false;
            this.serverLevel = player.level() instanceof ServerLevel level ? level : null;
            this.libraryId = HexiconData.getLibraryId(hexiconStack);
        }

        private Player player() {
            return player;
        }

        private InteractionHand hand() {
            return hand;
        }

        private ItemStack hexiconStack() {
            return hexiconStack;
        }

        private ServerLevel serverLevel() {
            return serverLevel;
        }

        private UUID libraryId() {
            return libraryId;
        }

        private Map<Integer, WidgetGroup> chapterGroups() {
            return chapterGroups;
        }

        private Map<Integer, ChapterFocusWidget> chapterFocusWidgets() {
            return chapterFocusWidgets;
        }

        private Map<Integer, SelectableSlotWidget> slotWidgets() {
            return slotWidgets;
        }

        private int selectedChapter() {
            return selectedChapter;
        }

        private int selectedSlot() {
            return selectedSlot;
        }

        private void bindEditWidgets(WidgetGroup editGroup) {
            this.editGroup = editGroup;
            this.editName = getTextField(editGroup, "^edit_name$");
            this.editLabel = editGroup.getFirstWidgetById("^edit_label$");
            bindApplyButton(editGroup);
            bindSpellIconGrid(editGroup, this);
        }

        private void selectChapter(int chapterIndex) {
            this.selectedChapter = chapterIndex;
            this.selectedSlot = 0;
            this.editingChapter = true;
            HexiconData.setSelectedBarAndSlot(hexiconStack, chapterIndex, 0);
            syncSelection();
            refreshEditorPanel();
        }

        private void selectSpell(int chapterIndex, int slotIndex) {
            int absoluteSlot = HexiconData.getAbsoluteSlotIndex(chapterIndex, slotIndex);
            SelectableSlotWidget slotWidget = slotWidgets.get(absoluteSlot);
            if (slotWidget == null || HexiconPageContainer.payloadIdFromStack(slotWidget.getItem()) == null) {
                return;
            }

            this.selectedChapter = chapterIndex;
            this.selectedSlot = slotIndex;
            this.editingChapter = false;
            HexiconData.setSelectedBarAndSlot(hexiconStack, chapterIndex, slotIndex);
            syncSelection();
            refreshEditorPanel();
        }

        private void syncSelection() {
            if (serverLevel != null) {
                HexiconData.refreshCachedSelectedBound(serverLevel, hexiconStack);
            }
            if (player.level().isClientSide) {
                HexwrightNetworking.sendHexiconSelection(hand, selectedChapter, selectedSlot, libraryId);
            }
            applyChapterSelectionVisuals();
            applySlotSelectionVisuals();
        }

        private void refreshEditorPanel() {
            if (editGroup == null) {
                return;
            }

            UUID selectedPayloadId = getSelectedPayloadId();
            updateChapterPreview(editGroup, hexiconStack, selectedChapter);

            if (editingChapter) {
                setWidgetText(editLabel, "Edit Chapter");
                if (editName != null) {
                    editName.setCurrentString(HexiconData.getChapterName(hexiconStack, selectedChapter));
                }
                ResourceLocation chapterIcon = HexiconData.getChapterIcon(hexiconStack, selectedChapter);
                selectedIcon = chapterIcon == null ? null : chapterIcon.toString();
            } else {
                HexiconData.SpellDisplay display = serverLevel != null && selectedPayloadId != null
                    ? HexiconData.getSpellDisplay(serverLevel, selectedPayloadId, selectedSlot)
                    : getSelectedMarkerDisplay();
                setWidgetText(editLabel, "Edit Page");
                if (editName != null) {
                    editName.setCurrentString(display.name());
                }
                selectedIcon = display.icon() == null ? null : display.icon().toString();
            }

            updateIconSelectionVisual(this);
        }

        private void bindApplyButton(WidgetGroup editGroup) {
            ButtonWidget visualButton = getButton(editGroup, "(?i)^apply$");
            if (visualButton == null) {
                Hexwright.LOGGER.error("spell_book.ui is missing an Apply button");
                return;
            }

            visualButton.setOnPressCallback(clickData -> applyChanges());
            this.applyButton = visualButton;
        }

        private void applyChanges() {
            ResourceLocation icon = parseIcon(selectedIcon);
            String nameInput = editName == null ? "" : editName.getCurrentString();

            if (player.level().isClientSide) {
                HexwrightNetworking.sendHexiconApply(hand, selectedChapter, selectedSlot, editingChapter, nameInput, selectedIcon, libraryId);
                applyLocalChanges(nameInput, icon);
                return;
            }

            if (!editingChapter) {
                if (serverLevel != null) {
                    UUID payloadId = HexiconData.getPayloadId(serverLevel, libraryId, selectedChapter, selectedSlot);
                    if (payloadId != null) {
                        HexiconData.setSpellDisplay(serverLevel, payloadId, normalizeName(nameInput, "Spell " + (selectedSlot + 1)), icon);
                        player.getInventory().setChanged();
                    }
                }
                applyLocalChanges(nameInput, icon);
                return;
            }

            HexiconData.setChapterName(hexiconStack, selectedChapter, normalizeName(nameInput, "Chapter " + (selectedChapter + 1)));
            HexiconData.setChapterIcon(hexiconStack, selectedChapter, icon);
            if (serverLevel != null) {
                HexiconData.refreshCachedSelectedBound(serverLevel, hexiconStack);
            }
            player.getInventory().setChanged();
            applyLocalChanges(nameInput, icon);
        }

        private UUID getSelectedPayloadId() {
            if (serverLevel != null) {
                return HexiconData.getPayloadId(serverLevel, libraryId, selectedChapter, selectedSlot);
            }
            SelectableSlotWidget slotWidget = slotWidgets.get(HexiconData.getAbsoluteSlotIndex(selectedChapter, selectedSlot));
            return slotWidget == null ? null : HexiconPageContainer.payloadIdFromStack(slotWidget.getItem());
        }

        private HexiconData.SpellDisplay getSelectedMarkerDisplay() {
            String fallback = "Spell " + (selectedSlot + 1);
            SelectableSlotWidget slotWidget = slotWidgets.get(HexiconData.getAbsoluteSlotIndex(selectedChapter, selectedSlot));
            if (slotWidget == null) {
                return new HexiconData.SpellDisplay(fallback, null);
            }
            CompoundTag tag = slotWidget.getItem().getTag();
            if (tag == null) {
                return new HexiconData.SpellDisplay(fallback, null);
            }
            String name = tag.getString(TAG_UI_DISPLAY_NAME);
            ResourceLocation icon = parseIcon(tag.getString(TAG_UI_DISPLAY_ICON));
            return new HexiconData.SpellDisplay(name == null || name.isBlank() ? fallback : name, icon);
        }

        private void applyLocalChanges(String nameInput, ResourceLocation icon) {
            selectedIcon = icon == null ? null : icon.toString();
            if (!editingChapter) {
                String normalizedName = normalizeName(nameInput, "Spell " + (selectedSlot + 1));
                updateSelectedSpellMarker(normalizedName, icon);
                setWidgetText(editLabel, "Edit Page");
                if (editName != null) {
                    editName.setCurrentString(normalizedName);
                }
                updateIconSelectionVisual(this);
                return;
            }

            HexiconData.setChapterName(hexiconStack, selectedChapter, normalizeName(nameInput, "Chapter " + (selectedChapter + 1)));
            HexiconData.setChapterIcon(hexiconStack, selectedChapter, icon);
            setWidgetText(editLabel, "Edit Chapter");
            if (editGroup != null) {
                updateChapterPreview(editGroup, hexiconStack, selectedChapter);
            }
            WidgetGroup chapterGroup = chapterGroups.get(selectedChapter);
            if (chapterGroup != null) {
                applyChapterMetadata(chapterGroup, hexiconStack, selectedChapter);
            }
            updateIconSelectionVisual(this);
        }

        private void updateSelectedSpellMarker(String name, ResourceLocation icon) {
            SelectableSlotWidget slotWidget = slotWidgets.get(HexiconData.getAbsoluteSlotIndex(selectedChapter, selectedSlot));
            if (slotWidget == null) {
                return;
            }
            ItemStack marker = slotWidget.getItem();
            UUID payloadId = HexiconPageContainer.payloadIdFromStack(marker);
            if (payloadId == null) {
                return;
            }
            ItemStack updated = marker.copy();
            CompoundTag tag = updated.getOrCreateTag();
            tag.putString(TAG_UI_DISPLAY_NAME, name);
            if (icon == null) {
                tag.remove(TAG_UI_DISPLAY_ICON);
            } else {
                tag.putString(TAG_UI_DISPLAY_ICON, icon.toString());
            }
            updated.setHoverName(Component.literal(name));
            slotWidget.setItem(updated);
        }

        private void applyChapterSelectionVisuals() {
            for (Map.Entry<Integer, WidgetGroup> entry : chapterGroups.entrySet()) {
                applyChapterMetadata(entry.getValue(), hexiconStack, entry.getKey());
                setChapterHeaderSelectionVisual(entry.getValue(), chapterFocusWidgets.get(entry.getKey()), entry.getKey() == selectedChapter);
            }
        }

        private void applySlotSelectionVisuals() {
            for (Map.Entry<Integer, SelectableSlotWidget> entry : slotWidgets.entrySet()) {
                entry.getValue().setSelected(!editingChapter && entry.getKey() == HexiconData.getAbsoluteSlotIndex(selectedChapter, selectedSlot));
            }
        }
    }

    private static final class SelectableSlotWidget extends SlotWidget {
        private final Runnable onSelect;
        private boolean selected;

        private SelectableSlotWidget(SlotWidget original, Runnable onSelect) {
            super();
            this.onSelect = onSelect;
            setId(original.getId());
            setSelfPosition(original.getSelfPosition());
            setBackgroundTexture(original.getBackgroundTexture());
            setDrawHoverOverlay(original.drawHoverOverlay);
            setDrawHoverTips(original.drawHoverTips);
            setHoverTooltips(original.getTooltipTexts());
        }

        private void setSelected(boolean selected) {
            this.selected = selected;
            if (selected) {
                setBackgroundTexture(new GuiTextureGroup(SlotWidget.ITEM_SLOT_TEXTURE, new ColorBorderTexture(1, PAGE_SELECTED_BORDER)));
            } else {
                setBackgroundTexture(SlotWidget.ITEM_SLOT_TEXTURE);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (isMouseOverElement(mouseX, mouseY)) {
                writeClientAction(1, buffer -> { });
                if (onSelect != null) {
                    onSelect.run();
                }
                return true;
            }
            return false;
        }

        @Override
        public void handleClientAction(int id, FriendlyByteBuf buffer) {
            super.handleClientAction(id, buffer);
            if (id == 1 && onSelect != null) {
                onSelect.run();
            }
        }

        @Override
        @Environment(EnvType.CLIENT)
        public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            CompoundTag tag = getItem().getTag();
            ResourceLocation icon = tag == null ? null : parseIcon(tag.getString(TAG_UI_DISPLAY_ICON));
            if (icon == null) {
                super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
                return;
            }

            drawBackgroundTexture(graphics, mouseX, mouseY);
            int iconX = getPositionX() + SLOT_ICON_PADDING;
            int iconY = getPositionY() + SLOT_ICON_PADDING;
            int iconW = Math.max(1, getSizeWidth() - SLOT_ICON_PADDING * 2);
            int iconH = Math.max(1, getSizeHeight() - SLOT_ICON_PADDING * 2);
            new ResourceTexture(icon).draw(graphics, mouseX, mouseY, iconX, iconY, iconW, iconH);
            drawOverlay(graphics, mouseX, mouseY, partialTicks);

            if (drawHoverOverlay && isMouseOverElement(mouseX, mouseY) && getHoverElement(mouseX, mouseY) == this) {
                RenderSystem.colorMask(true, true, true, false);
                DrawerHelper.drawSolidRect(graphics, getPositionX() + 1, getPositionY() + 1, 16, 16, 0x80FFFFFF);
                RenderSystem.colorMask(true, true, true, true);
            }
        }
    }

    private static final class SilentButtonWidget extends ButtonWidget {
        private SilentButtonWidget(int xPosition, int yPosition, int width, int height, java.util.function.Consumer<ClickData> onPressed) {
            super(xPosition, yPosition, width, height, IGuiTexture.EMPTY, onPressed);
        }

        @Override
        @Environment(EnvType.CLIENT)
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (isMouseOverElement(mouseX, mouseY)) {
                isClicked = true;
                ClickData clickData = new ClickData();
                writeClientAction(1, clickData::writeToBuf);
                if (onPressCallback != null) {
                    onPressCallback.accept(clickData);
                }
                return true;
            }
            return false;
        }
    }

    private record PageBinding(int absoluteSlot, int chapterNumber, int pageNumber, SlotWidget slotWidget) {
    }

    private static final class HexiconPageContainer extends SimpleContainer {
        private final List<PageBinding> bindings;
        private final ServerLevel serverLevel;
        private final UUID libraryId;
        private final ItemStack hexiconStack;
        private boolean syncing;

        private HexiconPageContainer(List<PageBinding> bindings, ServerLevel serverLevel, UUID libraryId, ItemStack hexiconStack) {
            super(bindings.size());
            this.bindings = bindings;
            this.serverLevel = serverLevel;
            this.libraryId = libraryId;
            this.hexiconStack = hexiconStack;

            syncing = true;
            try {
                for (int i = 0; i < bindings.size(); i++) {
                    PageBinding binding = bindings.get(i);
                    UUID payloadId = serverLevel != null && libraryId != null
                        ? HexiconData.getSlotReference(serverLevel, libraryId, binding.absoluteSlot)
                        : null;
                    super.setItem(i, markerFromPayload(payloadId, binding));
                }
            } finally {
                syncing = false;
            }
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.isEmpty() || payloadIdFromStack(stack) != null;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (syncing || serverLevel == null || libraryId == null) {
                return;
            }

            syncing = true;
            try {
                HexiconSavedData state = HexiconSavedData.open(serverLevel);
                List<UUID> previousPayloads = new ArrayList<>(bindings.size());

                for (PageBinding binding : bindings) {
                    previousPayloads.add(state.getSlotReference(libraryId, binding.absoluteSlot));
                }

                for (int i = 0; i < bindings.size(); i++) {
                    PageBinding binding = bindings.get(i);
                    UUID newPayloadId = payloadIdFromStack(getItem(i));
                    UUID oldPayloadId = previousPayloads.get(i);
                    if (!Objects.equals(newPayloadId, oldPayloadId)) {
                        state.setSlotReference(libraryId, binding.absoluteSlot, newPayloadId);
                    }
                }

                for (UUID oldPayloadId : previousPayloads) {
                    if (oldPayloadId != null && !state.isPayloadReferenced(oldPayloadId)) {
                        state.removePayload(oldPayloadId);
                    }
                }

                for (int i = 0; i < bindings.size(); i++) {
                    PageBinding binding = bindings.get(i);
                    UUID payloadId = payloadIdFromStack(getItem(i));
                    super.setItem(i, markerFromPayload(payloadId, binding));
                }

                if (hexiconStack != null) {
                    HexiconData.refreshCachedWrittenCount(serverLevel, hexiconStack);
                }
            } finally {
                syncing = false;
            }
        }

        private static UUID payloadIdFromStack(ItemStack stack) {
            if (stack.isEmpty()) {
                return null;
            }
            CompoundTag tag = stack.getTag();
            if (tag == null || !tag.hasUUID(TAG_UI_PAYLOAD_ID)) {
                return null;
            }
            return tag.getUUID(TAG_UI_PAYLOAD_ID);
        }

        private ItemStack markerFromPayload(UUID payloadId, PageBinding binding) {
            if (payloadId == null) {
                return ItemStack.EMPTY;
            }

            ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
            CompoundTag tag = stack.getOrCreateTag();
            tag.putUUID(TAG_UI_PAYLOAD_ID, payloadId);

            HexiconData.SpellDisplay display = serverLevel == null ? null : HexiconData.getSpellDisplay(serverLevel, payloadId, binding.pageNumber - 1);
            String displayName = display == null ? "Spell " + binding.pageNumber : display.name();
            tag.putString(TAG_UI_DISPLAY_NAME, displayName);
            if (display != null && display.icon() != null) {
                tag.putString(TAG_UI_DISPLAY_ICON, display.icon().toString());
            }
            stack.setHoverName(Component.literal(displayName));
            return stack;
        }
    }

    public static final class Holder implements IUIHolder {
        private final Player player;
        private final InteractionHand hand;

        private Holder(Player player, InteractionHand hand) {
            this.player = player;
            this.hand = hand;
        }

        static Holder forHand(Player player, InteractionHand hand) {
            return new Holder(player, hand);
        }

        @Override
        public ModularUI createUI(Player entityPlayer) {
            return null;
        }

        @Override
        public boolean isInvalid() {
            return !HexiconData.isHexiconStack(player.getItemInHand(hand));
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