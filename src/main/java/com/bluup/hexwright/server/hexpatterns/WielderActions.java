package com.bluup.hexwright.server.hexpatterns;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.RenderedSpell;
import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadCaster;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadLocation;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.common.staff_assembly.calc.CoreData;
import com.bluup.hexwright.common.staff_assembly.calc.CoreRegistry;
import com.bluup.hexwright.server.staff_assembly.StaffAssemblyData;
import com.bluup.hexwright.server.staff_assembly.StaffGreatSpellData;
import com.bluup.hexwright.server.staff_assembly.StaffPowers;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

public final class WielderActions {

    private WielderActions() {
    }

    public static void register() {
        Registry.register(IXplatAbstractions.INSTANCE.getActionRegistry(), Hexwright.id("wielders_gambit"),
            new ActionRegistryEntry(HexPattern.fromAngles("wewqqwqwqqwaeaqa", HexDir.NORTH_EAST), WIELDERS_GAMBIT));
    }

    private static final SpellAction WIELDERS_GAMBIT = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 1;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            ServerPlayer caster = env.getCaster();
            if (caster == null) {
                throw new MishapBadCaster();
            }

            ItemStack staff = StaffPowers.getCastingStaff(env);
            if (staff.isEmpty()) {
                throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_no_wielded_staff");
            }

            String powerId = corePowerId(staff);
            RenderedSpell spell = switch (powerId == null ? "" : powerId) {
                case StaffPowers.AREA_CAST_POWER_ID -> tuneArea(args, env, caster, staff);
                case StaffPowers.MINOR_AMPLIFY_POWER_ID -> eraseGreatSpell(args, caster, staff);
                default -> throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_no_tunable_core");
            };

            return new Result(spell, MediaConstants.DUST_UNIT / 10,
                List.of(ParticleSpray.burst(caster.position(), 0.5, 10)), 1L);
        }
    };


    private static RenderedSpell tuneArea(List<? extends Iota> args, CastingEnvironment env,
                                          ServerPlayer caster, ItemStack staff) {
        double width = OperatorUtils.getDouble(args, 0, 1);
        double max = StaffPowers.maxAreaWidth(caster, staff);
        if (width < StaffPowers.MIN_AREA_WIDTH || width > max) {
            throw MishapInvalidIota.of(args.get(0), 0, "hexwright.staff_area",
                format(StaffPowers.MIN_AREA_WIDTH), format(max));
        }

        return spell(() -> {
            StaffAssemblyData.setAreaWidth(staff, width);
            caster.displayClientMessage(
                Component.translatable("message.hexwright.core.area_width_set", format(width))
                    .withStyle(ChatFormatting.AQUA),
                true
            );
        });
    }

    private static RenderedSpell eraseGreatSpell(List<? extends Iota> args, ServerPlayer caster, ItemStack staff) {
        List<StaffGreatSpellData.LearnedGreatSpell> learned = StaffGreatSpellData.getLearned(staff);
        if (learned.isEmpty()) {
            throw new MishapBadLocation(caster.position(), "hexwright_staff_no_great_spells");
        }

        int slot = OperatorUtils.getInt(args, 0, 1);
        if (slot < 0 || slot >= learned.size()) {
            throw MishapInvalidIota.of(args.get(0), 0, "hexwright.staff_great_spell", learned.size() - 1);
        }

        return spell(() -> {
            StaffGreatSpellData.LearnedGreatSpell removed = StaffGreatSpellData.forget(staff, slot);
            if (removed == null) {
                return;
            }
            caster.displayClientMessage(
                Component.translatable("message.hexwright.core.great_spell_erased",
                        Component.translatable("hexcasting.action." + removed.opId()))
                    .withStyle(ChatFormatting.LIGHT_PURPLE),
                true
            );
        });
    }


    private static String corePowerId(ItemStack staff) {
        ItemStack coreItem = StaffAssemblyData.getCoreItem(staff);
        Optional<CoreData> coreData = CoreRegistry.lookup(coreItem.getItem());
        return coreData.map(CoreData::powerId).orElse(null);
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static RenderedSpell spell(Runnable effect) {
        return new RenderedSpell() {
            @Override
            public void cast(CastingEnvironment castEnv) {
                effect.run();
            }

            @Override
            public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                cast(castEnv);
                return image;
            }
        };
    }
}
