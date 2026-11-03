package com.bluup.hexwright.server.weapon;

import com.bluup.hexwright.server.soulforge.SoulforgedItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public interface SoulforgedWeapon extends SoulforgedItem {

    Component normalStrike();

    Component heavyStrike();

    default void appendHeld(ItemStack stack, List<Component> tooltip) {
    }

    List<Component> costLines();
}
