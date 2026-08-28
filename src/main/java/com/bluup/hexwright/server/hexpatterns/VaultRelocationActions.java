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
import at.petrak.hexcasting.api.casting.mishaps.MishapBadLocation;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import com.bluup.hexwright.server.portal.PortalWindow;
import com.bluup.hexwright.server.vault.VaultDimension;
import com.bluup.hexwright.server.vault.VaultManager;
import com.bluup.hexwright.server.vault.VaultPortalPlacement;
import com.bluup.hexwright.server.vault.VaultRecord;
import com.bluup.hexwright.server.vault.VaultRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class VaultRelocationActions {

    private VaultRelocationActions() {
    }

    public static void register() {
        Registry<ActionRegistryEntry> registry = IXplatAbstractions.INSTANCE.getActionRegistry();

        Registry.register(registry, Hexwright.id("vaults_relocation_gambit"),
            new ActionRegistryEntry(
                HexPattern.fromAngles("wqeawwaeqwqawewd", HexDir.SOUTH_WEST), VAULTS_RELOCATION_GAMBIT));
    }

    private static void report(CastingEnvironment env, Component message) {
        ServerPlayer caster = env.getCaster();
        if (caster != null) {
            caster.displayClientMessage(message, true);
        }
    }

    private static VaultRecord resolveMasterworkVault(CastingEnvironment env) {
        ServerPlayer caster = env.getCaster();
        ServerLevel world = env.getWorld();
        VaultRecord record = caster == null || !VaultDimension.isVaultLevel(world)
            ? null
            : VaultRegistry.get(world.getServer()).byPosition(caster.position());
        if (record == null || record.grade() != PocketCasterData.Quality.MASTERWORK) {
            throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_vault_relocation_outside");
        }
        return record;
    }

    private static final SpellAction VAULTS_RELOCATION_GAMBIT = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 1;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            VaultRecord record = resolveMasterworkVault(env);
            Vec3 target = OperatorUtils.getVec3(args, 0, getArgc());
            ServerPlayer caster = env.getCaster();
            Direction facing = caster.getDirection();
            ServerLevel overworld = env.getWorld().getServer().overworld();
            PortalWindow window = VaultPortalPlacement.windowAt(overworld, BlockPos.containing(target), facing);
            if (window == null) {
                throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_vault_relocation_no_room");
            }

            return new Result(new RenderedSpell() {
                @Override
                public void cast(CastingEnvironment castEnv) {
                    boolean moved = VaultManager.relocateDoor(castEnv.getWorld().getServer(), record, window);
                    report(castEnv, Component.translatable(
                        moved ? "hexwright.vault.door_relocated" : "hexwright.vault.door_relocate_failed",
                        record.id()));
                }

                @Override
                public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                    cast(castEnv);
                    return image;
                }
            }, MediaConstants.DUST_UNIT * 50, List.of(ParticleSpray.burst(window.center(), 0.5, 10)), 1L);
        }
    };
}
