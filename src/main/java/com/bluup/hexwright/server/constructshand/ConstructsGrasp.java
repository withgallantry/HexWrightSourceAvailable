package com.bluup.hexwright.server.constructshand;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.PatternShapeMatch;
import at.petrak.hexcasting.api.casting.castables.Action;
import at.petrak.hexcasting.api.casting.castables.SpecialHandler;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironmentComponent;
import at.petrak.hexcasting.api.casting.eval.OperationResult;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.Vec3Iota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.casting.mishaps.MishapBadLocation;
import at.petrak.hexcasting.api.casting.mishaps.MishapInvalidIota;
import at.petrak.hexcasting.api.casting.mishaps.MishapNotEnoughArgs;
import at.petrak.hexcasting.common.casting.PatternRegistryManifest;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class ConstructsGrasp {

    private static final String TAG_GRASPED = "hexwright:constructs_grasp";
    private static final String TAG_DIMENSION = "Dimension";
    private static final String TAG_POS = "Pos";

    private static final ResourceLocation ID = Hexwright.id("constructs_grasp");

    private static final CastingEnvironmentComponent.Key<Lent> KEY = new CastingEnvironmentComponent.Key<>() {
    };

    private ConstructsGrasp() {
    }

    public static void register() {
        Registry.register(IXplatAbstractions.INSTANCE.getSpecialHandlerRegistry(), ID, FACTORY);
        Registry.register(IXplatAbstractions.INSTANCE.getActionRegistry(), Hexwright.id("constructs_reach"),
            new ActionRegistryEntry(HexPattern.fromAngles("edaqedaqedwwewwwwdw", HexDir.EAST), REACH));
        CastingEnvironment.addCreateEventListener((env, userData) -> {
            Lent lent = Lent.read(userData);
            if (lent != null) {
                env.addExtension(lent);
            }
        });
    }

    public static boolean isFreeKey(HexPattern key, CastingEnvironment env) {
        PatternShapeMatch match = PatternRegistryManifest.matchPattern(key, env, false);
        return match instanceof PatternShapeMatch.Nothing
            || match instanceof PatternShapeMatch.Special special && special.key.location().equals(ID);
    }

    private static final SpecialHandler.Factory<Handler> FACTORY = (pattern, env) -> {
        BlockPos hand = ConstructsHands.nearestKeyed(env, pattern);
        return hand == null ? null : new Handler(hand);
    };

    private record Handler(BlockPos hand) implements SpecialHandler {
        @Override
        public Action act() {
            return (env, image, continuation) -> grasp(env, image, image.getStack(), continuation, hand);
        }

        @Override
        public Component getName() {
            return Component.translatable("hexcasting.spell.hexwright.constructs_grasp");
        }
    }

    private static final Action REACH = (env, image, continuation) -> {
        List<Iota> stack = new ArrayList<>(image.getStack());
        if (stack.isEmpty()) {
            throw new MishapNotEnoughArgs(1, 0);
        }
        Iota target = stack.remove(stack.size() - 1);
        if (!(target instanceof Vec3Iota vec)) {
            throw MishapInvalidIota.ofType(target, 0, "vector");
        }
        BlockPos hand = BlockPos.containing(vec.getVec3());
        env.assertPosInRange(hand);
        if (!(env.getWorld().getBlockEntity(hand) instanceof ConstructsHandBlockEntity)) {
            throw new MishapBadLocation(Vec3.atCenterOf(hand), "hexwright_no_hand");
        }
        return grasp(env, image, stack, continuation, hand);
    };

    private static OperationResult grasp(CastingEnvironment env, CastingImage image, List<Iota> stack,
                                         SpellContinuation continuation, BlockPos hand) {
        Lent lent = new Lent(env.getWorld().dimension(), hand, true);
        env.addExtension(lent);

        CompoundTag userData = image.getUserData().copy();
        userData.put(TAG_GRASPED, lent.write());
        CastingImage used = image.withUsedOp();
        CastingImage next = used.copy(stack, used.getParenCount(), used.getParenthesized(),
            used.getEscapeNext(), used.getOpsConsumed(), userData);

        HexwrightDebug.log(HexwrightDebug.CASTING, "[constructs hand] grasped {} in {}", hand, lent.dimension().location());
        return new OperationResult(next,
            List.of(new OperatorSideEffect.Particles(ParticleSpray.cloud(Vec3.atCenterOf(hand), 0.5, 8))),
            continuation, HexEvalSounds.NORMAL_EXECUTE);
    }

    public static @Nullable CastingEnvironment.HeldItemInfo heldItem(CastingEnvironment env,
                                                                     Predicate<ItemStack> stackOk) {
        ConstructsHandBlockEntity hand = graspedHand(env);
        if (hand == null || hand.getHeld().isEmpty() || !stackOk.test(hand.getHeld())) {
            return null;
        }
        hand.lend();
        return new CastingEnvironment.HeldItemInfo(hand.getHeld(), null);
    }

    public static List<ItemStack> withHeldFirst(CastingEnvironment env, List<ItemStack> usable) {
        ConstructsHandBlockEntity hand = graspedHand(env);
        if (hand == null || hand.getHeld().isEmpty()) {
            return usable;
        }
        hand.lend();
        List<ItemStack> withHand = new ArrayList<>(usable.size() + 1);
        withHand.add(hand.getHeld());
        withHand.addAll(usable);
        return withHand;
    }

    public static boolean replaceHeld(CastingEnvironment env, Predicate<ItemStack> stackOk, ItemStack replaceWith) {
        ConstructsHandBlockEntity hand = graspedHand(env);
        if (hand == null || hand.getHeld().isEmpty() || !stackOk.test(hand.getHeld())) {
            return false;
        }
        hand.setHeld(replaceWith);
        return true;
    }

    public static @Nullable ConstructsHandBlockEntity graspedHand(CastingEnvironment env) {
        Lent lent = env.getExtension(KEY);
        ServerLevel level = env.getWorld();
        if (lent == null || !lent.dimension().equals(level.dimension())) {
            return null;
        }
        if (!level.isLoaded(lent.pos()) || !env.isVecInAmbit(Vec3.atCenterOf(lent.pos()))) {
            return null;
        }
        return level.getBlockEntity(lent.pos()) instanceof ConstructsHandBlockEntity hand ? hand : null;
    }

    public static boolean graspedHere(CastingEnvironment env) {
        Lent lent = env.getExtension(KEY);
        return lent != null && lent.drawn();
    }

    private record Lent(ResourceKey<Level> dimension, BlockPos pos, boolean drawn) implements CastingEnvironmentComponent {
        @Override
        public Key<?> getKey() {
            return KEY;
        }

        CompoundTag write() {
            CompoundTag tag = new CompoundTag();
            tag.putString(TAG_DIMENSION, dimension.location().toString());
            tag.put(TAG_POS, NbtUtils.writeBlockPos(pos));
            return tag;
        }

        static @Nullable Lent read(CompoundTag userData) {
            if (!userData.contains(TAG_GRASPED, Tag.TAG_COMPOUND)) {
                return null;
            }
            CompoundTag tag = userData.getCompound(TAG_GRASPED);
            ResourceLocation dimension = ResourceLocation.tryParse(tag.getString(TAG_DIMENSION));
            if (dimension == null || !tag.contains(TAG_POS, Tag.TAG_COMPOUND)) {
                return null;
            }
            return new Lent(ResourceKey.create(Registries.DIMENSION, dimension),
                NbtUtils.readBlockPos(tag.getCompound(TAG_POS)), false);
        }
    }
}
