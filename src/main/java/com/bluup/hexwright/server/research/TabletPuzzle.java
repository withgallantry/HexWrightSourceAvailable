package com.bluup.hexwright.server.research;

import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class TabletPuzzle {

    public static final int COLS = 11;
    public static final int ROWS = 11;
    public static final int CELLS = COLS * ROWS;

    public static final int ALPHABET = 16;

    public record Break(int row, int start, int run) {

        public int end() {
            return start + run;
        }

        public boolean covers(int row, int col) {
            return row == this.row && col >= start && col < end();
        }
    }

    public record Scrap(int[] glyphs, int runStart, int runLength, int fits) {

        public boolean isDecoy() {
            return fits < 0;
        }

        public int context() {
            return runStart;
        }
    }

    private final TabletDamage damage;
    private final int[] glyphs = new int[CELLS];
    private final boolean[] missing = new boolean[CELLS];
    private final boolean[] pitted = new boolean[CELLS];
    private final List<Break> breaks = new ArrayList<>();
    private final List<Scrap> deck = new ArrayList<>();

    private TabletPuzzle(TabletDamage damage) {
        this.damage = damage;
    }

    public static TabletPuzzle generate(long seed, TabletDamage damage) {
        TabletPuzzle puzzle = new TabletPuzzle(damage);
        puzzle.build(RandomSource.create(seed));
        return puzzle;
    }

    private void build(RandomSource random) {
        for (int cell = 0; cell < CELLS; cell++) {
            glyphs[cell] = random.nextInt(ALPHABET);
        }

        int context = damage.context();
        int capacity = COLS / (2 * context + damage.maxRun());
        int[] perRow = breaksPerRow(random, Math.min(damage.breaks(), ROWS * capacity));
        List<Integer> keys = new ArrayList<>();
        for (int row = 0; row < ROWS; row++) {
            int count = perRow[row];
            if (count == 0) {
                continue;
            }
            int[] runs = new int[count];
            int slack = COLS;
            for (int i = 0; i < count; i++) {
                runs[i] = damage.minRun() + random.nextInt(damage.maxRun() - damage.minRun() + 1);
                slack -= 2 * context + runs[i];
            }
            int[] cuts = new int[count];
            for (int i = 0; i < count; i++) {
                cuts[i] = random.nextInt(slack + 1);
            }
            Arrays.sort(cuts);

            int segment = 0;
            for (int i = 0; i < count; i++) {
                int run = runs[i];
                int start = segment + cuts[i] + context;
                segment += 2 * context + run;

                int key = 0;
                for (int attempt = 0; attempt < 256; attempt++) {
                    for (int j = 0; j < context; j++) {
                        glyphs[cell(row, start - context + j)] = random.nextInt(ALPHABET);
                        glyphs[cell(row, start + run + j)] = random.nextInt(ALPHABET);
                    }
                    key = key(row, start, run, context);
                    if (!keys.contains(key)) {
                        break;
                    }
                }
                keys.add(key);
                breaks.add(new Break(row, start, run));
                for (int col = start; col < start + run; col++) {
                    missing[cell(row, col)] = true;
                }
            }
        }

        for (int i = 0; i < breaks.size(); i++) {
            Break b = breaks.get(i);
            deck.add(new Scrap(strip(b, context), context, b.run(), i));
        }
        addDecoys(random, context, keys);
        shuffle(random, deck);

        pit(random, context);
    }

    private void addDecoys(RandomSource random, int context, List<Integer> keys) {
        List<int[]> candidates = new ArrayList<>();
        for (int left = 0; left < breaks.size(); left++) {
            for (int right = 0; right < breaks.size(); right++) {
                if (left != right) {
                    candidates.add(new int[] {left, right});
                }
            }
        }
        shuffle(random, candidates);

        for (int[] pair : candidates) {
            if (deck.size() - breaks.size() >= damage.decoys()) {
                return;
            }
            Break left = breaks.get(pair[0]);
            Break right = breaks.get(pair[1]);
            int key = (packed(left.row(), left.start() - context, context) << 16)
                | packed(right.row(), right.end(), context);
            if (keys.contains(key)) {
                continue;
            }
            keys.add(key);

            int run = damage.minRun() + random.nextInt(damage.maxRun() - damage.minRun() + 1);
            int[] strip = new int[2 * context + run];
            for (int i = 0; i < context; i++) {
                strip[i] = glyphs[cell(left.row(), left.start() - context + i)];
                strip[context + run + i] = glyphs[cell(right.row(), right.end() + i)];
            }
            for (int i = 0; i < run; i++) {
                strip[context + i] = random.nextInt(ALPHABET);
            }
            deck.add(new Scrap(strip, context, run, -1));
        }
    }

    private void pit(RandomSource random, int context) {
        boolean[] protectedCell = new boolean[CELLS];
        for (Break b : breaks) {
            for (int col = b.start() - context; col < b.end() + context; col++) {
                protectedCell[cell(b.row(), col)] = true;
            }
        }
        for (int cell = 0; cell < CELLS; cell++) {
            if (!protectedCell[cell] && !missing[cell] && random.nextFloat() < damage.pitting()) {
                pitted[cell] = true;
            }
        }
    }

    private int[] strip(Break b, int context) {
        int[] strip = new int[2 * context + b.run()];
        for (int i = 0; i < strip.length; i++) {
            strip[i] = glyphs[cell(b.row(), b.start() - context + i)];
        }
        return strip;
    }

    private int key(int row, int start, int run, int context) {
        return (packed(row, start - context, context) << 16) | packed(row, start + run, context);
    }

    private int packed(int row, int col, int count) {
        int packed = 0;
        for (int i = 0; i < count; i++) {
            packed |= glyphs[cell(row, col + i)] << (4 * i);
        }
        return packed;
    }

    private static int[] breaksPerRow(RandomSource random, int count) {
        int[] perRow = new int[ROWS];
        for (int remaining = count; remaining > 0; remaining -= ROWS) {
            for (int row : pickRows(random, Math.min(remaining, ROWS))) {
                perRow[row]++;
            }
        }
        return perRow;
    }

    private static int[] pickRows(RandomSource random, int count) {
        int[] rows = new int[ROWS];
        for (int i = 0; i < ROWS; i++) {
            rows[i] = i;
        }
        for (int i = ROWS - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int swap = rows[i];
            rows[i] = rows[j];
            rows[j] = swap;
        }
        int[] picked = new int[count];
        System.arraycopy(rows, 0, picked, 0, count);
        Arrays.sort(picked);
        return picked;
    }

    private static <T> void shuffle(RandomSource random, List<T> list) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            T swap = list.get(i);
            list.set(i, list.get(j));
            list.set(j, swap);
        }
    }

    private static int cell(int row, int col) {
        return row * COLS + col;
    }


    public TabletDamage damage() {
        return damage;
    }

    public List<Break> breaks() {
        return List.copyOf(breaks);
    }

    public List<Scrap> deck() {
        return List.copyOf(deck);
    }

    public int glyph(int row, int col) {
        return glyphs[cell(row, col)];
    }

    public boolean isMissing(int row, int col) {
        return missing[cell(row, col)];
    }

    public boolean isPitted(int row, int col) {
        return pitted[cell(row, col)];
    }

    public int damagePercent() {
        int worn = 0;
        for (int cell = 0; cell < CELLS; cell++) {
            if (missing[cell] || pitted[cell]) {
                worn++;
            }
        }
        return worn * 100 / CELLS;
    }

    public boolean fits(int scrap, int breakIndex) {
        return scrap >= 0 && scrap < deck.size()
            && breakIndex >= 0 && breakIndex < breaks.size()
            && deck.get(scrap).fits() == breakIndex;
    }

    public List<String> problems() {
        List<String> problems = new ArrayList<>();
        int context = damage.context();

        for (Break b : breaks) {
            if (b.start() < context || b.end() + context > COLS) {
                problems.add("break at row " + b.row() + " has no room for its context");
            }
        }
        if (breaks.size() != damage.breaks()) {
            problems.add(breaks.size() + " breaks placed of " + damage.breaks() + " - the rows cannot hold them all");
        }
        for (int i = 0; i < breaks.size(); i++) {
            for (int j = i + 1; j < breaks.size(); j++) {
                Break a = breaks.get(i);
                Break b = breaks.get(j);
                if (a.row() == b.row() && a.start() - context < b.end() + context && b.start() - context < a.end() + context) {
                    problems.add("breaks on row " + a.row() + " share their context");
                }
            }
        }
        for (int i = 0; i < deck.size(); i++) {
            for (int j = i + 1; j < deck.size(); j++) {
                if (sameEnds(deck.get(i), deck.get(j))) {
                    problems.add("scraps " + i + " and " + j + " read the same - ambiguous");
                }
            }
        }
        for (int i = 0; i < breaks.size(); i++) {
            int fitting = 0;
            for (Scrap scrap : deck) {
                if (scrap.fits() == i) {
                    fitting++;
                }
            }
            if (fitting != 1) {
                problems.add("break " + i + " is matched by " + fitting + " scraps, not one");
            }
        }
        for (Scrap scrap : deck) {
            if (scrap.glyphs().length != 2 * scrap.context() + scrap.runLength()) {
                problems.add("a scrap's glyphs do not add up to its ends plus its run");
            }
        }
        for (Break b : breaks) {
            if (b.run() < 2 || b.run() > 3) {
                problems.add("break at row " + b.row() + " is " + b.run() + " runes long; the hollows are cut for 2-3");
            }
        }
        if (deck.size() > Integer.SIZE - 1 || breaks.size() > Integer.SIZE - 1) {
            problems.add("deck of " + deck.size() + " is too large for the session's masks");
        }
        return problems;
    }

    private static boolean sameEnds(Scrap a, Scrap b) {
        if (a.context() != b.context()) {
            return false;
        }
        for (int i = 0; i < a.context(); i++) {
            if (a.glyphs()[i] != b.glyphs()[i]) {
                return false;
            }
            if (a.glyphs()[a.glyphs().length - 1 - i] != b.glyphs()[b.glyphs().length - 1 - i]) {
                return false;
            }
        }
        return true;
    }
}
