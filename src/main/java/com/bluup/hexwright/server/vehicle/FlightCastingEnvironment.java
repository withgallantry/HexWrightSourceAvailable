package com.bluup.hexwright.server.vehicle;

import com.bluup.hexwright.server.hexpatterns.CastSounds;
import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.PatternShapeMatch;
import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.castables.Action;
import at.petrak.hexcasting.api.casting.castables.SpellAction;
import at.petrak.hexcasting.api.casting.eval.CastResult;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironmentComponent;
import at.petrak.hexcasting.api.casting.eval.MishapEnvironment;
import at.petrak.hexcasting.api.casting.eval.sideeffects.OperatorSideEffect;
import at.petrak.hexcasting.api.casting.mishaps.Mishap;
import at.petrak.hexcasting.api.casting.mishaps.MishapDisallowedSpell;
import at.petrak.hexcasting.api.mod.HexConfig;
import at.petrak.hexcasting.api.pigment.FrozenPigment;
import at.petrak.hexcasting.api.utils.MediaHelper;
import at.petrak.hexcasting.api.addldata.ADMediaHolder;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
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

public final class FlightCastingEnvironment extends CastingEnvironment {

    private static final double FLIGHT_QUERY_RADIUS = 128.0;

    private static final CastingEnvironmentComponent.Key<CastingEnvironmentComponent.ExtractMedia.Pre>
        RESERVOIR_KEY = new CastingEnvironmentComponent.Key<>() {
    };

    private final VehicleEntity vehicle;
    private final ServerPlayer rider;
    private final FlightExecutionContext context;
    private final FrozenPigment pigment = FrozenPigment.DEFAULT.get();
    private boolean mishapOccurred = false;

    public FlightCastingEnvironment(ServerLevel world, VehicleEntity vehicle, ServerPlayer rider, FlightExecutionContext context) {
        super(world);
        this.vehicle = vehicle;
        this.rider = rider;
        this.context = context;
        this.addExtension(new ReservoirComponent());
    }

    public FlightExecutionContext getContext() {
        return context;
    }

    public VehicleEntity getVehicle() {
        return vehicle;
    }

    public boolean hasMishap() {
        return mishapOccurred;
    }


    @Override
    public @Nullable LivingEntity getCastingEntity() {
        return null;
    }


    @Override
    public void precheckAction(PatternShapeMatch match) throws Mishap {
        super.precheckAction(match);

        ResourceLocation key = actionKey(match);
        if (!HexConfig.server().isActionAllowedInCircles(key)) {
            throw new MishapDisallowedSpell("disallowed_circle", key);
        }

        Action resolved = resolveAction(match);
        if (resolved instanceof SpellAction) {
            throw new MishapDisallowedSpell("disallowed_flight_spell", key);
        }
    }

    private static @Nullable Action resolveAction(PatternShapeMatch match) {
        if (match instanceof PatternShapeMatch.Special special) {
            return special.handler.act();
        }
        if (match instanceof PatternShapeMatch.Normal normal) {
            ActionRegistryEntry entry = IXplatAbstractions.INSTANCE.getActionRegistry().get(normal.key);
            return entry == null ? null : entry.action();
        }
        if (match instanceof PatternShapeMatch.PerWorld perWorld) {
            ActionRegistryEntry entry = IXplatAbstractions.INSTANCE.getActionRegistry().get(perWorld.key);
            return entry == null ? null : entry.action();
        }
        return null;
    }


    @Override
    protected long extractMediaEnvironment(long cost, boolean simulate) {
        if (cost <= 0) {
            return cost;
        }
        long costLeft = cost;
        List<ADMediaHolder> sources = MediaHelper.scanPlayerForMediaStuff(rider);
        for (ADMediaHolder source : sources) {
            long found = MediaHelper.extractMedia(source, costLeft, false, simulate);
            costLeft -= found;
            if (costLeft <= 0) {
                break;
            }
        }
        return costLeft;
    }

    private final class ReservoirComponent implements CastingEnvironmentComponent.ExtractMedia.Pre {
        @Override
        public CastingEnvironmentComponent.Key<?> getKey() {
            return RESERVOIR_KEY;
        }

        @Override
        public long onExtractMedia(long cost, boolean simulate) {
            if (cost <= 0) {
                return cost;
            }
            long available = vehicle.getInternalMedia();
            long paid = Math.min(cost, available);
            if (!simulate && paid > 0) {
                vehicle.setInternalMedia(available - paid);
            }
            return cost - paid;
        }
    }


    @Override
    protected boolean hasEditPermissionsAtEnvironment(BlockPos pos) {
        return false;
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
    public InteractionHand getCastingHand() {
        return InteractionHand.MAIN_HAND;
    }


    @Override
    protected boolean isVecInRangeEnvironment(Vec3 vec) {
        return vec.distanceToSqr(vehicle.position()) <= FLIGHT_QUERY_RADIUS * FLIGHT_QUERY_RADIUS;
    }

    @Override
    public boolean isEnlightened() {
        return false;
    }

    @Override
    public Vec3 mishapSprayPos() {
        return vehicle.position();
    }

    @Override
    public MishapEnvironment getMishapEnvironment() {
        return new VehicleMishapEnv(this.world);
    }

    @Override
    public FrozenPigment getPigment() {
        return pigment;
    }

    @Override
    public @Nullable FrozenPigment setPigment(@Nullable FrozenPigment newPigment) {
        return pigment;
    }

    @Override
    public void produceParticles(ParticleSpray particles, FrozenPigment colorizer) {
        particles.sprayParticles(this.world, colorizer);
    }

    @Override
    public void printMessage(Component message) {
        rider.sendSystemMessage(message);
    }

    @Override
    public int maxOpCount() {
        return VehicleConfig.MAX_FLIGHT_OP_COUNT;
    }


    @Override
    public void postExecution(CastResult result) {
        super.postExecution(CastSounds.muted(result));

        for (OperatorSideEffect sideEffect : result.getSideEffects()) {
            if (sideEffect instanceof OperatorSideEffect.DoMishap doMishap) {
                mishapOccurred = true;
                Component msg = doMishap.getMishap().errorMessageWithName(this, doMishap.getErrorCtx());
                if (msg != null) {
                    rider.sendSystemMessage(msg);
                }
            }
        }
    }
}
