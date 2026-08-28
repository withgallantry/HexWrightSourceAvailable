package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.worldgen.AreaWard;
import com.bluup.hexwright.server.worldgen.dungeon.DungeonBlast;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PrimedTnt.class)
public abstract class PrimedTntDungeonMixin implements DungeonBlast {

    @Unique
    private boolean hexwright$dungeonCharge;

    @Inject(method = "<init>(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/entity/LivingEntity;)V",
        at = @At("TAIL"))
    private void hexwright$noteDungeonCharge(Level level, double x, double y, double z,
                                             LivingEntity owner, CallbackInfo ci) {
        this.hexwright$dungeonCharge = AreaWard.dungeonCharge(level, BlockPos.containing(x, y, z));
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void hexwright$saveDungeonCharge(CompoundTag tag, CallbackInfo ci) {
        if (this.hexwright$dungeonCharge) {
            tag.putBoolean("HexwrightDungeonCharge", true);
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void hexwright$loadDungeonCharge(CompoundTag tag, CallbackInfo ci) {
        this.hexwright$dungeonCharge = tag.getBoolean("HexwrightDungeonCharge");
    }

    @Override
    public boolean hexwright$breaksDungeon() {
        return this.hexwright$dungeonCharge;
    }
}
