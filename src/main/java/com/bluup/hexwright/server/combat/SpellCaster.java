package com.bluup.hexwright.server.combat;

import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class SpellCaster {

    private record Frame(@Nullable LivingEntity caster, long tick) {
    }

    private static final ThreadLocal<List<Frame>> STACK = ThreadLocal.withInitial(ArrayList::new);

    private SpellCaster() {
    }

    public static void enter(@Nullable LivingEntity caster, long gameTime) {
        List<Frame> stack = STACK.get();
        if (!stack.isEmpty() && stack.get(stack.size() - 1).tick() != gameTime) {
            stack.clear();
        }
        stack.add(new Frame(caster, gameTime));
    }

    public static void exit() {
        List<Frame> stack = STACK.get();
        if (!stack.isEmpty()) {
            stack.remove(stack.size() - 1);
        }
    }

    public static @Nullable LivingEntity current(long gameTime) {
        List<Frame> stack = STACK.get();
        if (stack.isEmpty()) {
            return null;
        }
        Frame top = stack.get(stack.size() - 1);
        return top.tick() == gameTime ? top.caster() : null;
    }
}
