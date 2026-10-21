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
import at.petrak.hexcasting.api.casting.mishaps.MishapBadBlock;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadLocation;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.cloak.CloakField;
import com.bluup.hexwright.server.cloak.ShieldGeneratorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class ShieldGeneratorActions {

    private ShieldGeneratorActions() {
    }

    public static void register() {
        Registry<ActionRegistryEntry> registry = IXplatAbstractions.INSTANCE.getActionRegistry();

        Registry.register(registry, Hexwright.id("wards_invitation"),
            new ActionRegistryEntry(HexPattern.fromAngles("qqwqwqwqqwawedew", HexDir.SOUTH_WEST), WARDS_INVITATION));
    }

    private static final SpellAction WARDS_INVITATION = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 2;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            Vec3 target = OperatorUtils.getVec3(args, 0, getArgc());
            Entity guest = OperatorUtils.getEntity(args, 1, getArgc());
            env.assertVecInRange(target);
            env.assertEntityInRange(guest);

            BlockPos pos = BlockPos.containing(target);
            if (!(env.getWorld().getBlockEntity(pos) instanceof ShieldGeneratorBlockEntity generator)) {
                throw MishapBadBlock.of(pos, "hexwright.shield_generator");
            }
            CloakField field = generator.field();
            if (field == null) {
                throw new MishapBadLocation(target, "hexwright_ward_unwritten");
            }
            Entity caster = env.getCastingEntity();
            if (field.owner() == null || caster == null || !field.owner().equals(caster.getUUID())) {
                throw new MishapBadLocation(target, "hexwright_ward_not_yours");
            }

            return new Result(new RenderedSpell() {
                @Override
                public void cast(CastingEnvironment castEnv) {
                    Component name = guest.getDisplayName();
                    if (guest.getUUID().equals(field.owner())) {
                        CastRecipient.tell(castEnv, Component.translatable("hexwright.ward.access_owner", name));
                        return;
                    }
                    boolean invited = generator.toggleGuest(castEnv.getWorld(), guest.getUUID());
                    CastRecipient.tell(castEnv, Component.translatable(
                        invited ? "hexwright.ward.access_granted" : "hexwright.ward.access_revoked", name));
                }

                @Override
                public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                    cast(castEnv);
                    return image;
                }
            }, MediaConstants.DUST_UNIT / 10, List.of(ParticleSpray.burst(guest.position(), 0.5, 10)), 1L);
        }
    };
}
