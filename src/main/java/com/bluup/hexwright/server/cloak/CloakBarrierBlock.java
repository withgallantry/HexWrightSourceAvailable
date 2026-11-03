package com.bluup.hexwright.server.cloak;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CloakBarrierBlock extends Block {

    public CloakBarrierBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ownerPassing(level, pos, context) ? Shapes.empty() : Shapes.block();
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ownerPassing(level, pos, context) ? Shapes.empty() : Shapes.block();
    }

    private static boolean ownerPassing(BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!(context instanceof EntityCollisionContext entityContext) || entityContext.getEntity() == null
            || !(level instanceof Level world)) {
            return false;
        }
        Entity entity = entityContext.getEntity();
        for (CloakField field : CloakFieldStore.fieldsIn(world)) {
            if (field.admits(entity) && field.encloses(Vec3.atCenterOf(pos))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0f;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (level instanceof ServerLevel serverLevel) {
            CloakField field = CloakFieldImpacts.impact(serverLevel, hit.getLocation());
            if (field != null) {
                CloakFieldWear.projectile(serverLevel, field, hit.getLocation(), projectile);
            }
        }
    }
}
