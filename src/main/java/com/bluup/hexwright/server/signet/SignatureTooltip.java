package com.bluup.hexwright.server.signet;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

public record SignatureTooltip(byte[] bits) implements TooltipComponent {
}
