package com.bluup.hexwright.server.remnant;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadOffhandItem;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughMedia;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.common.remnant.Remnant;
import com.bluup.hexwright.common.remnant.RemnantSnapshot;
import com.bluup.hexwright.common.remnant.RemnantType;
import com.bluup.hexwright.server.fluid.TankRemnants;
import com.bluup.hexwright.server.hexpatterns.HexwrightConstMediaAction;
import net.minecraft.core.Registry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import ram.talia.moreiotas.api.casting.iota.ItemStackIota;
import ram.talia.moreiotas.api.casting.iota.StringIota;

import java.util.ArrayList;
import java.util.List;

public final class RemnantActions {

    private static final long PURIFICATION_COST = MediaConstants.DUST_UNIT;

    private static final long DISTILLATION_COST = MediaConstants.DUST_UNIT / 4;

    private RemnantActions() {
    }

    public static void register() {
        RemnantDrawState.register();

        Registry<ActionRegistryEntry> registry = IXplatAbstractions.INSTANCE.getActionRegistry();

        Registry.register(registry, Hexwright.id("remnant_purification"),
            new ActionRegistryEntry(
                HexPattern.fromAngles("wqwqwqwqweawwa", HexDir.SOUTH_WEST), REMNANT_PURIFICATION));
        Registry.register(registry, Hexwright.id("remnant_distillation"),
            new ActionRegistryEntry(
                HexPattern.fromAngles("wqwqwqwqweawwaweqqqqe", HexDir.SOUTH_WEST), REMNANT_DISTILLATION));
        Registry.register(registry, Hexwright.id("remnants_reflection"),
            new ActionRegistryEntry(
                HexPattern.fromAngles("wqwqwqwqweawwawwaw", HexDir.SOUTH_WEST), REMNANTS_REFLECTION));
        Registry.register(registry, Hexwright.id("remnant_decanting"),
            new ActionRegistryEntry(
                HexPattern.fromAngles("wqwqwqwqweawwaweqqqqeq", HexDir.SOUTH_WEST), REMNANT_DECANTING));
        Registry.register(registry, Hexwright.id("vessel_purification"),
            new ActionRegistryEntry(
                HexPattern.fromAngles("wqwqwqwqweawwawwawaw", HexDir.SOUTH_WEST), VESSEL_PURIFICATION));
    }

    private static final ConstMediaAction REMNANTS_REFLECTION = new HexwrightConstMediaAction() {
        @Override
        public int getArgc() {
            return 1;
        }

        @Override
        public long getMediaCost() {
            return 0;
        }

        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            if (!(args.get(0) instanceof RemnantsIota remnantsIota)) {
                throw MishapInvalidIota.ofType(args.get(0), 0, "hexwright.remnants");
            }
            List<Iota> names = new ArrayList<>();
            for (Remnant remnant : remnantsIota.getSnapshot().remnants()) {
                names.add(StringIota.makeUnchecked(remnant.type().name()));
            }
            return List.of(new ListIota(names));
        }
    };

    private static final ConstMediaAction VESSEL_PURIFICATION = new HexwrightConstMediaAction() {
        @Override
        public int getArgc() {
            return 1;
        }

        @Override
        public long getMediaCost() {
            return 0;
        }

        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            if (!(args.get(0) instanceof ItemStackIota stackIota)
                || !RemnantVessels.isVessel(stackIota.getItemStack())) {
                throw MishapInvalidIota.ofType(args.get(0), 0, "hexwright.bottle_item");
            }

            TankRemnants mix = BottleData.getMixture(stackIota.getItemStack());
            if (mix.kinds() > 1) {
                throw MishapInvalidIota.ofType(args.get(0), 0, "hexwright.bottle_single");
            }
            if (mix.isEmpty()) {
                return List.of(StringIota.makeUnchecked(""), new DoubleIota(0.0));
            }

            Remnant held = mix.contents().get(0);
            return List.of(
                StringIota.makeUnchecked(held.type().name()),
                new DoubleIota(held.drams()));
        }
    };

    private static final ConstMediaAction REMNANT_DECANTING = new HexwrightConstMediaAction() {
        @Override
        public int getArgc() {
            return 1;
        }

        @Override
        public long getMediaCost() {
            return 0;
        }

        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            double wanted = OperatorUtils.getPositiveDouble(args, 0, getArgc());

            CastingEnvironment.HeldItemInfo found =
                env.getHeldItemToOperateOn(stack -> RemnantVessels.isVessel(stack)
                    && BottleData.getMixture(stack).kinds() == 1);
            if (found == null) {
                CastingEnvironment.HeldItemInfo anyBottle =
                    env.getHeldItemToOperateOn(RemnantVessels::isVessel);
                ItemStack blamed = anyBottle == null ? ItemStack.EMPTY : anyBottle.stack();
                TankRemnants mix = BottleData.getMixture(blamed);
                throw MishapBadOffhandItem.of(blamed, mix.kinds() > 1
                    ? "hexwright.bottle_blend"
                    : "hexwright.bottle_filled");
            }

            ItemStack bottle = found.stack();
            Remnant drawn = BottleData.draw(bottle, wanted);
            if (drawn == null || drawn.isEmpty()) {
                throw MishapBadOffhandItem.of(bottle, "hexwright.bottle_filled");
            }
            return List.of(RemnantIota.mint(drawn, env.getWorld().getServer()));
        }
    };

    private static final ConstMediaAction REMNANT_PURIFICATION = new HexwrightConstMediaAction() {
        @Override
        public int getArgc() {
            return 1;
        }

        @Override
        public long getMediaCost() {
            return PURIFICATION_COST;
        }

        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            Entity target = OperatorUtils.getEntity(args, 0, getArgc());
            env.assertEntityInRange(target);
            if (!(target instanceof LivingEntity victim)) {
                throw MishapInvalidIota.ofType(args.get(0), 0, "hexwright.living_corpse");
            }

            RemnantExtraction.Refusal refusal = RemnantExtraction.refuse(victim);
            if (refusal != null) {
                throw MishapInvalidIota.ofType(args.get(0), 0, switch (refusal) {
                    case LIVING -> "hexwright.corpse_living";
                    case COLD -> "hexwright.corpse_cold";
                    case CLAIMED -> "hexwright.corpse_claimed";
                    case BARREN -> "hexwright.corpse_barren";
                });
            }

            if (env.extractMedia(getMediaCost(), true) > 0) {
                throw new MishapNotEnoughMedia(getMediaCost());
            }

            long now = env.getWorld().getServer().overworld().getGameTime();
            RemnantSnapshot snapshot = RemnantExtraction.reap(victim, now);
            if (snapshot == null) {
                throw MishapInvalidIota.ofType(args.get(0), 0, "hexwright.corpse_barren");
            }

            announce(env.getWorld(), victim);
            return List.of(new RemnantsIota(snapshot));
        }
    };

    private static final ConstMediaAction REMNANT_DISTILLATION = new HexwrightConstMediaAction() {
        @Override
        public int getArgc() {
            return 2;
        }

        @Override
        public long getMediaCost() {
            return DISTILLATION_COST;
        }

        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            if (!(args.get(0) instanceof RemnantsIota remnantsIota)) {
                throw MishapInvalidIota.ofType(args.get(0), 1, "hexwright.remnants");
            }
            RemnantSnapshot snapshot = remnantsIota.getSnapshot();

            Remnant chosen = select(args, snapshot);
            if (chosen.isEmpty()) {
                return List.of(RemnantIota.empty());
            }

            long now = env.getWorld().getServer().overworld().getGameTime();
            double potency = snapshot.potencyAt(now);
            if (potency <= 0.0) {
                throw MishapInvalidIota.ofType(args.get(0), 1, "hexwright.remnants_spent");
            }

            RemnantDrawState draws = RemnantDrawState.get(env.getWorld().getServer());
            if (draws.isDrawn(snapshot.id())) {
                throw MishapInvalidIota.ofType(args.get(0), 1, "hexwright.remnants_drawn");
            }

            if (env.extractMedia(getMediaCost(), true) > 0) {
                throw new MishapNotEnoughMedia(getMediaCost());
            }

            draws.markDrawn(snapshot, now);
            return List.of(RemnantIota.mint(
                chosen.withDrams(chosen.drams() * potency), env.getWorld().getServer()));
        }

        private Remnant select(List<? extends Iota> args, RemnantSnapshot snapshot) {
            List<Remnant> remnants = snapshot.remnants();
            Iota selector = args.get(1);

            if (selector instanceof StringIota name) {
                RemnantType wanted = RemnantType.byName(name.getString().trim());
                if (wanted != null) {
                    for (Remnant remnant : remnants) {
                        if (remnant.type() == wanted) {
                            return remnant;
                        }
                    }
                }
                return Remnant.EMPTY;
            }

            if (remnants.isEmpty()) {
                throw MishapInvalidIota.ofType(args.get(0), 1, "hexwright.remnants");
            }

            if (selector instanceof DoubleIota) {
                int index = OperatorUtils.getPositiveIntUnderInclusive(
                    args, 1, Math.max(0, remnants.size() - 1), getArgc());
                return remnants.get(index);
            }

            throw MishapInvalidIota.ofType(selector, 0, "hexwright.remnant_selector");
        }
    };

    private static void announce(ServerLevel level, LivingEntity victim) {
        level.sendParticles(ParticleTypes.SOUL, victim.getX(), victim.getY(0.5), victim.getZ(),
            18, 0.3, 0.4, 0.3, 0.03);
        level.sendParticles(ParticleTypes.ENCHANT, victim.getX(), victim.getY(0.8), victim.getZ(),
            12, 0.25, 0.3, 0.25, 0.4);
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
            SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 0.8f, 0.7f);
    }
}
