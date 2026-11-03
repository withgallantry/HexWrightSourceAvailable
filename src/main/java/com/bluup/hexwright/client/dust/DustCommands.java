package com.bluup.hexwright.client.dust;

import com.bluup.hexwright.server.dust.DustTuning;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.bluup.hexwright.client.command.ClientCommandGate;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

final class DustCommands {

    private static final Class<?>[] KNOB_OWNERS = {DustConfig.class, DustTuning.class};

    private DustCommands() {
    }

    static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("hexwrightdust")
                .requires(ClientCommandGate::creative)
                .then(ClientCommandManager.literal("quality")
                    .then(ClientCommandManager.argument("level", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (DustConfig.Quality quality : DustConfig.Quality.values()) {
                                builder.suggest(quality.name().toLowerCase(Locale.ROOT));
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> quality(ctx.getSource(), StringArgumentType.getString(ctx, "level")))))
                .then(ClientCommandManager.literal("set")
                    .then(ClientCommandManager.argument("knob", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (Class<?> owner : KNOB_OWNERS) {
                                for (Field field : owner.getFields()) {
                                    if (isKnob(field)) {
                                        builder.suggest(field.getName());
                                    }
                                }
                            }
                            return builder.buildFuture();
                        })
                        .then(ClientCommandManager.argument("value", FloatArgumentType.floatArg())
                            .executes(ctx -> set(ctx.getSource(), StringArgumentType.getString(ctx, "knob"),
                                FloatArgumentType.getFloat(ctx, "value"))))))
                .then(ClientCommandManager.literal("get")
                    .then(ClientCommandManager.argument("knob", StringArgumentType.word())
                        .executes(ctx -> get(ctx.getSource(), StringArgumentType.getString(ctx, "knob")))))
                .executes(ctx -> status(ctx.getSource()))));
    }

    private static int status(FabricClientCommandSource source) {
        for (String line : DustClient.describe()) {
            feedback(source, line);
        }
        return SINGLE_SUCCESS;
    }

    private static int quality(FabricClientCommandSource source, String level) {
        try {
            DustConfig.quality = DustConfig.Quality.valueOf(level.toUpperCase(Locale.ROOT));
            feedback(source, "Dust quality " + DustConfig.quality + ": " + DustConfig.quality.population + " grains per caster.");
        } catch (IllegalArgumentException e) {
            feedback(source, "No quality '" + level + "' - low, medium, high or ultra.");
        }
        return SINGLE_SUCCESS;
    }

    private static int set(FabricClientCommandSource source, String name, float value) {
        Field field = knob(name);
        if (field == null) {
            feedback(source, "No dust knob '" + name + "'.");
            return SINGLE_SUCCESS;
        }
        try {
            if (field.getType() == int.class) {
                field.setInt(null, Math.round(value));
            } else {
                field.setFloat(null, value);
            }
            feedback(source, name + " = " + field.get(null));
        } catch (IllegalAccessException e) {
            feedback(source, "Could not set " + name + ".");
        }
        return SINGLE_SUCCESS;
    }

    private static int get(FabricClientCommandSource source, String name) {
        Field field = knob(name);
        try {
            feedback(source, field == null ? "No dust knob '" + name + "'." : name + " = " + field.get(null));
        } catch (IllegalAccessException e) {
            feedback(source, "Could not read " + name + ".");
        }
        return SINGLE_SUCCESS;
    }

    private static Field knob(String name) {
        for (Class<?> owner : KNOB_OWNERS) {
            try {
                Field field = owner.getField(name);
                if (isKnob(field)) {
                    return field;
                }
            } catch (NoSuchFieldException e) {
            }
        }
        return null;
    }

    private static boolean isKnob(Field field) {
        int modifiers = field.getModifiers();
        return Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers)
            && (field.getType() == float.class || field.getType() == int.class);
    }

    private static void feedback(FabricClientCommandSource source, String message) {
        source.sendFeedback(Component.literal(message).withStyle(ChatFormatting.GOLD));
    }
}
