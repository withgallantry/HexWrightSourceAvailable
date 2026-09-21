package com.bluup.hexwright.inits;

import at.petrak.hexcasting.api.HexAPI;
import at.petrak.hexcasting.api.pigment.ColorProvider;
import at.petrak.hexcasting.api.pigment.FrozenPigment;
import at.petrak.hexcasting.common.lib.HexItems;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.HexwrightDebug;
import com.bluup.hexwright.client.block.ManifoldVaultRenderer;
import com.bluup.hexwright.client.block.ReliquaryMirrorRenderer;
import com.bluup.hexwright.client.block.StaffWorkstationRenderer;
import com.bluup.hexwright.client.hexicon.HexiconOverlay;
import com.bluup.hexwright.client.ldlib.widget.ContainerSlotGridWidget;
import com.bluup.hexwright.client.ldlib.widget.StaffItemDisplayWidget;
import com.bluup.hexwright.client.pentabox.PentaboxOverlay;
import com.bluup.hexwright.client.reliquary.SatchelBackpackInventoryOverlay;
import com.bluup.hexwright.client.signet.SignatureTooltipRenderer;
import com.bluup.hexwright.client.animation.PlayerAnimationRegistry;
import com.bluup.hexwright.client.animation.WeaponIdleAnimationClient;
import com.bluup.hexwright.client.staff_assembly.StaffBookOpenTracker;
import com.bluup.hexwright.client.staff_assembly.StaffCoreBeamClient;
import com.bluup.hexwright.client.staff_assembly.StaffCoreBoltVisualClient;
import com.bluup.hexwright.client.staff_assembly.StaffCoreSphereVisualClient;
import com.bluup.hexwright.client.staff_assembly.StaffTravellerWarpVisualClient;
import com.bluup.hexwright.client.staff_assembly.AnchoriteFlightAnimationClient;
import com.bluup.hexwright.client.vehicle.BroomTrailVisualClient;
import com.bluup.hexwright.client.wardingbox.WardingBoxLensOverlay;
import com.bluup.hexwright.client.wardingbox.WardingBoxVisualClient;
import com.bluup.hexwright.client.staff_assembly.StaffAssemblyScreen;
import com.bluup.hexwright.common.aspects.AspectMappings;
import com.bluup.hexwright.common.staff_assembly.StaffPart;
import com.bluup.hexwright.common.staff_assembly.StaffPartCategory;
import com.bluup.hexwright.common.staff_assembly.StaffParts;
import com.bluup.hexwright.common.staff_assembly.calc.IngredientCategory;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.item.AnchorSlateItem;
import com.bluup.hexwright.server.item.EndlessPouchItem;
import com.bluup.hexwright.server.item.HexiconItem;
import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.worldgen.dungeon.DungeonGroups;
import com.bluup.hexwright.server.menu.HexwrightMenus;
import com.bluup.hexwright.server.pentabox.PentaboxData;
import com.bluup.hexwright.server.reliquary.ReliquarySealItem;
import com.bluup.hexwright.server.reliquary.SatchelItem;
import com.bluup.hexwright.server.vehicle.BroomVariant;
import com.bluup.hexwright.server.vehicle.CarpetVariant;
import com.bluup.hexwright.server.signet.SignatureTooltip;
import com.bluup.hexwright.server.staff_assembly.HexwrightEntities;
import com.bluup.hexwright.server.staff_assembly.StaffAssemblyData;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.configurator.IConfigurableWidget;
import com.lowdragmc.lowdraglib.gui.editor.runtime.AnnotationDetector;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.platform.InputConstants;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class HexwrightClient implements ClientModInitializer {
    private static final KeyMapping PENTABOX_OVERLAY_KEY = new KeyMapping(
        "key.hexwright.pentabox_overlay",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_Y,
        "category.hexwright"
    );

    private static final KeyMapping HEXICON_OVERLAY_KEY = new KeyMapping(
        "key.hexwright.hexicon_overlay",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_V,
        "category.hexwright"
    );

    private static final KeyMapping TRAIL_TUNE_KEY = tunerKey("trail_tune");
    private static final KeyMapping TRAIL_TUNE_AXIS_KEY = tunerKey("trail_tune_axis");
    private static final KeyMapping TRAIL_TUNE_UP_KEY = tunerKey("trail_tune_up");
    private static final KeyMapping TRAIL_TUNE_DOWN_KEY = tunerKey("trail_tune_down");

    private static KeyMapping tunerKey(String name) {
        return new KeyMapping("key.hexwright." + name, InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(), "category.hexwright");
    }

    private static final Map<UUID, ColorProvider> SOUL_COLOR_CACHE = new ConcurrentHashMap<>();

    private static final float BROOM_SHADOW_RADIUS = 0.5f;
    private static final float CARPET_SHADOW_RADIUS = 0.8f;

    @Override
    public void onInitializeClient() {
        HexwrightNetworking.registerClient();
        TooltipComponentCallback.EVENT.register(data ->
            data instanceof SignatureTooltip signature ? new SignatureTooltipRenderer(signature) : null);
        PlayerAnimationRegistry.register();
        com.bluup.hexwright.client.weapon.WeaponModelAnchors.register();
        StaffCoreBeamClient.register();
        StaffCoreSphereVisualClient.register();
        com.bluup.hexwright.client.staff_assembly.ShieldContactShaders.register();
        StaffCoreBoltVisualClient.register();
        StaffTravellerWarpVisualClient.register();
        com.bluup.hexwright.client.staff_assembly.StaffTipAnchors.register();
        com.bluup.hexwright.client.staff_assembly.StaffTipFlash.register();
        com.bluup.hexwright.client.staff_assembly.StaffTipCommands.register();
        WardingBoxVisualClient.register();
        com.bluup.hexwright.client.weapon.GroundSlamWaveClient.register();
        com.bluup.hexwright.client.weapon.WeaponSlashVisualClient.register();
        com.bluup.hexwright.client.weapon.SlashWaveVisualClient.register();
        com.bluup.hexwright.client.weapon.SkyfallVisualClient.register();
        WardingBoxLensOverlay.register();
        BroomTrailVisualClient.register();
        com.bluup.hexwright.client.dust.DustClient.register();
        ManifoldVaultRenderer.register();
        ReliquaryMirrorRenderer.register();
        com.bluup.hexwright.client.talisman.TalismanItemRenderer.register();
        StaffWorkstationRenderer.register();
        com.bluup.hexwright.client.block.RuinedPortalFrameRenderer.register();
        com.bluup.hexwright.client.block.VaultPlinthRenderer.register();
        com.bluup.hexwright.client.block.DungeonPropRenderer.register();
        com.bluup.hexwright.client.block.PlacedBottleRenderer.register();
        com.bluup.hexwright.client.block.LiquefactriumRenderer.register();
        com.bluup.hexwright.client.block.HexidTankRenderer.register();
        com.bluup.hexwright.client.block.HexidPipePreview.register();
        com.bluup.hexwright.client.block.AlembixVesselVisualClient.register();
        com.bluup.hexwright.client.block.AlembixDebugCommands.register();
        com.bluup.hexwright.client.armour.MageAttireCommands.register();
        com.bluup.hexwright.client.armour.MageAttireRenderer.register();
        com.bluup.hexwright.client.armour.PassagePortalVisualClient.register();
        com.bluup.hexwright.client.render.TranslucentBlockOutline.register();
        com.bluup.hexwright.client.render.emissive.EmissiveItemModels.register();
        com.bluup.hexwright.client.render.emissive.BlockGlow.register();
        com.bluup.hexwright.client.render.emissive.EmissiveBloom.register();
        com.bluup.hexwright.client.spellcasting.SpellcastingGridCommands.register();
        com.bluup.hexwright.client.photon.PhotonSceneTextures.register();
        com.bluup.hexwright.client.portal.PortalOptions.load();
        com.bluup.hexwright.client.portal.PortalShaders.register();
        com.bluup.hexwright.client.portal.PortalPlaneRenderer.register();
        com.bluup.hexwright.client.portal.ClientPortalManager.register();
        com.bluup.hexwright.client.portal.VoidTearShaders.register();
        com.bluup.hexwright.client.portal.VoidTearRenderer.register();
        com.bluup.hexwright.client.portal.ClientVoidTears.register();
        com.bluup.hexwright.client.block.RuinedPortalRifts.register();
        com.bluup.hexwright.client.portal.PortalCrossingPredictor.register();
        com.bluup.hexwright.client.portal.ArrivalTrace.register();
        com.bluup.hexwright.client.portal.RemoteLevelManager.register();
        com.bluup.hexwright.client.portal.DimensionLeakFixCompat.register();
        com.bluup.hexwright.client.portal.RemotePhotonVisuals.register();

        com.bluup.hexwright.compat.accessories.AccessoriesClientCompat.register();

        BlockRenderLayerMap.INSTANCE.putBlock(HexwrightBlocks.WORKTABLE_BLOCK, RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlock(HexwrightBlocks.HEXID_TANK_BLOCK, RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlock(HexwrightBlocks.HEXID_PIPE_BLOCK, RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlock(HexwrightBlocks.ALEMBIX_BLOCK, RenderType.translucent());
        BlockRenderLayerMap.INSTANCE.putBlock(HexwrightBlocks.LIQUEFACTRIUM_BLOCK, RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlock(HexwrightBlocks.STAFF_ASSEMBLY_BLOCK, RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlock(HexwrightBlocks.RESONANCE_TOWER_BLOCK, RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlock(HexwrightBlocks.MANIFOLD_VAULT_BLOCK, RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlock(HexwrightBlocks.HARMONIC_EXCHANGE_BLOCK, RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlock(HexwrightBlocks.HARMONIC_EMITTER_BLOCK, RenderType.translucent());
        BlockRenderLayerMap.INSTANCE.putBlock(HexwrightBlocks.WARDING_BOX_BLOCK, RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlock(HexwrightBlocks.COALESCER_BLOCK, RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlock(HexwrightBlocks.EXCHANGE_BRIDGE_BLOCK, RenderType.translucent());
        BlockRenderLayerMap.INSTANCE.putBlock(HexwrightBlocks.LEYWELL_BLOCK, RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlock(
            com.bluup.hexwright.server.block.VaultDecorBlocks.NETHER_LABORATORY_DOOR, RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putFluids(RenderType.translucent(),
            com.bluup.hexwright.server.fluid.HexidFluids.HEXID,
            com.bluup.hexwright.server.fluid.HexidFluids.FLOWING_HEXID);
        net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry.INSTANCE.register(
            com.bluup.hexwright.server.fluid.HexidFluids.HEXID,
            com.bluup.hexwright.server.fluid.HexidFluids.FLOWING_HEXID,
            net.fabricmc.fabric.api.client.render.fluid.v1.SimpleFluidRenderHandler.coloredWater(
                com.bluup.hexwright.server.fluid.HexidFluids.TINT));


        EntityRendererRegistry.register(HexwrightEntities.STAFF_CORE_BOLT, NoopRenderer::new);
        EntityRendererRegistry.register(HexwrightEntities.HEX_ARROW,
            com.bluup.hexwright.client.weapon.HexArrowRenderer::new);
        EntityRendererRegistry.register(HexwrightEntities.SLASH_WAVE, NoopRenderer::new);
        EntityRendererRegistry.register(HexwrightEntities.BROOM,
            context -> new com.bluup.hexwright.client.vehicle.VehicleEntityRenderer<>(context, BROOM_SHADOW_RADIUS));
        EntityRendererRegistry.register(HexwrightEntities.CARPET,
            context -> new com.bluup.hexwright.client.vehicle.VehicleEntityRenderer<>(context, CARPET_SHADOW_RADIUS));
        EntityRendererRegistry.register(com.bluup.hexwright.server.boss.HexwrightBossEntities.QUARTZ_GOLEM,
            com.bluup.hexwright.client.boss.QuartzGolemRenderer::new);
        EntityRendererRegistry.register(com.bluup.hexwright.server.boss.HexwrightBossEntities.QUARTZ_SPIKES,
            com.bluup.hexwright.client.boss.QuartzSpikeRenderer::new);
        EntityRendererRegistry.register(com.bluup.hexwright.server.boss.HexwrightBossEntities.QUARTZ_PUPA,
            com.bluup.hexwright.client.boss.QuartzPupaRenderer::new);
        EntityRendererRegistry.register(com.bluup.hexwright.server.boss.HexwrightBossEntities.QUARTZ_SHARDLING,
            com.bluup.hexwright.client.boss.QuartzShardlingRenderer::new);
        EntityRendererRegistry.register(com.bluup.hexwright.server.boss.HexwrightBossEntities.ANCIENT_GOLEM,
            com.bluup.hexwright.client.boss.ancient.AncientGolemRenderer::new);
        EntityRendererRegistry.register(com.bluup.hexwright.server.boss.HexwrightBossEntities.ANCIENT_BALLISTA,
            com.bluup.hexwright.client.boss.ancient.AncientBallistaRenderer::new);
        EntityRendererRegistry.register(com.bluup.hexwright.server.boss.HexwrightBossEntities.ANCIENT_MERCENARY,
            com.bluup.hexwright.client.boss.ancient.AncientMercenaryRenderer::new);
        EntityRendererRegistry.register(com.bluup.hexwright.server.boss.HexwrightBossEntities.GOLEM_HEAD,
            NoopRenderer::new);
        EntityRendererRegistry.register(com.bluup.hexwright.server.boss.HexwrightBossEntities.GOLEM_BOULDER,
            com.bluup.hexwright.client.boss.ancient.GolemBoulderRenderer::new);
        EntityRendererRegistry.register(com.bluup.hexwright.server.boss.HexwrightBossEntities.BALLISTA_BOLT,
            com.bluup.hexwright.client.boss.ancient.BallistaBoltRenderer::new);
        EntityRendererRegistry.register(com.bluup.hexwright.server.boss.HexwrightBossEntities.WITHER_STORM,
            com.bluup.hexwright.client.boss.storm.WitherStormRenderer::new);
        EntityRendererRegistry.register(com.bluup.hexwright.server.boss.HexwrightBossEntities.STORM_HEAD,
            NoopRenderer::new);
        EntityRendererRegistry.register(com.bluup.hexwright.server.boss.HexwrightBossEntities.BACTERIA_BOLT,
            com.bluup.hexwright.client.boss.storm.BacteriaBoltRenderer::new);
        EntityRendererRegistry.register(com.bluup.hexwright.server.boss.HexwrightBossEntities.RIFT_PORTAL,
            com.bluup.hexwright.client.boss.storm.RiftPortalRenderer::new);
        EntityRendererRegistry.register(com.bluup.hexwright.server.boss.HexwrightBossEntities.RIFT_CHUNK,
            com.bluup.hexwright.client.boss.storm.RiftChunkRenderer::new);
        EntityRendererRegistry.register(com.bluup.hexwright.server.boss.HexwrightBossEntities.COG_SENTINEL,
            com.bluup.hexwright.client.boss.cog.CogSentinelRenderer::new);
        EntityRendererRegistry.register(com.bluup.hexwright.server.boss.HexwrightBossEntities.COG_SPARK,
            com.bluup.hexwright.client.boss.cog.CogSparkRenderer::new);
        EntityRendererRegistry.register(com.bluup.hexwright.server.boss.HexwrightBossEntities.COG_BLAST,
            com.bluup.hexwright.client.boss.cog.CogBlastRenderer::new);
        EntityRendererRegistry.register(com.bluup.hexwright.server.boss.HexwrightBossEntities.CORRUPT_EXPERIMENT,
            context -> new com.bluup.hexwright.client.mob.ConstructRenderer<>(context,
                new com.bluup.hexwright.client.boss.corrupt.CorruptExperimentModel(), 1.4f));
        EntityRendererRegistry.register(com.bluup.hexwright.server.boss.HexwrightBossEntities.CORRUPT_CUBE,
            context -> new com.bluup.hexwright.client.mob.ConstructRenderer<>(context,
                new com.bluup.hexwright.client.boss.corrupt.CorruptCubeModel(), 0.0f));
        EntityRendererRegistry.register(com.bluup.hexwright.server.boss.HexwrightBossEntities.CORRUPT_CONSTRUCT,
            context -> new com.bluup.hexwright.client.mob.ConstructRenderer<>(context,
                new com.bluup.hexwright.client.boss.corrupt.CorruptConstructModel(), 0.6f));
        EntityRendererRegistry.register(com.bluup.hexwright.server.boss.HexwrightBossEntities.CORRUPT_SHARD,
            com.bluup.hexwright.client.boss.corrupt.CorruptShardRenderer::new);
        EntityRendererRegistry.register(com.bluup.hexwright.server.mob.HexwrightMobEntities.EXPERIMENTAL_CONSTRUCT,
            context -> new com.bluup.hexwright.client.mob.ConstructRenderer<>(context, "experimental_construct", 0.7f));
        EntityRendererRegistry.register(com.bluup.hexwright.server.mob.HexwrightMobEntities.SERVITOR_CONSTRUCT,
            context -> new com.bluup.hexwright.client.mob.ConstructRenderer<>(context, "servitor_construct", 0.6f));
        EntityRendererRegistry.register(com.bluup.hexwright.server.mob.HexwrightMobEntities.FRACTURED_CONSTRUCT,
            context -> new com.bluup.hexwright.client.mob.ConstructRenderer<>(context,
                new com.bluup.hexwright.client.mob.FracturedConstructModel(), 0.4f));
        EntityRendererRegistry.register(com.bluup.hexwright.server.mob.HexwrightMobEntities.RUNESTONE_TITAN,
            context -> new com.bluup.hexwright.client.mob.ConstructRenderer<>(context, "runestone_titan", 1.6f));
        EntityRendererRegistry.register(com.bluup.hexwright.server.powerorb.PowerOrbEntities.SPIRIT_GOLEM,
            com.bluup.hexwright.client.powerorb.SpiritGolemRenderer::new);
        EntityRendererRegistry.register(com.bluup.hexwright.server.powerorb.PowerOrbEntities.SPIRIT_WARD,
            com.bluup.hexwright.client.powerorb.SpiritWardRenderer::new);
        EntityRendererRegistry.register(com.bluup.hexwright.server.powerorb.PowerOrbEntities.SANCTUARY_HANDS,
            com.bluup.hexwright.client.powerorb.SanctuaryHandsRenderer::new);
        com.bluup.hexwright.client.boss.storm.StormLightning.register();
        com.bluup.hexwright.client.boss.cog.CogScorchClient.register();
        com.bluup.hexwright.client.boss.ancient.GolemZones.register();
        com.bluup.hexwright.client.vehicle.CarpetTailModels.registerModels();

        registerCustomLdlibWidgets();

        MenuScreens.register(HexwrightMenus.STAFF_ASSEMBLY_MENU, StaffAssemblyScreen::new);

        KeyBindingHelper.registerKeyBinding(PENTABOX_OVERLAY_KEY);
        KeyBindingHelper.registerKeyBinding(HEXICON_OVERLAY_KEY);
        KeyBindingHelper.registerKeyBinding(TRAIL_TUNE_KEY);
        KeyBindingHelper.registerKeyBinding(TRAIL_TUNE_AXIS_KEY);
        KeyBindingHelper.registerKeyBinding(TRAIL_TUNE_UP_KEY);
        KeyBindingHelper.registerKeyBinding(TRAIL_TUNE_DOWN_KEY);
        ClientTickEvents.END_CLIENT_TICK.register(client -> PentaboxOverlay.onClientTick(client, PENTABOX_OVERLAY_KEY.isDown()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> HexiconOverlay.onClientTick(client, HEXICON_OVERLAY_KEY.isDown()));
        ClientTickEvents.END_CLIENT_TICK.register(SatchelBackpackInventoryOverlay::onClientTick);
        ClientTickEvents.END_CLIENT_TICK.register(StaffBookOpenTracker::onClientTick);
        ClientTickEvents.END_CLIENT_TICK.register(AnchoriteFlightAnimationClient::onClientTick);
        ClientTickEvents.END_CLIENT_TICK.register(WeaponIdleAnimationClient::onClientTick);
        ClientTickEvents.END_CLIENT_TICK.register(com.bluup.hexwright.client.weapon.WeaponTrailVisualClient::onClientTick);
        ClientTickEvents.END_CLIENT_TICK.register(client ->
            com.bluup.hexwright.client.weapon.WeaponTrailTuner.onClientTick(client,
                TRAIL_TUNE_KEY, TRAIL_TUNE_AXIS_KEY, TRAIL_TUNE_UP_KEY, TRAIL_TUNE_DOWN_KEY));
        ClientTickEvents.END_CLIENT_TICK.register(com.bluup.hexwright.client.staff_assembly.StaffAmethystLearnClient::onClientTick);
        ClientTickEvents.END_CLIENT_TICK.register(com.bluup.hexwright.client.vehicle.VehicleDescendInputTracker::onClientTick);
        ClientTickEvents.END_CLIENT_TICK.register(com.bluup.hexwright.client.vehicle.VehicleDebugOverlay::onClientTick);
        HudRenderCallback.EVENT.register(PentaboxOverlay::onHudRender);
        HudRenderCallback.EVENT.register(HexiconOverlay::onHudRender);
        HudRenderCallback.EVENT.register(com.bluup.hexwright.client.weapon.WeaponTrailTuner::onHudRender);
        HudRenderCallback.EVENT.register(com.bluup.hexwright.client.vehicle.VehicleFlightHud::onHudRender);
        HudRenderCallback.EVENT.register(com.bluup.hexwright.client.vehicle.VehicleDebugOverlay::onHudRender);
        HudRenderCallback.EVENT.register(com.bluup.hexwright.client.debug.SpectaclesBlockOverlay::onHudRender);
        ItemTooltipCallback.EVENT.register((stack, context, lines) -> {
            Minecraft client = Minecraft.getInstance();
            if (isCrucibleScreenOpen(client)) {
                appendCrucibleOutputTooltip(stack, lines);
            }

            if (!PentaboxData.isLinkedStack(stack)) {
                return;
            }
            lines.add(Component.translatable("tooltip.hexwright.pentabox.contained").withStyle(ChatFormatting.DARK_AQUA));
        });

        ColorProviderRegistry.ITEM.register((stack, tintIndex) -> {
            if (tintIndex != 1) {
                return 0xFFFFFFFF;
            }

            Minecraft client = Minecraft.getInstance();
            UUID owner = client.player != null ? client.player.getUUID() : Util.NIL_UUID;
            ColorProvider provider = SOUL_COLOR_CACHE.computeIfAbsent(owner, uuid ->
                new FrozenPigment(new ItemStack(HexItems.UUID_PIGMENT), uuid).getColorProvider());

            float time = client.level != null
                ? client.level.getGameTime() + client.getFrameTime()
                : (System.currentTimeMillis() / 50f);
            return provider.getColor(time, Vec3.ZERO);
        }, HexwrightItems.HEXICON);

        ColorProviderRegistry.ITEM.register((stack, tintIndex) -> {
            if (tintIndex != 0 || !(stack.getItem() instanceof DyeableLeatherItem dyeable)) {
                return 0xFFFFFFFF;
            }
            return dyeable.getColor(stack);
        }, HexwrightItems.SEALED_SATCHEL);

        ColorProviderRegistry.ITEM.register((stack, tintIndex) -> {
            if (tintIndex != 0 || !(stack.getItem() instanceof DyeableLeatherItem dyeable)) {
                return 0xFFFFFFFF;
            }
            return dyeable.getColor(stack);
        }, HexwrightItems.MANTLE_OF_ASCENSION, HexwrightItems.HAT_OF_ASCENSION);

        ColorProviderRegistry.ITEM.register((stack, tintIndex) -> {
            if (tintIndex != 0 || !(stack.getItem() instanceof DyeableLeatherItem dyeable)) {
                return 0xFFFFFFFF;
            }
            return dyeable.getColor(stack);
        }, HexwrightItems.GOLEM_POWER_ORB, HexwrightItems.SANCTUARY_POWER_ORB);

        ItemProperties.register(HexwrightItems.HEXICON, HexAPI.modLoc("variant"),
            new ClampedItemPropertyFunction() {
                @SuppressWarnings("deprecation")
                @Override
                public float call(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity,
                                  int seed) {
                    return this.unclampedCall(stack, level, entity, seed);
                }

                @Override
                public float unclampedCall(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity,
                                           int seed) {
                    return HexiconItem.glamourOf(stack);
                }
            });

        ItemProperties.register(HexwrightItems.SEALED_SATCHEL, HexAPI.modLoc("variant"),
            new ClampedItemPropertyFunction() {
                @SuppressWarnings("deprecation")
                @Override
                public float call(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity,
                                  int seed) {
                    return this.unclampedCall(stack, level, entity, seed);
                }

                @Override
                public float unclampedCall(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity,
                                           int seed) {
                    return SatchelItem.designOf(stack);
                }
            });

        ItemProperties.register(HexwrightItems.CONFIGURABLE_STAFF, Hexwright.id("model"),
            (stack, level, entity, seed) -> {
                List<StaffPart> models = StaffParts.options(StaffPartCategory.MODEL);
                String modelId = StaffParts.modelId(stack);

                int index = -1;
                for (int i = 0; i < models.size(); i++) {
                    if (models.get(i).id().equals(modelId)) {
                        index = i;
                        break;
                    }
                }

                if (index < 0) {
                    return 0.0f;
                }

                int denominator = Math.max(1, models.size());
                return (index + 1) / (float) denominator;
            });

        ItemProperties.register(HexwrightItems.CONFIGURABLE_STAFF, Hexwright.id("open"),
            (stack, level, entity, seed) -> {
                Minecraft client = Minecraft.getInstance();
                if (entity == client.player) {
                    return StaffBookOpenTracker.isOpenLocally(stack) ? 1.0f : 0.0f;
                }
                if (entity != null && StaffBookOpenTracker.isOpenRemote(entity.getUUID())) {
                    return 1.0f;
                }
                return 0.0f;
            });

        ItemProperties.register(HexwrightItems.HARMONIZED_PENTABOX, Hexwright.id("state"),
            (stack, level, entity, seed) -> {
                boolean hasAnyStored = false;
                for (var contained : PentaboxData.loadItems(stack)) {
                    if (!contained.isEmpty()) {
                        hasAnyStored = true;
                        break;
                    }
                }
                if (!hasAnyStored) {
                    return 0.1f;
                }
                if (PentaboxData.getSelectedStack(stack).isEmpty()) {
                    return 0.2f;
                }
                return 0.0f;
            });

        ItemProperties.register(HexwrightItems.RELIQUARY_SEAL, Hexwright.id("attuned"),
            (stack, level, entity, seed) -> ReliquarySealItem.attunedPos(stack) != null ? 1.0f : 0.0f);

        ColorProviderRegistry.ITEM.register((stack, tintIndex) -> {
            if (tintIndex != 0) {
                return 0xFFFFFFFF;
            }
            var mix = com.bluup.hexwright.server.remnant.BottleData.getMixture(stack);
            return mix.isEmpty() ? 0xFFFFFFFF : mix.tint(0xFFFFFF) | 0xFF000000;
        }, HexwrightItems.HEX_ENGRAVED_BOTTLE);

        ItemProperties.register(HexwrightItems.HEX_ENGRAVED_BOTTLE, Hexwright.id("bottle"),
            new ClampedItemPropertyFunction() {
                @SuppressWarnings("deprecation")
                @Override
                public float call(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity,
                                  int seed) {
                    return this.unclampedCall(stack, level, entity, seed);
                }

                @Override
                public float unclampedCall(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity,
                                           int seed) {
                    return com.bluup.hexwright.server.remnant.BottleData.modelIndex(stack);
                }
            });

        var drawableBows = new java.util.ArrayList<>(HexwrightItems.ARCHER_GREAT_BOWS.values());
        drawableBows.add(HexwrightItems.ETERNAL_BOW);
        drawableBows.add(HexwrightItems.STARFALL_BOW);
        for (var bow : drawableBows) {
            ItemProperties.register(bow, new ResourceLocation("pull"),
                (stack, level, entity, seed) -> {
                    if (entity == null || entity.getUseItem() != stack) {
                        return 0.0f;
                    }
                    return (stack.getUseDuration() - entity.getUseItemRemainingTicks()) / 20.0f;
                });
            ItemProperties.register(bow, new ResourceLocation("pulling"),
                (stack, level, entity, seed) ->
                    entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0f : 0.0f);
        }

        ItemProperties.register(HexwrightItems.BROOM, Hexwright.id("variant"),
            (stack, level, entity, seed) -> BroomVariant.of(stack).modelFraction());
        ItemProperties.register(HexwrightItems.CARPET, Hexwright.id("variant"),
            (stack, level, entity, seed) -> CarpetVariant.of(stack).modelFraction());

        ItemProperties.register(HexwrightItems.ANCHOR_SLATE, Hexwright.id("rune"),
            new ClampedItemPropertyFunction() {
                @SuppressWarnings("deprecation")
                @Override
                public float call(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity,
                                  int seed) {
                    return this.unclampedCall(stack, level, entity, seed);
                }

                @Override
                public float unclampedCall(ItemStack stack, @Nullable ClientLevel level,
                                           @Nullable LivingEntity entity, int seed) {
                    return DungeonGroups.runeIndexOf(AnchorSlateItem.runeOf(stack));
                }
            });
    }

    private static boolean isCrucibleScreenOpen(Minecraft client) {
        if (client.screen == null) {
            return false;
        }
        String screenTitle = client.screen.getTitle().getString();
        String crucibleTitle = Component.translatable("block.hexwright.crucible").getString();
        return screenTitle.equals(crucibleTitle);
    }

    private static void appendCrucibleOutputTooltip(ItemStack stack, List<Component> lines) {
        AspectMappings.profileFor(stack.getItem()).ifPresent(profile -> {
            if (profile.data().categories().isEmpty()) {
                return;
            }

            lines.add(Component.translatable("tooltip.hexwright.crucible.produces").withStyle(ChatFormatting.GRAY));
            List<IngredientCategory> sortedAspects = profile.data().categories().stream()
                .sorted()
                .collect(Collectors.toList());
            for (IngredientCategory aspect : sortedAspects) {
                lines.add(Component.literal("  ")
                    .append(EndlessPouchItem.aspectLine(aspect, profile.essenceYield()))
                    .withStyle(ChatFormatting.DARK_PURPLE));
            }
        });
    }

    private static void registerCustomLdlibWidgets() {
        registerCustomLdlibWidget(StaffItemDisplayWidget.class, StaffItemDisplayWidget::new);
        registerCustomLdlibWidget(ContainerSlotGridWidget.class, ContainerSlotGridWidget::new);
        AnnotationDetector.REGISTER_WIDGETS.sort((a, b) -> b.annotation().priority() - a.annotation().priority());
    }

    private static void registerCustomLdlibWidget(Class<? extends IConfigurableWidget> widgetClass, Supplier<IConfigurableWidget> creator) {
        LDLRegister register = widgetClass.getAnnotation(LDLRegister.class);
        if (register == null) {
            return;
        }

        boolean alreadyPresent = AnnotationDetector.REGISTER_WIDGETS.stream()
            .anyMatch(wrapper -> wrapper.annotation().name().equals(register.name()));
        if (alreadyPresent) {
            return;
        }

        AnnotationDetector.REGISTER_WIDGETS.add(new AnnotationDetector.Wrapper<>(register, widgetClass, creator));
        HexwrightDebug.log(HexwrightDebug.RENDER, "Registered custom LDLib widget '{}' in group '{}'",
            register.name(), register.group());
    }
}
