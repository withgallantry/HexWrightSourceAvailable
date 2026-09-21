package com.bluup.hexwright.server.reliquary;

import at.petrak.hexcasting.api.HexAPI;
import at.petrak.hexcasting.api.addldata.ADMediaHolder;
import at.petrak.hexcasting.api.casting.ParticleSpray;
import at.petrak.hexcasting.api.casting.eval.CastingEnvironment;
import at.petrak.hexcasting.api.casting.eval.MishapEnvironment;
import at.petrak.hexcasting.api.pigment.FrozenPigment;
import at.petrak.hexcasting.api.utils.MediaHelper;
import at.petrak.hexcasting.common.lib.HexAttributes;
import at.petrak.hexcasting.api.casting.eval.env.PlayerBasedCastEnv;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class ChestCastEnv extends CastingEnvironment {

    public interface HeldSlot {
        ItemStack get();

        void set(ItemStack stack);
    }

    private final ServerPlayer opener;
    private final InteractionHand castingHand;
    private final HeldSlot heldSlot;

    private @Nullable ItemStack docked;

    public ChestCastEnv(ServerPlayer opener, InteractionHand castingHand, HeldSlot heldSlot) {
        super(opener.serverLevel());
        this.opener = opener;
        this.castingHand = castingHand;
        this.heldSlot = heldSlot;
    }

    public ServerPlayer opener() {
        return this.opener;
    }

    private ItemStack docked() {
        if (docked == null) {
            docked = heldSlot.get().copy();
        }
        return docked;
    }

    public void commitHeldSlot() {
        if (docked == null || ItemStack.matches(heldSlot.get(), docked)) {
            return;
        }
        heldSlot.set(docked);
    }

    public ItemStack heldSlotStack() {
        return docked();
    }

    private static final String TAG_SCRATCH = "hexwright_scratch_copy";

    public static ItemStack markScratch(ItemStack stack) {
        if (!stack.isEmpty()) {
            stack.getOrCreateTag().putBoolean(TAG_SCRATCH, true);
        }
        return stack;
    }

    public static boolean isScratch(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean(TAG_SCRATCH);
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
        return new ChestMishapEnv(this.world);
    }

    @Override
    public InteractionHand getCastingHand() {
        return this.castingHand;
    }

    @Override
    public Vec3 mishapSprayPos() {
        return this.opener.position();
    }

    @Override
    protected long extractMediaEnvironment(long cost, boolean simulate) {
        List<ADMediaHolder> sources = MediaHelper.scanPlayerForMediaStuff(this.opener);
        for (ADMediaHolder source : sources) {
            cost -= MediaHelper.extractMedia(source, cost, false, simulate);
            if (cost <= 0) {
                break;
            }
        }
        return Math.max(cost, 0);
    }

    @Override
    protected double getCostModifier(@NotNull ResourceLocation loc) {
        return super.getCostModifier(loc)
            * this.opener.getAttributeValue(HexAttributes.MEDIA_CONSUMPTION_MODIFIER);
    }

    @Override
    protected boolean isVecInRangeEnvironment(Vec3 vec) {
        double ambit = this.opener.getAttributes().hasAttribute(HexAttributes.AMBIT_RADIUS)
            ? this.opener.getAttributeValue(HexAttributes.AMBIT_RADIUS)
            : PlayerBasedCastEnv.DEFAULT_AMBIT_RADIUS;
        return vec.distanceToSqr(this.opener.position()) <= ambit * ambit + 0.00000000001;
    }

    @Override
    protected boolean hasEditPermissionsAtEnvironment(BlockPos pos) {
        return this.opener.gameMode.getGameModeForPlayer() != GameType.ADVENTURE
            && this.world.mayInteract(this.opener, pos);
    }

    @Override
    public boolean isEnlightened() {
        var advancement = this.world.getServer().getAdvancements()
            .getAdvancement(HexAPI.modLoc("enlightenment"));
        return advancement != null && this.opener.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    @Override
    public List<ItemStack> getUsableStacks(StackDiscoveryMode mode) {
        return getUsableStacksForPlayer(mode, this.castingHand, this.opener);
    }

    @Override
    public List<HeldItemInfo> getPrimaryStacks() {
        List<HeldItemInfo> base = getPrimaryStacksForPlayer(this.castingHand, this.opener);
        List<HeldItemInfo> copies = new ArrayList<>(base.size());
        for (HeldItemInfo info : base) {
            copies.add(new HeldItemInfo(markScratch(info.stack().copy()), null));
        }
        ItemStack held = docked();
        if (held.isEmpty()) {
            return copies;
        }
        List<HeldItemInfo> withSlot = new ArrayList<>(copies.size() + 1);
        withSlot.add(new HeldItemInfo(held, null));
        withSlot.addAll(copies);
        return withSlot;
    }

    @Override
    public boolean replaceItem(Predicate<ItemStack> stackOk, ItemStack replaceWith, @Nullable InteractionHand hand) {
        if (!stackOk.test(docked())) {
            return false;
        }
        this.docked = replaceWith.copy();
        return true;
    }

    @Override
    public FrozenPigment getPigment() {
        return HexAPI.instance().getColorizer(this.opener);
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
        this.opener.sendSystemMessage(message);
    }
}
