package com.bluup.hexwright.server.progression;

import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.nbt.CompoundTag;

public final class PlayerMastery {

    private int bestQuality = -1;

    private final int[] craftsByQuality = new int[PocketCasterData.Quality.values().length];

    public boolean record(PocketCasterData.Quality quality) {
        craftsByQuality[quality.ordinal()]++;
        if (quality.ordinal() > bestQuality) {
            bestQuality = quality.ordinal();
            return true;
        }
        return false;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Best", bestQuality);
        tag.putIntArray("Crafts", craftsByQuality.clone());
        return tag;
    }

    public static PlayerMastery load(CompoundTag tag) {
        PlayerMastery mastery = new PlayerMastery();
        mastery.bestQuality = tag.getInt("Best");
        if (!tag.contains("Best")) {
            mastery.bestQuality = -1;
        }
        int[] crafts = tag.getIntArray("Crafts");
        System.arraycopy(crafts, 0, mastery.craftsByQuality, 0,
            Math.min(crafts.length, mastery.craftsByQuality.length));
        return mastery;
    }
}
