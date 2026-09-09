package com.bluup.hexwright.server.vehicle;

import at.petrak.hexcasting.api.misc.MediaConstants;
import com.bluup.hexwright.server.item.ArtifactItem;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import com.bluup.hexwright.server.progression.MakersMark;
import com.bluup.hexwright.server.staff_assembly.HexwrightEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.RenderProvider;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class BroomItem extends VehicleItem implements ArtifactItem, GeoItem {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Supplier<Object> renderProvider = GeoItem.makeRenderer(this);

    public BroomItem(Properties properties) {
        super(properties);
    }

    @Override
    protected VehicleEntity createEntity(ServerLevel level) {
        return new BroomEntity(HexwrightEntities.BROOM, level);
    }

    @Override
    protected long getMediaCapacity(ItemStack stack) {
        return BroomVariant.of(stack).mediaCapacity(gradeOf(stack));
    }

    @Override
    protected boolean mountsOnDeploy() {
        return true;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(BroomVariant.of(stack).nameKey());
    }

    @Override
    public boolean isArtifact(ItemStack stack) {
        return BroomVariant.of(stack).isArtifact();
    }

    private static PocketCasterData.Quality gradeOf(ItemStack stack) {
        return VehicleData.getQuality(stack.getOrCreateTagElement(VehicleData.ROOT_TAG));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        MakersMark.appendTooltip(stack, tooltip);

        BroomVariant variant = BroomVariant.of(stack);
        PocketCasterData.Quality quality = gradeOf(stack);
        tooltip.add(variant.isArtifact()
            ? ArtifactItem.tooltipLine()
            : Component.translatable(
                "tooltip.hexwright.vehicle.grade",
                Component.translatable(quality.translationKey())
            ).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable(
            "tooltip.hexwright.vehicle.top_speed",
            String.format("%.2f", variant.maxHorizontalSpeed(quality))
        ).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable(
            "tooltip.hexwright.vehicle.reservoir",
            variant.mediaCapacity(quality) / MediaConstants.DUST_UNIT
        ).withStyle(ChatFormatting.AQUA));
        if (variant.passengerCapacity() > 1) {
            tooltip.add(Component.translatable(
                "tooltip.hexwright.vehicle.seats", variant.passengerCapacity()
            ).withStyle(ChatFormatting.AQUA));
        }
    }


    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public Supplier<Object> getRenderProvider() {
        return renderProvider;
    }

    @Override
    public void createRenderer(Consumer<Object> consumer) {
        consumer.accept(new RenderProvider() {
            private com.bluup.hexwright.client.vehicle.BroomItemRenderer renderer;

            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new com.bluup.hexwright.client.vehicle.BroomItemRenderer();
                }
                return renderer;
            }
        });
    }
}
