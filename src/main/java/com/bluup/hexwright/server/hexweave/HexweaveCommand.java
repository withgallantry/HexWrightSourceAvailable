package com.bluup.hexwright.server.hexweave;

import at.petrak.hexcasting.api.addldata.ADIotaHolder;
import at.petrak.hexcasting.api.casting.iota.EntityIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.casting.math.HexCoord;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.server.command.CommandGate;
import com.bluup.hexwright.server.hexpatterns.StoredHex;
import com.bluup.hexwright.server.pocketcaster.PocketCasterCastEnv;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public final class HexweaveCommand {

    static final String DEV_KEY = "hexwright:dev_hexweave";

    private static final int SHOWN_RUNS = 12;

    private HexweaveCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("hexwright")
                .requires(CommandGate::creative)
                .then(Commands.literal("weave")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("run")
                        .then(Commands.argument("graph", StringArgumentType.greedyString())
                            .executes(HexweaveCommand::run)))
                    .then(Commands.literal("save")
                        .then(Commands.argument("graph", StringArgumentType.greedyString())
                            .executes(HexweaveCommand::save)))
                    .then(Commands.literal("runstored").executes(HexweaveCommand::runStored))
                    .then(Commands.literal("bind")
                        .then(Commands.argument("graph", StringArgumentType.greedyString())
                            .executes(HexweaveCommand::bind)))
                    .then(Commands.literal("unbind").executes(HexweaveCommand::unbind))
                    .then(Commands.literal("selftest")
                        .executes(ctx -> HexweaveSelfTest.run(ctx.getSource()))
                        .then(Commands.literal("persist").executes(ctx -> HexweaveSelfTest.persist(ctx.getSource())))
                        .then(Commands.literal("restored").executes(ctx -> HexweaveSelfTest.restored(ctx.getSource())))))));
    }

    private static int run(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        Hexweave weave;
        try {
            weave = fromHotbar(player, StringArgumentType.getString(ctx, "graph"));
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
        HexweaveCasting.Activation activation = HexweaveCasting.activate(weave,
            weave.starts(WeaveTrigger.Kind.ON_USE), player.serverLevel(), () -> new PocketCasterCastEnv(player, InteractionHand.MAIN_HAND),
            new EntityIota(player));
        report(source, activation);
        return SINGLE_SUCCESS;
    }

    static Hexweave fromHotbar(ServerPlayer player, String graph) {
        Set<Integer> ids = new TreeSet<>();
        Set<Integer> linkedInto = new HashSet<>();
        List<WeaveLink> links = new ArrayList<>();
        for (String chain : graph.split(",")) {
            Integer previous = null;
            for (String step : chain.split(">", -1)) {
                int id = slot(step.trim());
                ids.add(id);
                if (previous != null) {
                    links.add(new WeaveLink(previous, id));
                    linkedInto.add(id);
                }
                previous = id;
            }
        }

        List<WeaveThread> threads = new ArrayList<>();
        List<Integer> started = new ArrayList<>();
        for (int id : ids) {
            threads.add(new WeaveThread(id, entriesIn(player, id)));
            if (!linkedInto.contains(id)) {
                started.add(id);
            }
        }
        List<WeaveTrigger> triggers = started.isEmpty() ? List.of()
            : List.of(new WeaveTrigger(1, WeaveTrigger.Kind.ON_USE, started));
        return Hexweave.of(threads, links, triggers);
    }

    private static int slot(String text) {
        int slot;
        try {
            slot = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + text + "' is not a hotbar slot. Write the graph like 1>2,1>3.");
        }
        if (slot < 1 || slot > 9) {
            throw new IllegalArgumentException("Hotbar slots run from 1 to 9, not " + slot + ".");
        }
        return slot;
    }

    private static List<ThreadEntry> entriesIn(ServerPlayer player, int slot) {
        ItemStack stack = player.getInventory().getItem(slot - 1);
        ADIotaHolder holder = IXplatAbstractions.INSTANCE.findDataHolder(stack);
        List<Iota> hex = StoredHex.decode(holder == null ? null : holder.readIota(player.serverLevel()));
        if (hex == null) {
            throw new IllegalArgumentException("Hotbar slot " + slot + " holds no hex.");
        }
        List<ThreadEntry> entries = new ArrayList<>(hex.size());
        int nextColumn = 0;
        for (Iota iota : hex) {
            if (!(iota instanceof PatternIota patternIota)) {
                entries.add(new EmbeddedIota(IotaType.serialize(iota), new HexCoord(nextColumn, 0)));
                nextColumn += 2;
                continue;
            }
            HexPattern pattern = patternIota.getPattern();
            List<HexCoord> points = pattern.positions();
            int left = points.stream().mapToInt(HexCoord::getQ).min().orElse(0);
            int right = points.stream().mapToInt(HexCoord::getQ).max().orElse(0);
            int shift = nextColumn - left;
            entries.add(new PlacedPattern(pattern, new HexCoord(shift, 0)));
            nextColumn = shift + right + 2;
        }
        return entries;
    }

    private static int save(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ItemStack held = player.getOffhandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.literal("Hold something in your off hand to store the weave on."));
            return 0;
        }
        Hexweave weave;
        try {
            weave = fromHotbar(player, StringArgumentType.getString(ctx, "graph"));
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
        store(held, weave);
        source.sendSuccess(() -> Component.literal("Stored a weave of " + weave.threads().size() + " Threads and "
            + weave.links().size() + " links on " + held.getHoverName().getString() + ".")
            .withStyle(ChatFormatting.AQUA), false);
        return SINGLE_SUCCESS;
    }

    private static int runStored(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        Hexweave weave;
        try {
            weave = stored(player.getOffhandItem());
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
        HexweaveCasting.Activation activation = HexweaveCasting.activate(weave,
            weave.starts(WeaveTrigger.Kind.ON_USE), player.serverLevel(), () -> new PocketCasterCastEnv(player, InteractionHand.MAIN_HAND),
            new EntityIota(player));
        report(source, activation);
        return SINGLE_SUCCESS;
    }

    private static int bind(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ItemStack orb = heldOrb(player);
        if (orb == null) {
            source.sendFailure(Component.literal("Hold a Woven Orb to bind a weave into it."));
            return 0;
        }
        Hexweave weave;
        try {
            weave = fromHotbar(player, StringArgumentType.getString(ctx, "graph"));
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
        WovenOrbItem.bind(orb, weave);
        source.sendSuccess(() -> Component.literal("Bound a weave of " + weave.threads().size() + " Threads and "
            + weave.links().size() + " links into the Woven Orb.").withStyle(ChatFormatting.AQUA), false);
        return SINGLE_SUCCESS;
    }

    private static int unbind(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ItemStack orb = heldOrb(source.getPlayerOrException());
        if (orb == null) {
            source.sendFailure(Component.literal("Hold a Woven Orb to clear it."));
            return 0;
        }
        WovenOrbItem.clear(orb);
        source.sendSuccess(() -> Component.literal("The Woven Orb is blank again.").withStyle(ChatFormatting.AQUA),
            false);
        return SINGLE_SUCCESS;
    }

    private static @Nullable ItemStack heldOrb(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof WovenOrbItem) {
                return stack;
            }
        }
        return null;
    }

    static void store(ItemStack stack, Hexweave weave) {
        stack.getOrCreateTag().put(DEV_KEY, HexweaveNbt.write(weave));
    }

    static Hexweave stored(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(DEV_KEY, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Hold an item with a stored weave in your off hand.");
        }
        return HexweaveNbt.read(tag.getCompound(DEV_KEY));
    }

    private static void report(CommandSourceStack source, HexweaveCasting.Activation activation) {
        String ending = switch (activation.stop()) {
            case FINISHED -> "finished";
            case INVOCATION_CAP -> "stopped at the " + WeaveExecutionBudget.MAX_INVOCATIONS + "-invocation cap, "
                + activation.dropped() + " left unrun";
            case OP_BUDGET -> "stopped when the shared op budget ran out, " + activation.dropped() + " left unrun";
        };
        source.sendSuccess(() -> Component.literal("Weave " + ending + ": " + activation.runs().size()
            + " Thread runs, " + activation.opsConsumed() + " ops.").withStyle(ChatFormatting.AQUA), false);

        List<HexweaveCasting.Run> runs = activation.runs();
        for (int i = 0; i < Math.min(runs.size(), SHOWN_RUNS); i++) {
            HexweaveCasting.Run run = runs.get(i);
            String line = "#" + (i + 1) + " Thread " + run.threadId()
                + (run.source() == null ? "" : " (from " + run.source() + ")")
                + ": " + run.outcome().name().toLowerCase(Locale.ROOT)
                + (run.emitted() == null ? "" : ", emitted " + run.emitted().display().getString() + " -> " + run.targets());
            source.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
        }
        if (runs.size() > SHOWN_RUNS) {
            source.sendSuccess(() -> Component.literal("... and " + (runs.size() - SHOWN_RUNS)
                + " more; /hexwright debug weave on logs every one.").withStyle(ChatFormatting.GRAY), false);
        }
    }
}
