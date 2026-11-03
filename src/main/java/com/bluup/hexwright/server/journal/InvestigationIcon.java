package com.bluup.hexwright.server.journal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

public record InvestigationIcon(ResourceLocation texture, float u, float v, float width, float height,
                                @Nullable Integer background) {

    public static InvestigationIcon whole(ResourceLocation texture) {
        return new InvestigationIcon(texture, 0f, 0f, 1f, 1f, null);
    }

    public static InvestigationIcon parse(String name, JsonElement json) {
        if (json.isJsonPrimitive()) {
            return whole(new ResourceLocation(json.getAsString()));
        }
        JsonObject object = GsonHelper.convertToJsonObject(json, "icon '" + name + "'");
        ResourceLocation texture = new ResourceLocation(GsonHelper.getAsString(object, "texture"));

        float sheetWidth = GsonHelper.getAsFloat(object, "sheet_width", 1f);
        float sheetHeight = GsonHelper.getAsFloat(object, "sheet_height", 1f);
        if (sheetWidth <= 0f || sheetHeight <= 0f) {
            throw new IllegalArgumentException("icon '" + name + "' has a zero or negative sheet size");
        }

        return new InvestigationIcon(
            texture,
            GsonHelper.getAsFloat(object, "u", 0f) / sheetWidth,
            GsonHelper.getAsFloat(object, "v", 0f) / sheetHeight,
            GsonHelper.getAsFloat(object, "width", sheetWidth) / sheetWidth,
            GsonHelper.getAsFloat(object, "height", sheetHeight) / sheetHeight,
            parseColour(name, object.get("background"))
        );
    }

    private static @Nullable Integer parseColour(String name, @Nullable JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return null;
        }
        if (json instanceof JsonPrimitive primitive && primitive.isNumber()) {
            return primitive.getAsInt();
        }
        String text = GsonHelper.convertToString(json, "background of icon '" + name + "'").trim();
        String digits = text.startsWith("#") ? text.substring(1)
            : text.startsWith("0x") || text.startsWith("0X") ? text.substring(2)
            : text;
        if (digits.length() != 6 && digits.length() != 8) {
            throw new IllegalArgumentException(
                "icon '" + name + "' has background '" + text + "'; expected #RRGGBB or #AARRGGBB");
        }
        try {
            long value = Long.parseLong(digits, 16);
            return (int) (digits.length() == 6 ? value | 0xFF000000L : value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "icon '" + name + "' has background '" + text + "', which isn't hexadecimal", e);
        }
    }

    public IGuiTexture toTexture() {
        ResourceTexture whole = new ResourceTexture(texture);
        IGuiTexture sprite = u == 0f && v == 0f && width == 1f && height == 1f
            ? whole
            : whole.getSubTexture(u, v, width, height);
        if (background == null) {
            return sprite;
        }
        return new GuiTextureGroup(new ColorRectTexture(background), sprite);
    }
}
