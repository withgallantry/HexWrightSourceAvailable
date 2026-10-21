package com.bluup.hexwright.server.constructshand;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.RenderedSpell;
import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.iota.EntityIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.NullIota;
import at.petrak.hexcasting.api.casting.iota.Vec3Iota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadLocation;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.misc.MediaConstants;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.hexpatterns.HexwrightSpellAction;
import com.bluup.hexwright.server.portal.PortalWindow;
import com.bluup.hexwright.server.vault.VaultKeyItem;
import com.bluup.hexwright.server.vault.VaultManager;
import com.bluup.hexwright.server.vault.VaultPortalPlacement;
import com.bluup.hexwright.server.vault.VaultRecord;
import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class ConstructsGesture {

    private static final String NAME = "[Construct's Hand]";

    private ConstructsGesture() {
    }

    public static void register() {
        Registry.register(IXplatAbstractions.INSTANCE.getActionRegistry(), Hexwright.id("constructs_gesture"),
            new ActionRegistryEntry(HexPattern.fromAngles("dadadeqdwwdq", HexDir.NORTH_EAST), GESTURE));
    }

    private static final SpellAction GESTURE = new HexwrightSpellAction() {
        @Override
        public int getArgc() {
            return 1;
        }

        @Override
        public Result execute(List<? extends Iota> args, CastingEnvironment env) {
            ConstructsHandBlockEntity hand = ConstructsGrasp.graspedHand(env);
            if (hand == null) {
                throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_no_grasp");
            }
            Iota target = args.get(0);
            RenderedSpell spell;
            if (target instanceof Vec3Iota vec) {
                BlockPos pos = BlockPos.containing(vec.getVec3());
                env.assertPosInRangeForEditing(pos);
                spell = new Spell(hand, pos, null);
            } else if (target instanceof EntityIota entity) {
                env.assertEntityInRange(entity.getEntity());
                spell = new Spell(hand, null, entity.getEntity());
            } else if (target instanceof NullIota) {
                spell = new Spell(hand, null, null);
            } else {
                throw MishapInvalidIota.ofType(target, 0, "hexwright.constructs_gesture_target");
            }
            if (hand.getHeld().getItem() instanceof VaultKeyItem) {
                spell = openVaultDoor(env, hand, target instanceof Vec3Iota vec ? BlockPos.containing(vec.getVec3()) : null);
            }
            return new Result(spell, MediaConstants.DUST_UNIT,
                List.of(ParticleSpray.burst(palm(hand), 0.3, 6)), 1L);
        }
    };

    private record Spell(ConstructsHandBlockEntity hand, @Nullable BlockPos block, @Nullable Entity entity)
        implements RenderedSpell {

        @Override
        public void cast(CastingEnvironment env) {
            if (hand.isRemoved() || (entity != null && !entity.isAlive())) {
                return;
            }
            ServerLevel level = env.getWorld();
            FakePlayer player = env.getCastingEntity() == null
                ? FakePlayer.get(level)
                : FakePlayer.get(level, new GameProfile(env.getCastingEntity().getUUID(), NAME));

            Vec3 eye = floating(hand);
            player.moveTo(eye.x, eye.y - player.getEyeHeight(), eye.z, 0.0f, 0.0f);
            player.lookAt(EntityAnchorArgument.Anchor.EYES, aim(eye));
            player.setItemInHand(InteractionHand.MAIN_HAND, hand.getHeld());
            player.getCooldowns().removeCooldown(hand.getHeld().getItem());
            try {
                InteractionResult result = InteractionResult.PASS;
                if (block != null) {
                    result = player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                        hitOn(block, eye));
                } else if (entity != null) {
                    result = player.interactOn(entity, InteractionHand.MAIN_HAND);
                }
                if (!result.consumesAction()) {
                    player.gameMode.useItem(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND);
                }
                ItemStack left = player.getMainHandItem();
                hand.setHeld(left.isEmpty() ? ItemStack.EMPTY : left);
                Inventory inventory = player.getInventory();
                for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                    ItemStack given = inventory.getItem(slot);
                    if (slot != inventory.selected && !given.isEmpty()) {
                        Block.popResource(level, hand.getBlockPos().above(), given);
                    }
                }
            } finally {
                player.stopUsingItem();
                player.getInventory().clearContent();
            }
        }

        @Override
        public CastingImage cast(CastingEnvironment env, CastingImage image) {
            cast(env);
            return image;
        }

        private Vec3 aim(Vec3 eye) {
            if (block != null) {
                return Vec3.atCenterOf(block);
            }
            if (entity != null) {
                return entity.getBoundingBox().getCenter();
            }
            return eye.add(facing(hand));
        }
    }

    private static RenderedSpell openVaultDoor(CastingEnvironment env, ConstructsHandBlockEntity hand,
                                               @Nullable BlockPos at) {
        ItemStack key = hand.getHeld();
        Integer vaultId = VaultKeyItem.boundVault(key);
        VaultRecord record = vaultId == null ? null : VaultManager.getVault(env.getWorld().getServer(), vaultId);
        if (record == null) {
            throw new MishapBadLocation(env.mishapSprayPos(), "hexwright_key_unbound");
        }
        int frameId = VaultKeyItem.frameId(key);
        return new RenderedSpell() {
            @Override
            public void cast(CastingEnvironment castEnv) {
                ServerLevel level = castEnv.getWorld();
                Direction facing = hand.getBlockState().getValue(ConstructsHandBlock.FACING);
                BlockPos base = at != null ? at : hand.getBlockPos().relative(facing, 2);
                PortalWindow window = VaultPortalPlacement.windowNear(level, base, facing, 2);
                if (window == null) {
                    castEnv.printMessage(Component.translatable("hexwright.vault.no_room"));
                    return;
                }
                VaultManager.openVault(level.getServer(), level, opener(hand, castEnv), record.id(), frameId, window);
            }

            @Override
            public CastingImage cast(CastingEnvironment castEnv, CastingImage image) {
                cast(castEnv);
                return image;
            }
        };
    }

    public static VaultManager.Opener opener(ConstructsHandBlockEntity hand, CastingEnvironment env) {
        return new VaultManager.Opener(hand.identity(), "Construct's Hand at " + hand.getBlockPos().toShortString(),
            env::printMessage);
    }

    private static BlockHitResult hitOn(BlockPos block, Vec3 from) {
        Vec3 centre = Vec3.atCenterOf(block);
        Vec3 toward = from.subtract(centre);
        Direction face = Direction.getNearest(toward.x, toward.y, toward.z);
        Vec3 point = centre.add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        return new BlockHitResult(point, face, block, false);
    }

    private static Vec3 palm(ConstructsHandBlockEntity hand) {
        Direction facing = hand.getBlockState().getValue(ConstructsHandBlock.FACING);
        BlockPos pos = hand.getBlockPos();
        return new Vec3(pos.getX() + 0.5, pos.getY() + 13.0 / 16.0, pos.getZ() + 0.5)
            .add(Vec3.atLowerCornerOf(facing.getNormal()).scale(-1.25 / 16.0));
    }

    private static Vec3 floating(ConstructsHandBlockEntity hand) {
        Direction facing = hand.getBlockState().getValue(ConstructsHandBlock.FACING);
        BlockPos pos = hand.getBlockPos();
        return new Vec3(pos.getX() + 0.5, pos.getY() + 19.7 / 16.0, pos.getZ() + 0.5)
            .add(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.8 / 16.0));
    }

    private static Vec3 facing(ConstructsHandBlockEntity hand) {
        Direction facing = hand.getBlockState().getValue(ConstructsHandBlock.FACING);
        return Vec3.atLowerCornerOf(facing.getNormal()).add(0.0, 1.0, 0.0).normalize();
    }
}
