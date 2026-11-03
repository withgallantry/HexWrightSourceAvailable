package com.bluup.hexwright.server.staff_assembly;

import com.bluup.hexwright.common.staff_assembly.calc.CoreRegistry;
import com.bluup.hexwright.server.hexicon.HexiconData;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import com.bluup.hexwright.server.progression.MakersMark;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class StaffCoreItem extends Item {

    public enum Kind {
        AMETHYST("amethyst_core"),
        AREA("quartz_core"),
        SCRIBE("scribe_core"),
        TRAVELLER("traveller_core"),
        BEAM("echo_core");

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    private final Kind kind;

    public StaffCoreItem(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public Component getName(ItemStack stack) {
        Component quality = Component.translatable(StaffCoreData.getQuality(stack).translationKey());
        return Component.translatable("item.hexwright." + kind.id() + ".named", quality);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        MakersMark.appendTooltip(stack, tooltip);

        CoreRegistry.lookup(this).ifPresent(data ->
            data.description().forEach(line -> tooltip.add(line.copy().withStyle(ChatFormatting.GRAY))));

        appendEffectTooltip(stack, kind, tooltip);
    }

    public static void appendEffectTooltip(ItemStack coreStack, Kind kind, List<Component> tooltip) {
        PocketCasterData.Quality quality = StaffCoreData.getQuality(coreStack);
        int percent = (int) Math.round(StaffCoreData.gradeFraction(quality) * 100.0);
        switch (kind) {
            case AREA -> {
                tooltip.add(Component.translatable("tooltip.hexwright.staff_core.area_reach", percent)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
                tooltip.add(Component.translatable("tooltip.hexwright.staff_core.area_rate",
                        trimWhole(StaffCoreData.areaCastIntervalTicks(quality) / 20.0))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            case TRAVELLER -> tooltip.add((percent >= 100
                    ? Component.translatable("tooltip.hexwright.staff_core.traveller_free")
                    : Component.translatable("tooltip.hexwright.staff_core.traveller_discount", percent))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
            case SCRIBE -> tooltip.add(Component.translatable(
                    "tooltip.hexwright.staff_core.scribe_chapters",
                    StaffCoreData.scribeChapters(quality),
                    HexiconData.BARS)
                .withStyle(ChatFormatting.LIGHT_PURPLE));
            case BEAM -> tooltip.add(Component.translatable(
                    "tooltip.hexwright.staff_core.echo_ambit",
                    trimWhole(StaffCoreData.echoImpactAmbit(quality)))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
            default -> {
            }
        }
    }

    private static String trimWhole(double value) {
        return value == Math.rint(value) ? String.valueOf((int) value) : String.valueOf(value);
    }
}
