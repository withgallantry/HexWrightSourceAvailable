package com.bluup.hexwright.server.hexpatterns;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.RenderedSpell;
import at.petrak.hexcasting.api.casting.castables.ConstMediaAction;
import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.iota.NullIota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadLocation;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.common.staff_assembly.calc.IngredientCategory;
import com.bluup.hexwright.server.block.CoalescerBlockEntity;
import com.bluup.hexwright.server.crucible.EssencePouchData;
import com.bluup.hexwright.server.item.EndlessPouchItem;
import com.bluup.hexwright.server.network.EssenceNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import ram.talia.moreiotas.api.casting.iota.ItemTypeIota;
import ram.talia.moreiotas.api.casting.iota.StringIota;

import java.util.ArrayList;
import java.util.List;

public final class HexwrightActions {

    private HexwrightActions() {
    }

    public static void register() {
        Registry<ActionRegistryEntry> registry = IXplatAbstractions.INSTANCE.getActionRegistry();

        Registry.register(registry, Hexwright.id("essence_purification"),
            new ActionRegistryEntry(HexPattern.fromAngles("ewedadadew", HexDir.WEST), ESSENCE_PURIFICATION));
        Registry.register(registry, Hexwright.id("aspect_distillation"),
            new ActionRegistryEntry(HexPattern.fromAngles("ewedadadeade", HexDir.WEST), ASPECT_DISTILLATION));
        Registry.register(registry, Hexwright.id("coalescing_gambit"),
            new ActionRegistryEntry(HexPattern.fromAngles("dadadeewe", HexDir.NORTH_EAST), COALESCING_GAMBIT));
        Registry.register(registry, Hexwright.id("artisans_selection"),
            new ActionRegistryEntry(HexPattern.fromAngles("dadadeewewdwwwd", HexDir.NORTH_EAST), ARTISANS_SELECTION));
    }

    private static final ConstMediaAction ESSENCE_PURIFICATION = new HexwrightConstMediaAction() {
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
            Vec3 vec = OperatorUtils.getVec3(args, 0, getArgc());
            env.assertVecInRange(vec);
            ItemStack pouch = EssenceNetwork.pouchAt(env.getWorld(), BlockPos.containing(vec));
            boolean isPouch = pouch.getItem() instanceof EndlessPouchItem;
            List<Iota> aspects = new ArrayList<>();
            for (IngredientCategory aspect : IngredientCategory.values()) {
                boolean held = isPouch && EssencePouchData.get(pouch, aspect) > 0.0;
                aspects.add(held
                    ? StringIota.makeUnchecked(EndlessPouchItem.aspectName(aspect).getString())
                    : new NullIota());
            }
            return List.of(new ListIota(aspects));
        }
    };

    private static final ConstMediaAction ASPECT_DISTILLATION = new HexwrightConstMediaAction() {
        @Override
        public int getArgc() {
            return 2;
        }

        @Override
        public long getMediaCost() {
            return 0;
        }

        @Override
        public List<Iota> execute(List<? extends Iota> args, CastingEnvironment env) {
            Vec3 vec = OperatorUtils.getVec3(args, 0, getArgc());
            int ordinal = OperatorUtils.getPositiveIntUnderInclusive(
                args, 1, IngredientCategory.values().length - 1, getArgc());
            env.assertVecInRange(vec);
            ItemStack pouch = EssenceNetwork.pouchAt(env.getWorld(), BlockPos.containing(vec));
            IngredientCategory aspect = IngredientCategory.values()[ordinal];
            double amount = pouch.getItem() instanceof EndlessPouchItem
                ? EssencePouchData.get(pouch, aspect) : 0.0;
            return List.of(new DoubleIota(amount));
        }
    };

    private static final SpellAction COALESCING_GAMBIT = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 2;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            Vec3 vec = OperatorUtils.getVec3(args, 0, getArgc());
            int amount = OperatorUtils.getPositiveIntUnderInclusive(args, 1, 64, getArgc());
            env.assertVecInRange(vec);
            BlockPos pos = BlockPos.containing(vec);
            if (!(env.getWorld().getBlockEntity(pos) instanceof CoalescerBlockEntity coalescer)) {
                throw new MishapBadLocation(vec, "coalescer");
            }
            return new Result(new RenderedSpell() {
                @Override
                public void cast(CastingEnvironment castEnv) {
                    coalescer.workOnce(amount);
                }

                @Override
                public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                    cast(castEnv);
                    return image;
                }
            }, MediaConstants.DUST_UNIT, List.of(ParticleSpray.burst(Vec3.atCenterOf(pos), 0.5, 10)), 1L);
        }
    };

    private static final SpellAction ARTISANS_SELECTION = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 2;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            Vec3 vec = OperatorUtils.getVec3(args, 0, getArgc());
            Iota raw = args.get(1);
            env.assertVecInRange(vec);
            BlockPos pos = BlockPos.containing(vec);
            if (!(env.getWorld().getBlockEntity(pos) instanceof CoalescerBlockEntity coalescer)) {
                throw new MishapBadLocation(vec, "coalescer");
            }

            if (!(raw instanceof ItemTypeIota typeIota)) {
                throw MishapInvalidIota.ofType(raw, 0, "hexwright.coalescer_seed");
            }
            Item item = typeIota.getItem();
            if (item == Items.AIR) {
                throw MishapInvalidIota.ofType(raw, 0, "hexwright.coalescer_seed");
            }

            return new Result(new RenderedSpell() {
                @Override
                public void cast(CastingEnvironment castEnv) {
                    coalescer.seedFromType(item);
                }

                @Override
                public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                    cast(castEnv);
                    return image;
                }
            }, MediaConstants.DUST_UNIT / 10, List.of(ParticleSpray.burst(Vec3.atCenterOf(pos), 0.5, 10)), 1L);
        }
    };
}
