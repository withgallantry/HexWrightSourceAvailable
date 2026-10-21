package com.bluup.hexwright.client.inspiration;

import com.bluup.hexwright.Hexwright;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class InspirationHud {

    private static final ResourceLocation BAR = Hexwright.id("textures/gui/inspiration_bar.png");
    public static final int BAR_WIDTH = 182;
    public static final int BAR_HEIGHT = 5;
    private static final int TEXTURE_HEIGHT = BAR_HEIGHT * 2;

    private static final long SHOWN_MILLIS = 3000L;
    private static final long FADE_MILLIS = 600L;

    private static final int LEVEL_COLOUR = 0x4CD6FF;
    public static final int LEVEL_RAISE = 6;
    private static final int GAP_ABOVE_ROWS = 2;

    private static final int XP_BAR_FROM_BOTTOM = 29;
    private static final int STATUS_ROW_FROM_BOTTOM = 39;
    private static final int STATUS_ROW_STEP = 10;

    private InspirationHud() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register(InspirationHud::onHudRender);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ClientInspiration::clear));
    }

    private static void onHudRender(GuiGraphics graphics, float partialTick) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.options.hideGui || player.isSpectator()) {
            return;
        }
        long since = ClientInspiration.millisSinceGain();
        if (since >= SHOWN_MILLIS + FADE_MILLIS) {
            return;
        }
        float alpha = since < SHOWN_MILLIS ? 1.0F : 1.0F - (since - SHOWN_MILLIS) / (float) FADE_MILLIS;

        int x = graphics.guiWidth() / 2 - BAR_WIDTH / 2;
        int y = topOfStatusRows(client, player, graphics.guiHeight()) - GAP_ABOVE_ROWS - BAR_HEIGHT;
        drawBar(graphics, client.font, x, y, alpha);
    }

    public static void drawBar(GuiGraphics graphics, Font font, int x, int y, float alpha) {
        RenderSystem.enableBlend();
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.blit(BAR, x, y, 0, 0, BAR_WIDTH, BAR_HEIGHT, BAR_WIDTH, TEXTURE_HEIGHT);
        int filled = (int) (ClientInspiration.fraction() * (BAR_WIDTH + 1));
        if (filled > 0) {
            graphics.blit(BAR, x, y, 0, BAR_HEIGHT, filled, BAR_HEIGHT, BAR_WIDTH, TEXTURE_HEIGHT);
        }
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();

        drawLevel(graphics, font, x + BAR_WIDTH / 2, y - LEVEL_RAISE, alpha);
    }

    private static void drawLevel(GuiGraphics graphics, Font font, int centreX, int y, float alpha) {
        int levels = ClientInspiration.levels();
        int alphaBits = Mth.floor(alpha * 255.0F);
        if (levels <= 0 || alphaBits < 4) {
            return;
        }
        String text = String.valueOf(levels);
        int x = centreX - font.width(text) / 2;
        int outline = alphaBits << 24;
        graphics.drawString(font, text, x + 1, y, outline, false);
        graphics.drawString(font, text, x - 1, y, outline, false);
        graphics.drawString(font, text, x, y + 1, outline, false);
        graphics.drawString(font, text, x, y - 1, outline, false);
        graphics.drawString(font, text, x, y, outline | LEVEL_COLOUR, false);
    }

    private static int topOfStatusRows(Minecraft client, LocalPlayer player, int height) {
        if (client.gameMode == null || !client.gameMode.canHurtPlayer()) {
            return height - XP_BAR_FROM_BOTTOM;
        }
        int statusRow = height - STATUS_ROW_FROM_BOTTOM;

        float maxHealth = Math.max((float) player.getAttributeValue(Attributes.MAX_HEALTH), player.getHealth());
        int absorption = Mth.ceil(player.getAbsorptionAmount());
        int heartRows = Mth.ceil((maxHealth + absorption) / 2.0F / 10.0F);
        int heartRowStep = Math.max(10 - (heartRows - 2), 3);
        int left = statusRow - (heartRows - 1) * heartRowStep;
        if (player.getArmorValue() > 0) {
            left -= STATUS_ROW_STEP;
        }

        int right = statusRow - (Math.max(mountHeartRows(player), 1) - 1) * STATUS_ROW_STEP;
        if (player.isEyeInFluid(FluidTags.WATER) || player.getAirSupply() < player.getMaxAirSupply()) {
            right -= STATUS_ROW_STEP;
        }
        return Math.min(left, right);
    }

    private static int mountHeartRows(LocalPlayer player) {
        if (!(player.getVehicle() instanceof LivingEntity mount) || !mount.showVehicleHealth()) {
            return 0;
        }
        int hearts = Math.min((int) (mount.getMaxHealth() + 0.5F) / 2, 30);
        return Mth.ceil(hearts / 10.0F);
    }
}
