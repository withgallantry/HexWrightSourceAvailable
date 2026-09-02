package com.bluup.hexwright.client.render.emissive;

import com.mojang.serialization.Codec;
import net.minecraft.client.OptionInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.OptionEnum;

import java.util.List;

public final class EmissiveBloomOptions {

    private EmissiveBloomOptions() {
    }

    public enum LampGlow implements OptionEnum {
        OFF(0, "options.hexwright.lamp_glow.off", 0),
        NEAR(1, "options.hexwright.lamp_glow.near", 24),
        NORMAL(2, "options.hexwright.lamp_glow.normal", 48),
        FAR(3, "options.hexwright.lamp_glow.far", 80);

        private static final LampGlow[] BY_ID = values();

        private final int id;
        private final String key;
        private final int distance;

        LampGlow(int id, String key, int distance) {
            this.id = id;
            this.key = key;
            this.distance = distance;
        }

        @Override
        public int getId() {
            return id;
        }

        @Override
        public String getKey() {
            return key;
        }

        public int distance() {
            return distance;
        }

        static LampGlow byId(int id) {
            return BY_ID[Math.floorMod(id, BY_ID.length)];
        }

        static LampGlow current(EmissiveBloomConfig config) {
            if (!config.blockGlow) {
                return OFF;
            }
            LampGlow closest = NORMAL;
            int best = Integer.MAX_VALUE;
            for (LampGlow step : BY_ID) {
                if (step == OFF) {
                    continue;
                }
                int gap = Math.abs(step.distance - config.blockGlowDistance);
                if (gap < best) {
                    best = gap;
                    closest = step;
                }
            }
            return closest;
        }
    }

    public static OptionInstance<Boolean> bloomOption() {
        return OptionInstance.createBoolean(
            "options.hexwright.bloom",
            OptionInstance.cachedConstantTooltip(
                Component.translatable("options.hexwright.bloom.tooltip")),
            EmissiveBloomConfigManager.get().enabled,
            EmissiveBloomOptions::setBloom);
    }

    public static OptionInstance<LampGlow> lampGlowOption() {
        return new OptionInstance<>(
            "options.hexwright.lamp_glow",
            OptionInstance.cachedConstantTooltip(
                Component.translatable("options.hexwright.lamp_glow.tooltip")),
            OptionInstance.forOptionEnum(),
            new OptionInstance.Enum<>(List.of(LampGlow.values()),
                Codec.INT.xmap(LampGlow::byId, LampGlow::getId)),
            LampGlow.current(EmissiveBloomConfigManager.get()),
            EmissiveBloomOptions::setLampGlow);
    }

    private static void setBloom(boolean enabled) {
        EmissiveBloomConfig config = EmissiveBloomConfigManager.get();
        if (config.enabled != enabled) {
            config.enabled = enabled;
            EmissiveBloomConfigManager.save();
        }
    }

    private static void setLampGlow(LampGlow step) {
        EmissiveBloomConfig config = EmissiveBloomConfigManager.get();
        boolean enabled = step != LampGlow.OFF;
        int distance = enabled ? step.distance() : config.blockGlowDistance;
        if (config.blockGlow == enabled && config.blockGlowDistance == distance) {
            return;
        }
        config.blockGlow = enabled;
        config.blockGlowDistance = EmissiveBloomConfigManager.clampBlockGlowDistance(distance);
        EmissiveBloomConfigManager.save();
    }
}
