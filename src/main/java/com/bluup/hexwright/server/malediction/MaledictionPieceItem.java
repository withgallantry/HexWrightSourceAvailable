package com.bluup.hexwright.server.malediction;

import com.bluup.hexwright.common.malediction.MaledictionFinish;
import com.bluup.hexwright.common.malediction.MaledictionForm;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MaledictionPieceItem extends Item {

    private final @Nullable MaledictionForm form;
    private final @Nullable MaledictionFinish finish;

    private MaledictionPieceItem(@Nullable MaledictionForm form, @Nullable MaledictionFinish finish, Properties properties) {
        super(properties);
        this.form = form;
        this.finish = finish;
    }

    public static MaledictionPieceItem of(MaledictionForm form, Properties properties) {
        return new MaledictionPieceItem(form, null, properties.rarity(form.rarity()));
    }

    public static MaledictionPieceItem of(MaledictionFinish finish, Properties properties) {
        return new MaledictionPieceItem(null, finish, properties.rarity(finish.rarity()));
    }

    public @Nullable MaledictionForm form() {
        return form;
    }

    public @Nullable MaledictionFinish finish() {
        return finish;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        MaledictionState state = MaledictionState.get(serverPlayer.server);
        boolean learned = form != null
            ? state.learn(player.getUUID(), form)
            : state.learn(player.getUUID(), finish);
        Component what = Component.translatable(form != null ? form.nameKey() : finish.nameKey());
        if (!learned) {
            player.displayClientMessage(Component.translatable(form != null
                ? "message.hexwright.malediction.form_known" : "message.hexwright.malediction.finish_known", what)
                .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE,
            SoundSource.PLAYERS, 0.9F, 0.8F);
        player.displayClientMessage(Component.translatable(form != null
            ? "message.hexwright.malediction.form_learned" : "message.hexwright.malediction.finish_learned", what)
            .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(form != null
            ? "tooltip.hexwright.malediction.study_form" : "tooltip.hexwright.malediction.study_finish")
            .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
    }
}
