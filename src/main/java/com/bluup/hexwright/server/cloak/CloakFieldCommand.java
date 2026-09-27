package com.bluup.hexwright.server.cloak;

import com.bluup.hexwright.server.command.CommandGate;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.UUID;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class CloakFieldCommand {

    private static final int DEFAULT_RADIUS = 12;
    private static final double REMOVE_REACH = 128.0;

    private CloakFieldCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("hexwright")
                .requires(CommandGate::creative)
                .then(Commands.literal("cloak")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("add")
                        .executes(ctx -> add(ctx, DEFAULT_RADIUS, DEFAULT_RADIUS))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(CloakField.MIN_SIZE, CloakField.MAX_RADIUS))
                            .executes(ctx -> {
                                int radius = IntegerArgumentType.getInteger(ctx, "radius");
                                return add(ctx, radius, radius);
                            })
                            .then(Commands.argument("height", IntegerArgumentType.integer(CloakField.MIN_SIZE, CloakField.MAX_HEIGHT))
                                .executes(ctx -> add(ctx, IntegerArgumentType.getInteger(ctx, "radius"),
                                    IntegerArgumentType.getInteger(ctx, "height"))))))
                    .then(Commands.literal("remove").executes(CloakFieldCommand::removeNearest))
                    .then(Commands.literal("clear").executes(CloakFieldCommand::clear))
                    .then(Commands.literal("list").executes(CloakFieldCommand::list)))));
    }

    private static int add(CommandContext<CommandSourceStack> ctx, int radius, int height) {
        CommandSourceStack source = ctx.getSource();
        Vec3 at = source.getPosition();
        Vec3 center = new Vec3(Mth.floor(at.x) + 0.5, Mth.floor(at.y), Mth.floor(at.z) + 0.5);
        UUID owner = source.getEntity() == null ? null : source.getEntity().getUUID();
        CloakFieldStore.add(source.getLevel(), new CloakField(UUID.randomUUID(), center, radius, height, owner));
        source.sendSuccess(() -> Component.literal(
            "Cloak field of radius " + radius + ", height " + height + " placed at " + describe(center) + ".")
            .withStyle(ChatFormatting.AQUA), false);
        return SINGLE_SUCCESS;
    }

    private static int removeNearest(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        Vec3 at = source.getPosition();
        CloakField nearest = null;
        double best = REMOVE_REACH * REMOVE_REACH;
        for (CloakField field : CloakFieldStore.fieldsIn(level)) {
            double distanceSq = field.center().distanceToSqr(at);
            if (distanceSq <= best) {
                best = distanceSq;
                nearest = field;
            }
        }
        if (nearest == null) {
            source.sendFailure(Component.literal("No cloak field within " + (int) REMOVE_REACH + " blocks."));
            return 0;
        }
        CloakFieldStore.remove(level, nearest.id());
        Vec3 removed = nearest.center();
        source.sendSuccess(() -> Component.literal("Removed the cloak field at " + describe(removed) + ".")
            .withStyle(ChatFormatting.GRAY), false);
        return SINGLE_SUCCESS;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        int removed = CloakFieldStore.clear(source.getLevel());
        source.sendSuccess(() -> Component.literal("Removed " + removed + " cloak field(s) from this dimension.")
            .withStyle(ChatFormatting.GRAY), false);
        return removed;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Collection<CloakField> fields = CloakFieldStore.fieldsIn(source.getLevel());
        if (fields.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No cloak fields in this dimension.")
                .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        for (CloakField field : fields) {
            source.sendSuccess(() -> Component.literal(
                "radius " + (int) field.radius() + ", height " + (int) field.height() + " at " + describe(field.center()))
                .withStyle(ChatFormatting.GRAY), false);
        }
        return fields.size();
    }

    private static String describe(Vec3 center) {
        return Mth.floor(center.x) + " " + Mth.floor(center.y) + " " + Mth.floor(center.z);
    }
}
