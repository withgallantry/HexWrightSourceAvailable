package com.bluup.hexwright.server.wardingbox;

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
import com.bluup.hexwright.server.block.WardingBoxBlockEntity;
import com.bluup.hexwright.server.worldgen.AreaWard;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class WardingBoxCastEnv extends CastingEnvironment implements AreaWard.Unsealed, MediaGrantOwner {

    private final WardingBoxBlockEntity box;

    public WardingBoxCastEnv(ServerLevel world, WardingBoxBlockEntity box) {
        super(world);
        this.box = box;
    }

    public WardingBoxBlockEntity getBox() {
        return this.box;
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
        return new WardingBoxMishapEnv(this.world);
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
                    box.postMishap(msg);
                }
            }
        }
    }

    @Override
    public Vec3 mishapSprayPos() {
        return Vec3.atCenterOf(box.getBlockPos());
    }

    @Override
    public net.minecraft.core.BlockPos hexwright$grantPos() {
        return box.getBlockPos();
    }

    @Override
    protected long extractMediaEnvironment(long cost, boolean simulate) {
        long paid = box.payMedia(cost, simulate);
        return cost - paid;
    }

    @Override
    protected boolean isVecInRangeEnvironment(Vec3 vec) {
        return box.wardedArea().inflate(2.0).contains(vec);
    }

    @Override
    public boolean isEnlightened() {
        if (getCastingEntity() == null) {
            return true;
        }
        return super.isEnlightened();
    }

    @Override
    protected boolean hasEditPermissionsAtEnvironment(BlockPos pos) {
        return true;
    }

    @Override
    public boolean ignoresAreaWard() {
        return this.box.isDungeonTrap();
    }

    @Override
    public InteractionHand getCastingHand() {
        return InteractionHand.MAIN_HAND;
    }

    @Override
    public List<ItemStack> getUsableStacks(StackDiscoveryMode mode) {
        ServerPlayer caster = this.getCaster();
        if (caster != null) {
            return getUsableStacksForPlayer(mode, null, caster);
        }
        return new ArrayList<>();
    }

    @Override
    public List<HeldItemInfo> getPrimaryStacks() {
        ServerPlayer caster = this.getCaster();
        if (caster != null) {
            return getPrimaryStacksForPlayer(InteractionHand.OFF_HAND, caster);
        }
        return List.of();
    }

    @Override
    public boolean replaceItem(Predicate<ItemStack> stackOk, ItemStack replaceWith, @Nullable InteractionHand hand) {
        ServerPlayer caster = this.getCaster();
        if (caster != null) {
            return replaceItemForPlayer(stackOk, replaceWith, hand, caster);
        }
        return false;
    }

    @Override
    public FrozenPigment getPigment() {
        return box.getPigment();
    }

    @Override
    public @Nullable FrozenPigment setPigment(@Nullable FrozenPigment pigment) {
        return box.setPigmentFromHex(pigment);
    }

    @Override
    public void produceParticles(ParticleSpray particles, FrozenPigment colorizer) {
        particles.sprayParticles(this.world, colorizer);
    }

    @Override
    public void printMessage(Component message) {
        box.postMessage(message);
    }
}
