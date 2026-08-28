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
import com.bluup.hexwright.server.vault.VaultAccess;
import com.bluup.hexwright.server.vault.VaultKeyItem;
import com.bluup.hexwright.server.vault.VaultManager;
import com.bluup.hexwright.server.vault.VaultRecord;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class VaultActions {

    private VaultActions() {
    }

    public static void register() {
        Registry<ActionRegistryEntry> registry = IXplatAbstractions.INSTANCE.getActionRegistry();

        Registry.register(registry, Hexwright.id("vaults_gambit"),
            new ActionRegistryEntry(HexPattern.fromAngles("wqeawwaeqwq", HexDir.SOUTH_WEST), VAULTS_GAMBIT));
        Registry.register(registry, Hexwright.id("vaults_invitation"),
            new ActionRegistryEntry(HexPattern.fromAngles("wqeawwaeqwqaeqaqe", HexDir.SOUTH_WEST),
                VAULTS_INVITATION));
    }

    private static VaultRecord resolveHeldVault(CastingEnvironment env) {
        CastingEnvironment.HeldItemInfo held =
            env.getHeldItemToOperateOn(stack -> stack.getItem() instanceof VaultKeyItem);
        ItemStack key = held == null ? ItemStack.EMPTY : held.stack();
        if (key.isEmpty() || !(key.getItem() instanceof VaultKeyItem)) {
            throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_held_keyless");
        }
        Integer vaultId = VaultKeyItem.boundVault(key);
        VaultRecord record = vaultId == null ? null : VaultManager.getVault(env.getWorld().getServer(), vaultId);
        if (record == null) {
            throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_key_unbound");
        }
        return record;
    }

    private static void report(CastingEnvironment env, Component message) {
        ServerPlayer caster = env.getCaster();
        if (caster != null) {
            caster.displayClientMessage(message, true);
        }
    }

    private static final SpellAction VAULTS_GAMBIT = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 0;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            VaultRecord record = resolveHeldVault(env);
            return new Result(new RenderedSpell() {
                @Override
                public void cast(CastingEnvironment castEnv) {
                    VaultRecord.Access mode = VaultAccess.toggleMode(castEnv.getWorld().getServer(), record);
                    report(castEnv, Component.translatable(
                        mode == VaultRecord.Access.ALLOWED_ONLY
                            ? "hexwright.vault.access_set_allowed_only"
                            : "hexwright.vault.access_set_everyone"));
                }

                @Override
                public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                    cast(castEnv);
                    return image;
                }
            }, MediaConstants.DUST_UNIT / 10, List.of(ParticleSpray.burst(env.mishapSprayPos(), 0.5, 10)), 1L);
        }
    };

    private static final SpellAction VAULTS_INVITATION = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 1;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            ServerPlayer guest = OperatorUtils.getPlayer(args, 0, getArgc());
            env.assertEntityInRange(guest);
            VaultRecord record = resolveHeldVault(env);
            return new Result(new RenderedSpell() {
                @Override
                public void cast(CastingEnvironment castEnv) {
                    Component name = guest.getDisplayName();
                    if (guest.getUUID().equals(record.owner())) {
                        report(castEnv, Component.translatable(
                            "hexwright.vault.access_owner", name, record.id()));
                        return;
                    }
                    boolean invited = VaultAccess.toggleGuest(castEnv.getWorld().getServer(), record, guest);
                    report(castEnv, Component.translatable(
                        invited ? "hexwright.vault.access_granted" : "hexwright.vault.access_revoked",
                        name, record.id()));
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
