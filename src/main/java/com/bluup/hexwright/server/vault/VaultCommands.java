package com.bluup.hexwright.server.vault;

import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import com.bluup.hexwright.server.portal.PortalWindow;
import com.bluup.hexwright.server.worldgen.decadentvault.DecadentVaultRegistry;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

public final class VaultCommands {

    private VaultCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("vault")
            .executes(context -> {
                for (String line : HELP) {
                    context.getSource().sendSuccess(() -> Component.literal(line), false);
                }
                return Command.SINGLE_SUCCESS;
            })
            .then(Commands.literal("create")
                .requires(source -> source.hasPermission(2))
                .executes(context -> create(context, PocketCasterData.Quality.FINE, false, null))
                .then(Commands.argument("grade", StringArgumentType.word())
                    .suggests(GRADES)
                    .executes(context -> create(context,
                        gradeWord(context), artifactWord(context), null))
                    .then(Commands.argument("build", StringArgumentType.word())
                        .suggests(BUILDS)
                        .executes(context -> create(context,
                            gradeWord(context), artifactWord(context),
                            StringArgumentType.getString(context, "build"))))))
            .then(Commands.literal("blank")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("grade", StringArgumentType.word())
                    .suggests(GRADES)
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        give(player, artifactWord(context)
                            ? VaultKeyItem.artifact()
                            : VaultKeyItem.blank(gradeWord(context)));
                        return Command.SINGLE_SUCCESS;
                    })))
            .then(Commands.literal("list")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    List<VaultRecord> owned = VaultManager.getVaults(player.server, player.getUUID());
                    context.getSource().sendSuccess(() -> Component.translatable(
                        "hexwright.vault.list_header", owned.size()), false);
                    for (VaultRecord record : owned) {
                        VaultPortalSession session = VaultManager.sessionByVault(record.id());
                        String state = session == null ? "closed" : session.state().toString();
                        context.getSource().sendSuccess(() -> Component.literal(
                            "  Vault " + record.id() + " - "
                                + (record.artifact() ? "ARTIFACT" : record.grade()) + " "
                                + VaultRooms.layoutOf(record)
                                + (record.build().isEmpty() ? "" : " (" + record.build() + ")")
                                + " - " + state), false);
                    }
                    return owned.size();
                }))
            .then(Commands.literal("open")
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    Integer id = impliedVault(player);
                    if (id == null) {
                        List<VaultRecord> owned = VaultManager.getVaults(player.server, player.getUUID());
                        context.getSource().sendFailure(owned.isEmpty()
                            ? Component.translatable("hexwright.vault.none_owned")
                            : Component.translatable("hexwright.vault.name_one", ids(owned)));
                        return 0;
                    }
                    return open(context, player, id);
                })
                .then(Commands.argument("id", IntegerArgumentType.integer(0))
                    .executes(context -> open(context, context.getSource().getPlayerOrException(),
                        IntegerArgumentType.getInteger(context, "id")))))
            .then(Commands.literal("close")
                .executes(context -> {
                    VaultManager.requestCloseVault(context.getSource().getPlayerOrException());
                    return Command.SINGLE_SUCCESS;
                }))
            .then(Commands.literal("key")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("id", IntegerArgumentType.integer(0))
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        int id = IntegerArgumentType.getInteger(context, "id");
                        VaultRecord record = VaultManager.getVault(player.server, id);
                        if (record == null) {
                            context.getSource().sendFailure(
                                Component.translatable("hexwright.vault.unknown", id));
                            return 0;
                        }
                        give(player, VaultKeyItem.forVault(id, record.grade(), record.artifact()));
                        return Command.SINGLE_SUCCESS;
                    })))
            .then(Commands.literal("tp")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("id", IntegerArgumentType.integer(0))
                    .executes(context -> {
                        ServerPlayer player = context.getSource().getPlayerOrException();
                        VaultRecord record = VaultManager.getVault(player.server,
                            IntegerArgumentType.getInteger(context, "id"));
                        ServerLevel vaultLevel = VaultDimension.level(player.server);
                        if (record == null || vaultLevel == null) {
                            context.getSource().sendFailure(
                                Component.translatable("hexwright.vault.unknown",
                                    IntegerArgumentType.getInteger(context, "id")));
                            return 0;
                        }
                        for (ChunkPos chunk : VaultRooms.roomChunks(record)) {
                            vaultLevel.getChunk(chunk.x, chunk.z);
                        }
                        Vec3 arrival = VaultRooms.interiorArrival(record);
                        player.teleportTo(vaultLevel, arrival.x, arrival.y, arrival.z,
                            player.getYRot(), player.getXRot());
                        VaultContainment.grant(player, record);
                        return Command.SINGLE_SUCCESS;
                    })))
            .then(Commands.literal("rebuild")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    Integer id = impliedVault(player);
                    if (id == null) {
                        context.getSource().sendFailure(
                            Component.translatable("hexwright.vault.none_owned"));
                        return 0;
                    }
                    return rebuild(context, id);
                })
                .then(Commands.argument("id", IntegerArgumentType.integer(0))
                    .executes(context -> rebuild(context, IntegerArgumentType.getInteger(context, "id")))))
            .then(Commands.literal("decadent")
                .requires(source -> source.hasPermission(2))
                .executes(VaultCommands::rebuildDecadent)
                .then(Commands.literal("create")
                    .executes(VaultCommands::createDecadent)))
            .then(Commands.literal("locate")
                .requires(source -> source.hasPermission(2))
                .executes(VaultCommands::locateDecadent))
            .then(Commands.literal("escape")
                .executes(context -> {
                    VaultManager.escape(context.getSource().getPlayerOrException());
                    return Command.SINGLE_SUCCESS;
                }))
            .then(Commands.literal("debug")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    for (String line : VaultManager.debugSummary(context.getSource().getServer())) {
                        context.getSource().sendSuccess(() -> Component.literal(line), false);
                    }
                    return Command.SINGLE_SUCCESS;
                })));
    }

    private static final SuggestionProvider<CommandSourceStack> BUILDS = (context, builder) -> {
        for (String id : VaultBuilds.ids()) {
            builder.suggest(id);
        }
        return builder.buildFuture();
    };

    private static final String ARTIFACT_WORD = "artifact";

    private static final SuggestionProvider<CommandSourceStack> GRADES = (context, builder) -> {
        for (PocketCasterData.Quality grade : PocketCasterData.Quality.values()) {
            builder.suggest(grade.name().toLowerCase(Locale.ROOT));
        }
        builder.suggest(ARTIFACT_WORD);
        return builder.buildFuture();
    };

    private static boolean artifactWord(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, "grade").equalsIgnoreCase(ARTIFACT_WORD);
    }

    private static PocketCasterData.Quality gradeWord(CommandContext<CommandSourceStack> context) {
        return artifactWord(context)
            ? PocketCasterData.Quality.MASTERWORK
            : PocketCasterData.Quality.byName(StringArgumentType.getString(context, "grade"));
    }

    private static int create(CommandContext<CommandSourceStack> context,
                              PocketCasterData.Quality grade, boolean artifact,
                              @Nullable String build)
        throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        VaultRecord record = VaultManager.createVault(player, grade, artifact, build);
        ItemStack key = VaultKeyItem.forVault(record.id(), record.grade(), record.artifact());
        give(player, key);
        context.getSource().sendSuccess(() -> Component.translatable("hexwright.vault.created",
            record.id(), VaultKeyItem.tierLabel(key)), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int rebuild(CommandContext<CommandSourceStack> context,
                               int vaultId) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        VaultRecord record = VaultManager.getVault(player.server, vaultId);
        if (record == null) {
            context.getSource().sendFailure(Component.translatable("hexwright.vault.unknown", vaultId));
            return 0;
        }
        if (!VaultManager.rebuildVault(player.server, record)) {
            context.getSource().sendFailure(Component.translatable("hexwright.vault.no_dimension"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("hexwright.vault.rebuilt", vaultId), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int rebuildDecadent(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockPos entrance = DecadentVaultRegistry.get(player.server).rebuild(level, player.position());
        if (entrance == null) {
            context.getSource().sendFailure(Component.translatable("hexwright.vault.no_decadent"));
            return 0;
        }
        player.teleportTo(level, entrance.getX() + 0.5, entrance.getY(), entrance.getZ() + 0.5,
            player.getYRot(), player.getXRot());
        context.getSource().sendSuccess(
            () -> Component.translatable("hexwright.vault.decadent_rebuilt"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int createDecadent(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        BlockPos entrance = DecadentVaultRegistry.get(player.server).createFor(level, player.blockPosition());
        player.teleportTo(level, entrance.getX() + 0.5, entrance.getY(), entrance.getZ() + 0.5,
            player.getYRot(), player.getXRot());
        context.getSource().sendSuccess(
            () -> Component.translatable("hexwright.vault.decadent_created"), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int locateDecadent(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        DecadentVaultRegistry.Located nearest = DecadentVaultRegistry.get(player.server)
            .nearest(player.level().dimension(), player.blockPosition());
        if (nearest == null) {
            context.getSource().sendFailure(Component.translatable("hexwright.vault.no_decadent_found"));
            return 0;
        }
        BlockPos target = nearest.portal() != null ? nearest.portal() : nearest.origin();
        long distance = Math.round(Math.sqrt(player.blockPosition().distSqr(target)));
        context.getSource().sendSuccess(() -> Component.translatable("hexwright.vault.decadent_located",
            target.getX(), target.getY(), target.getZ(), distance), false);
        return Command.SINGLE_SUCCESS;
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static int open(CommandContext<CommandSourceStack> context,
                            ServerPlayer player, int vaultId) {
        PortalWindow window = VaultPortalPlacement.windowInFrontOf(player);
        if (window == null) {
            context.getSource().sendFailure(Component.translatable("hexwright.vault.no_room"));
            return 0;
        }
        return VaultManager.openVault(player, vaultId, window) ? Command.SINGLE_SUCCESS : 0;
    }

    private static @Nullable Integer impliedVault(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            Integer held = VaultKeyItem.boundVault(player.getItemInHand(hand));
            if (held != null) {
                return held;
            }
        }
        List<VaultRecord> owned = VaultManager.getVaults(player.server, player.getUUID());
        return owned.size() == 1 ? owned.get(0).id() : null;
    }

    private static String ids(List<VaultRecord> records) {
        StringBuilder builder = new StringBuilder();
        for (VaultRecord record : records) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(record.id());
        }
        return builder.toString();
    }

    private static final String[] HELP = {
        "(a blank Vault Key cuts itself to a new vault on first use;",
        " craft a cut key with a blank one to copy it - a key is the access)",
        "/vault create [grade] [build] - create a vault at a grade and receive its key (op)",
        "/vault blank <grade>  - a blank key struck at a grade (op)",
        "/vault list         - vaults you created, their grade and their state",
        "/vault open [id]    - open a vault in front of you (defaults to the held key's)",
        "/vault close        - close your active vault portal",
        "/vault key <id>     - a key bound to a vault (op)",
        "/vault tp <id>      - teleport into a vault room (op)",
        "/vault rebuild [id] - regenerate a vault's template over what is there (op)",
        "/vault decadent    - rebuild the Decadent Vault you are standing in (op)",
        "/vault decadent create - guarantee a fresh Decadent Vault under you, for testing (op)",
        "/vault locate       - nearest Decadent Vault in your dimension (op)",
        "/vault escape       - teleport out of the vault dimension",
        "/vault debug        - active session diagnostics (op)",
    };
}
