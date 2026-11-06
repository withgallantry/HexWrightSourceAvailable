package com.bluup.hexwright.client.inversion;

import com.bluup.hexwright.server.inversion.InvertedBlocks;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

@Environment(EnvType.CLIENT)
public final class InvertedBlockColors {

    private static final Map<InvertedBlocks.Skin, Integer> SKINS = Map.ofEntries(
        Map.entry(InvertedBlocks.Skin.GRASS, 0x504980), Map.entry(InvertedBlocks.Skin.DIRT, 0x576FFF),
        Map.entry(InvertedBlocks.Skin.STONE, 0x675EA5), Map.entry(InvertedBlocks.Skin.SAND, 0x434191),
        Map.entry(InvertedBlocks.Skin.GRAVEL, 0x595495), Map.entry(InvertedBlocks.Skin.SNOW, 0x4C4A74),
        Map.entry(InvertedBlocks.Skin.ICE, 0x785E6A), Map.entry(InvertedBlocks.Skin.TERRACOTTA, 0x4D72FF),
        Map.entry(InvertedBlocks.Skin.MUD, 0xA69EFF), Map.entry(InvertedBlocks.Skin.MOSS, 0x7D5DFF),
        Map.entry(InvertedBlocks.Skin.CLAY, 0x584D7E));
    private static final Map<InvertedBlocks.Wood, Integer> LOGS = Map.of(
        InvertedBlocks.Wood.OAK, 0x7584FF, InvertedBlocks.Wood.SPRUCE, 0xD9FFFF, InvertedBlocks.Wood.BIRCH, 0x3B3455,
        InvertedBlocks.Wood.JUNGLE, 0x96A5FF, InvertedBlocks.Wood.ACACIA, 0x7B74CD, InvertedBlocks.Wood.DARK_OAK, 0xD3F1FF,
        InvertedBlocks.Wood.MANGROVE, 0x98A8FF, InvertedBlocks.Wood.CHERRY, 0xE9FFFF);
    private static final Map<InvertedBlocks.Wood, Integer> LEAVES = Map.of(
        InvertedBlocks.Wood.OAK, 0x404791, InvertedBlocks.Wood.SPRUCE, 0x4951A6, InvertedBlocks.Wood.BIRCH, 0x464FA0,
        InvertedBlocks.Wood.JUNGLE, 0x3B4292, InvertedBlocks.Wood.ACACIA, 0x3D458D, InvertedBlocks.Wood.DARK_OAK, 0x3D448B,
        InvertedBlocks.Wood.MANGROVE, 0x474FA3, InvertedBlocks.Wood.CHERRY, 0x283B6C, InvertedBlocks.Wood.AZALEA, 0x6659FF,
        InvertedBlocks.Wood.FLOWERING_AZALEA, 0x5C5CFF);
    private static final Map<InvertedBlocks.Form, Integer> FORMS = Map.of(
        InvertedBlocks.Form.GRASS, 0x6269BD, InvertedBlocks.Form.FERN, 0x737BDD, InvertedBlocks.Form.FLOWER, 0x504CC0,
        InvertedBlocks.Form.BUSH, 0x85C2FF, InvertedBlocks.Form.TALL_LOWER, 0x6F77D6, InvertedBlocks.Form.TALL_UPPER, 0x5E66B6,
        InvertedBlocks.Form.CANE, 0x604FFF, InvertedBlocks.Form.MUSHROOM, 0x5D83FF);
    private static final int LAYER = 0x4C4A74;
    private static final int VINE = 0x4F58B4;
    private static final float WANDER = 0.08f;

    private InvertedBlockColors() {
    }

    public static void register() {
        ColorProviderRegistry.BLOCK.register((state, level, pos, tintIndex) -> wander(tint(state), pos),
            InvertedBlocks.all().toArray(new Block[0]));
        BlockRenderLayerMap.INSTANCE.putBlock(InvertedBlocks.LEAVES, RenderType.cutoutMipped());
        BlockRenderLayerMap.INSTANCE.putBlock(InvertedBlocks.GROWTH, RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlock(InvertedBlocks.VINE, RenderType.cutout());
    }

    private static int tint(BlockState state) {
        if (state.is(InvertedBlocks.GROUND)) {
            return SKINS.get(state.getValue(InvertedBlocks.SKIN));
        }
        if (state.is(InvertedBlocks.LOG)) {
            return LOGS.get(state.getValue(InvertedBlocks.LOG_WOOD));
        }
        if (state.is(InvertedBlocks.LEAVES)) {
            return LEAVES.get(state.getValue(InvertedBlocks.LEAF_WOOD));
        }
        if (state.is(InvertedBlocks.GROWTH)) {
            return FORMS.get(state.getValue(InvertedBlocks.FORM));
        }
        return state.is(InvertedBlocks.VINE) ? VINE : LAYER;
    }

    private static int wander(int tint, @Nullable BlockPos pos) {
        if (pos == null) {
            return tint;
        }
        float scale = 1.0f + WANDER * (((Mth.getSeed(pos) >>> 16) & 0xFF) / 127.5f - 1.0f);
        int r = Math.min(255, Math.round(((tint >> 16) & 0xFF) * scale));
        int g = Math.min(255, Math.round(((tint >> 8) & 0xFF) * scale));
        int b = Math.min(255, Math.round((tint & 0xFF) * scale));
        return r << 16 | g << 8 | b;
    }
}
