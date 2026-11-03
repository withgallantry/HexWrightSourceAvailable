package com.bluup.hexwright.compat.hexic;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.RenderedSpell;
import at.petrak.hexcasting.api.casting.arithmetic.Arithmetic;
import at.petrak.hexcasting.api.casting.arithmetic.operator.Operator;
import at.petrak.hexcasting.api.casting.arithmetic.operator.OperatorBasic;
import at.petrak.hexcasting.api.casting.arithmetic.predicates.IotaMultiPredicate;
import at.petrak.hexcasting.api.casting.arithmetic.predicates.IotaPredicate;
import at.petrak.hexcasting.api.casting.castables.Action;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.OperationResult;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.EntityIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.NullIota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughArgs;
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughMedia;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import at.petrak.hexcasting.common.lib.hex.HexIotaTypes;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.hexpatterns.CastRecipient;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.Registry;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.eu.net.pool.hexic.BoxedView;
import org.eu.net.pool.hexic.BoxedView$;
import org.eu.net.pool.hexic.CostTracker;
import org.eu.net.pool.hexic.InventoryView;
import org.eu.net.pool.hexic.VariantIota;
import ram.talia.moreiotas.api.casting.iota.ItemStackIota;
import ram.talia.moreiotas.common.casting.arithmetic.ItemArithmetic;

import java.util.ArrayList;
import java.util.List;

final class HexicViews {

    private HexicViews() {
    }

    static void register() {
        Registry.register(IXplatAbstractions.INSTANCE.getArithmeticRegistry(),
            Hexwright.id("item_concepts"), CONCEPT_ARITHMETIC);

        Registry.register(IXplatAbstractions.INSTANCE.getActionRegistry(), Hexwright.id("couriers_distillation"),
            new ActionRegistryEntry(HexPattern.fromAngles("weeeewqdda", HexDir.WEST), COURIERS_DISTILLATION));
    }

    private static boolean isItemConcept(Iota iota) {
        return iota instanceof VariantIota<?> concept && concept.data() instanceof ItemVariant;
    }

    private static final Operator CONCEPT_TO_STACK = new OperatorBasic(2,
        IotaMultiPredicate.pair(HexicViews::isItemConcept, IotaPredicate.ofType(HexIotaTypes.DOUBLE))) {
        @Override
        public Iterable<Iota> apply(Iterable<? extends Iota> iotas, CastingEnvironment env) {
            List<Iota> args = new ArrayList<>();
            iotas.forEach(args::add);
            ItemVariant item = (ItemVariant) ((VariantIota<?>) args.get(0)).data();
            int count = OperatorUtils.getPositiveInt(args, 1, 2);
            return List.of(ItemStackIota.createFiltered(item.toStack(count)));
        }
    };

    private static final Arithmetic CONCEPT_ARITHMETIC = new Arithmetic() {
        @Override
        public String arithName() {
            return "hexwright_item_concepts";
        }

        @Override
        public Iterable<HexPattern> opTypes() {
            return List.of(ItemArithmetic.MAKE_ITEM);
        }

        @Override
        public Operator getOperator(HexPattern pattern) {
            return CONCEPT_TO_STACK;
        }
    };

    private static final Action COURIERS_DISTILLATION = (env, image, continuation) -> {
        List<Iota> stack = new ArrayList<>(image.getStack());
        if (stack.size() < 2) {
            throw new MishapNotEnoughArgs(2, stack.size());
        }
        Iota viewArg = stack.get(stack.size() - 2);
        Iota wantedArg = stack.get(stack.size() - 1);
        if (!(viewArg instanceof BoxedView.Instance view)) {
            throw new MishapInvalidIota(viewArg, 1, BoxedView$.MODULE$.typeName());
        }
        if (!(wantedArg instanceof ItemStackIota wantedIota) || wantedIota.getItemStack().isEmpty()) {
            throw MishapInvalidIota.ofType(wantedArg, 0, "moreiotas.item_stack");
        }
        stack.subList(stack.size() - 2, stack.size()).clear();
        ItemStack wanted = wantedIota.getItemStack();
        ItemVariant item = ItemVariant.of(wanted);
        VariantIota<?> concept = (VariantIota<?>) VariantIota.ofItem(item);
        int asked = Math.min(wanted.getCount(), wanted.getMaxStackSize());
        CastingImage used = image.withUsedOp();

        if (Transaction.isOpen()) {
            stack.add(new NullIota());
            return new OperationResult(withStack(used, stack), List.of(), continuation, HexEvalSounds.NORMAL_EXECUTE);
        }
        long cost;
        try (Transaction priced = Transaction.openOuter()) {
            CostTracker tracker = new CostTracker(0L);
            long available = extract(env, view, concept, asked, priced, tracker);
            concept.tax(available, tracker, priced);
            cost = tracker.cost() + MediaConstants.DUST_UNIT;
        }
        if (env.extractMedia(cost, true) > 0) {
            throw new MishapNotEnoughMedia(cost);
        }
        Vec3 dropAt = CastRecipient.dropPos(env);
        return new OperationResult(withStack(used, stack), List.of(
            new OperatorSideEffect.ConsumeMedia(cost),
            new OperatorSideEffect.Particles(ParticleSpray.burst(dropAt, 0.5, 10)),
            new OperatorSideEffect.AttemptSpell(deliver(view, concept, item, asked, dropAt), true, true)),
            continuation, HexEvalSounds.SPELL);
    };

    private static long extract(CastingEnvironment env, BoxedView.Instance view, VariantIota<?> concept, int asked,
                                Transaction transaction, CostTracker tracker) {
        long taken = view.view().tryExtract(concept, asked, new InventoryView.WriteContext(env, transaction, tracker));
        return Math.min(taken, asked);
    }

    private static CastingImage withStack(CastingImage image, List<Iota> stack) {
        return image.copy(stack, image.getParenCount(), image.getParenthesized(),
            image.getEscapeNext(), image.getOpsConsumed(), image.getUserData());
    }

    private static RenderedSpell deliver(BoxedView.Instance view, VariantIota<?> concept, ItemVariant item, int asked,
                                         Vec3 at) {
        return new RenderedSpell() {
            @Override
            public void cast(CastingEnvironment env) {
                materialise(env);
            }

            @Override
            public CastingImage cast(CastingEnvironment env, CastingImage image) {
                List<Iota> stack = new ArrayList<>(image.getStack());
                stack.add(materialise(env));
                return withStack(image, stack);
            }

            private Iota materialise(CastingEnvironment env) {
                if (Transaction.isOpen()) {
                    return new NullIota();
                }
                int taken;
                try (Transaction transfer = Transaction.openOuter()) {
                    taken = (int) extract(env, view, concept, asked, transfer, new CostTracker(0L));
                    transfer.commit();
                }
                if (taken == 0) {
                    return new NullIota();
                }
                ItemEntity dropped = new ItemEntity(env.getWorld(), at.x, at.y, at.z, item.toStack(taken));
                dropped.setPickUpDelay(10);
                env.getWorld().addFreshEntity(dropped);
                return new EntityIota(dropped);
            }
        };
    }
}
