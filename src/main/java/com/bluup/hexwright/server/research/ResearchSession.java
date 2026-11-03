package com.bluup.hexwright.server.research;

import com.bluup.hexwright.server.inspiration.InspirationLevels;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class ResearchSession {

    public static final int TRAY = 3;

    public enum Outcome {
        NOTHING,
        PLACED,
        WRONG,
        COMPLETED,
        RAN_OUT
    }

    private static final int FLASH_TICKS = 8;

    private static final float WRONG_COST = 0.10f;

    private long seed;
    private TabletDamage damage = TabletDamage.WEATHERED;
    private @Nullable TabletPuzzle puzzle;

    private int filled;

    private int used;

    private final int[] tray = new int[TRAY];

    private int selected = 1;

    private int ticksLeft;
    private int ticksStaked;
    private @Nullable UUID owner;

    private Outcome flash = Outcome.NOTHING;
    private int flashTicks;

    private boolean lingering;

    private @Nullable String learned;

    private int version;

    public ResearchSession() {
        Arrays.fill(tray, -1);
    }


    public boolean hasTablet() {
        return puzzle != null;
    }

    public void setTablet(long seed, TabletDamage damage) {
        if (puzzle != null && this.seed == seed && this.damage == damage) {
            return;
        }
        this.seed = seed;
        this.damage = damage;
        this.puzzle = TabletPuzzle.generate(seed, damage);
        reset();
        selected = damage.suggestedLevels();
    }

    public void clear() {
        puzzle = null;
        reset();
    }

    private void reset() {
        filled = 0;
        used = 0;
        Arrays.fill(tray, -1);
        ticksLeft = 0;
        ticksStaked = 0;
        owner = null;
        flash = Outcome.NOTHING;
        flashTicks = 0;
        lingering = false;
        learned = null;
        version++;
    }


    public boolean isRunning() {
        return puzzle != null && ticksLeft > 0 && !isComplete();
    }

    public boolean isComplete() {
        return puzzle != null && Integer.bitCount(filled) == puzzle.breaks().size();
    }

    public boolean isLingering() {
        return lingering;
    }

    public void dismiss() {
        if (lingering) {
            clear();
        }
    }

    public @Nullable String learned() {
        return learned;
    }

    public void learned(@Nullable String recipe) {
        learned = recipe;
        version++;
    }

    public int selected() {
        return selected;
    }

    public int stakeLevels() {
        return selected * damage.inspirationPerStep();
    }

    public boolean canSelect() {
        return hasTablet() && !isRunning() && !isLingering();
    }

    public void select(int levels) {
        if (isRunning()) {
            return;
        }
        int clamped = Mth.clamp(levels, 1, InspirationLevels.MAX_COMMIT);
        if (clamped != selected) {
            selected = clamped;
            version++;
        }
    }

    public void begin(UUID owner, RandomSource random) {
        this.owner = owner;
        this.ticksStaked = InspirationLevels.clockTicks(selected);
        this.ticksLeft = ticksStaked;
        filled = 0;
        used = 0;
        Arrays.fill(tray, -1);
        deal(random);
        version++;
    }

    public @Nullable UUID owner() {
        return owner;
    }

    public void deal(RandomSource random) {
        if (puzzle == null) {
            return;
        }
        List<Integer> discarded = new ArrayList<>();
        if (Arrays.stream(tray).allMatch(slot -> slot >= 0)) {
            for (int slot : tray) {
                discarded.add(slot);
            }
            Arrays.fill(tray, -1);
        }

        List<Integer> available = available();
        List<Integer> fresh = new ArrayList<>(available);
        fresh.removeAll(discarded);
        List<Integer> pool = fresh.size() >= TRAY ? fresh : available;

        for (int slot = 0; slot < TRAY && !pool.isEmpty(); slot++) {
            if (tray[slot] < 0) {
                tray[slot] = pool.remove(random.nextInt(pool.size()));
            }
        }
        version++;
    }

    private List<Integer> available() {
        List<Integer> available = new ArrayList<>();
        if (puzzle == null) {
            return available;
        }
        for (int scrap = 0; scrap < puzzle.deck().size(); scrap++) {
            int candidate = scrap;
            if ((used & (1 << scrap)) == 0 && Arrays.stream(tray).noneMatch(held -> held == candidate)) {
                available.add(scrap);
            }
        }
        return available;
    }

    public Outcome place(int traySlot, int breakIndex, RandomSource random) {
        if (puzzle == null || !isRunning() || traySlot < 0 || traySlot >= TRAY) {
            return Outcome.NOTHING;
        }
        int scrap = tray[traySlot];
        if (scrap < 0 || breakIndex < 0 || breakIndex >= puzzle.breaks().size() || isFilled(breakIndex)) {
            return Outcome.NOTHING;
        }

        if (!puzzle.fits(scrap, breakIndex)) {
            ticksLeft = Math.max(0, ticksLeft - Math.round(ticksStaked * WRONG_COST));
            return mark(ticksLeft == 0 ? Outcome.RAN_OUT : Outcome.WRONG);
        }

        filled |= 1 << breakIndex;
        used |= 1 << scrap;
        tray[traySlot] = -1;
        if (isComplete()) {
            Arrays.fill(tray, -1);
            lingering = true;
            return mark(Outcome.COMPLETED);
        }
        deal(random);
        return mark(Outcome.PLACED);
    }

    private Outcome mark(Outcome outcome) {
        flash = outcome;
        flashTicks = FLASH_TICKS;
        version++;
        return outcome;
    }

    public Outcome tick() {
        if (flashTicks > 0 && --flashTicks == 0) {
            flash = Outcome.NOTHING;
            version++;
        }
        if (!isRunning()) {
            return Outcome.NOTHING;
        }
        if (--ticksLeft <= 0) {
            ticksLeft = 0;
            return mark(Outcome.RAN_OUT);
        }
        return Outcome.NOTHING;
    }


    public TabletDamage damage() {
        return damage;
    }

    public long seed() {
        return seed;
    }

    public @Nullable TabletPuzzle puzzle() {
        return puzzle;
    }

    public boolean isFilled(int breakIndex) {
        return (filled & (1 << breakIndex)) != 0;
    }

    public int filledCount() {
        return Integer.bitCount(filled);
    }

    public int breakCount() {
        return puzzle == null ? 0 : puzzle.breaks().size();
    }

    public int trayScrap(int slot) {
        return slot < 0 || slot >= TRAY ? -1 : tray[slot];
    }

    public int ticksLeft() {
        return ticksLeft;
    }

    public int ticksStaked() {
        return ticksStaked;
    }

    public float remaining() {
        return ticksStaked <= 0 ? 0f : Mth.clamp(ticksLeft / (float) ticksStaked, 0f, 1f);
    }

    public Outcome flash() {
        return flash;
    }

    public int version() {
        return version;
    }


    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(puzzle != null);
        if (puzzle == null) {
            return;
        }
        buf.writeLong(seed);
        buf.writeEnum(damage);
        buf.writeVarInt(filled);
        buf.writeVarInt(used);
        for (int scrap : tray) {
            buf.writeByte(scrap);
        }
        buf.writeByte(selected);
        buf.writeVarInt(ticksLeft);
        buf.writeVarInt(ticksStaked);
        buf.writeEnum(flash);
        buf.writeBoolean(lingering);
        if (lingering) {
            buf.writeUtf(learned == null ? "" : learned);
        }
    }

    public void read(FriendlyByteBuf buf) {
        if (!buf.readBoolean()) {
            clear();
            return;
        }
        seed = buf.readLong();
        damage = buf.readEnum(TabletDamage.class);
        puzzle = TabletPuzzle.generate(seed, damage);
        filled = buf.readVarInt();
        used = buf.readVarInt();
        for (int slot = 0; slot < TRAY; slot++) {
            tray[slot] = buf.readByte();
        }
        selected = buf.readByte();
        ticksLeft = buf.readVarInt();
        ticksStaked = buf.readVarInt();
        flash = buf.readEnum(Outcome.class);
        lingering = buf.readBoolean();
        String recipe = lingering ? buf.readUtf() : "";
        learned = recipe.isEmpty() ? null : recipe;
        version++;
    }

    public void save(CompoundTag tag) {
        if (puzzle == null) {
            return;
        }
        tag.putLong("Seed", seed);
        tag.putString("Damage", damage.getSerializedName());
        tag.putInt("Filled", filled);
        tag.putInt("Used", used);
        tag.putIntArray("Tray", tray.clone());
        tag.putInt("Selected", selected);
        tag.putInt("TicksLeft", ticksLeft);
        tag.putInt("TicksStaked", ticksStaked);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
    }

    public void load(CompoundTag tag) {
        if (!tag.contains("Seed")) {
            clear();
            return;
        }
        seed = tag.getLong("Seed");
        damage = TabletDamage.byName(tag.getString("Damage"), TabletDamage.WEATHERED);
        puzzle = TabletPuzzle.generate(seed, damage);
        filled = tag.getInt("Filled");
        used = tag.getInt("Used");
        int[] saved = tag.getIntArray("Tray");
        for (int slot = 0; slot < TRAY; slot++) {
            tray[slot] = slot < saved.length ? saved[slot] : -1;
        }
        selected = tag.contains("Selected") ? Mth.clamp(tag.getInt("Selected"), 1, InspirationLevels.MAX_COMMIT)
            : damage.suggestedLevels();
        ticksLeft = tag.getInt("TicksLeft");
        ticksStaked = tag.getInt("TicksStaked");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        if (isComplete()) {
            clear();
        }
        version++;
    }
}
