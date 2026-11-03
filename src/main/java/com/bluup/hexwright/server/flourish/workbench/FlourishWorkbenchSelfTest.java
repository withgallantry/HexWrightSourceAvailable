package com.bluup.hexwright.server.flourish.workbench;

import com.bluup.hexwright.common.flourish.Flourish;
import com.bluup.hexwright.common.flourish.FlourishDesign;
import com.bluup.hexwright.common.flourish.FlourishFigure;
import com.bluup.hexwright.common.flourish.FlourishMotif;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.command.CommandGate;
import com.bluup.hexwright.server.flourish.FlourishBlankItem;
import com.bluup.hexwright.server.flourish.FlourishItem;
import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.menu.UiTemplates;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class FlourishWorkbenchSelfTest {

    private static final FlourishDesign FULL = new FlourishDesign(List.of(
        new FlourishDesign.Layer(FlourishMotif.GEARS, FlourishMotif.Placement.STANDING, 4, 3, 2),
        new FlourishDesign.Layer(FlourishMotif.SHARD, FlourishMotif.Placement.SCATTER, 0, 0, 1),
        new FlourishDesign.Layer(FlourishMotif.LIGHTNING, FlourishMotif.Placement.SKYFALL, 2, 10, 3),
        new FlourishDesign.Layer(FlourishMotif.FLAME, FlourishMotif.Placement.FOOT, 1, 5, 0)),
        null, FlourishDesign.Motes.SPIRAL);

    private static final FlourishFigure FULL_FIGURE = new FlourishFigure(List.of(
        new FlourishFigure.Ring(FlourishFigure.Shape.STAR, 12, 3, 10, 3, -3, 3, 3),
        new FlourishFigure.Ring(FlourishFigure.Shape.GLYPHS, 3, 1, 1, 0, 3, 0, 0),
        new FlourishFigure.Ring(FlourishFigure.Shape.SPIRAL, 7, 2, 5, 2, 1, 1, 1),
        new FlourishFigure.Ring(FlourishFigure.Shape.GRID, 9, 0, 8, 1, -1, 2, 2),
        new FlourishFigure.Ring(FlourishFigure.Shape.TEETH, 4, 1, 6, 0, 2, 0, 3),
        new FlourishFigure.Ring(FlourishFigure.Shape.LOOPS, 6, 0, 9, 1, 0, 0, 0)),
        FlourishFigure.Entrance.CONVERGE, FlourishFigure.Exit.BURST, FlourishFigure.TEMPOS - 1);

    private static final FlourishDesign FIGURES = new FlourishDesign(List.of(
        new FlourishDesign.Layer(FlourishMotif.FIGURE, FlourishMotif.Placement.GROUND, 3, 0, 0, FULL_FIGURE),
        new FlourishDesign.Layer(FlourishMotif.FIGURE, FlourishMotif.Placement.ABOVE, 1, 2, 1, FlourishFigure.DEFAULT),
        new FlourishDesign.Layer(FlourishMotif.FIGURE, FlourishMotif.Placement.SCATTER, 0, 4, 2,
            FlourishFigure.of(FlourishFigure.Shape.ROSE))),
        null, FlourishDesign.Motes.NONE);

    private static final FlourishDesign EPIC = new FlourishDesign(
        List.of(FlourishDesign.Layer.of(FlourishMotif.DARK_SUN)), Flourish.HEXAGRAM, FlourishDesign.Motes.NONE);

    private final CommandSourceStack source;
    private final ServerLevel level;
    private int failures;

    private FlourishWorkbenchSelfTest(CommandSourceStack source) {
        this.source = source;
        this.level = source.getServer().overworld();
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("hexwright")
                .requires(CommandGate::creative)
                .then(Commands.literal("flourish")
                    .then(Commands.literal("selftest").executes(ctx -> run(ctx.getSource()))))));
    }

    private static int run(CommandSourceStack source) {
        FlourishWorkbenchSelfTest test = new FlourishWorkbenchSelfTest(source);
        long started = System.nanoTime();
        test.format();
        test.window();
        test.inscribing();
        long millis = (System.nanoTime() - started) / 1_000_000L;
        int failed = test.failures;
        source.sendSuccess(() -> Component.literal("[flourish selftest] "
                + (failed == 0 ? "all checks passed" : failed + " checks FAILED") + " in " + millis + " ms")
            .withStyle(failed == 0 ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        return failed == 0 ? 1 : 0;
    }

    private void format() {
        for (FlourishDesign design : List.of(FlourishDesign.DEFAULT, FULL, EPIC, FIGURES)) {
            check("design tag", design.equals(FlourishDesign.load(design.save())), design.save().toString());
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            design.write(buf);
            check("design wire", design.equals(FlourishDesign.read(buf)) && buf.readableBytes() == 0,
                buf.writerIndex() + " bytes");
        }
        List<String> lost = new ArrayList<>();
        for (FlourishMotif motif : FlourishMotif.values()) {
            FlourishDesign single = new FlourishDesign(List.of(FlourishDesign.Layer.of(motif)), null, FlourishDesign.Motes.NONE);
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            single.write(buf);
            if (!single.equals(FlourishDesign.load(single.save())) || !single.equals(FlourishDesign.read(buf))) {
                lost.add(motif.id());
            }
        }
        check("every motif", lost.isEmpty(), FlourishMotif.values().length + " motifs" + (lost.isEmpty() ? "" : ", lost " + lost));
        check("known mask", Flourish.values().length <= Long.SIZE, Flourish.values().length + " flourishes");
        FlourishDesign.Layer clamped = new FlourishDesign.Layer(FlourishMotif.SEAL, FlourishMotif.Placement.CENTRE, 9, 99, 5);
        check("design clamps", clamped.placement() == FlourishMotif.Placement.GROUND && clamped.size() == FlourishDesign.SIZES - 1
            && clamped.delay() == FlourishDesign.MAX_DELAY && clamped.shade() == 1, clamped.toString());
        check("design rarity", FULL.rarity() == Rarity.EPIC && FlourishDesign.DEFAULT.rarity() == Rarity.UNCOMMON
                && FIGURES.rarity() == Rarity.UNCOMMON && FIGURES.sources().isEmpty(),
            FULL.rarity() + ", " + FlourishDesign.DEFAULT.rarity() + ", " + FIGURES.rarity());

        FlourishFigure.Ring ring = new FlourishFigure.Ring(FlourishFigure.Shape.STAR, 3, 9, 0, 7, -9, 5, -1);
        check("ring clamps", ring.count() == 5 && ring.form() == 0 && ring.radius() == 1 && ring.weight() == FlourishFigure.WEIGHTS - 1
            && ring.spin() == -FlourishFigure.MAX_SPIN && ring.turn() == 1 && ring.hue() == FlourishFigure.HUES - 1, ring.toString());
        FlourishDesign.Layer found = new FlourishDesign.Layer(FlourishMotif.SEAL, FlourishMotif.Placement.GROUND, 2, 0, 0,
            FlourishFigure.DEFAULT);
        FlourishDesign.Layer bare = new FlourishDesign.Layer(FlourishMotif.FIGURE, FlourishMotif.Placement.SCATTER, 2, 0, 0, null);
        check("figure only on a figure", found.figure() == null && FlourishFigure.DEFAULT.equals(bare.figure())
                && bare.placement() == FlourishMotif.Placement.SCATTER,
            found + " / " + bare);
    }

    private void window() {
        try {
            boolean fromProject = UiTemplates.loadOptional(FlourishWorkbenchWindow.UI_PROJECT) != null;
            WidgetGroup root = FlourishWorkbenchWindow.build(newWorkbench(), FakePlayer.get(level));
            List<String> missing = new ArrayList<>();
            for (Map.Entry<Class<? extends Widget>, Integer> piece : Map.<Class<? extends Widget>, Integer>ofEntries(
                Map.entry(FlourishPreviewWidget.class, 1), Map.entry(MotifPickerWidget.class, 1),
                Map.entry(LayerTabWidget.class, FlourishDesign.MAX_LAYERS),
                Map.entry(WorkbenchOptionWidget.class, WorkbenchOption.values().length),
                Map.entry(WorkbenchActionWidget.class, WorkbenchAction.values().length),
                Map.entry(WorkbenchStatusWidget.class, 1), Map.entry(WorkbenchTooltipsWidget.class, 1),
                Map.entry(WorkbenchCaptionWidget.class, WorkbenchCaption.values().length),
                Map.entry(ShapePickerWidget.class, 1), Map.entry(RingTabWidget.class, FlourishFigure.MAX_RINGS),
                Map.entry(WorkbenchPageGroup.class, WorkbenchPage.values().length)).entrySet()) {
                int found = root.getWidgetsByType(piece.getKey()).size();
                if (found < piece.getValue()) {
                    missing.add(piece.getKey().getSimpleName() + " (" + found + " of " + piece.getValue() + ")");
                }
            }
            int slots = root.getWidgetsByType(SlotWidget.class).size();
            if (slots != 37) {
                missing.add("slots (" + slots + " of 37)");
            }
            if (!(root.widgets.get(0) instanceof WorkbenchSession)) {
                missing.add("the session, first");
            }
            check("workbench window", missing.isEmpty(), missing.isEmpty()
                ? "built %d widgets at %dx%d from %s".formatted(count(root), root.getSize().width, root.getSize().height,
                    fromProject ? FlourishWorkbenchWindow.UI_PROJECT + ".ui" : "the built-in layout")
                : "missing " + String.join(", ", missing));
        } catch (Throwable t) {
            check("workbench window", false, t.toString());
        }
    }

    private void inscribing() {
        FakePlayer player = FakePlayer.get(level);
        FlourishWorkbenchBlockEntity workbench = newWorkbench();
        check("needs a blank", workbench.inscribe(player, FlourishDesign.DEFAULT) != null, "refused with no blank");

        workbench.setItem(FlourishWorkbenchBlockEntity.SLOT, new ItemStack(HexwrightItems.FLOURISH_BLANK));
        check("uncommon is free", workbench.inscribe(player, FlourishDesign.DEFAULT) == null
                && FlourishDesign.DEFAULT.equals(FlourishBlankItem.design(workbench.blank()))
                && FlourishItem.isWearable(workbench.blank()),
            String.valueOf(workbench.blank().getTag()));

        check("figures are free", workbench.inscribe(player, FIGURES) == null
                && FIGURES.equals(FlourishBlankItem.design(workbench.blank())),
            String.valueOf(workbench.blank().getTag()));
        workbench.inscribe(player, FlourishDesign.DEFAULT);

        check("epic is borrowed", workbench.inscribe(player, EPIC) != null
                && FlourishDesign.DEFAULT.equals(FlourishBlankItem.design(workbench.blank())),
            "refused without an Eclipse found, and the blank kept its design");

        ItemStack eclipse = new ItemStack(HexwrightItems.FLOURISHES.get(Flourish.ECLIPSE));
        player.getInventory().add(eclipse);
        try {
            check("carried is known", workbench.inscribe(player, EPIC) == null
                    && EPIC.equals(FlourishBlankItem.design(workbench.blank()))
                    && workbench.blank().getRarity() == Rarity.EPIC,
                String.valueOf(workbench.blank().getTag()));
        } finally {
            player.getInventory().clearContent();
        }
    }

    private static FlourishWorkbenchBlockEntity newWorkbench() {
        return new FlourishWorkbenchBlockEntity(BlockPos.ZERO, HexwrightBlocks.FLOURISH_WORKBENCH_BLOCK.defaultBlockState());
    }

    private static int count(Widget widget) {
        int total = 1;
        if (widget instanceof WidgetGroup group) {
            for (Widget child : group.widgets) {
                total += count(child);
            }
        }
        return total;
    }

    private void check(String test, boolean ok, String detail) {
        if (ok) {
            source.sendSuccess(() -> Component.literal("[" + test + "] pass - " + detail), false);
        } else {
            failures++;
            source.sendFailure(Component.literal("[" + test + "] FAIL - " + detail));
        }
    }
}
