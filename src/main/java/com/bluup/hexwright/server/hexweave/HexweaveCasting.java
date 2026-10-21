package com.bluup.hexwright.server.hexweave;

import at.petrak.hexcasting.api.casting.eval.CastResult;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironmentComponent;
import at.petrak.hexcasting.api.casting.eval.ExecutionClientView;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.mishaps.MishapEvalTooMuch;
import at.petrak.hexcasting.common.lib.hex.HexIotaTypes;
import com.bluup.hexwright.HexwrightDebug;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;

public final class HexweaveCasting {

    public enum Stop {
        FINISHED,
        INVOCATION_CAP,
        OP_BUDGET
    }

    public enum Outcome {
        COMPLETED,
        MISHAPPED,
        OUT_OF_OPS,
        UNFINISHED,
        CRASHED
    }

    public record Run(int threadId, @Nullable Integer source, @Nullable Iota input, long opsBefore,
                      long opsAfter, Outcome outcome, List<Iota> finalStack, @Nullable Iota emitted,
                      List<Integer> targets) {
    }

    public record Activation(List<Run> runs, Stop stop, long opsConsumed, int dropped) {
    }

    private record Invocation(int threadId, @Nullable Integer source, @Nullable CompoundTag payload) {
    }

    private HexweaveCasting() {
    }

    public static Activation activate(Hexweave weave, List<Integer> starts, ServerLevel level,
                                      Supplier<? extends CastingEnvironment> environments,
                                      @Nullable Iota seed) {
        return activate(weave, starts, level, environments, seed, new WeaveExecutionBudget());
    }

    public static Activation activate(Hexweave weave, List<Integer> starts, ServerLevel level,
                                      Supplier<? extends CastingEnvironment> environments,
                                      @Nullable Iota seed, WeaveExecutionBudget budget) {
        CompoundTag seedTag = seed == null ? null : IotaType.serialize(seed);
        Deque<Invocation> pending = new ArrayDeque<>();
        for (int id : starts) {
            pending.add(new Invocation(id, null, seedTag));
        }
        if (HexwrightDebug.on(HexwrightDebug.WEAVE)) {
            HexwrightDebug.log(HexwrightDebug.WEAVE,
                "[weave] activation of {} Threads, starting {}, seed {}, budget already spent {} ops / {} runs",
                weave.threads().size(), starts, describe(seedTag), budget.opsConsumed(), budget.invocations());
        }

        List<Run> runs = new ArrayList<>();
        Stop stop = Stop.FINISHED;
        while (!pending.isEmpty()) {
            if (budget.invocationsExhausted()) {
                stop = Stop.INVOCATION_CAP;
                HexwrightDebug.log(HexwrightDebug.WEAVE,
                    "[weave] stopped at the invocation cap ({}) with {} invocations still pending",
                    WeaveExecutionBudget.MAX_INVOCATIONS, pending.size());
                break;
            }
            CastingEnvironment env = environments.get();
            if (budget.opsExhausted(env.maxOpCount())) {
                stop = Stop.OP_BUDGET;
                HexwrightDebug.log(HexwrightDebug.WEAVE,
                    "[weave] stopped with the op budget spent ({} of {}), {} invocations still pending",
                    budget.opsConsumed(), env.maxOpCount(), pending.size());
                break;
            }
            Invocation next = pending.poll();
            long opsBefore = budget.opsConsumed();
            env.addExtension(budget);
            Run run = run(weave, next, env, level, opsBefore, runs.size() + 1);
            runs.add(run);
            budget.recordRun(opsBefore, run.opsAfter(), run.outcome() == Outcome.OUT_OF_OPS);
            if (run.outcome() == Outcome.OUT_OF_OPS) {
                stop = Stop.OP_BUDGET;
                HexwrightDebug.log(HexwrightDebug.WEAVE,
                    "[weave] stopped: Thread {} ran out of the shared op budget, {} invocations still pending",
                    run.threadId(), pending.size());
                break;
            }
            if (run.emitted() != null) {
                CompoundTag payload = IotaType.serialize(run.emitted());
                for (int target : run.targets()) {
                    pending.add(new Invocation(target, run.threadId(), payload));
                }
            }
        }

        HexwrightDebug.log(HexwrightDebug.WEAVE,
            "[weave] activation ended ({}): {} invocations; budget now {} ops / {} runs",
            stop, runs.size(), budget.opsConsumed(), budget.invocations());
        return new Activation(List.copyOf(runs), stop, budget.opsConsumed(), pending.size());
    }

    private static Run run(Hexweave weave, Invocation invocation, CastingEnvironment env, ServerLevel level,
                           long opsBefore, int number) {
        WeaveThread thread = weave.thread(invocation.threadId());
        Iota input = null;
        try {
            input = invocation.payload() == null ? null : IotaType.deserialize(invocation.payload().copy(), level);
            List<Iota> stack = input == null ? List.of() : List.of(input);
            CastingImage image = new CastingImage().copy(stack, 0, List.of(), false, opsBefore, new CompoundTag());

            MishapWatch watch = new MishapWatch();
            env.addExtension(watch);
            CastingVM vm = new CastingVM(image, env);
            ExecutionClientView view = vm.queueExecuteAndWrapIotas(thread.program(level), level);
            CastingImage end = vm.getImage();

            Outcome outcome;
            if (watch.outOfOps) {
                outcome = Outcome.OUT_OF_OPS;
            } else if (watch.mishapped || !view.getResolutionType().getSuccess()) {
                outcome = Outcome.MISHAPPED;
            } else if (end.getParenCount() > 0 || end.getEscapeNext()) {
                outcome = Outcome.UNFINISHED;
            } else {
                outcome = Outcome.COMPLETED;
            }

            List<Iota> finalStack = end.getStack();
            List<Integer> linked = weave.targetsOf(thread.id());
            boolean fires = !linked.isEmpty() && outcome == Outcome.COMPLETED && !finalStack.isEmpty();
            Iota emitted = fires ? finalStack.get(finalStack.size() - 1) : null;
            List<Integer> targets = fires ? linked : List.of();

            Run run = new Run(thread.id(), invocation.source(), input, opsBefore, end.getOpsConsumed(), outcome,
                finalStack, emitted, targets);
            if (HexwrightDebug.on(HexwrightDebug.WEAVE)) {
                logRun(number, thread, invocation, run, !linked.isEmpty());
            }
            return run;
        } catch (RuntimeException e) {
            HexwrightDebug.log(HexwrightDebug.WEAVE, "[weave] #{} Thread {} threw", number, thread.id(), e);
            return new Run(thread.id(), invocation.source(), input, opsBefore, opsBefore, Outcome.CRASHED,
                List.of(), null, List.of());
        }
    }

    private static void logRun(int number, WeaveThread thread, Invocation invocation, Run run, boolean linked) {
        String tail;
        if (!linked) {
            tail = "Tail unlinked";
        } else if (run.emitted() != null) {
            tail = "emitted " + describe(IotaType.serialize(run.emitted())) + " -> " + run.targets();
        } else if (run.outcome() != Outcome.COMPLETED) {
            tail = "Tail suppressed (" + run.outcome() + ")";
        } else {
            tail = "Tail suppressed (empty stack)";
        }
        HexwrightDebug.log(HexwrightDebug.WEAVE,
            "[weave] #{} Thread {} from {}: input {} (instance {}), ops {} -> {}, {}, final stack {}, {}",
            number, thread.id(), invocation.source() == null ? "a trigger" : "Thread " + invocation.source(),
            describe(invocation.payload()),
            run.input() == null ? "-" : Integer.toHexString(System.identityHashCode(run.input())),
            run.opsBefore(), run.opsAfter(), run.outcome(), run.finalStack().size(), tail);
    }

    private static String describe(@Nullable CompoundTag iota) {
        if (iota == null) {
            return "none";
        }
        String text = IotaType.getDisplay(iota).getString();
        if (text.length() > 60) {
            text = text.substring(0, 57) + "...";
        }
        return iota.getString(HexIotaTypes.KEY_TYPE) + " '" + text + "'";
    }

    private static final class MishapWatch implements CastingEnvironmentComponent.PostExecution {

        private static final Key<MishapWatch> KEY = new Key<>() {
        };

        private boolean mishapped;
        private boolean outOfOps;

        @Override
        public Key<?> getKey() {
            return KEY;
        }

        @Override
        public void onPostExecution(CastResult result) {
            for (OperatorSideEffect effect : result.getSideEffects()) {
                if (effect instanceof OperatorSideEffect.DoMishap doMishap) {
                    mishapped = true;
                    outOfOps |= doMishap.getMishap() instanceof MishapEvalTooMuch;
                }
            }
        }
    }
}
