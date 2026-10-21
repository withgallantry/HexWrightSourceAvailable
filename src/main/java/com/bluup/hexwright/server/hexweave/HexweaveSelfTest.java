package com.bluup.hexwright.server.hexweave;

import at.petrak.hexcasting.api.addldata.ADIotaHolder;
import at.petrak.hexcasting.api.casting.eval.CastResult;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironmentComponent;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.EntityIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.iota.NullIota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.casting.iota.Vec3Iota;
import at.petrak.hexcasting.api.casting.eval.SpecialPatterns;
import at.petrak.hexcasting.api.casting.math.HexAngle;
import at.petrak.hexcasting.api.casting.math.HexCoord;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.common.lib.HexItems;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.server.hexpatterns.StackIota;
import com.bluup.hexwright.server.pocketcaster.PocketCasterCastEnv;
import com.bluup.hexwright.server.hexweave.HexweaveCasting.Activation;
import com.bluup.hexwright.server.hexweave.HexweaveCasting.Outcome;
import com.bluup.hexwright.server.hexweave.HexweaveCasting.Run;
import com.bluup.hexwright.server.hexweave.HexweaveCasting.Stop;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

final class HexweaveSelfTest {

    private static final HexPattern MINDS = HexPattern.fromAngles("qaq", HexDir.NORTH_EAST);
    private static final HexPattern DUP = HexPattern.fromAngles("aadaa", HexDir.EAST);
    private static final HexPattern DROP = HexPattern.fromAngles("a", HexDir.SOUTH_EAST);
    private static final HexPattern LOOK = HexPattern.fromAngles("wa", HexDir.EAST);
    private static final HexPattern IMPULSE = HexPattern.fromAngles("awqqqwaqw", HexDir.SOUTH_WEST);
    private static final HexPattern HALT = HexPattern.fromAngles("aqdee", HexDir.SOUTH_WEST);
    private static final HexPattern THANATOS = HexPattern.fromAngles("qqaed", HexDir.SOUTH_EAST);
    private static final HexPattern STACK_LEN = HexPattern.fromAngles("qwaeawqaeaqa", HexDir.NORTH_WEST);
    private static final HexPattern ADD = HexPattern.fromAngles("waaw", HexDir.NORTH_EAST);
    private static final HexPattern OPEN = HexPattern.fromAngles("qqq", HexDir.WEST);
    private static final HexPattern CLOSE = HexPattern.fromAngles("eee", HexDir.EAST);
    private static final HexPattern EVAL = HexPattern.fromAngles("deaqq", HexDir.SOUTH_EAST);
    private static final Vec3Iota VECTOR = new Vec3Iota(new Vec3(1, 2, 3));

    private static final String KEY_G = "key.keyboard.g";
    private static final String KEY_H = "key.keyboard.h";

    private final CommandSourceStack source;
    private final ServerLevel level;
    private final FakePlayer player;
    private final List<CastingEnvironment> environments = new ArrayList<>();
    private final List<Integer> javaDepths = new ArrayList<>();
    private boolean probing;
    private int failures;
    private Pig pig;

    private HexweaveSelfTest(CommandSourceStack source) {
        this.source = source;
        this.level = source.getServer().overworld();
        this.player = FakePlayer.get(level);
    }

    static int run(CommandSourceStack source) {
        return new HexweaveSelfTest(source).withFixture(test -> {
            test.singleThread();
            test.handOff();
            test.fanOut();
            test.branchMishap();
            test.manyToOne();
            test.emptyStack();
            test.cycle();
            test.sharedBudget();
            test.validation();
            test.roundTrip();
            test.geometry();
            test.corruptLink();
            test.duplicateIds();
            test.illegalLinks();
            test.badVersion();
            test.versionOne();
            test.malformedPatterns();
            test.excessiveCounts();
            test.sharedContext();
            test.storedOnItem();
            test.orbStorage();
            test.blankOrb();
            test.brokenOrb();
            test.orbActivation();
            test.orbMedia();
            test.orbCap();
            test.budgetAttachment();
            test.orbInputs();
            test.orbSensors();
            test.orbCrowds();
            test.embeddedIotas();
            test.loomChecks();
        });
    }

    static int persist(CommandSourceStack source) {
        return new HexweaveSelfTest(source).withFixture(HexweaveSelfTest::persistToChest);
    }

    static int restored(CommandSourceStack source) {
        return new HexweaveSelfTest(source).withFixture(HexweaveSelfTest::readFromChest);
    }

    private int withFixture(Consumer<HexweaveSelfTest> checks) {
        Vec3 at = Vec3.atBottomCenterOf(ground());
        player.moveTo(at.x, at.y, at.z, 0f, 0f);
        player.getInventory().clearContent();
        player.getInventory().setItem(20, new ItemStack(HexItems.AMETHYST_DUST, 64));

        Pig created = EntityType.PIG.create(level);
        if (created == null) {
            source.sendFailure(Component.literal("[setup] could not create a pig"));
            return 0;
        }
        pig = created;
        pig.moveTo(at.x, at.y, at.z, 0f, 0f);
        pig.setNoAi(true);
        level.addFreshEntity(pig);
        try {
            checks.accept(this);
        } finally {
            pig.discard();
            player.getInventory().clearContent();
        }
        String summary = failures == 0 ? "all checks passed" : failures + " checks FAILED";
        source.sendSuccess(() -> Component.literal("[weave selftest] " + summary), false);
        return failures == 0 ? 1 : 0;
    }

    private void singleThread() {
        clearHotbar();
        write(1, DUP, LOOK, IMPULSE);

        int dust = dust();
        CastingImage seeded = new CastingImage().copy(List.of(new EntityIota(pig)), 0, List.of(), false, 0L,
            new CompoundTag());
        CastingVM direct = new CastingVM(seeded, new PocketCasterCastEnv(player, InteractionHand.MAIN_HAND));
        direct.queueExecuteAndWrapIotas(List.of(new PatternIota(DUP), new PatternIota(LOOK), new PatternIota(IMPULSE)),
            level);
        int directSpent = dust - dust();

        pig.setDeltaMovement(Vec3.ZERO);
        EntityIota seed = new EntityIota(pig);
        dust = dust();
        Activation activation = activate("1", seed);
        int weaveSpent = dust - dust();
        Run run = activation.runs().isEmpty() ? null : activation.runs().get(0);

        check("A", activation.runs().size() == 1 && run.source() == null && run.threadId() == 1,
            "one root run of Thread 1");
        if (run == null) {
            return;
        }
        check("A", run.input() instanceof EntityIota entity && entity.getEntity() == pig && run.input() != seed,
            "seeded with its own copy of the host iota (the same pig, a new iota)");
        check("A", run.outcome() == Outcome.COMPLETED && run.finalStack().isEmpty(),
            "completed with an empty stack, so it started with the seed and nothing else");
        check("A", weaveSpent == directSpent && weaveSpent > 0,
            "paid " + weaveSpent + " dust from the caster's inventory; a direct Pocket Caster-style cast paid "
                + directSpent);
        check("A", pig.getDeltaMovement().length() > 0.5, "the Impulse landed on the pig");
    }

    private void handOff() {
        clearHotbar();
        write(1, number(7), number(3));
        write(2, STACK_LEN);
        Activation activation = activate("1>2", new EntityIota(pig));

        check("B", threadOrder(activation).equals(List.of(1, 2)), "ran " + threadOrder(activation));
        if (activation.runs().size() != 2) {
            return;
        }
        Run a = activation.runs().get(0);
        Run b = activation.runs().get(1);
        check("B", a.finalStack().size() == 3 && value(a.emitted()) == 3.0 && a.targets().equals(List.of(2)),
            "A ended with 3 iotas and emitted only the top one, 3, to [2]");
        check("B", Integer.valueOf(1).equals(b.source()) && value(b.input()) == 3.0 && b.input() != a.emitted(),
            "B was handed 3 as a fresh instance");
        check("B", b.finalStack().size() == 2 && value(b.finalStack().get(1)) == 1.0,
            "Flock's Reflection in B counted a stack of 1 - nothing else of A's arrived");
        check("B", environments.size() == 2 && environments.get(0) != environments.get(1),
            "each Thread had its own environment");
    }

    private void fanOut() {
        clearHotbar();
        write(1, number(3));
        write(2, number(1), ADD);
        write(3, STACK_LEN);
        Activation activation = activate("1>2,1>3", new EntityIota(pig));
        check("C", threadOrder(activation).equals(List.of(1, 2, 3)), "ran " + threadOrder(activation)
            + " - B before C by id");
        if (activation.runs().size() == 3) {
            Run b = activation.runs().get(1);
            Run c = activation.runs().get(2);
            check("C", value(b.input()) == 3.0 && value(c.input()) == 3.0 && b.input() != c.input(),
                "B and C each got their own instance of 3");
            check("C", value(top(b)) == 4.0 && c.finalStack().size() == 2,
                "B computed 4 from its copy; C still saw a stack of one");
        }

        clearHotbar();
        write(1);
        write(2);
        write(3);
        StackIota seed = new StackIota(new ItemStack(Items.DIAMOND, 5), null);
        activation = activate("1>2,1>3", seed);
        if (activation.runs().size() == 3
            && activation.runs().get(1).input() instanceof StackIota inB
            && activation.runs().get(2).input() instanceof StackIota inC
            && activation.runs().get(0).emitted() instanceof StackIota emitted) {
            inB.getStack().shrink(3);
            check("C", inC.getStack().getCount() == 5 && emitted.getStack().getCount() == 5
                    && seed.getStack().getCount() == 5 && inB.getStack().getCount() == 2,
                "shrinking B's ItemStack left C's, A's and the host's at 5");
        } else {
            check("C", false, "Stack iota fan-out ran " + threadOrder(activation));
        }
    }

    private void branchMishap() {
        clearHotbar();
        write(1, number(3));
        write(2, ADD);
        write(3, STACK_LEN);
        write(4, STACK_LEN);
        Activation activation = activate("1>2,1>3,2>4", new EntityIota(pig));
        check("D", threadOrder(activation).equals(List.of(1, 2, 3)), "ran " + threadOrder(activation)
            + " - Thread 4 never did");
        if (activation.runs().size() == 3) {
            Run b = activation.runs().get(1);
            Run c = activation.runs().get(2);
            check("D", b.outcome() == Outcome.MISHAPPED && b.emitted() == null, "B mishapped and emitted nothing");
            check("D", c.outcome() == Outcome.COMPLETED, "C completed regardless");
        }
        check("D", activation.stop() == Stop.FINISHED, "the activation finished normally");
    }

    private void manyToOne() {
        clearHotbar();
        write(1, number(1));
        write(3, number(2));
        write(4, STACK_LEN);
        Activation activation = activate("1>4,3>4", new EntityIota(pig));
        check("E", threadOrder(activation).equals(List.of(1, 3, 4, 4)), "ran " + threadOrder(activation));
        if (activation.runs().size() == 4) {
            Run first = activation.runs().get(2);
            Run second = activation.runs().get(3);
            check("E", Integer.valueOf(1).equals(first.source()) && value(first.input()) == 1.0
                    && Integer.valueOf(3).equals(second.source()) && value(second.input()) == 2.0,
                "D ran once from A with 1 and once from C with 2");
            check("E", first.finalStack().size() == 2 && second.finalStack().size() == 2,
                "each D invocation had its own one-iota stack");
        }
    }

    private void emptyStack() {
        clearHotbar();
        write(1, DROP);
        write(2, STACK_LEN);
        Activation activation = activate("1>2", new EntityIota(pig));
        check("F", threadOrder(activation).equals(List.of(1))
                && activation.runs().get(0).outcome() == Outcome.COMPLETED
                && activation.runs().get(0).emitted() == null,
            "A dropped its only iota, completed, and Thread 2 never ran");

        clearHotbar();
        write(1, number(5), HALT);
        write(2, STACK_LEN);
        activation = activate("1>2", new EntityIota(pig));
        check("F", threadOrder(activation).equals(List.of(1))
                && activation.runs().get(0).outcome() == Outcome.COMPLETED
                && activation.runs().get(0).finalStack().isEmpty(),
            "top-level Charon's Gambit cleared A's stack; Thread 2 never ran");
    }

    private void cycle() {
        clearHotbar();
        write(1, number(1));
        Activation lone = activate("1>1", new EntityIota(pig));
        check("G", lone.runs().isEmpty(), "1>1 alone has nothing linking into it but itself, so nothing starts it: "
            + lone.runs().size() + " runs");

        clearHotbar();
        write(1);
        write(2, number(1));
        long started = System.nanoTime();
        Activation loop = activate("1>2,2>2", new EntityIota(pig));
        long micros = (System.nanoTime() - started) / 1000;
        check("G", loop.stop() == Stop.INVOCATION_CAP && loop.runs().size() == WeaveExecutionBudget.MAX_INVOCATIONS
                && loop.dropped() > 0,
            "1>2,2>2 stopped at the cap after " + loop.runs().size() + " runs (" + loop.dropped()
                + " dropped, " + loop.opsConsumed() + " ops, " + micros + " us)");

        javaDepths.clear();
        probing = true;
        activate("1>2,2>2", new EntityIota(pig));
        probing = false;
        int shallowest = javaDepths.stream().mapToInt(Integer::intValue).min().orElse(0);
        int deepest = javaDepths.stream().mapToInt(Integer::intValue).max().orElse(0);
        check("G", !javaDepths.isEmpty() && deepest - shallowest < 16,
            "Java stack depth stayed between " + shallowest + " and " + deepest + " frames across every run");

        clearHotbar();
        write(1);
        write(2);
        Activation free = activate("1>2,2>2", new EntityIota(pig));
        check("G", free.stop() == Stop.INVOCATION_CAP && free.opsConsumed() == 0,
            "a loop of empty Threads spends no ops and still stops at the cap");
    }

    private void sharedBudget() {
        clearHotbar();
        write(1, THANATOS, number(1), number(1), number(1));
        write(2, THANATOS, number(1), number(1), number(1));
        write(3, THANATOS, number(1), number(1), number(1));
        Activation small = activate("1>2>3", new EntityIota(pig), () -> 10);
        List<Double> left = new ArrayList<>();
        for (Run run : small.runs()) {
            left.add(run.finalStack().size() > 1 ? value(run.finalStack().get(1)) : Double.NaN);
        }
        check("H", threadOrder(small).equals(List.of(1, 2, 3)) && left.equals(List.of(10.0, 6.0, 2.0)),
            "with a 10-op budget, Thanatos' Reflection read " + left + " in Threads 1, 2 and 3");
        check("H", small.runs().size() == 3 && small.runs().get(2).outcome() == Outcome.OUT_OF_OPS
                && small.stop() == Stop.OP_BUDGET && small.opsConsumed() == 10,
            "Thread 3 ran out mid-way (each Thread needs 4 ops, so separate budgets would have let it finish)");

        HexPattern[] burner = {OPEN, DUP, EVAL, CLOSE, DUP, EVAL};
        List<Iota> burnerHex = new ArrayList<>();
        for (HexPattern pattern : burner) {
            burnerHex.add(new PatternIota(pattern));
        }
        new CastingVM(new CastingImage(), new PocketCasterCastEnv(player, InteractionHand.MAIN_HAND))
            .queueExecuteAndWrapIotas(burnerHex, level);

        clearHotbar();
        write(1, number(1));
        write(2, burner);
        write(3, THANATOS);
        long started = System.nanoTime();
        Activation burn = activate("1>2,1>3", new EntityIota(pig));
        long millis = (System.nanoTime() - started) / 1_000_000;

        started = System.nanoTime();
        new CastingVM(new CastingImage(), new PocketCasterCastEnv(player, InteractionHand.MAIN_HAND))
            .queueExecuteAndWrapIotas(burnerHex, level);
        long directMillis = (System.nanoTime() - started) / 1_000_000;
        check("H", threadOrder(burn).equals(List.of(1, 2)) && burn.runs().get(1).outcome() == Outcome.OUT_OF_OPS
                && burn.stop() == Stop.OP_BUDGET && burn.dropped() == 1,
            "an endless Hermes loop in Thread 2 spent the default budget (" + burn.opsConsumed() + " ops in "
                + millis + " ms; the same loop cast directly as one hex took " + directMillis
                + " ms) and Thread 3 never got one of its own");
    }

    private void validation() {
        WeaveThread one = new WeaveThread(1, List.of());
        WeaveThread two = new WeaveThread(2, List.of());
        refused("Val", "duplicate Thread id", () -> Hexweave.of(List.of(one, one), List.of(), List.of()));
        refused("Val", "link to a missing Thread", () -> Hexweave.of(List.of(one),
            List.of(new WeaveLink(1, 9)), List.of()));
        refused("Val", "duplicate link", () -> Hexweave.of(List.of(one, two),
            List.of(new WeaveLink(1, 2), new WeaveLink(1, 2)), List.of()));
        refused("Val", "a trigger starting a missing Thread", () -> Hexweave.of(List.of(one), List.of(),
            List.of(new WeaveTrigger(1, WeaveTrigger.Kind.ON_USE, List.of(9)))));
        refused("Val", "a trigger starting one Thread twice", () -> Hexweave.of(List.of(one), List.of(),
            List.of(new WeaveTrigger(1, WeaveTrigger.Kind.ON_USE, List.of(1, 1)))));
        refused("Val", "a Key trigger with no key", () -> Hexweave.of(List.of(one), List.of(),
            List.of(new WeaveTrigger(1, WeaveTrigger.Kind.KEY, List.of(1)))));
        refused("Val", "an On Use trigger with a key", () -> Hexweave.of(List.of(one), List.of(),
            List.of(new WeaveTrigger(1, WeaveTrigger.Kind.ON_USE, KEY_G, List.of(1)))));
        refused("Val", "two triggers with one id", () -> Hexweave.of(List.of(one, two), List.of(),
            List.of(new WeaveTrigger(1, WeaveTrigger.Kind.ON_USE, List.of(1)),
                new WeaveTrigger(1, WeaveTrigger.Kind.ON_USE, List.of(2)))));
        try {
            Hexweave free = Hexweave.of(List.of(one, two), List.of(new WeaveLink(1, 2), new WeaveLink(2, 1)),
                List.of(new WeaveTrigger(1, WeaveTrigger.Kind.ON_USE, List.of(2, 1))));
            check("Val", free.starts(WeaveTrigger.Kind.ON_USE).equals(List.of(1, 2)),
                "links either way between two Threads were accepted - there are no armatures to switch on - and "
                    + "the trigger's Threads came back in id order: " + free.starts(WeaveTrigger.Kind.ON_USE));
        } catch (IllegalArgumentException e) {
            check("Val", false, "a weave with links both ways was refused: " + e.getMessage());
        }

        clearHotbar();
        write(1, number(1));
        write(2, number(1));
        writeIota(3, new ListIota(List.of(new DoubleIota(4))));
        refused("Val", "the same link twice in a graph", () -> HexweaveCommand.fromHotbar(player, "1>2,1>2"));
        refused("Val", "a slot that is not a number", () -> HexweaveCommand.fromHotbar(player, "1>x"));
        refused("Val", "an empty hotbar slot", () -> HexweaveCommand.fromHotbar(player, "1>5"));
        Hexweave mixed = HexweaveCommand.fromHotbar(player, "1>3");
        check("Val", mixed.thread(3).entries().size() == 1 && mixed.thread(3).entries().get(0) instanceof EmbeddedIota,
            "a hex holding a number rather than a pattern was taken with the number embedded, as the Loom copies one");
    }

    private void roundTrip() {
        clearHotbar();
        write(1, number(7), number(3));
        write(2, STACK_LEN);
        write(3, number(1), ADD);
        write(4, STACK_LEN);
        Hexweave original = HexweaveCommand.fromHotbar(player, "1>2,1>3,3>4");
        CompoundTag tag = HexweaveNbt.write(original);
        Hexweave copy = HexweaveNbt.read(tag);
        check("I", sameStructure(original, copy),
            "Thread ids, triggers, pattern order, patterns, origins and links all came back equal");
        check("I", HexweaveNbt.write(copy).equals(tag), "writing the copy gives back the same tag");

        List<String> before = trace(activate(original, new EntityIota(pig), null, new WeaveExecutionBudget()));
        List<String> after = trace(activate(copy, new EntityIota(pig), null, new WeaveExecutionBudget()));
        check("I", before.equals(after) && before.size() == 4, "the copy ran exactly as the original: " + after);
    }

    private void geometry() {
        List<ThreadEntry> placed = List.of(
            new PlacedPattern(DUP, new HexCoord(0, 0)),
            new PlacedPattern(LOOK, new HexCoord(12, -5)),
            new PlacedPattern(STACK_LEN, new HexCoord(-20, 9)),
            new PlacedPattern(THANATOS, new HexCoord(300, -300)),
            new PlacedPattern(ADD, new HexCoord(-Hexweave.MAX_ORIGIN, Hexweave.MAX_ORIGIN)));
        Hexweave weave = Hexweave.of(List.of(new WeaveThread(7, placed)), List.of(), List.of());
        Hexweave copy = HexweaveNbt.read(HexweaveNbt.write(weave));
        check("J", copy.thread(7).entries().equals(placed),
            "five patterns at five origins, out to the limit of " + Hexweave.MAX_ORIGIN + ", came back exact");

        List<HexCoord> lookPoints = LOOK.positions();
        HexCoord end = lookPoints.get(lookPoints.size() - 1);
        refused("J", "two patterns on the same points", () -> Hexweave.of(List.of(new WeaveThread(1,
            List.of(new PlacedPattern(DUP, HexCoord.getOrigin()), new PlacedPattern(DUP, HexCoord.getOrigin())))),
            List.of(), List.of()));
        refused("J", "a pattern starting on another's last point (" + end.getQ() + ", " + end.getR() + ")",
            () -> Hexweave.of(List.of(new WeaveThread(1,
                List.of(new PlacedPattern(LOOK, HexCoord.getOrigin()), new PlacedPattern(ADD, end)))), List.of(),
                List.of()));
        int lookRight = lookPoints.stream().mapToInt(HexCoord::getQ).max().orElse(0);
        int addLeft = ADD.positions().stream().mapToInt(HexCoord::getQ).min().orElse(0);
        HexCoord clear = new HexCoord(lookRight + 1 - addLeft, 0);
        try {
            Hexweave.of(List.of(new WeaveThread(1, List.of(
                new PlacedPattern(LOOK, HexCoord.getOrigin()), new PlacedPattern(ADD, clear)))), List.of(), List.of());
            check("J", true, "the same two patterns side by side, sharing no point, are accepted");
        } catch (IllegalArgumentException e) {
            check("J", false, "two patterns sharing no point were refused: " + e.getMessage());
        }
        refused("J", "an origin past the limit", () -> Hexweave.of(List.of(new WeaveThread(1,
            List.of(new PlacedPattern(DUP, new HexCoord(Hexweave.MAX_ORIGIN + 1, 0))))), List.of(), List.of()));
        refused("J", "a pattern that retraces its own line", () -> Hexweave.of(List.of(new WeaveThread(1, List.of(new PlacedPattern(new HexPattern(HexDir.EAST, new ArrayList<>(List.of(HexAngle.BACK))),
                HexCoord.getOrigin())))), List.of(), List.of()));
    }

    private void corruptLink() {
        CompoundTag tag = storedDiamond();
        links(tag).getCompound(0).putInt("To", 99);
        refusedLoad("K", "a link to missing Thread 99", tag);
    }

    private void duplicateIds() {
        CompoundTag tag = storedDiamond();
        threads(tag).getCompound(1).putInt("Id", threads(tag).getCompound(0).getInt("Id"));
        refusedLoad("L", "two Threads stored with the same id", tag);
    }

    private void illegalLinks() {
        CompoundTag missing = storedDiamond();
        triggers(missing).getCompound(0).putIntArray("Threads", new int[]{1, 99});
        refusedLoad("M", "a trigger starting missing Thread 99", missing);

        CompoundTag twice = storedDiamond();
        triggers(twice).getCompound(0).putIntArray("Threads", new int[]{1, 1});
        refusedLoad("M", "a trigger starting Thread 1 twice", twice);

        CompoundTag unknown = storedDiamond();
        triggers(unknown).getCompound(0).putString("Kind", "on_sneeze");
        refusedLoad("M", "a trigger waiting for an event this build doesn't know", unknown);

        CompoundTag shared = storedDiamond();
        triggers(shared).add(triggers(shared).getCompound(0).copy());
        refusedLoad("M", "two triggers stored with one id", shared);

        CompoundTag keyless = storedDiamond();
        triggers(keyless).getCompound(0).putString("Kind", "key_use");
        refusedLoad("M", "a Use + Key trigger stored with no Key", keyless);

        CompoundTag noTargets = storedDiamond();
        triggers(noTargets).getCompound(0).remove("Threads");
        refusedLoad("M", "a trigger with no list of Threads", noTargets);

        CompoundTag linkTwice = storedDiamond();
        links(linkTwice).add(links(linkTwice).getCompound(0).copy());
        refusedLoad("M", "the same link stored twice", linkTwice);
    }

    private void badVersion() {
        CompoundTag future = storedDiamond();
        future.putInt("Version", HexweaveNbt.VERSION + 1);
        refusedLoad("N", "Version " + (HexweaveNbt.VERSION + 1), future);

        CompoundTag missing = storedDiamond();
        missing.remove("Version");
        refusedLoad("N", "no Version at all", missing);

        CompoundTag text = storedDiamond();
        text.putString("Version", "2");
        refusedLoad("N", "a Version written as text", text);
    }

    private void versionOne() {
        CompoundTag current = storedDiamond();
        CompoundTag old = asVersionOne(current);
        Hexweave expected = HexweaveNbt.read(current);
        Hexweave upgraded;
        try {
            upgraded = HexweaveNbt.read(old);
        } catch (IllegalArgumentException e) {
            check("AO", false, "a Version 1 diamond would not load: " + e.getMessage());
            return;
        }
        check("AO", sameStructure(upgraded, expected)
                && upgraded.starts(WeaveTrigger.Kind.ON_USE).equals(List.of(1)),
            "a Version 1 diamond, Thread 1 its only root, read as one On Use trigger starting Thread 1, with "
                + "every Thread and link as stored");
        List<String> before = trace(activate(expected, new EntityIota(pig), null, new WeaveExecutionBudget()));
        List<String> after = trace(activate(upgraded, new EntityIota(pig), null, new WeaveExecutionBudget()));
        check("AO", before.equals(after) && after.size() == 5, "and it ran exactly as the Version 2 diamond: " + after);
        check("AO", HexweaveNbt.summarize(old).readable()
                && HexweaveNbt.summarize(old).links() == HexweaveNbt.summarize(current).links(),
            "its tooltip summary is readable and counts " + HexweaveNbt.summarize(old).links()
                + " links, as it will once saved again");

        CompoundTag noTail = old.copy();
        threads(noTail).getCompound(0).putBoolean("Egress", false);
        refusedLoad("AO", "a Version 1 link out of a Thread stored without a Tail", noTail);
        CompoundTag noEye = old.copy();
        threads(noEye).getCompound(1).putBoolean("Ingress", false);
        refusedLoad("AO", "a Version 1 link into a Thread stored without an Eye", noEye);

        CompoundTag rootless = old.copy();
        threads(rootless).getCompound(0).putBoolean("Ingress", true);
        try {
            check("AO", HexweaveNbt.read(rootless).triggers().isEmpty(),
                "a Version 1 weave with no root became one with no trigger: it never ran, and still doesn't");
        } catch (IllegalArgumentException e) {
            check("AO", false, "a Version 1 weave with no root would not load: " + e.getMessage());
        }
    }

    private static CompoundTag asVersionOne(CompoundTag tag) {
        CompoundTag old = tag.copy();
        old.putInt("Version", 1);
        old.remove("Triggers");
        Set<Integer> entered = new HashSet<>();
        Set<Integer> leaving = new HashSet<>();
        for (int i = 0; i < links(old).size(); i++) {
            leaving.add(links(old).getCompound(i).getInt("From"));
            entered.add(links(old).getCompound(i).getInt("To"));
        }
        for (int i = 0; i < threads(old).size(); i++) {
            CompoundTag entry = threads(old).getCompound(i);
            entry.putBoolean("Ingress", entered.contains(entry.getInt("Id")));
            entry.putBoolean("Egress", leaving.contains(entry.getInt("Id")));
        }
        return old;
    }

    private void malformedPatterns() {
        CompoundTag badAngle = storedDiamond();
        pattern(badAngle).putByteArray(HexPattern.TAG_ANGLES, new byte[]{0, 9});
        refusedLoad("O", "an angle byte of 9", badAngle);

        CompoundTag badDir = storedDiamond();
        pattern(badDir).putByte(HexPattern.TAG_START_DIR, (byte) 7);
        refusedLoad("O", "a start direction of 7", badDir);

        CompoundTag negative = storedDiamond();
        pattern(negative).putByte(HexPattern.TAG_START_DIR, (byte) -1);
        refusedLoad("O", "a start direction of -1", negative);

        CompoundTag retraced = storedDiamond();
        pattern(retraced).putByteArray(HexPattern.TAG_ANGLES, new byte[]{(byte) HexAngle.BACK.ordinal()});
        refusedLoad("O", "a pattern that doubles back on itself", retraced);

        CompoundTag intAngles = storedDiamond();
        pattern(intAngles).putIntArray(HexPattern.TAG_ANGLES, new int[]{0, 1});
        refusedLoad("O", "angles stored as ints", intAngles);

        CompoundTag noPattern = storedDiamond();
        patternEntry(noPattern).remove("Pattern");
        refusedLoad("O", "a pattern entry with no Pattern", noPattern);

        CompoundTag textOrigin = storedDiamond();
        patternEntry(textOrigin).putString("OriginQ", "0");
        refusedLoad("O", "an origin written as text", textOrigin);

        CompoundTag notAList = storedDiamond();
        notAList.putInt("Threads", 3);
        refusedLoad("O", "Threads that is not a list", notAList);
    }

    private void excessiveCounts() {
        CompoundTag onePattern = patternEntry(storedDiamond()).copy();

        CompoundTag manyThreads = storedDiamond();
        ListTag threadList = new ListTag();
        for (int i = 0; i <= Hexweave.MAX_THREADS; i++) {
            threadList.add(threadEntry(i, List.of()));
        }
        manyThreads.put("Threads", threadList);
        refusedLoad("P", (Hexweave.MAX_THREADS + 1) + " Threads", manyThreads);

        CompoundTag manyLinks = storedDiamond();
        ListTag linkList = new ListTag();
        for (int i = 0; i <= Hexweave.MAX_LINKS; i++) {
            linkList.add(links(manyLinks).getCompound(0).copy());
        }
        manyLinks.put("Links", linkList);
        refusedLoad("P", (Hexweave.MAX_LINKS + 1) + " links", manyLinks);

        CompoundTag manyTriggers = storedDiamond();
        ListTag triggerList = new ListTag();
        for (int i = 0; i <= Hexweave.MAX_TRIGGERS; i++) {
            CompoundTag trigger = triggers(manyTriggers).getCompound(0).copy();
            trigger.putInt("Id", i + 1);
            triggerList.add(trigger);
        }
        manyTriggers.put("Triggers", triggerList);
        refusedLoad("P", (Hexweave.MAX_TRIGGERS + 1) + " triggers", manyTriggers);

        CompoundTag crowdedThread = storedDiamond();
        threads(crowdedThread).set(0, threadEntry(1, Collections.nCopies(Hexweave.MAX_PATTERNS_PER_THREAD + 1,
            onePattern)));
        refusedLoad("P", (Hexweave.MAX_PATTERNS_PER_THREAD + 1) + " patterns in one Thread", crowdedThread);

        CompoundTag crowdedWeave = new CompoundTag();
        crowdedWeave.putInt("Version", HexweaveNbt.VERSION);
        ListTag bigThreads = new ListTag();
        int perThread = Hexweave.MAX_PATTERNS_PER_THREAD;
        for (int i = 0; i * perThread <= Hexweave.MAX_PATTERNS; i++) {
            bigThreads.add(threadEntry(i, Collections.nCopies(perThread, onePattern)));
        }
        crowdedWeave.put("Threads", bigThreads);
        crowdedWeave.put("Links", new ListTag());
        refusedLoad("P", bigThreads.size() * perThread + " patterns across the weave", crowdedWeave);

        CompoundTag longPattern = storedDiamond();
        pattern(longPattern).putByteArray(HexPattern.TAG_ANGLES, new byte[Hexweave.MAX_PATTERN_LENGTH + 1]);
        refusedLoad("P", "a pattern of " + (Hexweave.MAX_PATTERN_LENGTH + 1) + " angles", longPattern);
    }

    private void sharedContext() {
        clearHotbar();
        for (int slot = 1; slot <= 6; slot++) {
            write(slot, THANATOS, number(1), number(1), number(1));
        }
        Hexweave first = HexweaveCommand.fromHotbar(player, "1>2>3");
        Hexweave second = HexweaveCommand.fromHotbar(player, "4>5>6");
        IntSupplier twenty = () -> 20;

        WeaveExecutionBudget shared = new WeaveExecutionBudget();
        Activation a = activate(first, new EntityIota(pig), twenty, shared);
        long afterA = shared.opsConsumed();
        int runsAfterA = shared.invocations();
        Activation b = activate(second, new EntityIota(pig), twenty, shared);
        check("Q", thanatos(a).equals(List.of(20.0, 16.0, 12.0)) && afterA == 12 && runsAfterA == 3,
            "weave A, with a 20-op ceiling, read " + thanatos(a) + " and left the budget at " + afterA
                + " ops and " + runsAfterA + " runs");
        check("Q", thanatos(b).equals(List.of(8.0, 4.0)) && b.stop() == Stop.OP_BUDGET && shared.opsConsumed() == 20
                && shared.invocations() == 5,
            "weave B on the same budget read " + thanatos(b) + " and stopped with it spent, before its third Thread");

        Activation fresh = activate(second, new EntityIota(pig), twenty, new WeaveExecutionBudget());
        check("Q", thanatos(fresh).equals(List.of(20.0, 16.0, 12.0)) && fresh.stop() == Stop.FINISHED,
            "weave B on a fresh budget read " + thanatos(fresh) + " and finished");

        clearHotbar();
        write(1);
        write(2);
        write(4, number(1));
        WeaveExecutionBudget runs = new WeaveExecutionBudget();
        Activation loop = activate(HexweaveCommand.fromHotbar(player, "1>2,2>2"), new EntityIota(pig), null, runs);
        Activation starved = activate(HexweaveCommand.fromHotbar(player, "4"), new EntityIota(pig), null, runs);
        Activation unstarved = activate(HexweaveCommand.fromHotbar(player, "4"), new EntityIota(pig), null,
            new WeaveExecutionBudget());
        check("Q", loop.runs().size() == WeaveExecutionBudget.MAX_INVOCATIONS && starved.runs().isEmpty()
                && starved.stop() == Stop.INVOCATION_CAP && unstarved.runs().size() == 1,
            "a loop that used all " + WeaveExecutionBudget.MAX_INVOCATIONS + " runs left none for a second weave "
                + "on its budget; on a fresh one it ran");
    }

    private void storedOnItem() {
        clearHotbar();
        write(1, number(3));
        write(2, number(1), ADD);
        write(3, STACK_LEN);
        Hexweave original = HexweaveCommand.fromHotbar(player, "1>2,1>3");
        ItemStack stick = new ItemStack(Items.STICK);
        HexweaveCommand.store(stick, original);

        ItemStack reloaded;
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            NbtIo.write(stick.save(new CompoundTag()), new DataOutputStream(bytes));
            reloaded = ItemStack.of(NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        } catch (IOException e) {
            check("R", false, "the item could not be written and read: " + e);
            return;
        }
        Hexweave restored = HexweaveCommand.stored(reloaded);
        check("R", sameStructure(original, restored), "the item came back through NBT bytes with the same weave");
        List<String> before = trace(activate(original, new EntityIota(pig), null, new WeaveExecutionBudget()));
        List<String> after = trace(activate(restored, new EntityIota(pig), null, new WeaveExecutionBudget()));
        check("R", before.equals(after), "and it ran the same: " + after);

        ItemStack future = new ItemStack(Items.STICK);
        HexweaveCommand.store(future, original);
        future.getOrCreateTag().getCompound(HexweaveCommand.DEV_KEY).putInt("Version", 99);
        refused("R", "an item whose weave is Version 99", () -> HexweaveCommand.stored(future));
        refused("R", "an item with no weave", () -> HexweaveCommand.stored(new ItemStack(Items.STICK)));
    }

    private void orbStorage() {
        clearHotbar();
        write(1, number(3));
        write(2, number(1), ADD);
        write(3, STACK_LEN);
        Hexweave weave = HexweaveCommand.fromHotbar(player, "1>2,1>3");
        ItemStack orb = new ItemStack(HexLoom.WOVEN_ORB);
        check("S", !WovenOrbItem.hasWeave(orb), "a new Woven Orb is blank");

        WovenOrbItem.bind(orb, weave);
        CompoundTag stored = orb.getOrCreateTag().getCompound(WovenOrbItem.TAG_WEAVE);
        check("S", stored.equals(HexweaveNbt.write(weave)) && !orb.getOrCreateTag().contains(HexweaveCommand.DEV_KEY),
            "the weave is under '" + WovenOrbItem.TAG_WEAVE + "' in exactly HexweaveNbt's form, not the dev key");
        check("S", sameStructure(weave, WovenOrbItem.weave(orb)), "and reads back structurally equal");
        HexweaveNbt.Summary summary = HexweaveNbt.summarize(stored);
        check("S", summary.readable() && summary.threads() == 3 && summary.links() == 3,
            "a bound orb's tooltip summary reads 3 threads, 3 links (two between Threads, one from "
                + "its trigger)");
        WovenOrbItem.clear(orb);
        check("S", !WovenOrbItem.hasWeave(orb), "clearing it leaves it blank");
    }

    private void blankOrb() {
        ItemStack orb = new ItemStack(HexLoom.WOVEN_ORB);
        player.setItemInHand(InteractionHand.OFF_HAND, orb);
        int dust = dust();
        WovenOrbCasting.Result result = WovenOrbCasting.activate(player, InteractionHand.OFF_HAND, orb);
        InteractionResultHolder<ItemStack> used = orb.use(level, player, InteractionHand.OFF_HAND);
        check("T", result.outcome() == WovenOrbCasting.Outcome.BLANK && result.activation() == null
                && dust() == dust && used.getResult() == InteractionResult.PASS
                && !player.getCooldowns().isOnCooldown(HexLoom.WOVEN_ORB),
            "a blank orb reported BLANK, ran nothing, spent no dust, and its use passed without a cooldown");
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    }

    private void brokenOrb() {
        clearHotbar();
        write(1, number(1));
        ItemStack orb = new ItemStack(HexLoom.WOVEN_ORB);
        WovenOrbItem.bind(orb, HexweaveCommand.fromHotbar(player, "1"));
        orb.getOrCreateTag().getCompound(WovenOrbItem.TAG_WEAVE).putInt("Version", 5);
        int dust = dust();
        WovenOrbCasting.Result future = WovenOrbCasting.activate(player, InteractionHand.OFF_HAND, orb);
        check("U", future.outcome() == WovenOrbCasting.Outcome.BROKEN && future.activation() == null && dust() == dust,
            "an orb holding a Version 5 weave was refused and ran nothing");
        check("U", !HexweaveNbt.summarize(orb.getOrCreateTag().getCompound(WovenOrbItem.TAG_WEAVE)).readable(),
            "and its tooltip summary calls it unreadable");

        orb.getOrCreateTag().getCompound(WovenOrbItem.TAG_WEAVE).putInt("Version", HexweaveNbt.VERSION);
        orb.getOrCreateTag().getCompound(WovenOrbItem.TAG_WEAVE).putString("Threads", "tangled");
        WovenOrbCasting.Result tangled = WovenOrbCasting.activate(player, InteractionHand.OFF_HAND, orb);
        check("U", tangled.outcome() == WovenOrbCasting.Outcome.BROKEN, "so was one whose Threads were not a list");
    }

    private void orbActivation() {
        clearHotbar();
        write(1, STACK_LEN);
        write(2, number(1), ADD);
        write(3, STACK_LEN);
        ItemStack orb = new ItemStack(HexLoom.WOVEN_ORB);
        WovenOrbItem.bind(orb, HexweaveCommand.fromHotbar(player, "1>2,1>3"));
        player.setItemInHand(InteractionHand.OFF_HAND, orb);

        WovenOrbCasting.Result first = WovenOrbCasting.activate(player, InteractionHand.OFF_HAND, orb);
        Activation activation = first.activation();
        if (activation == null || !threadOrder(activation).equals(List.of(1, 2, 3))) {
            check("V", false, "the orb's weave ran " + (activation == null ? "nothing" : threadOrder(activation)));
            return;
        }
        Run root = activation.runs().get(0);
        check("V", root.source() == null && root.input() instanceof NullIota && root.finalStack().size() == 2
                && value(top(root)) == 1.0,
            "the root started with the one iota the orb seeded - the holder's entity, which reads as Null only "
                + "because a FakePlayer is not in the level (as in Phase 2)");
        check("V", value(top(activation.runs().get(1))) == 2.0 && activation.runs().get(2).finalStack().size() == 2,
            "the root's 1 reached both branches: Thread 2 made 2, Thread 3 saw a one-iota stack");
        check("V", first.budget() != null && first.budget().invocations() == 3
                && first.budget().opsConsumed() == activation.opsConsumed(),
            "the activation spent from a budget of its own: 3 runs, " + activation.opsConsumed() + " ops");
        WovenOrbCasting.Result second = WovenOrbCasting.activate(player, InteractionHand.OFF_HAND, orb);
        check("V", second.budget() != first.budget() && second.budget() != null && second.budget().invocations() == 3,
            "a second use got a new budget rather than the first one's remainder");

        WovenOrbTriggers.forget(player);
        InteractionResultHolder<ItemStack> used = orb.use(level, player, InteractionHand.OFF_HAND);
        check("V", used.getResult().consumesAction() && !player.getCooldowns().isOnCooldown(HexLoom.WOVEN_ORB),
            "using the item itself consumed the click, and set no item cooldown");
        WovenOrbTriggers.forget(player);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    }

    private void orbMedia() {
        clearHotbar();
        write(1);
        write(2, MINDS, DUP, LOOK, IMPULSE);
        write(3, MINDS, DUP, LOOK, IMPULSE);
        ItemStack orb = new ItemStack(HexLoom.WOVEN_ORB);
        WovenOrbItem.bind(orb, HexweaveCommand.fromHotbar(player, "1>2,1>3"));

        player.getInventory().setItem(20, new ItemStack(HexItems.AMETHYST_DUST, 64));
        int dust = dust();
        Activation paid = WovenOrbCasting.activate(player, InteractionHand.OFF_HAND, orb).activation();
        int spent = dust - dust();
        check("W", paid != null && paid.runs().size() == 3
                && paid.runs().stream().allMatch(run -> run.outcome() == Outcome.COMPLETED) && spent == 2,
            "two branches each Impulsing the holder paid " + spent + " dust between them - one each, none free");

        player.getInventory().setItem(20, ItemStack.EMPTY);
        Activation broke = WovenOrbCasting.activate(player, InteractionHand.OFF_HAND, orb).activation();
        check("W", broke != null && broke.runs().size() == 3
                && broke.runs().get(1).outcome() == Outcome.MISHAPPED
                && broke.runs().get(2).outcome() == Outcome.MISHAPPED && dust() == 0,
            "with no media both branches mishapped instead of casting (a FakePlayer has no overcast)");
        player.getInventory().setItem(20, new ItemStack(HexItems.AMETHYST_DUST, 64));
    }

    private void orbCap() {
        clearHotbar();
        write(1);
        write(2);
        write(3);
        ItemStack orb = new ItemStack(HexLoom.WOVEN_ORB);
        WovenOrbItem.bind(orb, HexweaveCommand.fromHotbar(player, "1>2,2>2,2>3,3>3"));
        WovenOrbCasting.Result capped = WovenOrbCasting.activate(player, InteractionHand.OFF_HAND, orb);
        check("X", capped.unravelled() && capped.activation() != null
                && capped.activation().stop() == Stop.INVOCATION_CAP && capped.activation().dropped() > 1,
            "an activation that dropped " + (capped.activation() == null ? "?" : capped.activation().dropped())
                + " invocations at the cap raised one notice");

        WovenOrbItem.bind(orb, HexweaveCommand.fromHotbar(player, "1"));
        check("X", !WovenOrbCasting.activate(player, InteractionHand.OFF_HAND, orb).unravelled(),
            "an activation that finished raised none");
    }

    private void orbInputs() {
        ItemStack orb = orbListeningFor(Ear.of(WeaveTrigger.Kind.ON_USE), Ear.of(WeaveTrigger.Kind.SNEAK_USE),
            new Ear(WeaveTrigger.Kind.KEY, KEY_G), new Ear(WeaveTrigger.Kind.KEY, KEY_H),
            new Ear(WeaveTrigger.Kind.KEY_USE, KEY_G));
        player.setItemInHand(InteractionHand.MAIN_HAND, orb);
        WovenOrbTriggers.forget(player);

        List<WovenOrbTriggers.Fired> use = WovenOrbTriggers.onUse(player, InteractionHand.MAIN_HAND, orb);
        List<WovenOrbTriggers.Fired> spam = WovenOrbTriggers.onUse(player, InteractionHand.MAIN_HAND, orb);
        check("AQ", started(use) == 1 && use.get(0).kind() == WeaveTrigger.Kind.ON_USE && spam.isEmpty(),
            "a use started only On Use's Thread, and a second click in the same moment was held off");

        player.setShiftKeyDown(true);
        List<WovenOrbTriggers.Fired> sneak = WovenOrbTriggers.onUse(player, InteractionHand.MAIN_HAND, orb);
        player.setShiftKeyDown(false);
        List<WovenOrbTriggers.Fired> pressG = WovenOrbTriggers.onKey(player, KEY_G, true, false);
        List<WovenOrbTriggers.Fired> pressH = WovenOrbTriggers.onKey(player, KEY_H, true, false);
        WovenOrbTriggers.onKey(player, KEY_H, false, false);
        List<WovenOrbTriggers.Fired> chord = WovenOrbTriggers.onUse(player, InteractionHand.MAIN_HAND, orb);
        WovenOrbTriggers.onKey(player, KEY_G, false, false);
        check("AQ", started(sneak) == 2 && started(pressG) == 3 && started(pressH) == 4 && started(chord) == 5
                && chord.get(0).key().equals(KEY_G),
            "Sneak + Use, Key G, Key H and Use + G each started their own Thread: each Key trigger answered its own "
                + "key, and a use while G was held was Use + G");

        WovenOrbTriggers.forget(player);
        List<WovenOrbTriggers.Fired> stray = WovenOrbTriggers.onKey(player, "key.keyboard.j", true, false);
        WovenOrbTriggers.onKey(player, "key.keyboard.j", false, false);
        List<WovenOrbTriggers.Fired> reversed = WovenOrbTriggers.onKey(player, KEY_G, true, true);
        WovenOrbTriggers.onKey(player, KEY_G, false, true);
        check("AQ", stray.isEmpty() && started(reversed) == 5 && reversed.get(0).kind() == WeaveTrigger.Kind.KEY_USE,
            "a key no trigger names fired nothing; G pressed while the use button was already held fired Use + G, "
                + "not Key G - the chord works either way round");

        ItemStack plain = orbListeningFor(WeaveTrigger.Kind.ON_USE);
        player.setItemInHand(InteractionHand.MAIN_HAND, plain);
        WovenOrbTriggers.forget(player);
        player.setShiftKeyDown(true);
        WovenOrbTriggers.onKey(player, KEY_G, true, false);
        List<WovenOrbTriggers.Fired> fallback = WovenOrbTriggers.onUse(player, InteractionHand.MAIN_HAND, plain);
        WovenOrbTriggers.onKey(player, KEY_G, false, false);
        player.setShiftKeyDown(false);
        check("AQ", started(fallback) == 1 && fallback.get(0).kind() == WeaveTrigger.Kind.ON_USE,
            "on an orb that listens only for On Use, a sneaking use with G held still fired On Use");

        WovenOrbTriggers.forget(player);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private void orbSensors() {
        ItemStack orb = orbListeningFor(WeaveTrigger.Kind.PROXIMITY, WeaveTrigger.Kind.PROJECTILE,
            WeaveTrigger.Kind.TARGETED, WeaveTrigger.Kind.GAZE, WeaveTrigger.Kind.FALLING, WeaveTrigger.Kind.PARRY);
        player.setItemInHand(InteractionHand.OFF_HAND, orb);
        WovenOrbTriggers.forget(player);
        Vec3 home = player.position();

        pig.moveTo(home.x + 10, home.y, home.z);
        boolean quiet = WovenOrbTriggers.tick(player).isEmpty();
        pig.moveTo(home.x + 1.2, home.y, home.z);
        List<WovenOrbTriggers.Fired> arrived = WovenOrbTriggers.tick(player);
        boolean stays = WovenOrbTriggers.tick(player).isEmpty();
        pig.moveTo(home.x + 10, home.y, home.z);
        WovenOrbTriggers.tick(player);
        pig.moveTo(home.x + 1.2, home.y, home.z);
        boolean waited = WovenOrbTriggers.tick(player).isEmpty();
        check("AR", quiet && arrived.size() == 1 && arrived.get(0).kind() == WeaveTrigger.Kind.PROXIMITY
                && startedWith(arrived.get(0), pig) && arrived.get(0).hand() == InteractionHand.OFF_HAND && stays
                && waited,
            "Proximity, from the off hand: nothing with the pig 10 blocks off; one fire, with the pig, when it "
                + "stepped close; none while it stayed; none when it stepped out and back within the wait");

        Arrow arrow = EntityType.ARROW.create(level);
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (arrow == null || zombie == null) {
            check("AR", false, "could not create an arrow and a zombie");
            return;
        }
        arrow.moveTo(home.x + 4, home.y + 1, home.z);
        arrow.setDeltaMovement(-1.5, 0, 0);
        level.addFreshEntity(arrow);
        List<WovenOrbTriggers.Fired> shot = WovenOrbTriggers.tick(player);
        boolean once = WovenOrbTriggers.tick(player).isEmpty();
        arrow.discard();
        check("AR", shot.size() == 1 && shot.get(0).kind() == WeaveTrigger.Kind.PROJECTILE && startedWith(shot.get(0), arrow)
                && once,
            "Incoming Projectile: an arrow 4 blocks off flying at the holder fired once, with the arrow, and not "
                + "again on the next tick");

        Arrow railgun = EntityType.ARROW.create(level);
        if (railgun == null) {
            check("AR", false, "could not create an arrow");
            return;
        }
        railgun.moveTo(home.x, home.y + 31, home.z);
        railgun.setDeltaMovement(0, -40, 0);
        level.addFreshEntity(railgun);
        boolean overlooked = WovenOrbTriggers.tick(player).isEmpty();
        List<WovenOrbTriggers.Fired> caught = WovenOrbTriggers.beforeMove(railgun, List.of(player));
        double caughtAt = railgun.position().distanceTo(player.getBoundingBox().getCenter());
        boolean caughtOnce = WovenOrbTriggers.beforeMove(railgun, List.of(player)).isEmpty();
        railgun.discard();
        check("AR", overlooked && caught.size() == 1 && startedWith(caught.get(0), railgun)
                && caughtAt <= WovenOrbTriggers.PROJECTILE_REACH + 0.01 && caughtOnce,
            "Incoming Projectile: an arrow 30 blocks overhead crossing to the holder in one 40-block move was missed "
                + "by the end-of-tick look, then caught once just before it moved, brought to " + caughtAt
                + " blocks off");

        zombie.moveTo(home.x - 6, home.y, home.z);
        zombie.setNoAi(true);
        level.addFreshEntity(zombie);
        boolean unseen = WovenOrbTriggers.tick(player).isEmpty();
        zombie.setTarget(player);
        List<WovenOrbTriggers.Fired> hunted = WovenOrbTriggers.tick(player);
        zombie.discard();
        check("AR", unseen && hunted.size() == 1 && hunted.get(0).kind() == WeaveTrigger.Kind.TARGETED
                && startedWith(hunted.get(0), zombie),
            "Targeted: a zombie nearby fired nothing until it set its sights on the holder, then fired once, with "
                + "the zombie");

        player.lookAt(EntityAnchorArgument.Anchor.EYES, pig.position().add(0, 0.4, 0));
        List<WovenOrbTriggers.Fired> gazes = new ArrayList<>();
        int firstAt = -1;
        for (int i = 1; i <= 40; i++) {
            List<WovenOrbTriggers.Fired> fired = WovenOrbTriggers.tick(player);
            if (!fired.isEmpty() && firstAt < 0) {
                firstAt = i;
            }
            gazes.addAll(fired);
        }
        check("AR", gazes.size() == 1 && gazes.get(0).kind() == WeaveTrigger.Kind.GAZE && startedWith(gazes.get(0), pig)
                && firstAt > WovenOrbTriggers.GAZE_TICKS - 2,
            "Gaze: staring at the pig fired once, after " + firstAt + " ticks, with the pig - and not again for the "
                + "rest of a two-second stare");
        player.lookAt(EntityAnchorArgument.Anchor.EYES, home.add(0, 10, 0));

        player.setOnGround(false);
        player.fallDistance = WovenOrbTriggers.FALL_DISTANCE + 1;
        List<WovenOrbTriggers.Fired> fell = WovenOrbTriggers.tick(player);
        boolean oncePerFall = WovenOrbTriggers.tick(player).isEmpty();
        player.fallDistance = 0;
        player.setOnGround(true);
        check("AR", fell.size() == 1 && fell.get(0).kind() == WeaveTrigger.Kind.FALLING && oncePerFall,
            "Falling: a fall past " + WovenOrbTriggers.FALL_DISTANCE + " blocks fired once, not on every tick of it");

        boolean unarmed = WovenOrbTriggers.onStruck(player, level.damageSources().mobAttack(pig)).isEmpty();
        WovenOrbTriggers.onUse(player, InteractionHand.OFF_HAND, orb);
        List<WovenOrbTriggers.Fired> parried = WovenOrbTriggers.onStruck(player, level.damageSources().mobAttack(pig));
        boolean closed = WovenOrbTriggers.onStruck(player, level.damageSources().mobAttack(pig)).isEmpty();
        check("AR", unarmed && parried.size() == 1 && parried.get(0).kind() == WeaveTrigger.Kind.PARRY
                && startedWith(parried.get(0), pig) && closed,
            "Parry: a blow with no use before it fired nothing; a blow just after a use fired once, with the "
                + "attacker; the next blow found the window closed");

        WovenOrbTriggers.forget(player);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    }

    private void orbCrowds() {
        ItemStack orb = orbListeningFor(WeaveTrigger.Kind.PROXIMITY, WeaveTrigger.Kind.PROJECTILE);
        player.setItemInHand(InteractionHand.OFF_HAND, orb);
        WovenOrbTriggers.forget(player);
        Vec3 home = player.position();
        pig.moveTo(home.x + 10, home.y, home.z);

        List<Arrow> volley = arrows(home, 2);
        List<WovenOrbTriggers.Fired> both = WovenOrbTriggers.tick(player);
        List<Arrow> late = arrows(home, 1);
        List<WovenOrbTriggers.Fired> after = WovenOrbTriggers.tick(player);
        boolean each = both.size() == 2 && both.stream().allMatch(fire -> fire.kind() == WeaveTrigger.Kind.PROJECTILE)
            && volley.stream().allMatch(arrow -> both.stream().anyMatch(fire -> startedWith(fire, arrow)));
        check("AX", each && after.size() == 1 && startedWith(after.get(0), late.get(0)),
            "two arrows arriving together fired twice, once with each, and a third the next moment fired too: "
                + both.size() + " then " + after.size());
        volley.forEach(Entity::discard);
        late.forEach(Entity::discard);
        WovenOrbTriggers.forget(player);

        int many = WovenOrbTriggers.MAX_ARRIVALS_PER_TICK + 3;
        List<Arrow> storm = arrows(home, many);
        int first = WovenOrbTriggers.tick(player).size();
        int second = WovenOrbTriggers.tick(player).size();
        int third = WovenOrbTriggers.tick(player).size();
        storm.forEach(Entity::discard);
        check("AX", first == WovenOrbTriggers.MAX_ARRIVALS_PER_TICK && second == 3 && third == 0,
            many + " arrows at once fired " + first + " then the other " + second + ", and then none: capped per "
                + "tick, but none dropped");
        WovenOrbTriggers.forget(player);

        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) {
            check("AX", false, "could not create a zombie");
            return;
        }
        zombie.setNoAi(true);
        zombie.moveTo(home.x - 10, home.y, home.z);
        level.addFreshEntity(zombie);
        WovenOrbTriggers.tick(player);
        pig.moveTo(home.x + 1.2, home.y, home.z);
        zombie.moveTo(home.x - 1.2, home.y, home.z);
        List<WovenOrbTriggers.Fired> swarm = WovenOrbTriggers.tick(player);
        check("AX", swarm.size() == 2 && swarm.stream().anyMatch(fire -> startedWith(fire, pig))
                && swarm.stream().anyMatch(fire -> startedWith(fire, zombie)),
            "a pig and a zombie stepping close together fired Proximity for each: " + swarm.size());
        zombie.discard();
        pig.moveTo(home.x + 10, home.y, home.z);

        ItemStack reaches = orbListeningFor(new Ear(WeaveTrigger.Kind.PROXIMITY, "", 1),
            new Ear(WeaveTrigger.Kind.PROXIMITY, "", 4));
        player.setItemInHand(InteractionHand.OFF_HAND, reaches);
        WovenOrbTriggers.forget(player);
        boolean quiet = WovenOrbTriggers.tick(player).isEmpty();
        pig.moveTo(home.x + 3.5, home.y, home.z);
        List<WovenOrbTriggers.Fired> outer = WovenOrbTriggers.tick(player);
        pig.moveTo(home.x + 1.2, home.y, home.z);
        List<WovenOrbTriggers.Fired> inner = WovenOrbTriggers.tick(player);
        check("AX", quiet && started(outer) == 2 && started(inner) == 1,
            "with Proximity triggers reaching 1 and 4 blocks, a pig 3 blocks off started only the reach-4 one's "
                + "Thread, and stepping within a block started the reach-1 one's");
        pig.moveTo(home.x + 10, home.y, home.z);

        WovenOrbTriggers.forget(player);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);

        List<WeaveThread> one = List.of(new WeaveThread(1, List.of()));
        CompoundTag written = HexweaveNbt.write(Hexweave.of(one, List.of(),
            List.of(new WeaveTrigger(1, WeaveTrigger.Kind.PROXIMITY, "", 5, List.of(1)))));
        CompoundTag unreached = written.copy();
        unreached.getList("Triggers", Tag.TAG_COMPOUND).getCompound(0).remove("Reach");
        check("AX", HexweaveNbt.read(written).triggers().iterator().next().reach() == 5
                && HexweaveNbt.read(unreached).triggers().iterator().next().reach() == WeaveTrigger.DEFAULT_REACH,
            "a reach of 5 was stored and read back, and a Proximity trigger stored without one reads as 1");
        refused("AX", "a Proximity reach of 6", () -> Hexweave.of(one, List.of(),
            List.of(new WeaveTrigger(1, WeaveTrigger.Kind.PROXIMITY, "", 6, List.of(1)))));
        refused("AX", "a reach on an On Use trigger", () -> Hexweave.of(one, List.of(),
            List.of(new WeaveTrigger(1, WeaveTrigger.Kind.ON_USE, "", 2, List.of(1)))));
        WeaveDraft draft = WeaveDraft.blank();
        draft.setKind(1, WeaveTrigger.Kind.PROXIMITY, "");
        int fresh = draft.reach(1);
        draft.setReach(1, 9);
        int held = draft.reach(1);
        draft.setKind(1, WeaveTrigger.Kind.ON_USE, "");
        check("AX", fresh == 1 && held == WeaveTrigger.MAX_REACH && draft.reach(1) == 0,
            "on the Loom a trigger turned Proximity starts at reach 1, a reach of 9 is held at 5, and turning it "
                + "back to On Use drops its reach");
    }

    private List<Arrow> arrows(Vec3 home, int count) {
        List<Arrow> arrows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Arrow arrow = EntityType.ARROW.create(level);
            if (arrow == null) {
                continue;
            }
            arrow.moveTo(home.x + 4, home.y + 1, home.z + (i % 5 - 2) * 0.2);
            arrow.setDeltaMovement(-1.5, 0, 0);
            level.addFreshEntity(arrow);
            arrows.add(arrow);
        }
        return arrows;
    }

    private record Ear(WeaveTrigger.Kind kind, String key, int reach) {

        Ear(WeaveTrigger.Kind kind, String key) {
            this(kind, key, kind.defaultReach());
        }

        static Ear of(WeaveTrigger.Kind kind) {
            return new Ear(kind, "");
        }
    }

    private static ItemStack orbListeningFor(WeaveTrigger.Kind... kinds) {
        return orbListeningFor(Arrays.stream(kinds).map(Ear::of).toArray(Ear[]::new));
    }

    private static ItemStack orbListeningFor(Ear... ears) {
        List<WeaveThread> threads = new ArrayList<>();
        List<WeaveTrigger> triggers = new ArrayList<>();
        for (int i = 0; i < ears.length; i++) {
            threads.add(new WeaveThread(i + 1, List.of()));
            triggers.add(new WeaveTrigger(i + 1, ears[i].kind(), ears[i].key(), ears[i].reach(), List.of(i + 1)));
        }
        ItemStack orb = new ItemStack(HexLoom.WOVEN_ORB);
        WovenOrbItem.bind(orb, Hexweave.of(threads, List.of(), triggers));
        return orb;
    }

    private static int started(List<WovenOrbTriggers.Fired> fired) {
        Activation activation = fired.size() == 1 ? fired.get(0).result().activation() : null;
        return activation == null || activation.runs().isEmpty() ? -1 : activation.runs().get(0).threadId();
    }

    private static boolean startedWith(WovenOrbTriggers.Fired fired, Entity entity) {
        Activation activation = fired.result().activation();
        return activation != null && !activation.runs().isEmpty()
            && activation.runs().get(0).input() instanceof EntityIota iota && iota.getEntity() == entity;
    }

    private void budgetAttachment() {
        clearHotbar();
        write(1, number(1));
        write(2, number(1));
        Hexweave weave = HexweaveCommand.fromHotbar(player, "1>2");
        WeaveExecutionBudget budget = new WeaveExecutionBudget();
        List<WeaveExecutionBudget> seen = new ArrayList<>();
        Supplier<CastingEnvironment> environments = () -> {
            WovenOrbCastEnv env = new WovenOrbCastEnv(player, InteractionHand.OFF_HAND);
            env.addExtension(new BudgetProbe(env, seen));
            return env;
        };
        HexweaveCasting.activate(weave, weave.starts(WeaveTrigger.Kind.ON_USE), level, environments, null, budget);
        check("Y", !seen.isEmpty() && seen.stream().allMatch(found -> found == budget),
            "every step of both Threads found the activation's own budget on its environment (" + seen.size()
                + " steps) - attachment only; nested accounting is still unproven");
        check("Y", WeaveExecutionBudget.of(new WovenOrbCastEnv(player, InteractionHand.OFF_HAND)) == null,
            "an environment outside a weave carries none");
    }

    private void loomChecks() {
        BlockPos at = ground().offset(-3, 0, 0);
        level.setBlockAndUpdate(at, HexLoom.BLOCK.defaultBlockState());
        if (!(level.getBlockEntity(at) instanceof HexLoomBlockEntity loom)) {
            check("Z", false, "no Loom at " + at.toShortString());
            return;
        }
        try {
            loomBlankDraft(loom);
            loomExistingGeometry(loom);
            loomDrawingOrder(loom);
            loomBoundary(loom);
            loomOverlap(loom);
            loomStale(loom);
            loomMultiThreadOpens(loom);
            loomRuntime(loom);
            loomRoundTrip(loom);
            loomAddRemove(loom);
            loomFanOut(loom);
            loomFanIn(loom);
            loomRoots(loom);
            loomNoRoot(loom);
            loomLinkValidation(loom);
            loomDeleteConnected(loom);
            loomTriggers(loom);
            loomUndo(loom);
            loomEmbedded(loom);
            loomLayout(loom);
            loomLegacyLayout(loom);
            loomRuntimeCompatibility(loom);
            loomStaleWorkspace(loom);
            loomPacking();
            loomDock(loom);
        } finally {
            loom.setOrb(ItemStack.EMPTY);
            level.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
        }
    }

    private void loomBlankDraft(HexLoomBlockEntity loom) {
        loom.setOrb(new ItemStack(HexLoom.WOVEN_ORB));
        WeaveDraft draft = HexLoomEditing.opening(loom.orb()).draft();
        ThreadDraft thread = draft == null ? null : draft.thread(1);
        check("Z1", draft != null && draft.threads().size() == 1 && draft.links().isEmpty() && thread != null
                && draft.triggers().equals(Set.of(1)) && draft.kind(1) == WeaveTrigger.Kind.ON_USE
                && draft.triggerLinks().equals(List.of(new TriggerLink(1, 1))) && thread.entries().isEmpty()
                && !WovenOrbItem.hasWeave(loom.orb()),
            "a blank orb opened as Thread 1, nothing drawn, linked from On Use trigger 1 - and was not changed by "
                + "opening");
        if (thread == null) {
            return;
        }
        boolean drawn = thread.add(number(3), new HexCoord(0, -4)) && thread.add(STACK_LEN, new HexCoord(-4, 3))
            && thread.add(ADD, new HexCoord(3, 2));
        int before = loom.revision();
        HexLoomEditing.SaveResult result = save(loom, draft);
        check("Z1", drawn && result.saved() && WovenOrbItem.hasWeave(loom.orb()) && loom.revision() > before
                && sameStructure(draft.toWeave(), WovenOrbItem.weave(loom.orb())),
            "three patterns drawn and saved: the orb now holds exactly that Thread and its trigger as a Version "
                + HexweaveNbt.VERSION + " weave");
    }

    private void loomExistingGeometry(HexLoomBlockEntity loom) {
        List<ThreadEntry> placed = List.of(
            new PlacedPattern(DUP, new HexCoord(-2, -3)),
            new PlacedPattern(LOOK, new HexCoord(4, -1)),
            new PlacedPattern(THANATOS, new HexCoord(-5, 4)),
            new PlacedPattern(ADD, new HexCoord(1, 5)));
        Hexweave original = Hexweave.of(List.of(new WeaveThread(5, placed)), List.of(), onUse(5));
        ItemStack orb = new ItemStack(HexLoom.WOVEN_ORB);
        WovenOrbItem.bind(orb, original);
        loom.setOrb(orb);
        WeaveDraft draft = HexLoomEditing.opening(loom.orb()).draft();
        ThreadDraft thread = draft == null ? null : draft.thread(5);
        check("Z2", thread != null && draft.threads().size() == 1 && thread.entries().equals(placed)
                && draft.triggerLinks().equals(List.of(new TriggerLink(1, 5))),
            "a stored Thread opened with its id, its trigger and every origin exactly as saved");
        if (thread == null) {
            return;
        }
        CompoundTag storedBefore = weaveTag(loom.orb()).copy();
        HexLoomEditing.SaveResult result = save(loom, draft);
        CompoundTag storedAfter = weaveTag(loom.orb());
        check("Z2", result.saved() && sameStructure(original, WovenOrbItem.weave(loom.orb()))
                && Objects.equals(storedAfter.get("Threads"), storedBefore.get("Threads"))
                && Objects.equals(storedAfter.get("Links"), storedBefore.get("Links"))
                && Objects.equals(storedAfter.get("Triggers"), storedBefore.get("Triggers")) && storedAfter.contains("Layout"),
            "saving it without changes wrote back byte-identical Threads, Links and Triggers; the only addition is the "
                + "Loom's Layout");
    }

    private void loomDrawingOrder(HexLoomBlockEntity loom) {
        loom.setOrb(new ItemStack(HexLoom.WOVEN_ORB));
        WeaveDraft draft = opened("Z3", loom);
        if (draft == null) {
            return;
        }
        ThreadDraft thread = draft.thread(1);
        boolean drawn = thread.add(THANATOS, new HexCoord(5, 0)) && thread.add(DUP, new HexCoord(-5, 1))
            && thread.add(LOOK, new HexCoord(0, -5)) && thread.add(ADD, new HexCoord(0, 4));
        save(loom, draft);
        List<HexPattern> saved = WovenOrbItem.weave(loom.orb()).thread(1).entries().stream()
            .map(entry -> ((PlacedPattern) entry).pattern()).toList();
        check("Z3", drawn && saved.equals(List.of(THANATOS, DUP, LOOK, ADD)),
            "four patterns drawn east, west, north then south were saved in that drawing order");
        thread.remove(1);
        save(loom, draft);
        saved = WovenOrbItem.weave(loom.orb()).thread(1).entries().stream().map(entry -> ((PlacedPattern) entry).pattern()).toList();
        check("Z3", saved.equals(List.of(THANATOS, LOOK, ADD)), "removing the second left the rest in order");
        boolean inserted = thread.insert(1, new PlacedPattern(DUP, new HexCoord(-5, 1)));
        save(loom, draft);
        saved = WovenOrbItem.weave(loom.orb()).thread(1).entries().stream().map(entry -> ((PlacedPattern) entry).pattern()).toList();
        check("Z3", inserted && saved.equals(List.of(THANATOS, DUP, LOOK, ADD)),
            "drawing it again into second place put it back there, the rest in order");
    }

    private void loomBoundary(HexLoomBlockEntity loom) {
        loom.setOrb(new ItemStack(HexLoom.WOVEN_ORB));
        WeaveDraft draft = HexLoomEditing.opening(loom.orb()).draft();
        HexCoord edge = new HexCoord(ThreadDraft.RADIUS - 1, 0);
        check("Z4", draft != null && !draft.thread(1).add(LOOK, edge), "the editor refused a stroke leaving the bound");

        CompoundTag outside = HexweaveNbt.write(Hexweave.of(List.of(new WeaveThread(1,
            List.of(new PlacedPattern(LOOK, edge)))), List.of(), onUse(1)));
        refusedSave("Z4", loom, "a Thread sent with a pattern past the bound", outside);
    }

    private void loomOverlap(HexLoomBlockEntity loom) {
        loom.setOrb(new ItemStack(HexLoom.WOVEN_ORB));
        WeaveDraft draft = HexLoomEditing.opening(loom.orb()).draft();
        check("Z5", draft != null && draft.thread(1).add(DUP, HexCoord.getOrigin())
                && !draft.thread(1).add(DUP, HexCoord.getOrigin()),
            "the editor refused a second pattern on the first one's points");

        CompoundTag overlapping = HexweaveNbt.write(Hexweave.of(List.of(new WeaveThread(1, List.of(
            new PlacedPattern(DUP, HexCoord.getOrigin()), new PlacedPattern(DUP, new HexCoord(4, 4))))), List.of(),
            onUse(1)));
        CompoundTag second = overlapping.getList("Threads", Tag.TAG_COMPOUND).getCompound(0)
            .getList("Patterns", Tag.TAG_COMPOUND).getCompound(1);
        second.putInt("OriginQ", 0);
        second.putInt("OriginR", 0);
        refusedSave("Z5", loom, "a Thread sent with two patterns on the same points", overlapping);

        CompoundTag unstarted = HexweaveNbt.write(Hexweave.of(List.of(new WeaveThread(1, List.of())), List.of(),
            List.of()));
        refusedSave("Z5", loom, "a lone Thread nothing starts", unstarted);
    }

    private void loomStale(HexLoomBlockEntity loom) {
        loom.setOrb(new ItemStack(HexLoom.WOVEN_ORB));
        int opened = loom.revision();
        WeaveDraft draft = opened("Z6", loom);
        if (draft == null) {
            return;
        }
        draft.thread(1).add(DUP, HexCoord.getOrigin());
        CompoundTag weave = HexweaveNbt.write(draft.toWeave(), draft.layout());

        ItemStack first = loom.orb();
        loom.setOrb(ItemStack.EMPTY);
        ItemStack replacement = new ItemStack(HexLoom.WOVEN_ORB);
        replacement.setHoverName(Component.literal("someone else's orb"));
        loom.setOrb(replacement);
        HexLoomEditing.SaveResult stale = HexLoomEditing.save(player, loom.getBlockPos(), opened, weave);
        check("Z6", !stale.saved() && !WovenOrbItem.hasWeave(replacement) && !WovenOrbItem.hasWeave(first),
            "after the orb was taken off and another set down, the old editor's save was refused and neither orb "
                + "was written: " + stale.message().getString());

        Vec3 home = player.position();
        player.moveTo(home.x + 20, home.y, home.z);
        HexLoomEditing.SaveResult far = HexLoomEditing.save(player, loom.getBlockPos(), loom.revision(), weave);
        player.moveTo(home.x, home.y, home.z);
        check("Z6", !far.saved() && !WovenOrbItem.hasWeave(replacement),
            "a save from 20 blocks away was refused too: " + far.message().getString());

        loom.setOrb(ItemStack.EMPTY);
        HexLoomEditing.SaveResult empty = HexLoomEditing.save(player, loom.getBlockPos(), loom.revision(), weave);
        check("Z6", !empty.saved(), "and so was one to an empty Loom");
    }

    private void loomMultiThreadOpens(HexLoomBlockEntity loom) {
        clearHotbar();
        write(1, number(1));
        write(2, STACK_LEN);
        ItemStack orb = new ItemStack(HexLoom.WOVEN_ORB);
        WovenOrbItem.bind(orb, HexweaveCommand.fromHotbar(player, "1>2"));
        loom.setOrb(orb);
        CompoundTag before = orb.getOrCreateTag().copy();
        WeaveDraft draft = HexLoomEditing.opening(loom.orb()).draft();
        check("Z7", draft != null && ids(draft).equals(List.of(1, 2)) && draft.links().equals(List.of(new WeaveLink(1, 2)))
                && loom.orb().getOrCreateTag().equals(before),
            "a two-Thread, one-link orb opened with both Threads and its link, and opening it changed nothing");

        orb.getOrCreateTag().getCompound(WovenOrbItem.TAG_WEAVE).putInt("Version", 9);
        CompoundTag broken = orb.getOrCreateTag().copy();
        CompoundTag single = HexweaveNbt.write(Hexweave.of(List.of(new WeaveThread(1, List.of())), List.of(),
            onUse(1)));
        HexLoomEditing.SaveResult overwrite = HexLoomEditing.save(player, loom.getBlockPos(), loom.revision(), single);
        check("Z7", HexLoomEditing.opening(loom.orb()).draft() == null && !overwrite.saved()
                && loom.orb().getOrCreateTag().equals(broken),
            "an orb with an unreadable weave was refused and left as it was");
    }

    private void loomRuntime(HexLoomBlockEntity loom) {
        loom.setOrb(new ItemStack(HexLoom.WOVEN_ORB));
        WeaveDraft draft = opened("Z8", loom);
        if (draft == null) {
            return;
        }
        ThreadDraft thread = draft.thread(1);
        thread.add(STACK_LEN, new HexCoord(-3, -2));
        thread.add(number(2), new HexCoord(2, -3));
        thread.add(ADD, new HexCoord(0, 3));
        save(loom, draft);
        ItemStack woven = loom.orb().copy();

        clearHotbar();
        write(1, STACK_LEN, number(2), ADD);
        ItemStack bound = new ItemStack(HexLoom.WOVEN_ORB);
        WovenOrbItem.bind(bound, HexweaveCommand.fromHotbar(player, "1"));

        Activation fromLoom = fire(woven);
        Activation fromCommand = fire(bound);
        check("Z8", fromLoom != null && fromCommand != null && trace(fromLoom).equals(trace(fromCommand))
                && value(top(fromLoom.runs().get(0))) == 3.0,
            "the Loom-woven orb ran exactly as the command-bound one (the Thread counted 1, added 2: 3), "
                + "though their patterns sit in different places");
    }

    private void loomRoundTrip(HexLoomBlockEntity loom) {
        loom.setOrb(new ItemStack(HexLoom.WOVEN_ORB));
        WeaveDraft draft = opened("AA", loom);
        if (draft == null) {
            return;
        }
        boolean woven = weave(draft, "1>2,1>3,2>4,3>4", List.of(List.of(number(3)), List.of(number(1), ADD),
            List.of(number(2), ADD), List.of(STACK_LEN)));
        Hexweave authored = draft.toWeave();
        HexLoomEditing.SaveResult result = save(loom, draft);
        WeaveDraft reopened = HexLoomEditing.opening(loom.orb()).draft();
        check("AA", woven && result.saved() && reopened != null && sameStructure(authored, WovenOrbItem.weave(loom.orb()))
                && sameStructure(authored, reopened.toWeave()),
            "four Threads, four links and six patterns were saved and reopened with the same ids, trigger, links, "
                + "program order and origins");
        Activation ran = fire(loom.orb());
        check("AA", ran != null && threadOrder(ran).equals(List.of(1, 2, 3, 4, 4))
                && value(top(ran.runs().get(1))) == 4.0 && value(top(ran.runs().get(2))) == 5.0,
            "and the orb ran as woven: " + (ran == null ? "nothing" : trace(ran)));
    }

    private void loomAddRemove(HexLoomBlockEntity loom) {
        loom.setOrb(new ItemStack(HexLoom.WOVEN_ORB));
        WeaveDraft draft = opened("AB", loom);
        if (draft == null) {
            return;
        }
        weave(draft, "1>2,1>3,2>4,3>4", List.of(List.of(number(1)), List.of(number(2)), List.of(number(3)),
            List.of(STACK_LEN)));
        List<ThreadEntry> second = draft.thread(2).entries();
        List<ThreadEntry> fourth = draft.thread(4).entries();
        WeaveLayout.Position fourthAt = draft.position(4);
        ThreadDraft fourthThread = draft.thread(4);
        draft.removeThread(3);
        boolean closedUp = ids(draft).equals(List.of(1, 2, 3)) && draft.thread(3) == fourthThread
            && draft.thread(3).entries().equals(fourth) && fourthAt.equals(draft.position(3))
            && draft.links().equals(List.of(new WeaveLink(1, 2), new WeaveLink(2, 3)));
        ThreadDraft added = draft.addThread(new WeaveLayout.Position(0, 88));
        check("AB", closedUp && added != null && added.id() == 4 && ids(draft).equals(List.of(1, 2, 3, 4)),
            "removing Thread 3 took 1>3 and 3>4 with it, and Thread 4 became 3 with its patterns, place and 2>4 link "
                + "(now 2>3): " + draft.links() + "; the next Thread added was " + (added == null ? "none" : added.id()));

        HexLoomEditing.SaveResult result = save(loom, draft);
        WeaveDraft reopened = HexLoomEditing.opening(loom.orb()).draft();
        check("AB", result.saved() && reopened != null && ids(reopened).equals(List.of(1, 2, 3, 4))
                && reopened.links().equals(List.of(new WeaveLink(1, 2), new WeaveLink(2, 3)))
                && reopened.thread(2).entries().equals(second) && reopened.thread(3).entries().equals(fourth),
            "saved and reopened: Threads 1 to 4, links 1>2 and 2>3, and the old Threads 2 and 4 drawn as before");
    }

    private void loomFanOut(HexLoomBlockEntity loom) {
        loom.setOrb(new ItemStack(HexLoom.WOVEN_ORB));
        WeaveDraft draft = opened("AC", loom);
        if (draft == null) {
            return;
        }
        boolean woven = weave(draft, "1>2,1>3", List.of(List.of(number(3)), List.of(number(1), ADD),
            List.of(STACK_LEN)));
        HexLoomEditing.SaveResult result = save(loom, draft);
        WeaveDraft reopened = HexLoomEditing.opening(loom.orb()).draft();
        check("AC", woven && result.saved() && reopened != null
                && reopened.links().equals(List.of(new WeaveLink(1, 2), new WeaveLink(1, 3)))
                && reopened.linksFrom(1).size() == 2,
            "saved and reopened: both links leave Thread 1's one Tail");

        Activation ran = fire(loom.orb());
        boolean fanned = ran != null && threadOrder(ran).equals(List.of(1, 2, 3));
        Run b = fanned ? ran.runs().get(1) : null;
        Run c = fanned ? ran.runs().get(2) : null;
        check("AC", fanned && value(b.input()) == 3.0 && value(c.input()) == 3.0 && b.input() != c.input()
                && value(top(b)) == 4.0 && c.finalStack().size() == 2,
            "run: Thread 1's Tail fired once and Threads 2 and 3 each got their own copy of its 3 (2 made 4; 3 "
                + "saw a one-iota stack): " + (ran == null ? "nothing" : trace(ran)));
    }

    private void loomFanIn(HexLoomBlockEntity loom) {
        loom.setOrb(new ItemStack(HexLoom.WOVEN_ORB));
        WeaveDraft draft = opened("AD", loom);
        if (draft == null) {
            return;
        }
        boolean woven = weave(draft, "1>2,1>3,2>3", List.of(List.of(number(1)), List.of(number(2), ADD),
            List.of(STACK_LEN)));
        HexLoomEditing.SaveResult result = save(loom, draft);
        WeaveDraft reopened = HexLoomEditing.opening(loom.orb()).draft();
        check("AD", woven && result.saved() && reopened != null && reopened.linksInto(3).size() == 2
                && reopened.entered(3),
            "saved and reopened: Thread 3's one Eye receives links from Threads 1 and 2");

        Activation ran = fire(loom.orb());
        boolean twice = ran != null && threadOrder(ran).equals(List.of(1, 2, 3, 3));
        check("AD", twice && Integer.valueOf(1).equals(ran.runs().get(2).source()) && value(ran.runs().get(2).input()) == 1.0
                && Integer.valueOf(2).equals(ran.runs().get(3).source()) && value(ran.runs().get(3).input()) == 3.0,
            "run: Thread 3 ran twice, once with Thread 1's 1 and once with Thread 2's 3: "
                + (ran == null ? "nothing" : trace(ran)));
    }

    private void loomRoots(HexLoomBlockEntity loom) {
        loom.setOrb(new ItemStack(HexLoom.WOVEN_ORB));
        WeaveDraft draft = opened("AE", loom);
        if (draft == null) {
            return;
        }
        boolean woven = weave(draft, "1>3,2>3", List.of(List.of(number(4)), List.of(number(5)), List.of(STACK_LEN)));
        HexLoomEditing.SaveResult result = save(loom, draft);
        Hexweave stored = result.saved() ? WovenOrbItem.weave(loom.orb()) : null;
        check("AE", woven && result.saved() && stored != null
                && stored.starts(WeaveTrigger.Kind.ON_USE).equals(List.of(1, 2)),
            "a weave whose trigger starts 1 and 2, both linked into 3, saved: " + result.message().getString());

        Activation ran = fire(loom.orb());
        boolean order = ran != null && threadOrder(ran).equals(List.of(1, 2, 3, 3));
        check("AE", order && ran.runs().get(0).source() == null && ran.runs().get(1).source() == null
                && value(ran.runs().get(2).input()) == 4.0 && value(ran.runs().get(3).input()) == 5.0,
            "run: both started with the host's seed, and Thread 3 ran once for each: "
                + (ran == null ? "nothing" : trace(ran)));
    }

    private void loomNoRoot(HexLoomBlockEntity loom) {
        loom.setOrb(new ItemStack(HexLoom.WOVEN_ORB));
        WeaveDraft draft = opened("AF", loom);
        if (draft == null) {
            return;
        }
        weave(draft, "1>2,2>1", List.of(List.of(number(1)), List.of(number(2))));
        check("AF", !draft.starts(WeaveTrigger.Kind.ON_USE) && draft.reachable().isEmpty(),
            "the editor sees that a two-Thread cycle no trigger links into never starts (so its Save stays disabled)");
        refusedSave("AF", loom, "a weave whose trigger starts nothing", HexweaveNbt.write(draft.toWeave(),
            draft.layout()));
        refusedSave("AF", loom, "a weave with no Threads at all",
            HexweaveNbt.write(Hexweave.of(List.of(), List.of(), List.of())));
    }

    private void loomLinkValidation(HexLoomBlockEntity loom) {
        loom.setOrb(new ItemStack(HexLoom.WOVEN_ORB));
        WeaveDraft draft = opened("AG", loom);
        if (draft == null) {
            return;
        }
        weave(draft, "1>2", List.of(List.of(number(1)), List.of(number(2)), List.of(number(3))));
        List<WeaveDraft.LinkCheck> checks = List.of(draft.checkLink(9, 2), draft.checkLink(1, 9),
            draft.checkLink(1, 2), draft.checkTriggerLink(9, 2), draft.checkTriggerLink(1, 9),
            draft.checkTriggerLink(1, 1));
        boolean refused = !draft.link(9, 2) && !draft.link(1, 9) && !draft.link(1, 2) && !draft.linkTrigger(9, 2)
            && !draft.linkTrigger(1, 9) && !draft.linkTrigger(1, 1) && draft.links().equals(List.of(new WeaveLink(1, 2)));
        check("AG", refused && checks.equals(List.of(WeaveDraft.LinkCheck.MISSING, WeaveDraft.LinkCheck.MISSING,
                WeaveDraft.LinkCheck.DUPLICATE, WeaveDraft.LinkCheck.MISSING, WeaveDraft.LinkCheck.MISSING,
                WeaveDraft.LinkCheck.DUPLICATE)),
            "the editor refused links from and to a missing Thread, a duplicate, a link from a missing trigger, one "
                + "to a missing Thread and a duplicate trigger link: " + checks);
        check("AG", draft.link(3, 2) && draft.link(2, 3),
            "and allowed links into a Thread a trigger also starts, and back again: no armature to switch on first");

        CompoundTag base = HexweaveNbt.write(draft.toWeave(), draft.layout());
        refusedSave("AG", loom, "a link from a missing Thread", withLink(base, 9, 2));
        refusedSave("AG", loom, "a link to a missing Thread", withLink(base, 1, 9));
        refusedSave("AG", loom, "the same link twice", withLink(base, 1, 2));
        CompoundTag strayTrigger = base.copy();
        strayTrigger.getList("Triggers", Tag.TAG_COMPOUND).getCompound(0).putIntArray("Threads", new int[]{1, 9});
        refusedSave("AG", loom, "a trigger starting a missing Thread", strayTrigger);
    }

    private void loomDeleteConnected(HexLoomBlockEntity loom) {
        loom.setOrb(new ItemStack(HexLoom.WOVEN_ORB));
        WeaveDraft draft = opened("AH", loom);
        if (draft == null) {
            return;
        }
        weave(draft, "1>2,1>3,2>3,3>2,2>4,2>2", List.of(List.of(number(1)), List.of(number(2)), List.of(number(3)),
            List.of(STACK_LEN)));
        int before = draft.links().size();
        draft.removeThread(2);
        boolean clean = draft.links().equals(List.of(new WeaveLink(1, 2))) && ids(draft).equals(List.of(1, 2, 3));
        check("AH", before == 6 && clean,
            "deleting Thread 2 - two links in, two out and one to itself - left only 1>3, now 1>2 as Thread 3 "
                + "closed up: " + draft.links());
        HexLoomEditing.SaveResult result = save(loom, draft);
        WeaveDraft reopened = HexLoomEditing.opening(loom.orb()).draft();
        check("AH", result.saved() && reopened != null && ids(reopened).equals(List.of(1, 2, 3))
                && reopened.links().equals(List.of(new WeaveLink(1, 2))),
            "and the weave saved and reopened with Threads 1, 2 and 3 and no dangling link");
    }

    private void loomTriggers(HexLoomBlockEntity loom) {
        loom.setOrb(new ItemStack(HexLoom.WOVEN_ORB));
        WeaveDraft draft = opened("AP", loom);
        if (draft == null) {
            return;
        }
        boolean woven = weave(draft, "1>3", List.of(List.of(number(4)), List.of(number(5)), List.of(STACK_LEN)));
        Integer second = draft.addTrigger(new WeaveLayout.Position(-50f, 60f));
        boolean linked = second != null && draft.linkTrigger(second, 2) && !draft.linkTrigger(second, 2);
        draft.moveTrigger(1, -61.5f, 3.25f);
        HexLoomEditing.SaveResult result = save(loom, draft);
        WeaveDraft reopened = HexLoomEditing.opening(loom.orb()).draft();
        check("AP", woven && linked && result.saved() && reopened != null && reopened.triggers().equals(Set.of(1, 2))
                && Set.copyOf(reopened.triggerLinks()).equals(Set.of(new TriggerLink(1, 1), new TriggerLink(1, 2),
                    new TriggerLink(2, 2)))
                && new WeaveLayout.Position(-61.5f, 3.25f).equals(reopened.triggerPosition(1))
                && reopened.kind(2) == WeaveTrigger.Kind.ON_USE,
            "two triggers - one starting Threads 1 and 2, the other Thread 2 - saved and reopened with their links, "
                + "kinds and places: " + (reopened == null ? "it would not reopen" : reopened.triggerLinks()));

        Activation ran = fire(loom.orb());
        check("AP", ran != null && threadOrder(ran).equals(List.of(1, 2, 2, 3)) && ran.runs().get(2).source() == null
                && value(ran.runs().get(3).input()) == 4.0,
            "using the orb ran Thread 1, Thread 2 once for each trigger, then Thread 3 from 1's Tail: "
                + (ran == null ? "nothing" : trace(ran)));

        Integer third = draft.addTrigger(new WeaveLayout.Position(-50f, -60f));
        boolean thirdSet = third != null && draft.linkTrigger(third, 3);
        if (third != null) {
            draft.setKind(third, WeaveTrigger.Kind.SNEAK_USE, "");
        }
        draft.removeTrigger(second);
        boolean closedUp = thirdSet && draft.triggers().equals(Set.of(1, 2))
            && draft.kind(2) == WeaveTrigger.Kind.SNEAK_USE
            && new WeaveLayout.Position(-50f, -60f).equals(draft.triggerPosition(2))
            && draft.triggerLinks().equals(List.of(new TriggerLink(1, 1), new TriggerLink(1, 2), new TriggerLink(2, 3)));
        Integer next = draft.addTrigger(new WeaveLayout.Position(-50f, 120f));
        check("AP", closedUp && Integer.valueOf(3).equals(next),
            "removing trigger 2 took its link, trigger 3 became 2 with its kind, place and link to Thread 3, and the next "
                + "trigger was " + next + ": " + draft.triggerLinks());
        if (next != null) {
            draft.removeTrigger(next);
        }
        draft.removeThread(2);
        check("AP", draft.triggerLinks().equals(List.of(new TriggerLink(1, 1), new TriggerLink(2, 2))),
            "removing Thread 2 then took trigger 1's link to it, and trigger 2's link to Thread 3 followed it to 2: "
                + draft.triggerLinks());
    }

    private void loomUndo(HexLoomBlockEntity loom) {
        loom.setOrb(new ItemStack(HexLoom.WOVEN_ORB));
        WeaveDraft draft = opened("AS", loom);
        if (draft == null) {
            return;
        }
        weave(draft, "1>2,1>3,2>3", List.of(List.of(number(1)), List.of(number(2)), List.of(STACK_LEN)));
        CompoundTag before = HexweaveNbt.write(draft.toWeave(), draft.layout());
        ThreadDraft second = draft.thread(2);
        ThreadDraft third = draft.thread(3);
        WeaveDraft.Snapshot snapshot = draft.snapshot();
        draft.removeThread(2);
        draft.thread(2).clear();
        draft.addTrigger(new WeaveLayout.Position(-50f, 60f));
        boolean changed = ids(draft).equals(List.of(1, 2)) && draft.thread(2) == third && third.entries().isEmpty();
        int editsChanged = draft.edits();
        draft.restore(snapshot);
        CompoundTag after = HexweaveNbt.write(draft.toWeave(), draft.layout());
        check("AS", changed && before.equals(after) && draft.thread(2) == second && draft.thread(3) == third
                && third.id() == 3 && !third.entries().isEmpty() && draft.edits() > editsChanged,
            "after deleting Thread 2, clearing the Thread that became 2 and adding a trigger, the snapshot put back "
                + "the same Threads 2 and 3 with their patterns, places and links, exactly as written before, and "
                + "edits went on up");
        HexLoomEditing.SaveResult result = save(loom, draft);
        check("AS", result.saved() && sameStructure(draft.toWeave(), WovenOrbItem.weave(loom.orb())),
            "and the restored draft saved as it is");
    }

    private void embeddedIotas() {
        CompoundTag vector = IotaType.serialize(VECTOR);
        EmbeddedIota knot = new EmbeddedIota(vector, new HexCoord(0, 0));
        PlacedPattern consider = new PlacedPattern(SpecialPatterns.CONSIDERATION, new HexCoord(-6, 0));

        Hexweave stored = Hexweave.of(List.of(new WeaveThread(1, List.of(consider, knot))), List.of(), onUse(1));
        CompoundTag written = HexweaveNbt.write(stored);
        Hexweave read = HexweaveNbt.read(written);
        check("AT", written.getInt("Version") == 3 && read.thread(1).entries().equals(stored.thread(1).entries())
                && read.thread(1).entries().get(1) instanceof EmbeddedIota back && back.iota().equals(vector),
            "a Consideration and an embedded vector were written as Version 3 and read back exactly");
        refused("AT", "a pattern drawn through an embedded iota's point", () -> Hexweave.of(List.of(new WeaveThread(1,
            List.of(knot, new PlacedPattern(DUP, HexCoord.getOrigin())))), List.of(), onUse(1)));
        refused("AT", "an embedded tag that names no iota type", () -> Hexweave.of(List.of(new WeaveThread(1,
            List.of(new EmbeddedIota(new CompoundTag(), HexCoord.getOrigin())))), List.of(), onUse(1)));
        ThreadDraft draft = ThreadDraft.empty(1);
        check("AT", draft.insert(0, knot) && !draft.accepts(new PlacedPattern(DUP, HexCoord.getOrigin()))
                && !draft.accepts(knot.at(HexCoord.getOrigin())) && draft.accepts(knot.at(new HexCoord(3, 3))),
            "on the Loom, a knot's point refuses a pattern and another knot, and a free point takes one");

        Hexweave escaped = Hexweave.of(List.of(new WeaveThread(1, List.of(consider, knot)), new WeaveThread(2, List.of())),
            List.of(new WeaveLink(1, 2)), onUse(1));
        Activation ran = activate(escaped, null, null, new WeaveExecutionBudget());
        Run first = ran.runs().isEmpty() ? null : ran.runs().get(0);
        check("AU", first != null && first.outcome() == Outcome.COMPLETED && first.emitted() instanceof Vec3Iota out
                && out.getVec3().equals(VECTOR.getVec3()) && ran.runs().size() == 2,
            "a Consideration then an embedded vector pushed the vector, and the Tail sent it on: " + trace(ran));
        Hexweave bare = Hexweave.of(List.of(new WeaveThread(1, List.of(knot))), List.of(), onUse(1));
        Activation mishap = activate(bare, null, null, new WeaveExecutionBudget());
        check("AU", !mishap.runs().isEmpty() && mishap.runs().get(0).outcome() == Outcome.MISHAPPED,
            "the same vector with no Consideration mishapped, as it would cast from a focus: " + trace(mishap));

        CompoundTag focus = IotaType.serialize(new ListIota(List.of(new PatternIota(SpecialPatterns.CONSIDERATION),
            VECTOR, new PatternIota(DUP), new DoubleIota(4))));
        List<ThreadEntry> copied = ThreadEntry.copiedFrom(focus);
        boolean shapes = copied.size() == 4 && copied.get(0) instanceof PlacedPattern a
            && a.pattern().anglesSignature().equals(SpecialPatterns.CONSIDERATION.anglesSignature())
            && copied.get(1) instanceof EmbeddedIota b && b.iota().equals(vector)
            && copied.get(2) instanceof PlacedPattern c && c.pattern().anglesSignature().equals(DUP.anglesSignature())
            && copied.get(3) instanceof EmbeddedIota;
        check("AW", shapes && ThreadEntry.copiedFrom(IotaType.serialize(VECTOR)).size() == 1
                && ThreadEntry.copiedFrom(IotaType.serialize(new NullIota())).isEmpty(),
            "a focus's list copied in as pattern, embedded, pattern, embedded in order; a bare vector as one entry; "
                + "an empty focus as nothing");
        ThreadDraft target = ThreadDraft.empty(1);
        target.add(MINDS, new HexCoord(0, 0));
        List<ThreadEntry> placed = target.fit(copied);
        boolean fitted = placed != null && target.insertAll(1, placed) && target.size() == 5
            && target.entries().get(0) instanceof PlacedPattern kept && kept.pattern().equals(MINDS);
        boolean sameProgram = fitted;
        for (int i = 0; fitted && i < copied.size(); i++) {
            ThreadEntry was = copied.get(i);
            ThreadEntry now = target.entries().get(i + 1);
            sameProgram &= was instanceof PlacedPattern wasPattern && now instanceof PlacedPattern nowPattern
                ? wasPattern.pattern().anglesSignature().equals(nowPattern.pattern().anglesSignature())
                : was instanceof EmbeddedIota wasIota && now instanceof EmbeddedIota nowIota && wasIota.iota().equals(nowIota.iota());
        }
        check("AW", sameProgram && target.outOfBounds().isEmpty(),
            "fitted into a Thread already holding a pattern, in one run after it, in bounds, each entry the same "
                + "pattern or iota as copied");
        int before = target.edits();
        List<ThreadEntry> tooMany = java.util.Collections.nCopies(400, new EmbeddedIota(IotaType.serialize(new DoubleIota(1)),
            HexCoord.getOrigin()));
        check("AW", target.fit(tooMany) == null && target.size() == 5 && target.edits() == before,
            "400 embedded iotas - more than the bound has points - were refused whole, and nothing changed");
    }

    private void loomEmbedded(HexLoomBlockEntity loom) {
        loom.setOrb(new ItemStack(HexLoom.WOVEN_ORB));
        WeaveDraft draft = opened("AV", loom);
        if (draft == null) {
            return;
        }
        ThreadDraft thread = draft.thread(1);
        thread.insert(0, new PlacedPattern(SpecialPatterns.CONSIDERATION, new HexCoord(-6, 0)));
        thread.insert(1, new EmbeddedIota(IotaType.serialize(VECTOR), new HexCoord(0, 0)));
        refusedSave("AV", loom, "an embedded vector the player carries nowhere", HexweaveNbt.write(draft.toWeave(), draft.layout()));

        ItemStack focus = new ItemStack(HexItems.FOCUS);
        ADIotaHolder holder = IXplatAbstractions.INSTANCE.findDataHolder(focus);
        holder.writeIota(new ListIota(List.of(new PatternIota(SpecialPatterns.CONSIDERATION), VECTOR)), false);
        player.getInventory().setItem(20, focus);
        HexLoomEditing.SaveResult carried = save(loom, draft);
        player.getInventory().setItem(20, ItemStack.EMPTY);
        WeaveDraft reopened = HexLoomEditing.opening(loom.orb()).draft();
        HexLoomEditing.SaveResult again = reopened == null ? null : save(loom, reopened);
        check("AV", carried.saved() && again != null && again.saved(),
            "with the vector on a focus in the inventory it saved; with the focus gone, saving the weave again "
                + "still did, the vector being in it already");

        if (reopened != null) {
            reopened.thread(1).insert(2, new EmbeddedIota(IotaType.serialize(new DoubleIota(7)), new HexCoord(3, 3)));
            refusedSave("AV", loom, "a second embedded iota, a number nobody carries",
                HexweaveNbt.write(reopened.toWeave(), reopened.layout()));
        }
    }

    private void loomLayout(HexLoomBlockEntity loom) {
        loom.setOrb(new ItemStack(HexLoom.WOVEN_ORB));
        WeaveDraft draft = opened("AI", loom);
        if (draft == null) {
            return;
        }
        weave(draft, "1>2,1>3", List.of(List.of(number(1)), List.of(number(2), ADD), List.of(STACK_LEN)));
        draft.move(1, -37.25f, 12.5f);
        draft.move(2, 140.75f, -88.125f);
        draft.move(3, 5000f, -5000f);
        save(loom, draft);
        WeaveDraft reopened = HexLoomEditing.opening(loom.orb()).draft();
        WeaveLayout.Position edge = new WeaveLayout.Position(WeaveLayout.EXTENT, -WeaveLayout.EXTENT);
        check("AI", reopened != null && new WeaveLayout.Position(-37.25f, 12.5f).equals(reopened.position(1))
                && new WeaveLayout.Position(140.75f, -88.125f).equals(reopened.position(2))
                && edge.equals(reopened.position(3)),
            "three positions came back exactly, the one dragged to 5000, -5000 held at the workspace's edge: "
                + (reopened == null ? "none" : reopened.layout().positions()));

        ItemStack first = loom.orb().copy();
        draft.move(1, 300f, 300f);
        draft.move(2, -300f, 0f);
        draft.move(3, 0f, -300f);
        save(loom, draft);
        ItemStack second = loom.orb().copy();
        Activation ranFirst = fire(first);
        Activation ranSecond = fire(second);
        check("AI", Objects.equals(weaveTag(first).get("Threads"), weaveTag(second).get("Threads"))
                && Objects.equals(weaveTag(first).get("Links"), weaveTag(second).get("Links"))
                && !Objects.equals(weaveTag(first).get("Layout"), weaveTag(second).get("Layout"))
                && ranFirst != null && ranSecond != null && trace(ranFirst).equals(trace(ranSecond)),
            "moving every Thread and saving again changed only the Layout: Threads and Links byte-identical, and "
                + "the orb ran the same both times");

        CompoundTag crafted = HexweaveNbt.write(draft.toWeave(), draft.layout());
        ListTag entries = crafted.getCompound("Layout").getList("Threads", Tag.TAG_COMPOUND);
        entries.getCompound(0).putFloat("X", Float.NaN);
        entries.getCompound(2).putFloat("X", 1e9f);
        entries.add(layoutEntry(99, 1f, 1f));
        entries.add(layoutEntry(2, 7f, 7f));
        HexLoomEditing.SaveResult cleaned = HexLoomEditing.save(player, loom.getBlockPos(), loom.revision(), crafted);
        WeaveLayout stored = WovenOrbItem.layout(loom.orb());
        check("AI", cleaned.saved() && stored.position(1) == null && stored.position(99) == null
                && new WeaveLayout.Position(-300f, 0f).equals(stored.position(2))
                && new WeaveLayout.Position(WeaveLayout.EXTENT, -300f).equals(stored.position(3)),
            "a submitted layout with a NaN, a coordinate of 10^9, an unknown Thread and a second entry for one Thread "
                + "was cleaned, not trusted, and the weave still saved: " + stored.positions());
    }

    private void loomLegacyLayout(HexLoomBlockEntity loom) {
        clearHotbar();
        write(1, number(1));
        write(2, number(2));
        write(3, number(3));
        write(4, STACK_LEN);
        ItemStack orb = new ItemStack(HexLoom.WOVEN_ORB);
        WovenOrbItem.bind(orb, HexweaveCommand.fromHotbar(player, "1>2,1>3,2>4,3>4"));
        loom.setOrb(orb);
        CompoundTag before = orb.getOrCreateTag().copy();
        Activation ranBefore = fire(orb);
        WeaveDraft draft = HexLoomEditing.opening(loom.orb()).draft();
        boolean arranged = draft != null;
        if (draft != null) {
            WeaveLayout.Position a = draft.position(1);
            WeaveLayout.Position b = draft.position(2);
            WeaveLayout.Position c = draft.position(3);
            WeaveLayout.Position d = draft.position(4);
            WeaveLayout.Position trigger = draft.triggerPosition(1);
            arranged = a.x() < b.x() && b.x() == c.x() && c.x() < d.x() && b.y() < c.y() && a.y() == 0 && d.y() == 0
                && trigger != null && trigger.x() < a.x() && trigger.y() == a.y();
        }
        check("AJ", arranged && !weaveTag(loom.orb()).contains("Layout") && loom.orb().getOrCreateTag().equals(before),
            "a Phase 4 weave with no layout opened arranged automatically - its trigger just left of Thread 1, "
                + "2 above 3 in the next column, 4 after them - and opening it wrote nothing: "
                + (draft == null ? "it would not open" : draft.layout().positions()));
        Activation ranAfter = fire(loom.orb());
        check("AJ", ranBefore != null && ranAfter != null && trace(ranBefore).equals(trace(ranAfter))
                && threadOrder(ranAfter).equals(List.of(1, 2, 3, 4, 4)),
            "and it ran exactly as it did before it was opened: " + (ranAfter == null ? "nothing" : trace(ranAfter)));
    }

    private void loomRuntimeCompatibility(HexLoomBlockEntity loom) {
        loom.setOrb(new ItemStack(HexLoom.WOVEN_ORB));
        WeaveDraft draft = opened("AK", loom);
        if (draft == null) {
            return;
        }
        weave(draft, "1>2,1>3,2>4,3>4", List.of(List.of(number(1)), List.of(number(2)), List.of(number(3)),
            List.of(STACK_LEN)));
        save(loom, draft);
        ItemStack woven = loom.orb().copy();

        clearHotbar();
        write(1, number(1));
        write(2, number(2));
        write(3, number(3));
        write(4, STACK_LEN);
        ItemStack bound = new ItemStack(HexLoom.WOVEN_ORB);
        WovenOrbItem.bind(bound, HexweaveCommand.fromHotbar(player, "1>2,1>3,2>4,3>4"));

        Activation fromLoom = fire(woven);
        Activation fromCommand = fire(bound);
        check("AK", fromLoom != null && fromCommand != null && trace(fromLoom).equals(trace(fromCommand))
                && threadOrder(fromLoom).equals(List.of(1, 2, 3, 4, 4)),
            "a diamond woven on the Loom ran exactly as the same diamond bound from the hotbar: "
                + (fromLoom == null ? "nothing" : trace(fromLoom)));
    }

    private void loomStaleWorkspace(HexLoomBlockEntity loom) {
        loom.setOrb(new ItemStack(HexLoom.WOVEN_ORB));
        int opened = loom.revision();
        WeaveDraft draft = opened("AL", loom);
        if (draft == null) {
            return;
        }
        weave(draft, "1>2", List.of(List.of(number(1)), List.of(STACK_LEN)));
        HexLoomEditing.SaveResult first = HexLoomEditing.save(player, loom.getBlockPos(), opened,
            HexweaveNbt.write(draft.toWeave(), draft.layout()));
        CompoundTag afterFirst = loom.orb().getOrCreateTag().copy();
        draw(draft.thread(2), ADD);
        HexLoomEditing.SaveResult stale = HexLoomEditing.save(player, loom.getBlockPos(), opened,
            HexweaveNbt.write(draft.toWeave(), draft.layout()));
        check("AL", first.saved() && !stale.saved() && loom.orb().getOrCreateTag().equals(afterFirst)
                && loom.revision() == first.revision(),
            "the first save went through; a second quoting the old revision was refused and the orb kept the first: "
                + stale.message().getString());
    }

    private void loomPacking() {
        Random random = new Random(6);
        WeaveDraft heavy = scattered(16, 32, random);
        WeaveDraft fullest = scattered(Hexweave.MAX_THREADS, Hexweave.MAX_PATTERNS / Hexweave.MAX_THREADS, random);
        CompoundTag heavyTag = HexweaveNbt.write(heavy.toWeave(), heavy.layout());
        CompoundTag fullestTag = HexweaveNbt.write(fullest.toWeave(), fullest.layout());
        try {
            byte[] heavyPacked = LoomNetworking.pack(heavyTag);
            byte[] fullestPacked = LoomNetworking.pack(fullestTag);
            check("AM", LoomNetworking.unpack(heavyPacked).equals(heavyTag)
                    && LoomNetworking.unpack(fullestPacked).equals(fullestTag)
                    && heavyPacked.length <= LoomNetworking.MAX_SAVE_BYTES,
                "16 Threads, " + patternCount(heavy) + " varied patterns: " + rawSize(heavyTag) + " bytes of NBT, "
                    + heavyPacked.length + " packed; the fullest weave (" + fullest.threads().size() + " Threads, "
                    + patternCount(fullest) + " patterns): " + rawSize(fullestTag) + " bytes, " + fullestPacked.length
                    + " packed; the limit is " + LoomNetworking.MAX_SAVE_BYTES + ". Both unpacked unchanged");
        } catch (IOException e) {
            check("AM", false, "a packed weave would not unpack: " + e);
        }

        CompoundTag bomb = new CompoundTag();
        bomb.putByteArray("Zeros", new byte[3_000_000]);
        byte[] packedBomb = LoomNetworking.pack(bomb);
        try {
            LoomNetworking.unpack(packedBomb);
            check("AM", false, "a " + packedBomb.length + "-byte payload unpacked to 3 MB");
        } catch (IOException | RuntimeException e) {
            check("AM", packedBomb.length <= LoomNetworking.MAX_SAVE_BYTES,
                "a " + packedBomb.length + "-byte payload that would unpack to 3 MB was refused: " + e.getMessage());
        }
    }

    private void loomDock(HexLoomBlockEntity loom) {
        loom.setOrb(ItemStack.EMPTY);
        BlockPos pos = loom.getBlockPos();
        BlockState state = level.getBlockState(pos);
        BlockHitResult side = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0, -0.5), Direction.NORTH, pos, false);
        BlockHitResult top = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, -0.1), Direction.UP, pos, false);

        ItemStack held = new ItemStack(HexLoom.WOVEN_ORB);
        WovenOrbItem.bind(held, Hexweave.of(List.of(new WeaveThread(1,
            List.of(new PlacedPattern(DUP, HexCoord.getOrigin())))), List.of(), onUse(1)));
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        InteractionResult docked = state.use(level, player, InteractionHand.MAIN_HAND, side);
        check("AN", docked.consumesAction() && WovenOrbItem.hasWeave(loom.orb())
                && player.getMainHandItem().isEmpty(),
            "using the Loom with a woven orb docked it and took it from the hand");

        int revision = loom.revision();
        InteractionResult opened = state.use(level, player, InteractionHand.MAIN_HAND, side);
        check("AN", opened.consumesAction() && !loom.orb().isEmpty() && loom.revision() == revision,
            "using a side with the orb docked left it there (the editor's path)");

        CompoundTag sent = loom.getUpdateTag().getCompound("Orb");
        ItemStack shown = ItemStack.of(sent);
        CompoundTag shownWeave = sent.getCompound("tag").getCompound(WovenOrbItem.TAG_WEAVE);
        int keptPatterns = Math.min(HexLoomBlockEntity.DISPLAY_PATTERNS,
            HexweaveNbt.peekPatternCount(loom.orb().getTag().getCompound(WovenOrbItem.TAG_WEAVE)));
        check("AN", shown.getItem() instanceof WovenOrbItem && WovenOrbItem.hasWeave(shown)
                && WovenOrbItem.listensFor(shown).isEmpty() && keptPatterns > 0
                && HexweaveNbt.peekPatternCount(shownWeave) == keptPatterns
                && WovenOrbItem.hasWeave(loom.orb()) && !WovenOrbItem.weave(loom.orb()).threads().isEmpty(),
            "watching clients are sent the orb with its first few patterns to show but not its weave ("
                + sent.toString().length() + " characters); the Loom's own orb keeps the weave");

        InteractionResult taken = state.use(level, player, InteractionHand.MAIN_HAND, top);
        CompoundTag emptied = loom.getUpdateTag();
        check("AN", taken.consumesAction() && loom.orb().isEmpty() && loom.revision() != revision
                && WovenOrbItem.hasWeave(player.getMainHandItem()) && !emptied.isEmpty()
                && ItemStack.of(emptied.getCompound("Orb")).isEmpty(),
            "using the top handed the orb back, weave intact, and the update clients get still says the dock is "
                + "empty: " + emptied);

        AABB facingNorth = state.setValue(HexLoomBlock.FACING, Direction.NORTH)
            .getShape(level, pos, CollisionContext.empty()).bounds();
        AABB facingEast = state.setValue(HexLoomBlock.FACING, Direction.EAST)
            .getShape(level, pos, CollisionContext.empty()).bounds();
        check("AN", facingNorth.getXsize() > facingNorth.getZsize()
                && Math.abs(facingEast.getXsize() - facingNorth.getZsize()) < 1e-6
                && Math.abs(facingEast.getZsize() - facingNorth.getXsize()) < 1e-6,
            "the frame turns with the Loom: facing north it is " + facingNorth + ", facing east " + facingEast);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private @Nullable WeaveDraft opened(String test, HexLoomBlockEntity loom) {
        WeaveDraft draft = HexLoomEditing.opening(loom.orb()).draft();
        if (draft == null) {
            check(test, false, "the orb on the Loom would not open");
        }
        return draft;
    }

    private HexLoomEditing.SaveResult save(HexLoomBlockEntity loom, WeaveDraft draft) {
        return HexLoomEditing.save(player, loom.getBlockPos(), loom.revision(),
            HexweaveNbt.write(draft.toWeave(), draft.layout()));
    }

    private void refusedSave(String test, HexLoomBlockEntity loom, String what, CompoundTag submitted) {
        CompoundTag before = loom.orb().getOrCreateTag().copy();
        int revision = loom.revision();
        HexLoomEditing.SaveResult result = HexLoomEditing.save(player, loom.getBlockPos(), revision, submitted);
        check(test, !result.saved() && loom.revision() == revision && loom.orb().getOrCreateTag().equals(before),
            what + " was refused and the orb left untouched: " + result.message().getString());
    }

    private @Nullable Activation fire(ItemStack orb) {
        return WovenOrbCasting.activate(player, InteractionHand.OFF_HAND, orb.copy()).activation();
    }

    private static boolean weave(WeaveDraft draft, String graph, List<List<HexPattern>> bodies) {
        boolean ok = true;
        for (int id = 2; id <= bodies.size(); id++) {
            ok &= draft.addThread(new WeaveLayout.Position((id - 1) * WeaveLayout.COLUMN_STEP, 0)) != null;
        }
        List<WeaveLink> links = new ArrayList<>();
        for (String edge : graph.split(",")) {
            String[] ends = edge.split(">");
            links.add(new WeaveLink(Integer.parseInt(ends[0]), Integer.parseInt(ends[1])));
        }
        for (WeaveLink link : links) {
            ok &= draft.link(link.from(), link.to());
        }
        for (TriggerLink link : List.copyOf(draft.triggerLinks())) {
            draft.unlink(link);
        }
        for (int id = 1; id <= bodies.size(); id++) {
            int thread = id;
            if (links.stream().noneMatch(link -> link.to() == thread)) {
                ok &= draft.linkTrigger(1, id);
            }
            ok &= draw(draft.thread(id), bodies.get(id - 1).toArray(HexPattern[]::new));
        }
        return ok;
    }

    private static boolean draw(ThreadDraft thread, HexPattern... patterns) {
        int radius = ThreadDraft.RADIUS;
        next:
        for (HexPattern pattern : patterns) {
            for (int r = -radius; r <= radius; r++) {
                for (int q = -radius; q <= radius; q++) {
                    if (thread.add(pattern, new HexCoord(q, r))) {
                        continue next;
                    }
                }
            }
            return false;
        }
        return true;
    }

    private static WeaveDraft scattered(int threads, int perThread, Random random) {
        WeaveDraft draft = WeaveDraft.blank();
        for (int i = 1; i < threads; i++) {
            draft.addThread(new WeaveLayout.Position(i % 8 * WeaveLayout.COLUMN_STEP, i / 8 * WeaveLayout.ROW_STEP));
        }
        for (ThreadDraft thread : draft.threads()) {
            for (int attempt = 0; thread.entries().size() < perThread && attempt < 2000; attempt++) {
                int radius = ThreadDraft.RADIUS;
                thread.add(randomPattern(random), new HexCoord(random.nextInt(2 * radius + 1) - radius,
                    random.nextInt(2 * radius + 1) - radius));
            }
        }
        return draft;
    }

    private static HexPattern randomPattern(Random random) {
        while (true) {
            StringBuilder angles = new StringBuilder();
            int length = 2 + random.nextInt(7);
            for (int i = 0; i < length; i++) {
                angles.append("wqaed".charAt(random.nextInt(5)));
            }
            try {
                return HexPattern.fromAngles(angles.toString(), HexDir.values()[random.nextInt(6)]);
            } catch (IllegalArgumentException | IllegalStateException e) {
            }
        }
    }

    private static int patternCount(WeaveDraft draft) {
        return draft.threads().stream().mapToInt(thread -> thread.entries().size()).sum();
    }

    private static List<Integer> ids(WeaveDraft draft) {
        return draft.threads().stream().map(ThreadDraft::id).toList();
    }

    private static CompoundTag weaveTag(ItemStack orb) {
        return orb.getOrCreateTag().getCompound(WovenOrbItem.TAG_WEAVE);
    }

    private static CompoundTag withLink(CompoundTag weave, int from, int to) {
        CompoundTag copy = weave.copy();
        CompoundTag link = new CompoundTag();
        link.putInt("From", from);
        link.putInt("To", to);
        copy.getList("Links", Tag.TAG_COMPOUND).add(link);
        return copy;
    }

    private static CompoundTag layoutEntry(int id, float x, float y) {
        CompoundTag entry = new CompoundTag();
        entry.putInt("Id", id);
        entry.putFloat("X", x);
        entry.putFloat("Y", y);
        return entry;
    }

    private static int rawSize(CompoundTag tag) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            NbtIo.write(tag, out);
        } catch (IOException e) {
            return -1;
        }
        return bytes.size();
    }

    private void persistToChest() {
        clearHotbar();
        write(1, number(3));
        write(2, number(1), ADD);
        write(3, STACK_LEN);
        ItemStack stick = new ItemStack(Items.STICK);
        HexweaveCommand.store(stick, HexweaveCommand.fromHotbar(player, "1>2,1>3"));

        BlockPos chest = chestPos();
        level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(chest) instanceof ChestBlockEntity box) {
            box.setItem(0, stick);
            box.setChanged();
            check("R", true, "stored the weave on a stick in a chest at " + chest.toShortString()
                + "; restart the server, then run /hexwright weave selftest restored");
        } else {
            check("R", false, "no chest at " + chest.toShortString());
        }
    }

    private void readFromChest() {
        BlockPos chest = chestPos();
        if (!(level.getBlockEntity(chest) instanceof ChestBlockEntity box)) {
            check("R", false, "no chest at " + chest.toShortString() + " - run /hexwright weave selftest persist first");
            return;
        }
        try {
            Hexweave weave = HexweaveCommand.stored(box.getItem(0));
            Activation activation = activate(weave, new EntityIota(pig), null, new WeaveExecutionBudget());
            check("R", threadOrder(activation).equals(List.of(1, 2, 3))
                    && value(top(activation.runs().get(1))) == 4.0
                    && activation.runs().get(2).finalStack().size() == 2,
                "after the restart the chest's weave loaded (" + weave.threads().size() + " Threads, "
                    + weave.links().size() + " links) and ran " + trace(activation));
        } catch (IllegalArgumentException e) {
            check("R", false, "the chest's weave would not load: " + e.getMessage());
        } finally {
            box.clearContent();
            level.setBlockAndUpdate(chest, Blocks.AIR.defaultBlockState());
        }
    }

    private BlockPos chestPos() {
        return ground().offset(3, 0, 0);
    }

    private BlockPos ground() {
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, level.getSharedSpawnPos());
    }

    private CompoundTag storedDiamond() {
        clearHotbar();
        write(1, number(1));
        write(2, number(2));
        write(3, number(3));
        write(4, STACK_LEN);
        return HexweaveNbt.write(HexweaveCommand.fromHotbar(player, "1>2,1>3,2>4,3>4"));
    }

    private static ListTag threads(CompoundTag tag) {
        return tag.getList("Threads", Tag.TAG_COMPOUND);
    }

    private static ListTag links(CompoundTag tag) {
        return tag.getList("Links", Tag.TAG_COMPOUND);
    }

    private static ListTag triggers(CompoundTag tag) {
        return tag.getList("Triggers", Tag.TAG_COMPOUND);
    }

    private static List<WeaveTrigger> onUse(Integer... threads) {
        return List.of(new WeaveTrigger(1, WeaveTrigger.Kind.ON_USE, List.of(threads)));
    }

    private static CompoundTag patternEntry(CompoundTag tag) {
        return threads(tag).getCompound(0).getList("Patterns", Tag.TAG_COMPOUND).getCompound(0);
    }

    private static CompoundTag pattern(CompoundTag tag) {
        return patternEntry(tag).getCompound("Pattern");
    }

    private static CompoundTag threadEntry(int id, List<CompoundTag> patterns) {
        CompoundTag entry = new CompoundTag();
        entry.putInt("Id", id);
        ListTag list = new ListTag();
        for (CompoundTag pattern : patterns) {
            list.add(pattern.copy());
        }
        entry.put("Patterns", list);
        return entry;
    }

    private void refusedLoad(String test, String what, CompoundTag tag) {
        long started = System.nanoTime();
        try {
            HexweaveNbt.read(tag);
            check(test, false, what + " loaded");
        } catch (IllegalArgumentException e) {
            long micros = (System.nanoTime() - started) / 1000;
            check(test, true, what + " refused in " + micros + " us: " + e.getMessage());
        } catch (RuntimeException e) {
            check(test, false, what + " threw " + e);
        }
    }

    private static boolean sameStructure(Hexweave a, Hexweave b) {
        return List.copyOf(a.threads()).equals(List.copyOf(b.threads())) && a.links().equals(b.links())
            && List.copyOf(a.triggers()).equals(List.copyOf(b.triggers()));
    }

    private static List<String> trace(Activation activation) {
        List<String> steps = new ArrayList<>();
        for (Run run : activation.runs()) {
            steps.add(run.threadId() + ":" + run.outcome() + ":" + run.finalStack().size() + ":"
                + (run.emitted() == null ? "-" : IotaType.serialize(run.emitted()).toString()));
        }
        return steps;
    }

    private static List<Double> thanatos(Activation activation) {
        List<Double> left = new ArrayList<>();
        for (Run run : activation.runs()) {
            left.add(run.finalStack().size() > 1 ? value(run.finalStack().get(1)) : Double.NaN);
        }
        return left;
    }

    private Activation activate(String graph, @Nullable Iota seed) {
        return activate(graph, seed, null);
    }

    private Activation activate(String graph, @Nullable Iota seed, @Nullable IntSupplier opBudget) {
        return activate(HexweaveCommand.fromHotbar(player, graph), seed, opBudget, new WeaveExecutionBudget());
    }

    private Activation activate(Hexweave weave, @Nullable Iota seed, @Nullable IntSupplier opBudget,
                                WeaveExecutionBudget budget) {
        environments.clear();
        Supplier<CastingEnvironment> factory = () -> {
            CastingEnvironment env = opBudget == null
                ? new PocketCasterCastEnv(player, InteractionHand.MAIN_HAND)
                : new PocketCasterCastEnv(player, InteractionHand.MAIN_HAND) {
                    @Override
                    public int maxOpCount() {
                        return opBudget.getAsInt();
                    }
                };
            if (probing) {
                env.addExtension(new DepthProbe(javaDepths));
            }
            environments.add(env);
            return env;
        };
        return HexweaveCasting.activate(weave, weave.starts(WeaveTrigger.Kind.ON_USE), level, factory, seed, budget);
    }

    private void refused(String test, String what, Runnable attempt) {
        try {
            attempt.run();
            check(test, false, what + " was accepted");
        } catch (IllegalArgumentException e) {
            check(test, true, what + " refused: " + e.getMessage());
        }
    }

    private void check(String test, boolean ok, String detail) {
        if (ok) {
            source.sendSuccess(() -> Component.literal("[" + test + "] pass - " + detail), false);
        } else {
            failures++;
            source.sendFailure(Component.literal("[" + test + "] FAIL - " + detail));
        }
    }

    private void clearHotbar() {
        for (int slot = 0; slot < 9; slot++) {
            player.getInventory().setItem(slot, ItemStack.EMPTY);
        }
    }

    private void write(int slot, HexPattern... patterns) {
        List<Iota> iotas = new ArrayList<>(patterns.length);
        for (HexPattern pattern : patterns) {
            iotas.add(new PatternIota(pattern));
        }
        writeIota(slot, new ListIota(iotas));
    }

    private void writeIota(int slot, Iota iota) {
        ItemStack focus = new ItemStack(HexItems.FOCUS);
        ADIotaHolder holder = IXplatAbstractions.INSTANCE.findDataHolder(focus);
        if (holder != null) {
            holder.writeIota(iota, false);
        }
        player.getInventory().setItem(slot - 1, focus);
    }

    private int dust() {
        return player.getInventory().countItem(HexItems.AMETHYST_DUST);
    }

    private static HexPattern number(int n) {
        return HexPattern.fromAngles("aqaa" + "w".repeat(n), HexDir.EAST);
    }

    private static List<Integer> threadOrder(Activation activation) {
        return activation.runs().stream().map(Run::threadId).toList();
    }

    private static @Nullable Iota top(Run run) {
        return run.finalStack().isEmpty() ? null : run.finalStack().get(run.finalStack().size() - 1);
    }

    private static double value(@Nullable Iota iota) {
        return iota instanceof DoubleIota number ? number.getDouble() : Double.NaN;
    }

    private record BudgetProbe(CastingEnvironment env, List<WeaveExecutionBudget> seen)
        implements CastingEnvironmentComponent.PostExecution {

        private static final Key<BudgetProbe> KEY = new Key<>() {
        };

        @Override
        public Key<?> getKey() {
            return KEY;
        }

        @Override
        public void onPostExecution(CastResult result) {
            seen.add(WeaveExecutionBudget.of(env));
        }
    }

    private record DepthProbe(List<Integer> depths) implements CastingEnvironmentComponent.PostExecution {

        private static final Key<DepthProbe> KEY = new Key<>() {
        };

        @Override
        public Key<?> getKey() {
            return KEY;
        }

        @Override
        public void onPostExecution(CastResult result) {
            depths.add(new Throwable().getStackTrace().length);
        }
    }
}
