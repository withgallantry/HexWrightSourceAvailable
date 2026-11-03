package com.bluup.hexwright.server.item;

import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.client.journal.ClientInvestigations;
import com.bluup.hexwright.server.journal.Investigation;
import com.bluup.hexwright.server.journal.Investigations;
import com.bluup.hexwright.server.journal.JournalUIFactory;
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
import java.util.Set;

public class JournalItem extends Item {

    public JournalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (JournalUIFactory.INSTANCE.openForHand(serverPlayer, hand)) {
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS,
                    0.9F, 0.9F + level.getRandom().nextFloat() * 0.2F);
            } else {
                Hexwright.LOGGER.error("Failed to open the Field Journal for hand {}", hand);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);

        Set<String> completed = ClientInvestigations.completed();
        List<Investigation> visible = Investigations.visible(completed);
        if (!visible.isEmpty()) {
            long done = visible.stream().filter(investigation -> completed.contains(investigation.id())).count();
            tooltip.add(Component.translatable("tooltip.hexwright.journal.investigations", done, visible.size())
                .withStyle(ChatFormatting.GRAY));
        }

        tooltip.add(Component.translatable("tooltip.hexwright.journal.hint").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
