package com.bluup.hexwright.compat.jade;

import com.bluup.hexwright.server.fluid.HexidTankBlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class HexwrightJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerFluidStorage(HexidTankFluidProvider.INSTANCE, HexidTankBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerFluidStorageClient(HexidTankFluidProvider.INSTANCE);
    }
}
