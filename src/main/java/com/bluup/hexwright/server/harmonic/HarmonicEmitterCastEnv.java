package com.bluup.hexwright.server.harmonic;

import com.bluup.hexwright.server.hexpatterns.CastSounds;
import com.bluup.hexwright.server.media.MediaGrantOwner;
import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.PatternShapeMatch;
import at.petrak.hexcasting.api.casting.eval.CastResult;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.MishapEnvironment;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.mishaps.Mishap;
import at.petrak.hexcasting.api.casting.mishaps.MishapDisallowedSpell;
import at.petrak.hexcasting.api.mod.HexConfig;
import at.petrak.hexcasting.api.pigment.FrozenPigment;
import com.bluup.hexwright.server.block.HarmonicEmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class HarmonicEmitterCastEnv extends CastingEnvironment implements MediaGrantOwner {

    public static final double AMBIT = 4.0;

    private final HarmonicEmitterBlockEntity emitter;

    public HarmonicEmitterCastEnv(ServerLevel world, HarmonicEmitterBlockEntity emitter) {
        super(world);
        this.emitter = emitter;
    }

    public HarmonicEmitterBlockEntity getEmitter() {
        return this.emitter;
    }

    @Override
    public @Nullable LivingEntity getCastingEntity() {
        return null;
    }

    @Override
    public @Nullable ServerPlayer getCaster() {
        return null;
    }

    @Override
    public MishapEnvironment getMishapEnvironment() {
        return new HarmonicEmitterMishapEnv(this.world);
    }

    @Override
    public void precheckAction(PatternShapeMatch match) throws Mishap {
        super.precheckAction(match);

        ResourceLocation key = actionKey(match);
        if (!HexConfig.server().isActionAllowedInCircles(key)) {
            throw new MishapDisallowedSpell("disallowed_circle", key);
        }
    }

    @Override
    public void postExecution(CastResult result) {
        super.postExecution(CastSounds.muted(result));

        for (OperatorSideEffect sideEffect : result.getSideEffects()) {
            if (sideEffect instanceof OperatorSideEffect.DoMishap doMishap) {
                Component msg = doMishap.getMishap().errorMessageWithName(this, doMishap.getErrorCtx());
                if (msg != null) {
                    emitter.postMishap(msg);
                }
            }
        }
    }

    @Override
    public Vec3 mishapSprayPos() {
        return Vec3.atCenterOf(emitter.getBlockPos());
    }

    @Override
    public net.minecraft.core.BlockPos hexwright$grantPos() {
        return emitter.getBlockPos();
    }

    @Override
    protected long extractMediaEnvironment(long cost, boolean simulate) {
        long paid = emitter.payMedia(cost, simulate);
        return cost - paid;
    }

    @Override
    protected boolean isVecInRangeEnvironment(Vec3 vec) {
        return new AABB(emitter.getBlockPos()).inflate(AMBIT).contains(vec);
    }

    @Override
    public boolean isEnlightened() {
        return true;
    }

    @Override
    protected boolean hasEditPermissionsAtEnvironment(BlockPos pos) {
        return true;
    }

    @Override
    public InteractionHand getCastingHand() {
        return InteractionHand.MAIN_HAND;
    }

    @Override
    public List<ItemStack> getUsableStacks(StackDiscoveryMode mode) {
        return new ArrayList<>();
    }

    @Override
    public List<HeldItemInfo> getPrimaryStacks() {
        return List.of();
    }

    @Override
    public boolean replaceItem(Predicate<ItemStack> stackOk, ItemStack replaceWith, @Nullable InteractionHand hand) {
        return false;
    }

    @Override
    public FrozenPigment getPigment() {
        return FrozenPigment.DEFAULT.get();
    }

    @Override
    public @Nullable FrozenPigment setPigment(@Nullable FrozenPigment pigment) {
        return null;
    }

    @Override
    public void produceParticles(ParticleSpray particles, FrozenPigment colorizer) {
        particles.sprayParticles(this.world, colorizer);
    }

    @Override
    public void printMessage(Component message) {
        emitter.postMessage(message);
    }
}
