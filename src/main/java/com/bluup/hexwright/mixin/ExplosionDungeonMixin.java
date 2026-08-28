package com.bluup.hexwright.mixin;

import com.bluup.hexwright.server.worldgen.AreaWard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(Explosion.class)
public abstract class ExplosionDungeonMixin {

    @Shadow
    @Final
    private Level level;

    @Shadow
    @Final
    @Nullable
    private Entity source;

    @Shadow
    public abstract List<BlockPos> getToBlow();

    @Inject(method = "explode", at = @At("TAIL"))
    private void hexwright$sealDungeon(CallbackInfo ci) {
        AreaWard.prune(this.level, this.getToBlow(), this.source);
    }
}
