package com.bluup.hexwright.server.weapon;

import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

public final class MeleeSwingState {

    private static final int NEVER_STRUCK = Integer.MIN_VALUE;

    private int lastStrikeTick = NEVER_STRUCK;

    private int lastAimedAttackTick = NEVER_STRUCK;

    private int lastInteractTick = NEVER_STRUCK;

    private boolean swingHeld;

    private int comboStep;

    @Nullable
    private Item comboWeapon;

    public boolean isFreshAttack(int now, int attackIntervalTicks) {
        return this.lastStrikeTick == NEVER_STRUCK
            || now - this.lastStrikeTick >= attackIntervalTicks;
    }

    public int peekStep(int now, Item weapon, int comboLength, int comboResetTicks) {
        if (comboLength <= 1
            || weapon != this.comboWeapon
            || this.lastStrikeTick == NEVER_STRUCK
            || now - this.lastStrikeTick > comboResetTicks) {
            return 0;
        }
        return (this.comboStep + 1) % comboLength;
    }

    public void noteAimedAttack(int now) {
        this.lastAimedAttackTick = now;
    }

    public boolean aimedAtSomething(int now) {
        return this.lastAimedAttackTick == now;
    }

    public void noteInteract(int now) {
        this.lastInteractTick = now;
    }

    public boolean interactedThisTick(int now) {
        return this.lastInteractTick == now;
    }

    public void holdSwing(int now, int attackIntervalTicks) {
        if (this.lastStrikeTick == NEVER_STRUCK
            || (now - this.lastStrikeTick) * 2 >= attackIntervalTicks) {
            this.swingHeld = true;
        }
    }

    public boolean hasHeldSwing() {
        return this.swingHeld;
    }

    public void dropHeldSwing() {
        this.swingHeld = false;
    }

    public void commit(int now, Item weapon, int step) {
        this.lastStrikeTick = now;
        this.comboWeapon = weapon;
        this.comboStep = step;
    }
}
