package com.bluup.hexwright.server.research;

import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.inspiration.InspirationLevels;
import com.bluup.hexwright.server.inspiration.InspirationState;
import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.menu.UiTemplates;
import com.bluup.hexwright.server.progression.RecipeUnlocks;
import com.lowdragmc.lowdraglib.gui.widget.SlotWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TabletSelfTest {

    public static final int DEFAULT_SAMPLE = 1000;

    private final CommandSourceStack source;
    private final ServerLevel level;
    private int failures;

    private TabletSelfTest(CommandSourceStack source) {
        this.source = source;
        this.level = source.getServer().overworld();
    }

    public static int run(CommandSourceStack source, int sample) {
        TabletSelfTest test = new TabletSelfTest(source);
        long started = System.nanoTime();
        for (TabletDamage damage : TabletDamage.values()) {
            test.tier(damage, sample);
        }
        test.window();
        test.play();

        long millis = (System.nanoTime() - started) / 1_000_000L;
        int failed = test.failures;
        source.sendSuccess(() -> Component.literal("[tablet selftest] "
                + (failed == 0 ? "all checks passed" : failed + " checks FAILED")
                + " in " + millis + " ms")
            .withStyle(failed == 0 ? ChatFormatting.GREEN : ChatFormatting.RED), false);
        return failed == 0 ? 1 : 0;
    }

    private void tier(TabletDamage damage, int sample) {
        int worst = 0;
        int best = 100;
        int deckLow = Integer.MAX_VALUE;
        int deckHigh = 0;
        String firstProblem = null;
        int problems = 0;

        for (int seed = 0; seed < sample; seed++) {
            TabletPuzzle puzzle = TabletPuzzle.generate(seed, damage);
            List<String> found = puzzle.problems();
            if (!found.isEmpty()) {
                problems += found.size();
                if (firstProblem == null) {
                    firstProblem = "seed " + seed + ": " + found.get(0);
                }
            }
            worst = Math.max(worst, puzzle.damagePercent());
            best = Math.min(best, puzzle.damagePercent());
            deckLow = Math.min(deckLow, puzzle.deck().size());
            deckHigh = Math.max(deckHigh, puzzle.deck().size());
        }
        check("tablet " + damage.getSerializedName(), problems == 0, problems == 0
            ? "%d tablets, damage %d-%d%%, deck %d-%d".formatted(sample, best, worst, deckLow, deckHigh)
            : problems + " problems, first " + firstProblem);
    }

    private void window() {
        try {
            boolean fromProject = UiTemplates.loadOptional(ResearchTableWindow.UI_PROJECT) != null;
            WidgetGroup root = ResearchTableWindow.build(newTable(level), FakePlayer.get(level));
            List<String> missing = new ArrayList<>();
            for (Class<? extends Widget> piece : List.of(ResearchSlabWidget.class, ResearchTrayWidget.class,
                ResearchGaugeWidget.class, ResearchUseWidget.class, ResearchCancelWidget.class, ResearchRedealWidget.class,
                ResearchStakeWidget.class)) {
                if (root.getWidgetsByType(piece).isEmpty()) {
                    missing.add(piece.getSimpleName());
                }
            }
            int slots = root.getWidgetsByType(SlotWidget.class).size();
            if (slots != 37) {
                missing.add("slots (" + slots + " of 37)");
            }
            check("tablet window", missing.isEmpty(), missing.isEmpty()
                ? "built %d widgets at %dx%d from %s".formatted(count(root), root.getSize().width, root.getSize().height,
                    fromProject ? ResearchTableWindow.UI_PROJECT + ".ui" : "the built-in layout")
                : "missing " + String.join(", ", missing));
        } catch (Throwable t) {
            check("tablet window", false, t.toString());
        }
    }

    private void play() {
        FakePlayer player = FakePlayer.get(level);
        UUID id = player.getUUID();
        InspirationState inspiration = InspirationState.get(source.getServer());
        int before = inspiration.total(id);
        try {
            ResearchTableBlockEntity table = newTable(level);
            ResearchSession session = table.session();

            table.setItem(ResearchTableBlockEntity.SLOT, new ItemStack(HexwrightItems.RUINED_TABLET));
            check("play insert", RuinedTabletItem.isRolled(table.tablet()) && session.hasTablet()
                    && session.seed() == RuinedTabletItem.seedOf(table.tablet()),
                "a tablet put in is rolled and read: " + session.damage().getSerializedName());
            check("play default stake", session.selected() == session.damage().suggestedLevels(),
                "the stake starts at the tier's suggestion, " + session.selected());
            check("play plain tier", !session.damage().isArtifact(), "a plain tablet rolls a plain tier");

            ItemStack artifact = new ItemStack(HexwrightItems.ARTIFACT_RUINED_TABLET);
            RuinedTabletItem.roll(artifact, level.random);
            check("play artifact tier", RuinedTabletItem.damageOf(artifact).isArtifact()
                    && RuinedTabletItem.damageOf(artifact).breaks() > TabletDamage.WEATHERED.breaks(),
                "an artifact tablet rolls an artifact tier: " + RuinedTabletItem.damageOf(artifact).getSerializedName());
            ResearchSession priced = new ResearchSession();
            priced.setTablet(RuinedTabletItem.seedOf(artifact), RuinedTabletItem.damageOf(artifact));
            priced.select(InspirationLevels.MAX_COMMIT);
            check("play artifact price", priced.stakeLevels() == 2 * priced.selected()
                    && session.stakeLevels() == session.selected(),
                "a full artifact stake costs " + priced.stakeLevels() + " levels for "
                    + ResearchArt.clock(InspirationLevels.clockTicks(priced.selected())) + "; a plain one costs its steps");

            if (!ResearchRewards.anythingLeft(player, session.damage())) {
                check("play reward", true, "skipped - the fake player has already learned every tablet recipe");
                return;
            }
            int learnedBefore = RecipeUnlocks.all(player).size();

            inspiration.set(id, 5);
            table.select(1);
            check("play refused", table.use(player) != null && !session.isRunning(),
                "half a level cannot stake one");

            inspiration.set(id, 35);
            table.select(2);
            check("play stake", table.use(player) == null && session.isRunning()
                    && inspiration.total(id) == 35 - InspirationLevels.cost(2)
                    && session.ticksStaked() == InspirationLevels.clockTicks(2),
                "two levels staked: " + inspiration.total(id) + " left, " + session.ticksStaked() + " ticks on the clock");
            check("play dealt", trayCount(session) == ResearchSession.TRAY, "three rubbings on the table");
            int[] dealt = {session.trayScrap(0), session.trayScrap(1), session.trayScrap(2)};
            int stakedLeft = inspiration.total(id);
            table.use(player);
            check("play use once", inspiration.total(id) == stakedLeft && session.trayScrap(0) == dealt[0]
                    && session.trayScrap(1) == dealt[1] && session.trayScrap(2) == dealt[2],
                "Use Inspiration mid-attempt takes nothing and deals nothing");
            table.redeal(player);
            boolean swapped = true;
            for (int slot = 0; slot < ResearchSession.TRAY; slot++) {
                for (int old : dealt) {
                    swapped &= session.trayScrap(slot) != old;
                }
            }
            check("play redeal", swapped && trayCount(session) == ResearchSession.TRAY && inspiration.total(id) == stakedLeft,
                "Fresh Rubbings swaps a full tray for three new ones and takes nothing");

            check("play locked", !table.canTakeTablet()
                    && !table.canTakeItem(table, ResearchTableBlockEntity.SLOT, table.tablet())
                    && !table.canPlaceItem(ResearchTableBlockEntity.SLOT, new ItemStack(HexwrightItems.RUINED_TABLET)),
                "nothing in or out of the slot while the clock runs");
            table.select(3);
            check("play stake fixed", session.selected() == 2, "the stake cannot change mid-attempt");

            wrongAnswer(table, player);
            solve(table, player);

            int learnedAfter = RecipeUnlocks.all(player).size();
            check("play paid", session.isComplete() && session.isLingering() && table.tablet().isEmpty()
                    && learnedAfter == learnedBefore + 1,
                "tablet spent, one recipe learned (" + learnedBefore + " -> " + learnedAfter + ")");
            check("play no rebate", inspiration.total(id) == 35 - InspirationLevels.cost(2)
                    && session.learned() != null && RecipeUnlocks.isUnlocked(player, session.learned()),
                "a pristine finish returns nothing, and the window is told what was learned: " + inspiration.total(id));

            cancelling(level, player);
            breakingMidAttempt(level, player);
        } catch (Throwable t) {
            check("play", false, t.toString());
        } finally {
            inspiration.set(id, before);
        }
    }

    private void wrongAnswer(ResearchTableBlockEntity table, FakePlayer player) {
        ResearchSession session = table.session();
        TabletPuzzle puzzle = session.puzzle();
        for (int slot = 0; slot < ResearchSession.TRAY; slot++) {
            for (int gap = 0; gap < session.breakCount(); gap++) {
                if (session.trayScrap(slot) >= 0 && !puzzle.fits(session.trayScrap(slot), gap)) {
                    int ticks = session.ticksLeft();
                    int scrap = session.trayScrap(slot);
                    table.place(player, slot, gap);
                    int cost = Math.round(session.ticksStaked() * 0.10f);
                    check("play wrong", session.ticksLeft() == ticks - cost && session.trayScrap(slot) == scrap
                            && !session.isFilled(gap),
                        "a wrong rubbing cost " + cost + " ticks and stayed in the tray");
                    return;
                }
            }
        }
        check("play wrong", false, "no wrong placement could be found to try");
    }

    private void solve(ResearchTableBlockEntity table, FakePlayer player) {
        ResearchSession session = table.session();
        TabletPuzzle puzzle = session.puzzle();
        int deals = 0;
        for (int step = 0; step < 500 && session.isRunning(); step++) {
            boolean placed = false;
            for (int slot = 0; slot < ResearchSession.TRAY && !placed; slot++) {
                int scrap = session.trayScrap(slot);
                if (scrap >= 0 && !puzzle.deck().get(scrap).isDecoy()
                    && !session.isFilled(puzzle.deck().get(scrap).fits())) {
                    table.place(player, slot, puzzle.deck().get(scrap).fits());
                    placed = true;
                }
            }
            if (!placed) {
                table.redeal(player);
                deals++;
            }
        }
        check("play solve", session.isComplete(),
            "restored " + session.filledCount() + "/" + session.breakCount() + " breaks with " + deals + " redeals");
    }

    private void cancelling(ServerLevel level, FakePlayer player) {
        InspirationState.get(source.getServer()).set(player.getUUID(), InspirationLevels.cost(1));
        ResearchTableBlockEntity table = newTable(level);
        table.setItem(ResearchTableBlockEntity.SLOT, new ItemStack(HexwrightItems.RUINED_TABLET));
        table.select(1);
        if (table.use(player) != null) {
            check("play cancel", true, "skipped - nothing left to learn for a second attempt");
            return;
        }
        table.cancel(player);
        check("play cancel", table.tablet().isEmpty() && !table.session().hasTablet(),
            "cancelling an attempt crumbles the tablet and clears the slab");
    }

    private void breakingMidAttempt(ServerLevel level, FakePlayer player) {
        InspirationState.get(source.getServer()).set(player.getUUID(), InspirationLevels.cost(1));
        ResearchTableBlockEntity table = newTable(level);
        table.setItem(ResearchTableBlockEntity.SLOT, new ItemStack(HexwrightItems.RUINED_TABLET));
        table.select(1);
        if (table.use(player) != null) {
            check("play break", true, "skipped - nothing left to learn for a second attempt");
            return;
        }
        BlockPos pos = level.getSharedSpawnPos().above(2);
        table.dropContents(level, pos);
        List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2),
            entity -> entity.getItem().is(HexwrightItems.RUINED_TABLET));
        boolean crumbled = dropped.isEmpty();
        dropped.forEach(ItemEntity::discard);
        check("play break", crumbled, "a table broken mid-attempt crumbles its tablet instead of dropping it");
    }

    private static ResearchTableBlockEntity newTable(ServerLevel level) {
        ResearchTableBlockEntity table = new ResearchTableBlockEntity(
            BlockPos.ZERO, HexwrightBlocks.WRIGHT_RESEARCH_TABLE_BLOCK.defaultBlockState());
        table.setLevel(level);
        return table;
    }

    private static int trayCount(ResearchSession session) {
        int count = 0;
        for (int slot = 0; slot < ResearchSession.TRAY; slot++) {
            if (session.trayScrap(slot) >= 0) {
                count++;
            }
        }
        return count;
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
