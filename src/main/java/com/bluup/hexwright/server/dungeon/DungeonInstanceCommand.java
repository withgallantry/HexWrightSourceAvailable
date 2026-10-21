package com.bluup.hexwright.server.dungeon;

import com.bluup.hexwright.server.command.CommandGate;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class DungeonInstanceCommand {

    private DungeonInstanceCommand() {
    }

    static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("hexwright")
                .requires(CommandGate::creative)
                .then(Commands.literal("dungeon")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("instance")
                        .then(Commands.literal("open")
                            .executes(ctx -> open(ctx, DungeonInstance.Kind.DUNGEON))
                            .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                    Arrays.stream(DungeonInstance.Kind.values())
                                        .map(kind -> kind.name().toLowerCase(Locale.ROOT)), builder))
                                .executes(DungeonInstanceCommand::openKind)))
                        .then(Commands.literal("list").executes(DungeonInstanceCommand::list))
                        .then(Commands.literal("enter")
                            .then(Commands.argument("id", IntegerArgumentType.integer(0))
                                .executes(DungeonInstanceCommand::enter)))
                        .then(Commands.literal("close")
                            .then(Commands.argument("id", IntegerArgumentType.integer(0))
                                .executes(DungeonInstanceCommand::close)))))));
    }

    private static int openKind(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "kind");
        for (DungeonInstance.Kind kind : DungeonInstance.Kind.values()) {
            if (kind.name().equalsIgnoreCase(name)) {
                return open(ctx, kind);
            }
        }
        ctx.getSource().sendFailure(Component.literal("No such instance kind: " + name));
        return 0;
    }

    private static int open(CommandContext<CommandSourceStack> ctx, DungeonInstance.Kind kind) {
        CommandSourceStack source = ctx.getSource();
        UUID opener = source.getEntity() == null ? Util.NIL_UUID : source.getEntity().getUUID();
        boolean opened = DungeonInstances.open(source.getLevel(), BlockPos.containing(source.getPosition()),
            Direction.fromYRot(source.getRotation().y), opener, kind,
            message -> source.sendSuccess(() -> message, false));
        return opened ? SINGLE_SUCCESS : 0;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        long now = server.overworld().getGameTime();
        DungeonInstanceRegistry registry = DungeonInstanceRegistry.get(server);
        if (registry.size() == 0) {
            source.sendSuccess(() -> Component.literal("No dungeon instances."), false);
            return SINGLE_SUCCESS;
        }
        for (DungeonInstance instance : registry.all()) {
            String rift = instance.riftHolds(now)
                ? "rift holds " + DungeonInstances.duration(instance.riftClosesAt() - now)
                : instance.state() == DungeonInstance.State.OPEN ? "rift closed" : "";
            BlockPos start = instance.safeRoom().box().getCenter();
            source.sendSuccess(() -> Component.literal("#" + instance.id() + " " + instance.kind() + " " + instance.state()
                + " in cell " + instance.cell() + " (safe room " + start.toShortString() + "), opened from "
                + instance.entrance().dimension().location() + "; " + rift + "; members "
                + instance.members().size() + ", departed " + instance.departed().size()
                + ", offline " + instance.offlineSince().size()).withStyle(ChatFormatting.AQUA), false);
        }
        return SINGLE_SUCCESS;
    }

    private static int enter(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        DungeonInstance instance = standing(source, IntegerArgumentType.getInteger(ctx, "id"));
        ServerLevel dungeons = DungeonDimension.level(source.getServer());
        if (instance == null || dungeons == null) {
            return 0;
        }
        DungeonInstanceRegistry registry = DungeonInstanceRegistry.get(source.getServer());
        if (instance.departed().remove(player.getUUID())) {
            registry.setDirty();
        }
        DungeonInstances.admit(dungeons, registry, instance, player);
        return SINGLE_SUCCESS;
    }

    private static int close(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        int id = IntegerArgumentType.getInteger(ctx, "id");
        DungeonInstanceRegistry registry = DungeonInstanceRegistry.get(server);
        DungeonInstance instance = registry.byId(id);
        ServerLevel dungeons = DungeonDimension.level(server);
        if (instance == null || dungeons == null || instance.state() == DungeonInstance.State.CLEARING) {
            source.sendFailure(Component.literal("No dungeon instance #" + id + " to close."));
            return 0;
        }
        DungeonInstances.tearDown(server, dungeons, registry, instance, "closed by " + source.getTextName());
        source.sendSuccess(() -> Component.literal("Dungeon instance #" + id + " closed; its cell is being cleared.")
            .withStyle(ChatFormatting.AQUA), true);
        return SINGLE_SUCCESS;
    }

    private static @Nullable DungeonInstance standing(CommandSourceStack source, int id) {
        DungeonInstance instance = DungeonInstanceRegistry.get(source.getServer()).byId(id);
        if (instance == null || instance.state() != DungeonInstance.State.OPEN) {
            source.sendFailure(Component.literal("No standing dungeon instance #" + id + "."));
            return null;
        }
        return instance;
    }
}
