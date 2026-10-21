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
import com.bluup.hexwright.server.constructshand.ConstructsGesture;
import com.bluup.hexwright.server.constructshand.ConstructsGrasp;
import com.bluup.hexwright.server.constructshand.ConstructsHandBlock;
import com.bluup.hexwright.server.constructshand.ConstructsHandBlockEntity;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import com.bluup.hexwright.server.portal.PortalWindow;
import com.bluup.hexwright.server.vault.VaultDimension;
import com.bluup.hexwright.server.vault.VaultKeyItem;
import com.bluup.hexwright.server.vault.VaultManager;
import com.bluup.hexwright.server.vault.VaultPortalSession;
import com.bluup.hexwright.server.vault.VaultPortalPlacement;
import com.bluup.hexwright.server.vault.VaultRecord;
import com.bluup.hexwright.server.vault.VaultRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

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

    private static VaultRecord resolveMasterworkVault(CastingEnvironment env) {
        ServerLevel world = env.getWorld();
        VaultRecord record = !VaultDimension.isVaultLevel(world)
            ? null
            : VaultRegistry.get(world.getServer()).byPosition(env.mishapSprayPos());
        if (record == null || record.grade() != PocketCasterData.Quality.MASTERWORK) {
            throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_vault_relocation_outside");
        }
        return record;
    }

    private static int resolveDoor(CastingEnvironment env, VaultRecord record, VaultManager.Opener opener) {
        CastingEnvironment.HeldItemInfo held =
            env.getHeldItemToOperateOn(stack -> stack.getItem() instanceof VaultKeyItem);
        ItemStack key = held == null ? ItemStack.EMPTY : held.stack();
        if (!(key.getItem() instanceof VaultKeyItem)) {
            throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_held_keyless");
        }
        Integer vaultId = VaultKeyItem.boundVault(key);
        if (vaultId == null || vaultId != record.id()) {
            throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_vault_relocation_wrong_key");
        }
        int frameId = VaultKeyItem.frameId(key);
        if (frameId == VaultRecord.MAIN_DOOR) {
            return frameId;
        }
        if (record.frame(frameId) == null) {
            throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_vault_relocation_frame_gone");
        }
        VaultPortalSession open = VaultManager.sessionFor(record.id(), frameId);
        if (open != null && !open.opener().equals(opener.id())) {
            throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_vault_relocation_frame_held");
        }
        return frameId;
    }

    private static @Nullable ConstructsHandBlockEntity keyHand(CastingEnvironment env) {
        ConstructsHandBlockEntity hand = ConstructsGrasp.graspedHand(env);
        return hand != null && hand.getHeld().getItem() instanceof VaultKeyItem ? hand : null;
    }

    private static final SpellAction VAULTS_RELOCATION_GAMBIT = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 1;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            VaultRecord record = resolveMasterworkVault(env);
            ConstructsHandBlockEntity hand = keyHand(env);
            if (hand == null && env.getCaster() == null) {
                throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_held_keyless");
            }
            VaultManager.Opener opener = hand != null
                ? ConstructsGesture.opener(hand, env)
                : VaultManager.Opener.of(env.getCaster());
            int frameId = resolveDoor(env, record, opener);
            Vec3 target = OperatorUtils.getVec3(args, 0, getArgc());
            Direction facing = hand != null
                ? hand.getBlockState().getValue(ConstructsHandBlock.FACING)
                : env.getCaster().getDirection();
            ServerLevel overworld = env.getWorld().getServer().overworld();
            PortalWindow window = VaultPortalPlacement.windowAt(overworld, BlockPos.containing(target), facing);
            if (window == null) {
                throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_vault_relocation_no_room");
            }

            return new Result(new RenderedSpell() {
                @Override
                public void cast(CastingEnvironment castEnv) {
                    if (VaultManager.relocateDoor(castEnv.getWorld().getServer(), record, frameId, window, opener)) {
                        opener.tell().accept(Component.translatable("hexwright.vault.door_relocated", record.id()));
                    }
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
