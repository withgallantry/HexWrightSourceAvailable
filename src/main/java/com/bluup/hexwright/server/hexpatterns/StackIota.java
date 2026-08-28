package com.bluup.hexwright.server.hexpatterns;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.bluup.hexwright.Hexwright;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StackIota extends Iota {

    @Nullable
    private final String source;

    public StackIota(ItemStack stack, @Nullable String source) {
        super(TYPE, stack);
        this.source = source;
    }

    public ItemStack getStack() {
        return (ItemStack) this.payload;
    }

    public @Nullable String getSource() {
        return source;
    }

    @Override
    public boolean isTruthy() {
        return !getStack().isEmpty();
    }

    @Override
    public boolean toleratesOther(Iota that) {
        return typesMatch(this, that)
            && that instanceof StackIota other
            && ItemStack.matches(this.getStack(), other.getStack());
    }

    @Override
    public @NotNull Tag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.put("Stack", getStack().copyWithCount(1).save(new CompoundTag()));
        tag.putInt("Count", getStack().getCount());
        if (source != null) {
            tag.putString("Source", source);
        }
        return tag;
    }

    public static final IotaType<StackIota> TYPE = new IotaType<>() {
        @Override
        public StackIota deserialize(Tag tag, ServerLevel world) throws IllegalArgumentException {
            if (!(tag instanceof CompoundTag compound)) {
                throw new IllegalArgumentException("Expected a compound tag for a stack iota");
            }
            ItemStack stack = ItemStack.of(compound.getCompound("Stack"));
            if (compound.contains("Count")) {
                stack.setCount(Math.max(1, compound.getInt("Count")));
            }
            String source = compound.contains("Source") ? compound.getString("Source") : null;
            return new StackIota(stack, source);
        }

        @Override
        public Component display(Tag tag) {
            if (tag instanceof CompoundTag compound) {
                ItemStack stack = ItemStack.of(compound.getCompound("Stack"));
                int count = compound.contains("Count") ? compound.getInt("Count") : stack.getCount();
                return Component.translatable("hexwright.iota.stack.display",
                    com.bluup.hexwright.server.reliquary.BulkCount.format(count), stack.getHoverName());
            }
            return Component.translatable("hexwright.iota.stack");
        }

        @Override
        public int color() {
            return 0xFF_B77FDB;
        }
    };

    public static void register() {
        Registry.register(IXplatAbstractions.INSTANCE.getIotaTypeRegistry(), Hexwright.id("stack"), TYPE);
    }
}
