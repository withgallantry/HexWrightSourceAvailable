package com.bluup.hexwright.server.fluid;

import com.bluup.hexwright.Hexwright;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributeHandler;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.PushReaction;

import java.util.Optional;

public final class HexidFluids {

    public static final double SATURATION = 0.10;

    public static final int MEDIA_PER_MB = (int) Math.round(HexidTank.MAX_MEDIA_PER_MB * SATURATION);

    public static final long MEDIA_PER_BUCKET = HexidTank.totalMedia(HexidTank.BUCKET_MB, MEDIA_PER_MB);

    public static final int TINT = 0x7A6BF0;

    public static final TagKey<Fluid> HEXID_TAG = TagKey.create(Registries.FLUID, Hexwright.id("hexid"));

    public static final FlowingFluid HEXID = new HexidFluid.Source();
    public static final FlowingFluid FLOWING_HEXID = new HexidFluid.Flowing();

    public static final LiquidBlock HEXID_BLOCK = new LiquidBlock(HEXID,
        BlockBehaviour.Properties.of()
            .replaceable()
            .noCollission()
            .strength(100.0f)
            .pushReaction(PushReaction.DESTROY)
            .noLootTable()
            .liquid()
            .sound(SoundType.EMPTY));

    public static final Item HEXID_BUCKET = new HexidBucketItem(HEXID,
        new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1));

    private static final FluidVariantAttributeHandler ATTRIBUTES = new FluidVariantAttributeHandler() {
        @Override
        public Component getName(FluidVariant variant) {
            return Component.translatable("fluid.hexwright.hexid");
        }

        @Override
        public Optional<SoundEvent> getFillSound(FluidVariant variant) {
            return Optional.of(SoundEvents.BUCKET_FILL);
        }

        @Override
        public Optional<SoundEvent> getEmptySound(FluidVariant variant) {
            return Optional.of(SoundEvents.BUCKET_EMPTY);
        }
    };

    public static boolean isHexid(FluidVariant resource) {
        return resource.isOf(HEXID) && !resource.hasNbt();
    }

    public static long mediaIn(long amountMb) {
        return HexidTank.totalMedia(amountMb, MEDIA_PER_MB);
    }

    public static void register() {
        Registry.register(BuiltInRegistries.FLUID, Hexwright.id("hexid"), HEXID);
        Registry.register(BuiltInRegistries.FLUID, Hexwright.id("flowing_hexid"), FLOWING_HEXID);
        Registry.register(BuiltInRegistries.BLOCK, Hexwright.id("hexid"), HEXID_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, Hexwright.id("hexid_bucket"), HEXID_BUCKET);

        FluidVariantAttributes.register(HEXID, ATTRIBUTES);
        FluidVariantAttributes.register(FLOWING_HEXID, ATTRIBUTES);

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
            .register(entries -> entries.accept(HEXID_BUCKET));
    }

    private HexidFluids() {
    }
}
