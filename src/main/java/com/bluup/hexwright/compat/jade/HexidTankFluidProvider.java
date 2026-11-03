package com.bluup.hexwright.compat.jade;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.fluid.HexidFluids;
import com.bluup.hexwright.server.fluid.HexidTank;
import com.bluup.hexwright.server.fluid.HexidTankBlockEntity;
import com.bluup.hexwright.server.fluid.HexidTankColumn;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.material.Fluids;
import snownee.jade.api.Accessor;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;

import java.util.List;
import java.util.Locale;

enum HexidTankFluidProvider implements IServerExtensionProvider<HexidTankBlockEntity, CompoundTag>,
    IClientExtensionProvider<CompoundTag, FluidView> {

    INSTANCE;

    private static final ResourceLocation UID = Hexwright.id("hexid_tank");
    private static final String TAG_MEDIA_PER_MB = "HexwrightMediaPerMb";

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public List<ViewGroup<CompoundTag>> getGroups(ServerPlayer player, ServerLevel level,
                                                  HexidTankBlockEntity target, boolean showDetails) {
        HexidTankBlockEntity column = HexidTankColumn.controller(level, target.getBlockPos());
        if (column == null || column.isRemnantStore()) {
            return List.of();
        }
        long perMb = JadeFluidObject.bucketVolume() / HexidTank.BUCKET_MB;
        JadeFluidObject contents = column.amountMb() <= 0
            ? JadeFluidObject.empty()
            : JadeFluidObject.of(column.isHexid() ? HexidFluids.HEXID : Fluids.WATER, column.amountMb() * perMb);
        CompoundTag view = FluidView.writeDefault(contents, column.capacityMb() * perMb);
        if (column.isHexid()) {
            view.putInt(TAG_MEDIA_PER_MB, column.mediaPerMb());
        }
        return List.of(new ViewGroup<>(List.of(view)));
    }

    @Override
    public List<ClientViewGroup<FluidView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<CompoundTag>> groups) {
        return ClientViewGroup.map(groups, tag -> {
            FluidView view = FluidView.readDefault(tag);
            if (view != null && tag.contains(TAG_MEDIA_PER_MB)) {
                String saturation = String.format(Locale.ROOT, "%.2f", tag.getInt(TAG_MEDIA_PER_MB) / 100.0);
                view.fluidName = Component.translatable("jade.hexwright.hexid_saturation", view.fluidName, saturation);
            }
            return view;
        }, null);
    }
}
