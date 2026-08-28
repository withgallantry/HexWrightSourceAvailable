package com.bluup.hexwright.server.harmonic;

import at.petrak.hexcasting.api.casting.iota.BooleanIota;
import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.Vec3Iota;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import ram.talia.moreiotas.api.casting.iota.StringIota;

import java.util.ArrayList;
import java.util.List;

public final class TransducerTriggers {

    public static final String ANY = "*";

    public static final String COMPARATOR = "#comparator";

    private TransducerTriggers() {
    }

    public static List<String> available(BlockState state) {
        List<String> triggers = new ArrayList<>();
        triggers.add(ANY);
        if (state.hasAnalogOutputSignal()) {
            triggers.add(COMPARATOR);
        }
        state.getProperties().stream().map(Property::getName).sorted().forEach(triggers::add);
        return triggers;
    }

    public static boolean applies(String trigger, BlockState state) {
        if (ANY.equals(trigger)) {
            return true;
        }
        if (COMPARATOR.equals(trigger)) {
            return state.hasAnalogOutputSignal();
        }
        return property(trigger, state) != null;
    }

    public static @Nullable String read(String trigger, Level level, BlockPos pos, BlockState state) {
        if (ANY.equals(trigger)) {
            return Integer.toString(Block.getId(state));
        }
        if (COMPARATOR.equals(trigger)) {
            return state.hasAnalogOutputSignal()
                ? Integer.toString(state.getAnalogOutputSignal(level, pos))
                : null;
        }
        Property<?> property = property(trigger, state);
        return property == null ? null : valueString(state, property);
    }

    public static Iota payload(String trigger, String reading, BlockState state, BlockPos pos) {
        if (ANY.equals(trigger)) {
            return new Vec3Iota(Vec3.atCenterOf(pos));
        }
        if (COMPARATOR.equals(trigger)) {
            return new DoubleIota(parseNumber(reading));
        }
        Property<?> property = property(trigger, state);
        if (property instanceof BooleanProperty) {
            return new BooleanIota(Boolean.parseBoolean(reading));
        }
        if (property instanceof IntegerProperty) {
            return new DoubleIota(parseNumber(reading));
        }
        return StringIota.makeUnchecked(reading);
    }

    public static String label(String trigger) {
        if (ANY.equals(trigger)) {
            return "gui.hexwright.harmonic_transducer.trigger.any";
        }
        if (COMPARATOR.equals(trigger)) {
            return "gui.hexwright.harmonic_transducer.trigger.comparator";
        }
        return titleCase(trigger);
    }

    private static @Nullable Property<?> property(String name, BlockState state) {
        return state.getBlock().getStateDefinition().getProperty(name);
    }

    private static <T extends Comparable<T>> String valueString(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static double parseNumber(String reading) {
        try {
            return Double.parseDouble(reading);
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private static String titleCase(String name) {
        StringBuilder out = new StringBuilder(name.length());
        boolean startOfWord = true;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '_') {
                out.append(' ');
                startOfWord = true;
                continue;
            }
            out.append(startOfWord ? Character.toUpperCase(c) : c);
            startOfWord = false;
        }
        return out.toString();
    }
}
