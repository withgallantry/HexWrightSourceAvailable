package com.bluup.hexwright.common.staff_assembly.calc;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public enum EfficiencyRating {
    CRUDE,
    SOUND,
    FINE,
    EXQUISITE,
    MASTERWORK;

    public Component label() {
        return Component.translatable("staff_quality.hexwright." + name().toLowerCase(Locale.ROOT));
    }

    public String translationKey() {
        return "staff_grade.hexwright." + name().toLowerCase(Locale.ROOT);
    }

    public ChatFormatting color() {
        return switch (this) {
            case CRUDE -> ChatFormatting.GRAY;
            case SOUND -> ChatFormatting.GREEN;
            case FINE -> ChatFormatting.AQUA;
            case EXQUISITE -> ChatFormatting.LIGHT_PURPLE;
            case MASTERWORK -> ChatFormatting.GOLD;
        };
    }

    public static EfficiencyRating fromEfficiency(double efficiency) {
        if (efficiency >= 0.95) return MASTERWORK;
        if (efficiency >= 0.85) return EXQUISITE;
        if (efficiency >= 0.70) return FINE;
        if (efficiency >= 0.50) return SOUND;
        return CRUDE;
    }
}
