package com.bluup.hexwright.client.hexweave;

import com.lowdragmc.lowdraglib.LDLib;
import com.lowdragmc.lowdraglib.gui.editor.data.Resources;
import com.lowdragmc.lowdraglib.gui.editor.data.UIProject;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.IOException;

@Environment(EnvType.CLIENT)
final class LoomDevTools {

    private LoomDevTools() {
    }

    static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("hexwright")
                .then(ClientCommandManager.literal("loom")
                    .requires(source -> source.getPlayer().isCreative())
                    .then(ClientCommandManager.literal("preview").executes(LoomDevTools::preview))
                    .then(ClientCommandManager.literal("export").executes(LoomDevTools::export)))));
    }

    private static int preview(CommandContext<FabricClientCommandSource> ctx) {
        Minecraft client = ctx.getSource().getClient();
        client.execute(() -> LoomWindow.open(LoomEditor.preview(false)));
        return Command.SINGLE_SUCCESS;
    }

    private static int export(CommandContext<FabricClientCommandSource> ctx) {
        File file = new File(LDLib.getLDLibDir(), "assets/ldlib/projects/ui/" + LoomWindow.TEMPLATE + ".ui");
        if (file.exists()) {
            ctx.getSource().sendError(Component.translatable("command.hexwright.loom.export_exists", file.getPath()));
            return 0;
        }
        try {
            File parent = file.getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("could not create " + parent);
            }
            NbtIo.write(new UIProject(Resources.defaultResource(), LoomLayout.build()).serializeNBT(), file);
        } catch (IOException e) {
            ctx.getSource().sendError(Component.translatable("command.hexwright.loom.export_failed", file.getPath(),
                String.valueOf(e.getMessage())));
            return 0;
        }
        ctx.getSource().sendFeedback(Component.translatable("command.hexwright.loom.exported", file.getPath()));
        return Command.SINGLE_SUCCESS;
    }
}
