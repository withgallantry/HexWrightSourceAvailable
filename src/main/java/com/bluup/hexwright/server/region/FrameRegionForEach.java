package com.bluup.hexwright.server.region;

import at.petrak.hexcasting.api.casting.SpellList;
import at.petrak.hexcasting.api.casting.eval.CastResult;
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.eval.vm.ContinuationFrame;
import at.petrak.hexcasting.api.casting.eval.vm.FrameEvaluate;
import at.petrak.hexcasting.api.casting.eval.vm.SpellContinuation;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.iota.Vec3Iota;
import at.petrak.hexcasting.common.lib.hex.HexEvalSounds;
import at.petrak.hexcasting.common.lib.hex.HexIotaTypes;
import kotlin.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public record FrameRegionForEach(
    Region region,
    long nextIndex,
    SpellList code,
    @Nullable List<Iota> baseStack,
    List<Iota> acc
) implements ContinuationFrame {

    @Override
    public @NotNull CastResult evaluate(@NotNull SpellContinuation continuation, @NotNull ServerLevel level,
                                        @NotNull CastingVM harness) {
        CastingImage image = harness.getImage();
        List<Iota> stack;
        if (baseStack == null) {
            stack = List.copyOf(image.getStack());
            RegionBatch.open(harness.getEnv());
        } else {
            acc.addAll(image.getStack());
            stack = baseStack;
            RegionBatch.checkpoint(harness.getEnv());
        }

        RegionBlocks.Cursor cursor = RegionBlocks.cursorAt(region, nextIndex);
        BlockPos pos = cursor.next();

        Iota stackTop;
        CastingImage nextImage;
        SpellContinuation nextContinuation;
        if (pos != null) {
            nextContinuation = continuation
                .pushFrame(new FrameRegionForEach(region, cursor.index(), code, stack, acc))
                .pushFrame(new FrameEvaluate(code, true));
            stackTop = new Vec3Iota(Vec3.atCenterOf(pos));
            nextImage = image.withUsedOp();
        } else {
            RegionBatch.close(harness.getEnv());
            stackTop = new ListIota(acc);
            nextImage = image;
            nextContinuation = continuation;
        }

        List<Iota> iterationStack = new ArrayList<>(stack);
        iterationStack.add(stackTop);
        return new CastResult(
            new ListIota(code),
            nextContinuation,
            withStack(nextImage.withResetEscape(), iterationStack),
            List.of(),
            ResolvedPatternType.EVALUATED,
            HexEvalSounds.THOTH);
    }

    @Override
    public @NotNull Pair<Boolean, List<Iota>> breakDownwards(@NotNull List<? extends Iota> stack) {
        List<Iota> broken = baseStack == null ? new ArrayList<>() : new ArrayList<>(baseStack);
        acc.addAll(stack);
        broken.add(new ListIota(acc));
        return new Pair<>(true, broken);
    }

    @Override
    public @NotNull CompoundTag serializeToNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("Region", region.save());
        tag.putLong("Cursor", nextIndex);
        tag.put("Code", new ListIota(code).serialize());
        if (baseStack != null) {
            tag.put("Base", new ListIota(baseStack).serialize());
        }
        tag.put("Accumulator", new ListIota(acc).serialize());
        return tag;
    }

    @Override
    public int size() {
        return code.size() + acc.size() + (baseStack == null ? 0 : baseStack.size()) + 1;
    }

    @Override
    public @NotNull Type<FrameRegionForEach> getType() {
        return TYPE;
    }

    static CastingImage withStack(CastingImage image, List<Iota> stack) {
        return image.copy(stack, image.getParenCount(), image.getParenthesized(),
            image.getEscapeNext(), image.getOpsConsumed(), image.getUserData());
    }

    public static final Type<FrameRegionForEach> TYPE = new Type<>() {
        @Override
        public @Nullable FrameRegionForEach deserializeFromNBT(@NotNull CompoundTag tag, @NotNull ServerLevel world) {
            Region region;
            try {
                region = Region.load(tag.getCompound("Region"));
            } catch (IllegalArgumentException malformed) {
                return null;
            }
            SpellList code = readList(tag, "Code", world);
            List<Iota> base = tag.contains("Base", Tag.TAG_LIST)
                ? readIotas(tag, "Base", world)
                : null;
            return new FrameRegionForEach(region, tag.getLong("Cursor"), code, base,
                readIotas(tag, "Accumulator", world));
        }
    };

    private static SpellList readList(CompoundTag tag, String key, ServerLevel world) {
        ListIota iota = HexIotaTypes.LIST.deserialize(tag.getList(key, Tag.TAG_COMPOUND), world);
        return iota == null ? new SpellList.LList(List.of()) : iota.getList();
    }

    private static List<Iota> readIotas(CompoundTag tag, String key, ServerLevel world) {
        List<Iota> iotas = new ArrayList<>();
        readList(tag, key, world).forEach(iotas::add);
        return iotas;
    }
}
