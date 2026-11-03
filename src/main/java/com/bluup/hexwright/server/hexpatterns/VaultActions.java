package com.bluup.hexwright.server.hexpatterns;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.OperatorUtils;
import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.RenderedSpell;
import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.EntityIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.NullIota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadLocation;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.vault.VaultAccess;
import com.bluup.hexwright.server.vault.VaultKeyItem;
import com.bluup.hexwright.server.vault.VaultManager;
import com.bluup.hexwright.server.vault.VaultRecord;
import com.bluup.hexwright.server.vault.VaultRooms;
import com.bluup.hexwright.server.vault.VaultSkies;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Consumer;

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
        Registry.register(registry, Hexwright.id("vaults_firmament"),
            new ActionRegistryEntry(HexPattern.fromAngles("wqeawwaeqwqeeeee", HexDir.SOUTH_WEST),
                VAULTS_FIRMAMENT));
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
        if (VaultKeyItem.frameId(key) != VaultRecord.MAIN_DOOR) {
            throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_key_secondary");
        }
        return record;
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
                    CastRecipient.tell(castEnv, Component.translatable(
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
            Iota raw = args.get(0);
            if (raw instanceof EntityIota) {
                return toggleGuest(OperatorUtils.getPlayer(args, 0, getArgc()), env);
            }
            if (raw instanceof NullIota) {
                VaultRecord record = resolveHeldVault(env);
                return doorSpell(env.mishapSprayPos(), castEnv -> {
                    VaultAccess.revokeAll(castEnv.getWorld().getServer(), record);
                    CastRecipient.tell(castEnv, Component.translatable(
                        "hexwright.vault.access_cleared", record.id()));
                });
            }
            if (raw instanceof DoubleIota) {
                VaultRecord record = resolveHeldVault(env);
                if (record.allowed().isEmpty()) {
                    throw MishapInvalidIota.of(raw, 0, "hexwright.vault_no_guests");
                }
                int index = OperatorUtils.getPositiveIntUnderInclusive(
                    args, 0, record.allowed().size() - 1, getArgc());
                return doorSpell(env.mishapSprayPos(), castEnv -> {
                    String name = VaultAccess.revokeGuest(castEnv.getWorld().getServer(), record, index);
                    CastRecipient.tell(castEnv, Component.translatable(
                        "hexwright.vault.access_revoked", name, record.id()));
                });
            }
            throw MishapInvalidIota.ofType(raw, 0, "hexwright.vault_invitee");
        }

        private Result toggleGuest(ServerPlayer guest, CastingEnvironment env) {
            env.assertEntityInRange(guest);
            VaultRecord record = resolveHeldVault(env);
            return doorSpell(guest.position(), castEnv -> {
                Component name = guest.getDisplayName();
                if (guest.getUUID().equals(record.owner())) {
                    CastRecipient.tell(castEnv, Component.translatable(
                        "hexwright.vault.access_owner", name, record.id()));
                    return;
                }
                boolean invited = VaultAccess.toggleGuest(castEnv.getWorld().getServer(), record, guest);
                CastRecipient.tell(castEnv, Component.translatable(
                    invited ? "hexwright.vault.access_granted" : "hexwright.vault.access_revoked",
                    name, record.id()));
            });
        }
    };

    private static final SpellAction VAULTS_FIRMAMENT = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 1;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            Iota raw = args.get(0);
            String sky;
            if (raw instanceof NullIota) {
                sky = null;
            } else if (raw instanceof DoubleIota) {
                sky = VaultSkies.CHOICES.get(
                    OperatorUtils.getPositiveIntUnderInclusive(args, 0, VaultSkies.CHOICES.size() - 1, getArgc()));
            } else {
                throw MishapInvalidIota.ofType(raw, 0, "hexwright.vault_sky");
            }
            VaultRecord record = resolveHeldVault(env);
            if (!VaultRooms.layoutOf(record).isOpenAir()) {
                throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_vault_roofed");
            }
            return doorSpell(env.mishapSprayPos(), castEnv -> {
                VaultSkies.set(castEnv.getWorld().getServer(), record, sky);
                CastRecipient.tell(castEnv, sky == null
                    ? Component.translatable("hexwright.vault.sky_cleared", record.id())
                    : Component.translatable("hexwright.vault.sky_set", record.id(),
                        Component.translatable("sky.hexwright." + sky)));
            });
        }
    };

    private static SpellAction.Result doorSpell(Vec3 sprayAt, Consumer<CastingEnvironment> effect) {
        return new SpellAction.Result(new RenderedSpell() {
            @Override
            public void cast(CastingEnvironment castEnv) {
                effect.accept(castEnv);
            }

            @Override
            public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                cast(castEnv);
                return image;
            }
        }, MediaConstants.DUST_UNIT / 10, List.of(ParticleSpray.burst(sprayAt, 0.5, 10)), 1L);
    }
}
