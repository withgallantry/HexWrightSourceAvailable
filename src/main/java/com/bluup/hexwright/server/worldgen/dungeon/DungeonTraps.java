package com.bluup.hexwright.server.worldgen.dungeon;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.api.misc.MediaConstants;
import com.bluup.hexwright.Hexwright;
import com.bluup.hexwright.server.block.HexwrightBlocks;
import com.bluup.hexwright.server.block.WardingBoxBlockEntity;
import com.bluup.hexwright.server.pocketcaster.PocketCasterData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

public final class DungeonTraps {

    private static final PocketCasterData.Quality GRADE = PocketCasterData.Quality.FINE;

    private static final long CHARGE = 500L * MediaConstants.DUST_UNIT;

    private static final int[] WIDTHS = {5, 7, 9};

    private static final int MIN_HEIGHT = 3;
    private static final int MAX_HEIGHT = 6;

    public enum Trap {
        BLAST(45, new String[] {
            "NORTH_EAST dd",
            "SOUTH_EAST aqaawww",
            "EAST aawaawaa",
        }),

        FIREBLAST(30, new String[] {
            "NORTH_EAST dd",
            "SOUTH_EAST aqaaqd",
            "EAST ddwddwdd",
        }),

        LAVA(25, new String[] {
            "NORTH_EAST dd",
            "EAST eaqawqadaqd",
        });

        private final int weight;
        private final String[] hex;

        Trap(int weight, String[] hex) {
            this.weight = weight;
            this.hex = hex;
        }

        CompoundTag spell() {
            List<Iota> patterns = new ArrayList<>(this.hex.length);
            for (String line : this.hex) {
                int space = line.indexOf(' ');
                patterns.add(new PatternIota(HexPattern.fromAnglesUnchecked(
                    line.substring(space + 1), HexDir.fromString(line.substring(0, space)))));
            }
            return IotaType.serialize(new ListIota(patterns));
        }

        int size() {
            return this.hex.length;
        }
    }

    public record Spec(Trap trap, int width, int height) {

        void save(CompoundTag tag) {
            tag.putString("Trap", this.trap.name());
            tag.putInt("TrapWidth", this.width);
            tag.putInt("TrapHeight", this.height);
        }

        static Spec load(CompoundTag tag) {
            String name = tag.getString("Trap");
            if (name.isEmpty()) {
                return null;
            }
            for (Trap trap : Trap.values()) {
                if (trap.name().equals(name)) {
                    return new Spec(trap, tag.getInt("TrapWidth"), tag.getInt("TrapHeight"));
                }
            }
            Hexwright.LOGGER.warn("Dungeon piece names unknown trap '{}'; leaving the floor bare", name);
            return null;
        }
    }

    private DungeonTraps() {
    }

    static Spec roll(RandomSource random) {
        int total = 0;
        for (Trap trap : Trap.values()) {
            total += trap.weight;
        }
        int roll = random.nextInt(total);
        Trap chosen = Trap.values()[0];
        for (Trap trap : Trap.values()) {
            if (roll < trap.weight) {
                chosen = trap;
                break;
            }
            roll -= trap.weight;
        }
        return new Spec(chosen,
            WIDTHS[random.nextInt(WIDTHS.length)],
            MIN_HEIGHT + random.nextInt(MAX_HEIGHT - MIN_HEIGHT + 1));
    }

    static void lay(ServerLevelAccessor level, BlockPos pos, Spec spec) {
        level.setBlock(pos, HexwrightBlocks.WARDING_BOX_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        BlockEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof WardingBoxBlockEntity box)) {
            Hexwright.LOGGER.warn("Dungeon trap at {} has no warding box behind it; leaving it inert", pos);
            return;
        }
        box.installDungeonTrap(GRADE, CHARGE, spec.trap().spell(), spec.trap().size(),
            spec.width(), spec.height());
    }
}
