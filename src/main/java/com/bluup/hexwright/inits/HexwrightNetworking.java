package com.bluup.hexwright.inits;

import com.bluup.hexwright.server.accessory.WornAccessories;
import at.petrak.hexcasting.api.pigment.FrozenPigment;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.client.reliquary.SatchelBackpackInventoryOverlay;
import com.bluup.hexwright.client.animation.PlayerAnimationClient;
import com.bluup.hexwright.client.staff_assembly.StaffCoreBoltVisualClient;
import com.bluup.hexwright.client.staff_assembly.StaffCoreSphereVisualClient;
import com.bluup.hexwright.client.staff_assembly.StaffTravellerWarpVisualClient;
import com.bluup.hexwright.common.animation.PlayerAnimationLayer;
import com.bluup.hexwright.server.hexicon.HexiconData;
import com.bluup.hexwright.server.hexicon.HexiconUIFactory;
import com.bluup.hexwright.server.item.HexwrightItems;
import com.bluup.hexwright.server.item.PentaboxItem;
import com.bluup.hexwright.server.pentabox.PentaboxData;
import com.bluup.hexwright.server.talisman.TalismanDesign;
import com.bluup.hexwright.server.talisman.TalismanItem;
import com.bluup.hexwright.server.reliquary.ReliquaryStore;
import com.bluup.hexwright.server.reliquary.ReliquaryWindow;
import com.bluup.hexwright.server.reliquary.SatchelItem;
import com.bluup.hexwright.server.reliquary.SatchelUIFactory;
import com.bluup.hexwright.server.signet.ArtisanSignetItem;
import com.bluup.hexwright.server.signet.SignatureMark;
import com.bluup.hexwright.server.staff_assembly.StaffCoreBeamHandler;
import com.bluup.hexwright.server.staff_assembly.StaffGreatSpellData;
import com.bluup.hexwright.server.staff_assembly.StaffPowers;
import at.petrak.hexcasting.api.casting.eval.ExecutionClientView;
import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType;
import at.petrak.hexcasting.api.casting.eval.vm.CastingImage;
import at.petrak.hexcasting.api.casting.eval.vm.CastingVM;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.common.msgs.MsgNewSpellPatternS2C;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public final class HexwrightNetworking {
    public static final ResourceLocation STAFF_CORE_BEAM_C2S = Hexwright.id("staff_core_beam_c2s");
    public static final ResourceLocation STAFF_CORE_SPHERE_VISUAL_S2C = Hexwright.id("staff_core_sphere_visual_s2c");
    public static final ResourceLocation STAFF_TRAVELLER_WARP_S2C = Hexwright.id("staff_traveller_warp_s2c");
    public static final ResourceLocation STAFF_TIP_FLASH_S2C = Hexwright.id("staff_tip_flash_s2c");
    public static final ResourceLocation PLAYER_ANIMATION_S2C = Hexwright.id("player_animation_s2c");
    public static final ResourceLocation PENTABOX_SELECT_C2S = Hexwright.id("pentabox_select_c2s");
    public static final ResourceLocation PENTABOX_OPEN_MENU_C2S = Hexwright.id("pentabox_open_menu_c2s");
    public static final ResourceLocation HEXICON_SELECT_C2S = Hexwright.id("hexicon_select_c2s");
    public static final ResourceLocation HEXICON_APPLY_C2S = Hexwright.id("hexicon_apply_c2s");
    public static final ResourceLocation HEXICON_OPEN_MENU_C2S = Hexwright.id("hexicon_open_menu_c2s");
    public static final ResourceLocation ASPECT_SYNC_S2C = Hexwright.id("aspect_sync_s2c");
    public static final ResourceLocation MASTERY_SYNC_S2C = Hexwright.id("mastery_sync_s2c");
    public static final ResourceLocation RECIPE_UNLOCK_SYNC_S2C = Hexwright.id("recipe_unlock_sync_s2c");
    public static final ResourceLocation RESONANCE_NAMES_SYNC_S2C = Hexwright.id("resonance_names_sync_s2c");
    public static final ResourceLocation ARTISAN_SIGNET_SIGN_C2S = Hexwright.id("artisan_signet_sign_c2s");
    public static final ResourceLocation TALISMAN_DESIGN_C2S = Hexwright.id("talisman_design_c2s");
    public static final ResourceLocation SATCHEL_BACKPACK_VIEW_REQUEST_C2S = Hexwright.id("satchel_backpack_view_request_c2s");
    public static final ResourceLocation SATCHEL_BACKPACK_VIEW_SYNC_S2C = Hexwright.id("satchel_backpack_view_sync_s2c");
    public static final ResourceLocation SATCHEL_BACKPACK_SLOT_CLICK_C2S = Hexwright.id("satchel_backpack_slot_click_c2s");
    public static final ResourceLocation STAFF_MODEL_SELECT_C2S = Hexwright.id("staff_model_select_c2s");
    public static final ResourceLocation HEXICON_BOOK_OPEN_C2S = Hexwright.id("hexicon_book_open_c2s");
    public static final ResourceLocation HEXICON_BOOK_OPEN_S2C = Hexwright.id("hexicon_book_open_s2c");
    public static final ResourceLocation AMETHYST_LEARN_C2S = Hexwright.id("amethyst_learn_c2s");
    public static final ResourceLocation AMETHYST_GREAT_SPELL_PUSH_C2S = Hexwright.id("amethyst_great_spell_push_c2s");
    public static final ResourceLocation VEHICLE_RECONCILE_S2C = Hexwright.id("vehicle_reconcile_s2c");
    public static final ResourceLocation WARD_TRIGGER_S2C = Hexwright.id("ward_trigger_s2c");
    public static final ResourceLocation PROJECTILE_HIT_S2C = Hexwright.id("projectile_hit_s2c");
    public static final ResourceLocation GROUND_SLAM_S2C = Hexwright.id("ground_slam_s2c");
    public static final ResourceLocation VEHICLE_DESCEND_INPUT_C2S = Hexwright.id("vehicle_descend_input_c2s");
    public static final ResourceLocation VEHICLE_DEBUG_REQUEST_C2S = Hexwright.id("vehicle_debug_request_c2s");
    public static final ResourceLocation VEHICLE_DEBUG_S2C = Hexwright.id("vehicle_debug_s2c");
    public static final ResourceLocation PORTAL_SYNC_S2C = Hexwright.id("portal_sync_s2c");
    public static final ResourceLocation VOID_TEAR_S2C = Hexwright.id("void_tear_s2c");
    public static final ResourceLocation PORTAL_USE_C2S = Hexwright.id("portal_use_c2s");
    public static final ResourceLocation PORTAL_ATTACK_C2S = Hexwright.id("portal_attack_c2s");
    public static final ResourceLocation PORTAL_CROSS_C2S = Hexwright.id("portal_cross_c2s");
    public static final ResourceLocation VAULT_REMOTE_LEVEL_INIT_S2C = Hexwright.id("vault_remote_level_init_s2c");
    public static final ResourceLocation VAULT_REMOTE_CHUNK_S2C = Hexwright.id("vault_remote_chunk_s2c");
    public static final ResourceLocation VAULT_REMOTE_FORGET_S2C = Hexwright.id("vault_remote_forget_s2c");
    public static final ResourceLocation VAULT_REMOTE_BLOCK_S2C = Hexwright.id("vault_remote_block_s2c");
    public static final ResourceLocation VAULT_REMOTE_ENTITIES_S2C = Hexwright.id("vault_remote_entities_s2c");
    public static final ResourceLocation VAULT_REMOTE_DESTROY_S2C = Hexwright.id("vault_remote_destroy_s2c");
    public static final ResourceLocation VAULT_RETAINED_C2S = Hexwright.id("vault_retained_c2s");

    private static final Map<UUID, List<ItemStack>> SATCHEL_BACKPACK_VIEW_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static final Map<UUID, Boolean> VEHICLE_DESCEND_HELD = new java.util.concurrent.ConcurrentHashMap<>();

    private HexwrightNetworking() {
    }

    public static void sendAspectSync(ServerPlayer player) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        com.bluup.hexwright.common.aspects.AspectMappings.write(buf);
        ServerPlayNetworking.send(player, ASPECT_SYNC_S2C, buf);
    }

    public static void sendMasterySync(ServerPlayer player) {
        com.bluup.hexwright.server.pocketcaster.PocketCasterData.Quality best =
            com.bluup.hexwright.server.progression.Mastery.of(player).bestQuality();
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(best == null ? -1 : best.ordinal());
        ServerPlayNetworking.send(player, MASTERY_SYNC_S2C, buf);
    }

    public static void sendRecipeUnlockSync(ServerPlayer player) {
        java.util.Set<String> unlocked = com.bluup.hexwright.server.progression.RecipeUnlocks.all(player);
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(unlocked.size());
        for (String recipe : unlocked) {
            buf.writeUtf(recipe);
        }
        ServerPlayNetworking.send(player, RECIPE_UNLOCK_SYNC_S2C, buf);
    }

    public static void sendResonanceNames(ServerPlayer player) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(true);
        com.bluup.hexwright.server.network.ResonanceNames.get(player.server).writeAll(buf);
        ServerPlayNetworking.send(player, RESONANCE_NAMES_SYNC_S2C, buf);
    }

    public static void broadcastResonanceName(net.minecraft.server.MinecraftServer server,
                                              String networkKey, String name) {
        for (ServerPlayer player : PlayerLookup.all(server)) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeBoolean(false);
            buf.writeVarInt(1);
            buf.writeUtf(networkKey);
            buf.writeUtf(name);
            ServerPlayNetworking.send(player, RESONANCE_NAMES_SYNC_S2C, buf);
        }
    }

    public static void sendVehicleReconciliation(ServerPlayer rider, int vehicleEntityId, Vec3 pos, Vec3 vel) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(vehicleEntityId);
        buf.writeDouble(pos.x);
        buf.writeDouble(pos.y);
        buf.writeDouble(pos.z);
        buf.writeDouble(vel.x);
        buf.writeDouble(vel.y);
        buf.writeDouble(vel.z);
        ServerPlayNetworking.send(rider, VEHICLE_RECONCILE_S2C, buf);
    }

    public static boolean isVehicleDescendHeld(ServerPlayer player) {
        return VEHICLE_DESCEND_HELD.getOrDefault(player.getUUID(), false);
    }

    private static final double DEBUG_READOUT_RANGE = 24.0;

    public static void requestVehicleDebug(int vehicleEntityId) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(vehicleEntityId);
        ClientPlayNetworking.send(VEHICLE_DEBUG_REQUEST_C2S, buf);
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(STAFF_CORE_BEAM_C2S, (server, player, handler, buf, responseSender) -> {
            boolean active = buf.readBoolean();
            boolean crosshairFree = buf.readBoolean();
            boolean leftClickBusy = buf.readBoolean();
            server.execute(() -> StaffCoreBeamHandler.handle(player, active, crosshairFree, leftClickBusy));
        });

        ServerPlayNetworking.registerGlobalReceiver(PENTABOX_SELECT_C2S, (server, player, handler, buf, responseSender) -> {
            InteractionHand hand = readHand(buf);
            int index = buf.readVarInt();
            server.execute(() -> PentaboxData.selectFromContext(player, hand, index));
        });

        ServerPlayNetworking.registerGlobalReceiver(PENTABOX_OPEN_MENU_C2S, (server, player, handler, buf, responseSender) -> {
            InteractionHand hand = readHand(buf);
            server.execute(() -> {
                ItemStack held = player.getItemInHand(hand);
                if (held.getItem() instanceof PentaboxItem) {
                    PentaboxItem.openMenuInHand(player, held, hand);
                    return;
                }
                if (PentaboxData.isLinkedStack(held)) {
                    ItemStack pentabox = PentaboxData.getLinkedPentabox(held);
                    if (pentabox.getItem() instanceof PentaboxItem) {
                        PentaboxItem.openMenuFromLinkedSlot(player, hand);
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(HEXICON_SELECT_C2S, (server, player, handler, buf, responseSender) -> {
            InteractionHand hand = readHand(buf);
            int barIndex = buf.readVarInt();
            int slotIndex = buf.readVarInt();
            UUID expectedLibraryId = buf.readBoolean() ? buf.readUUID() : null;

            server.execute(() -> {
                ItemStack held = player.getItemInHand(hand);
                if (!HexiconData.isHexiconStack(held)) {
                    return;
                }

                if (expectedLibraryId != null) {
                    UUID actualLibraryId = HexiconData.getLibraryId(held);
                    if (actualLibraryId != null && !expectedLibraryId.equals(actualLibraryId)) {
                        return;
                    }
                }

                HexiconData.setSelectedBarAndSlot(held, barIndex, slotIndex);
                if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    HexiconData.refreshCachedSelectedBound(serverLevel, held);
                    HexiconData.refreshCachedBarDisplay(serverLevel, held, barIndex);
                    HexiconData.refreshCachedWrittenCount(serverLevel, held);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(HEXICON_APPLY_C2S, (server, player, handler, buf, responseSender) -> {
            InteractionHand hand = readHand(buf);
            int barIndex = buf.readVarInt();
            int slotIndex = buf.readVarInt();
            boolean editingChapter = buf.readBoolean();
            UUID expectedLibraryId = buf.readBoolean() ? buf.readUUID() : null;
            String nameInput = buf.readUtf();
            String rawIcon = buf.readBoolean() ? buf.readUtf() : null;

            server.execute(() -> {
                ItemStack held = player.getItemInHand(hand);
                if (!HexiconData.isHexiconStack(held)) {
                    return;
                }

                if (expectedLibraryId != null) {
                    UUID actualLibraryId = HexiconData.getLibraryId(held);
                    if (actualLibraryId != null && !expectedLibraryId.equals(actualLibraryId)) {
                        return;
                    }
                }

                HexiconData.setSelectedBarAndSlot(held, barIndex, slotIndex);
                if (!(player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
                    return;
                }

                int clampedBar = Math.max(0, Math.min(HexiconData.getAvailableBars(held) - 1, barIndex));
                int clampedSlot = Math.max(0, Math.min(HexiconData.SLOTS_PER_BAR - 1, slotIndex));
                String fallbackSpellName = "Spell " + (clampedSlot + 1);
                String fallbackChapterName = "Chapter " + (clampedBar + 1);
                String normalizedName = normalizeName(nameInput, fallbackSpellName);
                ResourceLocation icon = parseIcon(rawIcon);

                UUID payloadId = HexiconData.getPayloadId(serverLevel, HexiconData.getLibraryId(held), clampedBar, clampedSlot);
                if (editingChapter) {
                    HexiconData.setChapterName(held, clampedBar, normalizeName(nameInput, fallbackChapterName));
                    HexiconData.setChapterIcon(held, clampedBar, icon);
                } else if (payloadId != null) {
                    HexiconData.setSpellDisplay(serverLevel, payloadId, normalizedName, icon);
                }

                HexiconData.refreshCachedSelectedBound(serverLevel, held);
                HexiconData.refreshCachedBarDisplay(serverLevel, held, clampedBar);
                HexiconData.refreshCachedWrittenCount(serverLevel, held);
                player.getInventory().setChanged();
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(HEXICON_OPEN_MENU_C2S, (server, player, handler, buf, responseSender) -> {
            InteractionHand hand = readHand(buf);
            server.execute(() -> {
                ItemStack held = player.getItemInHand(hand);
                if (HexiconData.isHexiconStack(held)) {
                    HexiconUIFactory.INSTANCE.openForHand(player, hand);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ARTISAN_SIGNET_SIGN_C2S, (server, player, handler, buf, responseSender) -> {
            InteractionHand hand = readHand(buf);
            byte[] bits = buf.readByteArray(SignatureMark.PACKED_BYTES);
            server.execute(() -> {
                ItemStack held = player.getItemInHand(hand);
                if (held.getItem() instanceof ArtisanSignetItem && !SignatureMark.hasMark(held) && bits.length == SignatureMark.PACKED_BYTES) {
                    SignatureMark.setMark(held, bits);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(TALISMAN_DESIGN_C2S, (server, player, handler, buf, responseSender) -> {
            InteractionHand hand = readHand(buf);
            byte[] pixels = buf.readByteArray(TalismanDesign.PACKED_BYTES);
            long mask = buf.readLong();
            server.execute(() -> {
                ItemStack held = player.getItemInHand(hand);
                if (held.getItem() instanceof TalismanItem
                    && TalismanDesign.isBlank(held)
                    && pixels.length == TalismanDesign.PACKED_BYTES
                    && mask != 0L) {
                    TalismanDesign.setDesign(held, pixels, mask);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SATCHEL_BACKPACK_VIEW_REQUEST_C2S, (server, player, handler, buf, responseSender) ->
            server.execute(() -> sendSatchelBackpackView(player)));

        ServerPlayNetworking.registerGlobalReceiver(SATCHEL_BACKPACK_SLOT_CLICK_C2S, (server, player, handler, buf, responseSender) -> {
            int slotIndex = buf.readVarInt();
            int targetMenuSlot = buf.readVarInt();
            boolean clientOwnedCursor = buf.readBoolean();
            ItemStack clientCarried = clientOwnedCursor ? buf.readItem() : ItemStack.EMPTY;
            server.execute(() ->
                handleSatchelBackpackSlotClick(player, slotIndex, targetMenuSlot, clientOwnedCursor, clientCarried));
        });

        ServerPlayNetworking.registerGlobalReceiver(HEXICON_BOOK_OPEN_C2S, (server, player, handler, buf, responseSender) -> {
            boolean open = buf.readBoolean();
            server.execute(() -> {
                for (ServerPlayer tracking : PlayerLookup.tracking(player)) {
                    if (tracking != player) {
                        ServerPlayNetworking.send(tracking, HEXICON_BOOK_OPEN_S2C, writeBookOpenBuf(player.getUUID(), open));
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AMETHYST_LEARN_C2S, (server, player, handler, buf, responseSender) -> {
            InteractionHand hand = readHand(buf);
            server.execute(() -> {
                ItemStack staff = player.getItemInHand(hand);
                if (!staff.is(HexwrightItems.CONFIGURABLE_STAFF) || !StaffPowers.hasAmethystCore(staff)) {
                    return;
                }

                InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                ItemStack scroll = player.getItemInHand(otherHand);
                if (!StaffGreatSpellData.isGreatSpellScroll(scroll)) {
                    return;
                }

                StaffGreatSpellData.LearnResult result = StaffGreatSpellData.learnFromScroll(staff, scroll);
                switch (result) {
                    case ADDED -> {
                        if (!player.getAbilities().instabuild) {
                            scroll.shrink(1);
                        }
                        player.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable(
                                "message.hexwright.amethyst_core.learned",
                                StaffGreatSpellData.count(staff),
                                StaffGreatSpellData.maxLearned(staff)
                            ).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE),
                            true
                        );
                    }
                    case ALREADY_LEARNED -> player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.hexwright.amethyst_core.duplicate")
                            .withStyle(net.minecraft.ChatFormatting.YELLOW),
                        true
                    );
                    case FULL -> player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.hexwright.amethyst_core.full", StaffGreatSpellData.maxLearned(staff))
                            .withStyle(net.minecraft.ChatFormatting.RED),
                        true
                    );
                    case SCROLL_NOT_READY -> player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.hexwright.amethyst_core.not_ready")
                            .withStyle(net.minecraft.ChatFormatting.RED),
                        true
                    );
                    case INVALID_SCROLL -> player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("message.hexwright.amethyst_core.invalid")
                            .withStyle(net.minecraft.ChatFormatting.RED),
                        true
                    );
                }

                player.getInventory().setChanged();
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AMETHYST_GREAT_SPELL_PUSH_C2S, (server, player, handler, buf, responseSender) -> {
            InteractionHand hand = readHand(buf);
            int learnedIndex = buf.readVarInt();
            server.execute(() -> {
                if (learnedIndex < 0 || learnedIndex >= StaffGreatSpellData.MAX_LEARNED) {
                    return;
                }

                ItemStack held = player.getItemInHand(hand);
                if (!held.is(HexwrightItems.CONFIGURABLE_STAFF)
                    || !StaffPowers.hasAmethystCore(held)) {
                    return;
                }

                List<StaffGreatSpellData.LearnedGreatSpell> learned = StaffGreatSpellData.getLearned(held);
                if (learnedIndex >= learned.size()) {
                    return;
                }

                CastingVM vm = IXplatAbstractions.INSTANCE.getStaffcastVM(player, hand);
                CastingImage image = vm.getImage();
                List<Iota> updatedStack = new ArrayList<>(image.getStack());
                updatedStack.add(learned.get(learnedIndex).toPatternIota());

                vm.setImage(image.copy(
                    updatedStack,
                    image.getParenCount(),
                    image.getParenthesized(),
                    image.getEscapeNext(),
                    image.getOpsConsumed(),
                    image.getUserData().copy()
                ));

                var descs = vm.generateDescs();
                ExecutionClientView view = new ExecutionClientView(
                    updatedStack.isEmpty(),
                    ResolvedPatternType.ESCAPED,
                    descs.getFirst(),
                    descs.getSecond()
                );
                IXplatAbstractions.INSTANCE.sendPacketToPlayer(player, new MsgNewSpellPatternS2C(view, -1));
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(VEHICLE_DESCEND_INPUT_C2S, (server, player, handler, buf, responseSender) -> {
            boolean held = buf.readBoolean();
            server.execute(() -> {
                if (held) {
                    VEHICLE_DESCEND_HELD.put(player.getUUID(), true);
                } else {
                    VEHICLE_DESCEND_HELD.remove(player.getUUID());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(VEHICLE_DEBUG_REQUEST_C2S, (server, player, handler, buf, responseSender) -> {
            int entityId = buf.readVarInt();
            server.execute(() -> {
                if (!(player.level().getEntity(entityId) instanceof com.bluup.hexwright.server.vehicle.VehicleEntity vehicle)) {
                    return;
                }
                boolean nearby = vehicle.hasPassenger(player)
                    || vehicle.distanceToSqr(player) <= DEBUG_READOUT_RANGE * DEBUG_READOUT_RANGE;
                if (!nearby || !WornAccessories.isWearing(player, HexwrightItems.WARDERS_SPECTACLES)) {
                    return;
                }
                FriendlyByteBuf response = PacketByteBufs.create();
                response.writeVarInt(entityId);
                com.bluup.hexwright.server.vehicle.VehicleDebugSnapshot.of(vehicle).write(response);
                ServerPlayNetworking.send(player, VEHICLE_DEBUG_S2C, response);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(PORTAL_USE_C2S, (server, player, handler, buf, responseSender) -> {
            UUID pairId = buf.readUUID();
            int side = buf.readVarInt();
            server.execute(() -> com.bluup.hexwright.server.portal.PortalInteraction.handleUse(player, pairId, side));
        });

        ServerPlayNetworking.registerGlobalReceiver(PORTAL_ATTACK_C2S, (server, player, handler, buf, responseSender) -> {
            UUID pairId = buf.readUUID();
            int side = buf.readVarInt();
            int action = buf.readVarInt();
            server.execute(() -> com.bluup.hexwright.server.portal.PortalInteraction
                .handleAttack(player, pairId, side, action));
        });

        ServerPlayNetworking.registerGlobalReceiver(PORTAL_CROSS_C2S, (server, player, handler, buf, responseSender) -> {
            UUID pairId = buf.readUUID();
            int side = buf.readVarInt();
            server.execute(() -> com.bluup.hexwright.server.portal.PortalManager.clientCross(player, pairId, side));
        });

        ServerPlayNetworking.registerGlobalReceiver(VAULT_RETAINED_C2S, (server, player, handler, buf, responseSender) -> {
            ResourceLocation dimension = buf.readResourceLocation();
            boolean retained = buf.readBoolean();
            server.execute(() -> com.bluup.hexwright.server.vault.VaultChunkStreamer
                .setRetained(player.getUUID(), dimension, retained));
        });

        ServerPlayNetworking.registerGlobalReceiver(STAFF_MODEL_SELECT_C2S, (server, player, handler, buf, responseSender) -> {
            net.minecraft.core.BlockPos pos = buf.readBlockPos();
            String modelId = buf.readUtf();
            server.execute(() -> {
                if (player.level().getBlockEntity(pos) instanceof com.bluup.hexwright.server.block.StaffAssemblyBlockEntity staffAssembly) {
                    staffAssembly.applyModelSelection(modelId);
                }
            });
        });
    }

    public static void sendPentaboxSelect(InteractionHand hand, int index) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        writeHand(buf, hand);
        buf.writeVarInt(index);
        ClientPlayNetworking.send(PENTABOX_SELECT_C2S, buf);
    }

    public static void sendPentaboxOpenMenu(InteractionHand hand) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        writeHand(buf, hand);
        ClientPlayNetworking.send(PENTABOX_OPEN_MENU_C2S, buf);
    }

    public static void sendHexiconSelection(InteractionHand hand, int barIndex, int slotIndex, @Nullable UUID libraryId) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        writeHand(buf, hand);
        buf.writeVarInt(barIndex);
        buf.writeVarInt(slotIndex);
        if (libraryId != null) {
            buf.writeBoolean(true);
            buf.writeUUID(libraryId);
        } else {
            buf.writeBoolean(false);
        }
        ClientPlayNetworking.send(HEXICON_SELECT_C2S, buf);
    }

    public static void sendHexiconApply(InteractionHand hand, int barIndex, int slotIndex, boolean editingChapter, String nameInput, @Nullable String rawIcon, @Nullable UUID libraryId) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        writeHand(buf, hand);
        buf.writeVarInt(barIndex);
        buf.writeVarInt(slotIndex);
        buf.writeBoolean(editingChapter);
        if (libraryId != null) {
            buf.writeBoolean(true);
            buf.writeUUID(libraryId);
        } else {
            buf.writeBoolean(false);
        }
        buf.writeUtf(nameInput == null ? "" : nameInput);
        if (rawIcon == null || rawIcon.isBlank()) {
            buf.writeBoolean(false);
        } else {
            buf.writeBoolean(true);
            buf.writeUtf(rawIcon);
        }
        ClientPlayNetworking.send(HEXICON_APPLY_C2S, buf);
    }

    public static void sendHexiconOpenMenu(InteractionHand hand) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        writeHand(buf, hand);
        ClientPlayNetworking.send(HEXICON_OPEN_MENU_C2S, buf);
    }

    public static void sendArtisanSignetSign(InteractionHand hand, byte[] packedBits) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        writeHand(buf, hand);
        buf.writeByteArray(packedBits);
        ClientPlayNetworking.send(ARTISAN_SIGNET_SIGN_C2S, buf);
    }

    public static void sendTalismanDesign(InteractionHand hand, byte[] packedPixels, long mask) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        writeHand(buf, hand);
        buf.writeByteArray(packedPixels);
        buf.writeLong(mask);
        ClientPlayNetworking.send(TALISMAN_DESIGN_C2S, buf);
    }

    public static void requestSatchelBackpackView() {
        ClientPlayNetworking.send(SATCHEL_BACKPACK_VIEW_REQUEST_C2S, PacketByteBufs.create());
    }

    public static void sendSatchelBackpackSlotClick(int slotIndex, int targetMenuSlot,
                                                    boolean clientOwnedCursor, ItemStack clientCarried) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(slotIndex);
        buf.writeVarInt(targetMenuSlot);
        buf.writeBoolean(clientOwnedCursor);
        if (clientOwnedCursor) {
            buf.writeItem(clientCarried);
        }
        ClientPlayNetworking.send(SATCHEL_BACKPACK_SLOT_CLICK_C2S, buf);
    }

    public static void sendStaffModelSelect(net.minecraft.core.BlockPos pos, String modelId) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeUtf(modelId);
        ClientPlayNetworking.send(STAFF_MODEL_SELECT_C2S, buf);
    }

    public static void sendHexiconBookOpen(boolean open) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(open);
        ClientPlayNetworking.send(HEXICON_BOOK_OPEN_C2S, buf);
    }

    public static void sendAmethystLearn(InteractionHand hand) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        writeHand(buf, hand);
        ClientPlayNetworking.send(AMETHYST_LEARN_C2S, buf);
    }

    public static void sendAmethystGreatSpellPush(InteractionHand hand, int learnedIndex) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        writeHand(buf, hand);
        buf.writeVarInt(learnedIndex);
        ClientPlayNetworking.send(AMETHYST_GREAT_SPELL_PUSH_C2S, buf);
    }

    public static void sendVehicleDescendInput(boolean held) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(held);
        ClientPlayNetworking.send(VEHICLE_DESCEND_INPUT_C2S, buf);
    }

    private static FriendlyByteBuf writeBookOpenBuf(UUID playerId, boolean open) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUUID(playerId);
        buf.writeBoolean(open);
        return buf;
    }

    private static InteractionHand readHand(FriendlyByteBuf buf) {
        return buf.readBoolean() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    private static void writeHand(FriendlyByteBuf buf, InteractionHand hand) {
        buf.writeBoolean(hand == InteractionHand.OFF_HAND);
    }

    private static String normalizeName(String input, String fallback) {
        String trimmed = input == null ? "" : input.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static @Nullable ResourceLocation parseIcon(@Nullable String rawIcon) {
        if (rawIcon == null || rawIcon.isBlank()) {
            return null;
        }
        return ResourceLocation.tryParse(rawIcon);
    }

    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(ASPECT_SYNC_S2C, (client, handler, buf, responseSender) -> {
            var mappings = com.bluup.hexwright.common.aspects.AspectMappings.read(buf);
            client.execute(() -> {
                com.bluup.hexwright.common.aspects.AspectMappings.setActive(mappings);
                if (client.hasSingleplayerServer()) {
                    return;
                }
                var connection = client.getConnection();
                if (connection != null) {
                    com.bluup.hexwright.common.aspects.RecipeAspects.rebuild(
                        connection.getRecipeManager(), connection.registryAccess());
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(MASTERY_SYNC_S2C, (client, handler, buf, responseSender) -> {
            int bestQuality = buf.readVarInt();
            client.execute(() -> com.bluup.hexwright.client.progression.ClientMastery.setBestQuality(bestQuality));
        });

        ClientPlayNetworking.registerGlobalReceiver(RECIPE_UNLOCK_SYNC_S2C, (client, handler, buf, responseSender) -> {
            int count = buf.readVarInt();
            List<String> unlocked = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                unlocked.add(buf.readUtf());
            }
            client.execute(() -> com.bluup.hexwright.client.progression.ClientRecipeUnlocks.set(unlocked));
        });

        ClientPlayNetworking.registerGlobalReceiver(RESONANCE_NAMES_SYNC_S2C, (client, handler, buf, responseSender) -> {
            boolean full = buf.readBoolean();
            int count = buf.readVarInt();
            Map<String, String> entries = new java.util.LinkedHashMap<>();
            for (int i = 0; i < count; i++) {
                entries.put(buf.readUtf(), buf.readUtf());
            }
            client.execute(() -> com.bluup.hexwright.common.network.ResonanceNameCache.apply(full, entries));
        });

        ClientPlayNetworking.registerGlobalReceiver(SATCHEL_BACKPACK_VIEW_SYNC_S2C, (client, handler, buf, responseSender) -> {
            boolean visible = buf.readBoolean();
            List<ItemStack> entries = List.of();
            if (visible) {
                int size = buf.readVarInt();
                List<ItemStack> decoded = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    decoded.add(buf.readItem());
                }
                entries = decoded;
            }
            List<ItemStack> finalEntries = entries;
            client.execute(() -> SatchelBackpackInventoryOverlay.handleServerSync(visible, finalEntries));
        });

        ClientPlayNetworking.registerGlobalReceiver(STAFF_CORE_SPHERE_VISUAL_S2C, (client, handler, buf, responseSender) -> {
            int entityId = buf.readVarInt();
            boolean active = buf.readBoolean();
            CompoundTag pigmentTag = buf.readNbt();
            double halfExtent = buf.readDouble();
            client.execute(() -> StaffCoreSphereVisualClient.handleSphereVisual(entityId, active, pigmentTag, halfExtent));
        });

        ClientPlayNetworking.registerGlobalReceiver(VEHICLE_RECONCILE_S2C, (client, handler, buf, responseSender) -> {
            int entityId = buf.readVarInt();
            Vec3 pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            Vec3 vel = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            client.execute(() -> {
                if (client.level != null
                    && client.level.getEntity(entityId) instanceof com.bluup.hexwright.server.vehicle.VehicleEntity vehicle) {
                    vehicle.applyServerReconciliation(pos, vel);
                }
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(VEHICLE_DEBUG_S2C, (client, handler, buf, responseSender) -> {
            int entityId = buf.readVarInt();
            com.bluup.hexwright.server.vehicle.VehicleDebugSnapshot snapshot =
                com.bluup.hexwright.server.vehicle.VehicleDebugSnapshot.read(buf);
            client.execute(() ->
                com.bluup.hexwright.client.vehicle.VehicleDebugOverlay.acceptSnapshot(entityId, snapshot));
        });

        ClientPlayNetworking.registerGlobalReceiver(STAFF_TRAVELLER_WARP_S2C, (client, handler, buf, responseSender) -> {
            int entityId = buf.readVarInt();
            Vec3 from = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            Vec3 to = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            client.execute(() -> StaffTravellerWarpVisualClient.handleWarp(entityId, from, to));
        });

        ClientPlayNetworking.registerGlobalReceiver(STAFF_TIP_FLASH_S2C, (client, handler, buf, responseSender) -> {
            int entityId = buf.readVarInt();
            float intensity = buf.readFloat();
            CompoundTag pigmentTag = buf.readNbt();
            client.execute(() -> com.bluup.hexwright.client.staff_assembly.StaffTipFlash
                .handleFlash(entityId, intensity, pigmentTag));
        });

        ClientPlayNetworking.registerGlobalReceiver(PLAYER_ANIMATION_S2C, (client, handler, buf, responseSender) -> {
            int entityId = buf.readVarInt();
            PlayerAnimationLayer layer = buf.readEnum(PlayerAnimationLayer.class);
            String clipName = buf.readBoolean() ? buf.readUtf() : null;
            client.execute(() -> PlayerAnimationClient.handle(entityId, layer, clipName));
        });

        ClientPlayNetworking.registerGlobalReceiver(WARD_TRIGGER_S2C, (client, handler, buf, responseSender) -> {
            BlockPos pos = buf.readBlockPos();
            int width = buf.readVarInt();
            int height = buf.readVarInt();
            client.execute(() -> com.bluup.hexwright.client.wardingbox.WardingBoxHitVisualClient
                .handleTrigger(pos, width, height));
        });

        ClientPlayNetworking.registerGlobalReceiver(PROJECTILE_HIT_S2C, (client, handler, buf, responseSender) -> {
            Vec3 point = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            client.execute(() -> StaffCoreBoltVisualClient.handleHit(point));
        });

        ClientPlayNetworking.registerGlobalReceiver(GROUND_SLAM_S2C, (client, handler, buf, responseSender) -> {
            BlockPos impact = buf.readBlockPos();
            float radius = buf.readFloat();
            client.execute(() -> com.bluup.hexwright.client.weapon.GroundSlamWaveClient
                .handleSlam(impact, radius));
        });

        ClientPlayNetworking.registerGlobalReceiver(HEXICON_BOOK_OPEN_S2C, (client, handler, buf, responseSender) -> {
            UUID playerId = buf.readUUID();
            boolean open = buf.readBoolean();
            client.execute(() -> com.bluup.hexwright.client.staff_assembly.StaffBookOpenTracker.setRemoteOpen(playerId, open));
        });

        ClientPlayNetworking.registerGlobalReceiver(PORTAL_SYNC_S2C, (client, handler, buf, responseSender) -> {
            int mode = buf.readByte();
            switch (mode) {
                case 0 -> {
                    int count = buf.readVarInt();
                    List<com.bluup.hexwright.server.portal.PortalPair> pairs = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        com.bluup.hexwright.server.portal.PortalPair pair =
                            com.bluup.hexwright.server.portal.PortalPair.read(buf);
                        if (pair.isValid()) {
                            pairs.add(pair);
                        }
                    }
                    client.execute(() -> com.bluup.hexwright.client.portal.ClientPortalManager.handleFullSync(pairs));
                }
                case 1 -> {
                    com.bluup.hexwright.server.portal.PortalPair pair = com.bluup.hexwright.server.portal.PortalPair.read(buf);
                    if (pair.isValid()) {
                        client.execute(() -> com.bluup.hexwright.client.portal.ClientPortalManager.handleAdd(pair));
                    }
                }
                case 2 -> {
                    UUID id = buf.readUUID();
                    client.execute(() -> com.bluup.hexwright.client.portal.ClientPortalManager.handleRemove(id));
                }
                default -> {
                }
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(VOID_TEAR_S2C, (client, handler, buf, responseSender) -> {
            com.bluup.hexwright.server.portal.VoidTear tear =
                com.bluup.hexwright.server.portal.VoidTear.read(buf, 0L);
            client.execute(() -> com.bluup.hexwright.client.portal.ClientVoidTears.add(tear));
        });


        ClientPlayNetworking.registerGlobalReceiver(VAULT_REMOTE_LEVEL_INIT_S2C, (client, handler, buf, responseSender) -> {
            ResourceLocation dimension = buf.readResourceLocation();
            ResourceLocation dimensionType = buf.readResourceLocation();
            client.execute(() -> com.bluup.hexwright.client.portal.RemoteLevelManager
                .handleLevelInit(dimension, dimensionType));
        });

        ClientPlayNetworking.registerGlobalReceiver(VAULT_REMOTE_CHUNK_S2C, (client, handler, buf, responseSender) -> {
            ResourceLocation dimension = buf.readResourceLocation();
            var packet = new net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket(buf);
            client.execute(() -> com.bluup.hexwright.client.portal.RemoteLevelManager
                .handleChunk(dimension, packet));
        });

        ClientPlayNetworking.registerGlobalReceiver(VAULT_REMOTE_FORGET_S2C, (client, handler, buf, responseSender) -> {
            ResourceLocation dimension = buf.readResourceLocation();
            int count = buf.readVarInt();
            long[] chunks = new long[count];
            for (int i = 0; i < count; i++) {
                chunks[i] = buf.readLong();
            }
            client.execute(() -> com.bluup.hexwright.client.portal.RemoteLevelManager
                .handleForget(dimension, chunks));
        });

        ClientPlayNetworking.registerGlobalReceiver(VAULT_REMOTE_BLOCK_S2C, (client, handler, buf, responseSender) -> {
            ResourceLocation dimension = buf.readResourceLocation();
            BlockPos pos = buf.readBlockPos();
            int stateId = buf.readVarInt();
            CompoundTag blockEntityTag = buf.readBoolean() ? buf.readNbt() : null;
            client.execute(() -> com.bluup.hexwright.client.portal.RemoteLevelManager
                .handleBlockUpdate(dimension, pos, stateId, blockEntityTag));
        });

        ClientPlayNetworking.registerGlobalReceiver(VAULT_REMOTE_ENTITIES_S2C, (client, handler, buf, responseSender) -> {
            ResourceLocation dimension = buf.readResourceLocation();
            int count = buf.readVarInt();
            List<com.bluup.hexwright.client.portal.RemoteLevelManager.EntitySnapshot> snapshots = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                int entityId = buf.readVarInt();
                boolean isPlayer = buf.readBoolean();
                UUID uuid = buf.readUUID();
                int typeId = isPlayer ? -1 : buf.readVarInt();
                double x = buf.readDouble();
                double y = buf.readDouble();
                double z = buf.readDouble();
                float yaw = buf.readFloat();
                float pitch = buf.readFloat();
                float headYaw = buf.readFloat();
                int worn = buf.readVarInt();
                List<ItemStack> equipment = new ArrayList<>(net.minecraft.world.entity.EquipmentSlot.values().length);
                for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                    equipment.add((worn & (1 << slot.ordinal())) != 0 ? buf.readItem() : ItemStack.EMPTY);
                }
                int dataCount = buf.readVarInt();
                List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> data = new ArrayList<>(dataCount);
                for (int value = 0; value < dataCount; value++) {
                    int dataId = buf.readUnsignedByte();
                    data.add(net.minecraft.network.syncher.SynchedEntityData.DataValue.read(buf, dataId));
                }
                snapshots.add(new com.bluup.hexwright.client.portal.RemoteLevelManager.EntitySnapshot(
                    entityId, isPlayer, uuid, typeId, x, y, z, yaw, pitch, headYaw,
                    List.copyOf(equipment), List.copyOf(data)));
            }
            client.execute(() -> com.bluup.hexwright.client.portal.RemoteLevelManager
                .handleEntities(dimension, snapshots));
        });

        ClientPlayNetworking.registerGlobalReceiver(VAULT_REMOTE_DESTROY_S2C, (client, handler, buf, responseSender) -> {
            ResourceLocation dimension = buf.readResourceLocation();
            int breakerId = buf.readVarInt();
            BlockPos pos = buf.readBlockPos();
            int stage = buf.readInt();
            client.execute(() -> com.bluup.hexwright.client.portal.RemoteLevelManager
                .handleDestroyProgress(dimension, breakerId, pos, stage));
        });
    }


    public static void sendPortalSync(ServerPlayer player, List<com.bluup.hexwright.server.portal.PortalPair> pairs) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeByte(0);
        buf.writeVarInt(pairs.size());
        for (com.bluup.hexwright.server.portal.PortalPair pair : pairs) {
            pair.write(buf);
        }
        ServerPlayNetworking.send(player, PORTAL_SYNC_S2C, buf);
    }

    public static void broadcastPortalAdd(ServerLevel level, com.bluup.hexwright.server.portal.PortalPair pair) {
        for (ServerPlayer player : PlayerLookup.world(level)) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeByte(1);
            pair.write(buf);
            ServerPlayNetworking.send(player, PORTAL_SYNC_S2C, buf);
        }
    }

    public static void broadcastVoidTear(ServerLevel level, com.bluup.hexwright.server.portal.VoidTear tear) {
        for (ServerPlayer player : PlayerLookup.world(level)) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            tear.write(buf);
            ServerPlayNetworking.send(player, VOID_TEAR_S2C, buf);
        }
    }

    public static void broadcastPortalRemove(ServerLevel level, UUID pairId) {
        for (ServerPlayer player : PlayerLookup.world(level)) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeByte(2);
            buf.writeUUID(pairId);
            ServerPlayNetworking.send(player, PORTAL_SYNC_S2C, buf);
        }
    }

    public static void sendPortalUse(UUID pairId, int side) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUUID(pairId);
        buf.writeVarInt(side);
        ClientPlayNetworking.send(PORTAL_USE_C2S, buf);
    }

    public static void sendPortalAttack(UUID pairId, int side, int action) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUUID(pairId);
        buf.writeVarInt(side);
        buf.writeVarInt(action);
        ClientPlayNetworking.send(PORTAL_ATTACK_C2S, buf);
    }

    public static void sendPortalCross(UUID pairId, int side) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUUID(pairId);
        buf.writeVarInt(side);
        ClientPlayNetworking.send(PORTAL_CROSS_C2S, buf);
    }


    public static void sendVaultRetained(ResourceLocation dimension, boolean retained) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeResourceLocation(dimension);
        buf.writeBoolean(retained);
        ClientPlayNetworking.send(VAULT_RETAINED_C2S, buf);
    }

    public static void sendVaultLevelInit(ServerPlayer player, ServerLevel level) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeResourceLocation(level.dimension().location());
        buf.writeResourceLocation(level.dimensionTypeId().location());
        ServerPlayNetworking.send(player, VAULT_REMOTE_LEVEL_INIT_S2C, buf);
    }

    public static void sendVaultChunk(ServerPlayer player, ServerLevel level,
                                      net.minecraft.world.level.chunk.LevelChunk chunk) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeResourceLocation(level.dimension().location());
        new net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket(
            chunk, level.getLightEngine(), null, null).write(buf);
        ServerPlayNetworking.send(player, VAULT_REMOTE_CHUNK_S2C, buf);
    }

    public static void sendVaultChunkForget(ServerPlayer player,
                                            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                                            java.util.List<net.minecraft.world.level.ChunkPos> chunks) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeResourceLocation(dimension.location());
        buf.writeVarInt(chunks.size());
        for (net.minecraft.world.level.ChunkPos chunk : chunks) {
            buf.writeLong(chunk.toLong());
        }
        ServerPlayNetworking.send(player, VAULT_REMOTE_FORGET_S2C, buf);
    }

    public static void sendVaultBlockUpdate(ServerLevel level, java.util.Set<UUID> viewers, BlockPos pos) {
        if (viewers.isEmpty()) {
            return;
        }
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
        net.minecraft.world.level.block.entity.BlockEntity blockEntity = level.getBlockEntity(pos);
        CompoundTag updateTag = blockEntity == null ? null : blockEntity.getUpdateTag();
        for (UUID id : viewers) {
            ServerPlayer viewer = level.getServer().getPlayerList().getPlayer(id);
            if (viewer == null) {
                continue;
            }
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeResourceLocation(level.dimension().location());
            buf.writeBlockPos(pos);
            buf.writeVarInt(net.minecraft.world.level.block.Block.getId(state));
            buf.writeBoolean(updateTag != null && !updateTag.isEmpty());
            if (updateTag != null && !updateTag.isEmpty()) {
                buf.writeNbt(updateTag);
            }
            ServerPlayNetworking.send(viewer, VAULT_REMOTE_BLOCK_S2C, buf);
        }
    }

    public static void sendVaultEntities(net.minecraft.server.MinecraftServer server, ServerLevel level,
                                         java.util.Set<UUID> viewers,
                                         java.util.List<net.minecraft.world.entity.Entity> entities) {
        for (UUID id : viewers) {
            ServerPlayer viewer = server.getPlayerList().getPlayer(id);
            if (viewer == null) {
                continue;
            }
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeResourceLocation(level.dimension().location());
            buf.writeVarInt(entities.size());
            for (net.minecraft.world.entity.Entity entity : entities) {
                buf.writeVarInt(entity.getId());
                boolean isPlayer = entity instanceof ServerPlayer;
                buf.writeBoolean(isPlayer);
                buf.writeUUID(entity.getUUID());
                if (!isPlayer) {
                    buf.writeVarInt(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getId(entity.getType()));
                }
                buf.writeDouble(entity.getX());
                buf.writeDouble(entity.getY());
                buf.writeDouble(entity.getZ());
                buf.writeFloat(entity.getYRot());
                buf.writeFloat(entity.getXRot());
                float headYaw = entity instanceof net.minecraft.world.entity.LivingEntity living
                    ? living.yHeadRot : entity.getYRot();
                buf.writeFloat(headYaw);

                net.minecraft.world.entity.EquipmentSlot[] slots =
                    net.minecraft.world.entity.EquipmentSlot.values();
                int worn = 0;
                if (entity instanceof net.minecraft.world.entity.LivingEntity wearer) {
                    for (net.minecraft.world.entity.EquipmentSlot slot : slots) {
                        if (!wearer.getItemBySlot(slot).isEmpty()) {
                            worn |= 1 << slot.ordinal();
                        }
                    }
                }
                buf.writeVarInt(worn);
                if (entity instanceof net.minecraft.world.entity.LivingEntity wearer) {
                    for (net.minecraft.world.entity.EquipmentSlot slot : slots) {
                        if ((worn & (1 << slot.ordinal())) != 0) {
                            buf.writeItem(wearer.getItemBySlot(slot));
                        }
                    }
                }

                java.util.List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> data =
                    entity.getEntityData().getNonDefaultValues();
                buf.writeVarInt(data == null ? 0 : data.size());
                if (data != null) {
                    for (net.minecraft.network.syncher.SynchedEntityData.DataValue<?> value : data) {
                        value.write(buf);
                    }
                }
            }
            ServerPlayNetworking.send(viewer, VAULT_REMOTE_ENTITIES_S2C, buf);
        }
    }

    public static void sendVaultRemoteDestroyProgress(ServerPlayer viewer, ResourceLocation dimension,
                                                      int breakerId, BlockPos pos, int stage) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeResourceLocation(dimension);
        buf.writeVarInt(breakerId);
        buf.writeBlockPos(pos);
        buf.writeInt(stage);
        ServerPlayNetworking.send(viewer, VAULT_REMOTE_DESTROY_S2C, buf);
    }

    public static void sendWardTrigger(ServerLevel level, BlockPos pos, int width, int height) {
        for (ServerPlayer tracking : PlayerLookup.tracking(level, pos)) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeBlockPos(pos);
            buf.writeVarInt(width);
            buf.writeVarInt(height);
            ServerPlayNetworking.send(tracking, WARD_TRIGGER_S2C, buf);
        }
    }

    public static void sendProjectileHit(ServerLevel level, Vec3 point) {
        BlockPos pos = BlockPos.containing(point);
        for (ServerPlayer tracking : PlayerLookup.tracking(level, pos)) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeDouble(point.x);
            buf.writeDouble(point.y);
            buf.writeDouble(point.z);
            ServerPlayNetworking.send(tracking, PROJECTILE_HIT_S2C, buf);
        }
    }

    public static void sendGroundSlam(ServerLevel level, BlockPos impact, float radius) {
        for (ServerPlayer tracking : PlayerLookup.tracking(level, impact)) {
            FriendlyByteBuf buf = PacketByteBufs.create();
            buf.writeBlockPos(impact);
            buf.writeFloat(radius);
            ServerPlayNetworking.send(tracking, GROUND_SLAM_S2C, buf);
        }
    }

    public static void sendStaffCoreBeam(boolean active, boolean crosshairFree, boolean leftClickBusy) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(active);
        buf.writeBoolean(crosshairFree);
        buf.writeBoolean(leftClickBusy);
        ClientPlayNetworking.send(STAFF_CORE_BEAM_C2S, buf);
    }

    public static void sendStaffCoreSphereVisual(ServerPlayer caster, boolean active, @Nullable FrozenPigment pigment, double halfExtent) {
        CompoundTag pigmentTag = pigment != null ? pigment.serializeToNBT() : null;
        ServerPlayNetworking.send(caster, STAFF_CORE_SPHERE_VISUAL_S2C, writeSphereBuf(caster.getId(), active, pigmentTag, halfExtent));

        for (ServerPlayer tracking : PlayerLookup.tracking(caster)) {
            if (tracking != caster) {
                ServerPlayNetworking.send(tracking, STAFF_CORE_SPHERE_VISUAL_S2C, writeSphereBuf(caster.getId(), active, pigmentTag, halfExtent));
            }
        }
    }

    private static FriendlyByteBuf writeSphereBuf(int entityId, boolean active, @Nullable CompoundTag pigmentTag, double halfExtent) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(entityId);
        buf.writeBoolean(active);
        buf.writeNbt(pigmentTag);
        buf.writeDouble(halfExtent);
        return buf;
    }

    public static void sendStaffTipFlash(ServerPlayer caster, float intensity, @Nullable CompoundTag pigmentTag) {
        ServerPlayNetworking.send(caster, STAFF_TIP_FLASH_S2C, writeTipFlashBuf(caster.getId(), intensity, pigmentTag));

        for (ServerPlayer tracking : PlayerLookup.tracking(caster)) {
            if (tracking != caster) {
                ServerPlayNetworking.send(tracking, STAFF_TIP_FLASH_S2C,
                    writeTipFlashBuf(caster.getId(), intensity, pigmentTag));
            }
        }
    }

    private static FriendlyByteBuf writeTipFlashBuf(int entityId, float intensity, @Nullable CompoundTag pigmentTag) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(entityId);
        buf.writeFloat(intensity);
        buf.writeNbt(pigmentTag);
        return buf;
    }

    public static void sendStaffTravellerWarp(ServerPlayer player, Vec3 from, Vec3 to) {
        ServerPlayNetworking.send(player, STAFF_TRAVELLER_WARP_S2C, writeWarpBuf(player.getId(), from, to));

        for (ServerPlayer tracking : PlayerLookup.tracking(player)) {
            if (tracking != player) {
                ServerPlayNetworking.send(tracking, STAFF_TRAVELLER_WARP_S2C, writeWarpBuf(player.getId(), from, to));
            }
        }
    }

    private static FriendlyByteBuf writeWarpBuf(int entityId, Vec3 from, Vec3 to) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(entityId);
        buf.writeDouble(from.x);
        buf.writeDouble(from.y);
        buf.writeDouble(from.z);
        buf.writeDouble(to.x);
        buf.writeDouble(to.y);
        buf.writeDouble(to.z);
        return buf;
    }

    public static void sendPlayerAnimation(ServerPlayer player, PlayerAnimationLayer layer, @Nullable String clipName) {
        ServerPlayNetworking.send(player, PLAYER_ANIMATION_S2C, writeAnimationBuf(player.getId(), layer, clipName));

        for (ServerPlayer tracking : PlayerLookup.tracking(player)) {
            if (tracking != player) {
                ServerPlayNetworking.send(tracking, PLAYER_ANIMATION_S2C, writeAnimationBuf(player.getId(), layer, clipName));
            }
        }
    }

    private static FriendlyByteBuf writeAnimationBuf(int entityId, PlayerAnimationLayer layer, @Nullable String clipName) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeVarInt(entityId);
        buf.writeEnum(layer);
        buf.writeBoolean(clipName != null);
        if (clipName != null) {
            buf.writeUtf(clipName);
        }
        return buf;
    }

    private static void sendSatchelBackpackView(ServerPlayer player, boolean visible, List<ItemStack> entries) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(visible);
        if (visible) {
            buf.writeVarInt(entries.size());
            for (ItemStack entry : entries) {
                buf.writeItem(entry);
            }
        }
        ServerPlayNetworking.send(player, SATCHEL_BACKPACK_VIEW_SYNC_S2C, buf);
    }

    private static void sendSatchelBackpackView(ServerPlayer player) {
        List<ItemStack> entries = computeSatchelBackpackView(player);
        if (entries == null) {
            SATCHEL_BACKPACK_VIEW_CACHE.remove(player.getUUID());
            sendSatchelBackpackView(player, false, List.of());
            return;
        }
        SATCHEL_BACKPACK_VIEW_CACHE.put(player.getUUID(), entries);
        sendSatchelBackpackView(player, true, entries);
    }

    private static @Nullable List<ItemStack> computeSatchelBackpackView(ServerPlayer player) {
        if (!WornAccessories.hasProvider() || player.getServer() == null) {
            return null;
        }
        ItemStack satchel = firstWornSatchel(player);
        if (satchel == null) {
            return null;
        }
        String key = SatchelItem.storeKey(satchel);
        if (key == null) {
            return List.of();
        }
        ItemStack openFocus = SatchelUIFactory.effectiveHookFocus(player, satchel, key, SatchelItem.Hook.OPEN);
        Supplier<ItemStack> heldSlot = () -> {
            ItemStack current = firstWornSatchel(player);
            return current == null ? ItemStack.EMPTY : SatchelItem.getHeld(current);
        };
        return ReliquaryWindow.runOpen(player, InteractionHand.MAIN_HAND, openFocus, heldSlot);
    }

    private static void handleSatchelBackpackSlotClick(ServerPlayer player, int slotIndex, int targetMenuSlot,
                                                        boolean clientOwnedCursor, ItemStack clientCarried) {
        if (slotIndex < 0 || !WornAccessories.hasProvider() || player.getServer() == null) {
            return;
        }
        ItemStack satchel = firstWornSatchel(player);
        if (satchel == null) {
            return;
        }
        String key = SatchelItem.storeKey(satchel);
        if (key == null) {
            return;
        }
        if (slotIndex >= ReliquaryStore.SLOTS) {
            return;
        }
        List<ItemStack> lastView = SATCHEL_BACKPACK_VIEW_CACHE.getOrDefault(player.getUUID(), List.of());
        ItemStack shown = slotIndex < lastView.size() ? lastView.get(slotIndex) : ItemStack.EMPTY;

        InteractionHand hand = InteractionHand.MAIN_HAND;
        Supplier<ItemStack> heldSlot = () -> {
            ItemStack current = firstWornSatchel(player);
            return current == null ? ItemStack.EMPTY : SatchelItem.getHeld(current);
        };
        net.minecraft.world.inventory.AbstractContainerMenu menu = player.containerMenu;
        boolean clientCursor = clientOwnedCursor && player.isCreative();
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty() && clientCursor) {
            carried = clientCarried;
        }
        boolean changed = false;

        if (carried.isEmpty()) {
            ItemStack clicked = shown;
            if (!clicked.isEmpty()) {
                ItemStack withdrawFocus = SatchelUIFactory.effectiveHookFocus(player, satchel, key, SatchelItem.Hook.WITHDRAW);
                ItemStack gathered = ReliquaryWindow.fireWithdraw(player, hand, withdrawFocus, heldSlot, clicked, slotIndex);
                if (!gathered.isEmpty()) {
                    net.minecraft.world.inventory.Slot target = targetMenuSlot >= 0 && targetMenuSlot < menu.slots.size()
                        ? menu.getSlot(targetMenuSlot)
                        : null;
                    if (target != null && target.container == player.getInventory()) {
                        ItemStack leftover = mergeIntoSlot(target, gathered);
                        ReliquaryWindow.grantWithdrawal(player, leftover, true);
                    } else if (clientCursor) {
                        ReliquaryWindow.grantWithdrawal(player, gathered, true);
                    } else {
                        menu.setCarried(gathered);
                    }
                    changed = true;
                }
            }
        } else {
            ItemStack occupant = shown;
            ItemStack cursorAfter = ItemStack.EMPTY;
            boolean canDeposit = true;
            if (!occupant.isEmpty()) {
                ItemStack withdrawFocus = SatchelUIFactory.effectiveHookFocus(player, satchel, key, SatchelItem.Hook.WITHDRAW);
                ItemStack evicted = ReliquaryWindow.fireWithdraw(player, hand, withdrawFocus, heldSlot, occupant, slotIndex);
                if (evicted.isEmpty()) {
                    canDeposit = false;
                } else {
                    cursorAfter = evicted;
                }
            }
            if (canDeposit) {
                ItemStack depositFocus = SatchelUIFactory.effectiveHookFocus(player, satchel, key, SatchelItem.Hook.DEPOSIT);
                ItemEntity entity = new ItemEntity(player.level(),
                    player.getX(), player.getY() + 0.5, player.getZ(), carried.copy());
                entity.setPickUpDelay(10);
                player.level().addFreshEntity(entity);
                ReliquaryWindow.fireDeposit(player, hand, depositFocus, heldSlot, entity, slotIndex);
                if (clientCursor) {
                    ReliquaryWindow.grantWithdrawal(player, cursorAfter, true);
                } else {
                    menu.setCarried(cursorAfter);
                }
                changed = true;
            }
        }

        if (!changed) {
            return;
        }
        menu.sendAllDataToRemote();
        sendSatchelBackpackView(player);
    }

    private static ItemStack mergeIntoSlot(net.minecraft.world.inventory.Slot slot, ItemStack stack) {
        if (stack.isEmpty() || !slot.mayPlace(stack)) {
            return stack;
        }
        ItemStack existing = slot.getItem();
        if (existing.isEmpty()) {
            ItemStack moved = stack.split(Math.min(slot.getMaxStackSize(stack), stack.getMaxStackSize()));
            slot.set(moved);
            return stack;
        }
        if (!ItemStack.isSameItemSameTags(existing, stack)) {
            return stack;
        }
        int room = Math.min(slot.getMaxStackSize(existing), existing.getMaxStackSize()) - existing.getCount();
        int moved = Math.min(room, stack.getCount());
        if (moved > 0) {
            existing.grow(moved);
            stack.shrink(moved);
            slot.setChanged();
        }
        return stack;
    }

    private static @Nullable ItemStack firstWornSatchel(ServerPlayer player) {
        for (ItemStack stack : WornAccessories.allWorn(player)) {
            if (stack.getItem() instanceof SatchelItem) {
                return stack;
            }
        }
        return null;
    }
}
