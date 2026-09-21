package com.bluup.hexwright.client.journal;

import com.bluup.hexwright.server.journal.Artifact;
import com.bluup.hexwright.server.journal.Artifacts;
import com.bluup.hexwright.server.journal.Investigation;
import com.bluup.hexwright.server.journal.InvestigationIcon;
import com.bluup.hexwright.server.journal.Investigations;
import com.bluup.hexwright.server.journal.JournalToastKind;
import com.bluup.hexwright.server.journal.LoreEntries;
import com.bluup.hexwright.server.journal.LoreEntry;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public final class JournalToast implements Toast {

    private static final long DISPLAY_TIME = 5000L;

    private static final long FADE_IN_MILLIS = 450L;

    private static final int WIDTH = 160;

    private static final int HEIGHT = 32;

    private static final int SLOT_INSET = 2;

    private static final int PANEL_TOP = SLOT_INSET;
    private static final int PANEL_BOTTOM = HEIGHT - SLOT_INSET;
    private static final int PANEL_HEIGHT = PANEL_BOTTOM - PANEL_TOP;

    private static final int ICON_SIZE = 16;
    private static final int ICON_X = 8;
    private static final int ICON_Y = PANEL_TOP + (PANEL_HEIGHT - ICON_SIZE) / 2;
    private static final int TEXT_X = ICON_X + ICON_SIZE + 6;

    private static final int HEADING_Y = PANEL_TOP + (PANEL_HEIGHT - 20) / 2;
    private static final int SUBJECT_Y = HEADING_Y + 11;

    private static final int PAGE = 0xE7D5B3;
    private static final int PAGE_HIGHLIGHT = 0xF3E6CC;
    private static final int INK = 0x884B2B;
    private static final int TITLE_INK = 0x6B3A1F;
    private static final int BODY_INK = 0x8A6440;

    private static final ResourceLocation LORE_ICON =
        new ResourceLocation("ldlib", "textures/field_journal/content/icons/lore.png");

    private final Component heading;
    private final Component subject;
    private final @Nullable InvestigationIcon icon;
    private final ItemStack item;

    private JournalToast(Component heading, Component subject, @Nullable InvestigationIcon icon) {
        this(heading, subject, icon, ItemStack.EMPTY);
    }

    private JournalToast(Component heading, Component subject, @Nullable InvestigationIcon icon, ItemStack item) {
        this.heading = heading;
        this.subject = subject;
        this.icon = icon;
        this.item = item;
    }

    public static void show(JournalToastKind kind, String id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        minecraft.getToasts().addToast(build(kind, id));
    }

    private static JournalToast build(JournalToastKind kind, String id) {
        Component heading = Component.translatable(switch (kind) {
            case STRUCTURE -> "toast.hexwright.journal.structure";
            case INVESTIGATION -> "toast.hexwright.journal.investigation";
            case LORE -> "toast.hexwright.journal.lore";
            case ARTIFACT -> "toast.hexwright.journal.artifact";
        });

        if (kind == JournalToastKind.ARTIFACT) {
            Artifact artifact = Artifacts.get().byId().get(id);
            return new JournalToast(
                heading,
                artifact == null ? Component.literal(id) : artifact.titleComponent(),
                null,
                artifact == null ? ItemStack.EMPTY : artifact.displayStack());
        }

        if (kind == JournalToastKind.LORE) {
            LoreEntry entry = LoreEntries.get().byId().get(id);
            return new JournalToast(
                heading,
                entry == null ? Component.literal(id) : entry.titleComponent(),
                InvestigationIcon.whole(LORE_ICON));
        }

        Investigation investigation = Investigations.get().byId().get(id);
        return new JournalToast(
            heading,
            investigation == null ? Component.literal(id) : investigation.titleComponent(),
            investigation == null ? null : Investigations.get().icons().get(investigation.icon()));
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        return HEIGHT;
    }

    @Override
    public Visibility render(GuiGraphics graphics, ToastComponent toasts, long visibleFor) {
        int alpha = alpha(visibleFor);
        if (alpha > 0) {
            drawPanel(graphics, alpha);
            drawIcon(graphics, alpha);
            drawText(graphics, toasts.getMinecraft().font, alpha);
        }
        return visibleFor >= (long) (DISPLAY_TIME * toasts.getNotificationDisplayTimeMultiplier())
            ? Visibility.HIDE
            : Visibility.SHOW;
    }

    private static int alpha(long visibleFor) {
        float progress = Mth.clamp((float) visibleFor / FADE_IN_MILLIS, 0f, 1f);
        float eased = progress * progress * (3f - 2f * progress);
        return Mth.floor(eased * 255f);
    }

    private static void drawPanel(GuiGraphics graphics, int alpha) {
        int border = argb(INK, alpha);
        graphics.fill(1, PANEL_TOP, WIDTH - 1, PANEL_TOP + 1, border);
        graphics.fill(1, PANEL_BOTTOM - 1, WIDTH - 1, PANEL_BOTTOM, border);
        graphics.fill(0, PANEL_TOP + 1, 1, PANEL_BOTTOM - 1, border);
        graphics.fill(WIDTH - 1, PANEL_TOP + 1, WIDTH, PANEL_BOTTOM - 1, border);

        graphics.fill(1, PANEL_TOP + 1, WIDTH - 1, PANEL_TOP + 2, argb(PAGE_HIGHLIGHT, alpha));
        graphics.fill(1, PANEL_TOP + 2, WIDTH - 1, PANEL_BOTTOM - 1, argb(PAGE, alpha));
    }

    private void drawIcon(GuiGraphics graphics, int alpha) {
        if (!item.isEmpty()) {
            if (alpha >= 128) {
                graphics.renderItem(item, ICON_X, ICON_Y);
            }
            return;
        }
        if (icon == null) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        ResourceTexture texture = new ResourceTexture(icon.texture());
        ResourceTexture sprite = icon.u() == 0f && icon.v() == 0f && icon.width() == 1f && icon.height() == 1f
            ? texture
            : texture.getSubTexture(icon.u(), icon.v(), icon.width(), icon.height());
        sprite.setColor(argb(INK, alpha));
        sprite.draw(graphics, 0, 0, ICON_X, ICON_Y, ICON_SIZE, ICON_SIZE);
    }

    private void drawText(GuiGraphics graphics, Font font, int alpha) {
        if (alpha < 4) {
            return;
        }
        int available = WIDTH - TEXT_X - 6;
        graphics.drawString(font, clip(font, heading, available), TEXT_X, HEADING_Y, argb(TITLE_INK, alpha), false);
        graphics.drawString(font, clip(font, subject, available), TEXT_X, SUBJECT_Y, argb(BODY_INK, alpha), false);
    }

    private static String clip(Font font, Component text, int available) {
        String rendered = text.getString();
        if (font.width(rendered) <= available) {
            return rendered;
        }
        return font.plainSubstrByWidth(rendered, available - font.width("...")) + "...";
    }

    private static int argb(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }
}
