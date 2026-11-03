package com.bluup.hexwright.server.research;

import com.bluup.hexwright.server.item.ArtifactItem;
import com.bluup.hexwright.server.progression.ArtifactProvenance;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class ArtifactRuinedTabletItem extends RuinedTabletItem {

    public ArtifactRuinedTabletItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isArtifact() {
        return true;
    }

    @Override
    public void restored(ItemStack stack, ServerPlayer player) {
        ArtifactProvenance.spend(stack, 1, player, "at a Wright Research Table");
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        ArtifactProvenance.hold(stack, entity);
    }

    @Override
    public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withStyle(ArtifactItem.COLOUR);
    }
}
