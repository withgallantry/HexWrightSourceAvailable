package com.bluup.hexwright.server.inversion;

import com.bluup.hexwright.server.boss.arbiter.ArbiterEntity;
import com.bluup.hexwright.server.command.CommandGate;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class InversionCommand {

    private InversionCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("hexwright")
                .requires(CommandGate::creative)
                .then(Commands.literal("inversion")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("begin")
                        .executes(ctx -> begin(ctx, false))
                        .then(Commands.literal("force").executes(ctx -> begin(ctx, true))))
                    .then(Commands.literal("end").executes(InversionCommand::endNearest))
                    .then(Commands.literal("list").executes(InversionCommand::list)))));
    }

    private static int begin(CommandContext<CommandSourceStack> ctx, boolean force) {
        CommandSourceStack source = ctx.getSource();
        Vec3 at = source.getPosition();
        int x = Mth.floor(at.x);
        int z = Mth.floor(at.z);
        boolean started = InversionWorks.begin(source.getLevel(), x, z, force,
            message -> source.sendSystemMessage(message.copy().withStyle(ChatFormatting.LIGHT_PURPLE)));
        if (!started) {
            source.sendFailure(Component.literal("Another square is already being looked over; try again shortly."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Looking over the ground round " + x + " " + z
            + (force ? ", taking it whatever stands there." : "...")).withStyle(ChatFormatting.GRAY), true);
        return SINGLE_SUCCESS;
    }

    private static int endNearest(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        Vec3 at = source.getPosition();
        Inversion nearest = null;
        double best = Double.MAX_VALUE;
        for (Inversion inversion : InversionStore.in(level)) {
            double distanceSq = Mth.square(inversion.x() - at.x) + Mth.square(inversion.z() - at.z);
            if (distanceSq < best) {
                best = distanceSq;
                nearest = inversion;
            }
        }
        if (nearest == null) {
            source.sendFailure(Component.literal("There is no Inversion in this dimension."));
            return 0;
        }
        AABB square = new AABB(nearest.minX(), level.getMinBuildHeight(), nearest.minZ(),
            nearest.minX() + Inversion.SIZE, level.getMaxBuildHeight(), nearest.minZ() + Inversion.SIZE);
        for (ArbiterEntity arbiter : level.getEntitiesOfClass(ArbiterEntity.class, square)) {
            arbiter.discard();
        }
        InversionWorks.recede(level, nearest.id());
        Inversion ended = nearest;
        source.sendSuccess(() -> Component.literal("The Inversion at " + ended.x() + " " + ended.z() + " is receding.")
            .withStyle(ChatFormatting.GRAY), true);
        return SINGLE_SUCCESS;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        Collection<Inversion> inversions = InversionStore.in(level);
        String surveying = InversionWorks.surveying();
        if (surveying != null) {
            source.sendSuccess(() -> Component.literal(surveying).withStyle(ChatFormatting.GRAY), false);
        }
        if (inversions.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No Inversion in this dimension.").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        long now = level.getGameTime();
        for (Inversion inversion : inversions) {
            source.sendSuccess(() -> Component.literal("at " + inversion.x() + " " + inversion.z() + ", "
                + inversion.phase().name().toLowerCase() + ", front " + (int) inversion.front(now) + " blocks out")
                .withStyle(ChatFormatting.GRAY), false);
        }
        return inversions.size();
    }
}
