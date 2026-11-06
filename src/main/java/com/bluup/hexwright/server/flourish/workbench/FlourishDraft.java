package com.bluup.hexwright.server.flourish.workbench;

import com.bluup.hexwright.common.flourish.Flourish;
import com.bluup.hexwright.common.flourish.FlourishDesign;
import com.bluup.hexwright.common.flourish.FlourishFigure;
import com.bluup.hexwright.common.flourish.FlourishMotif;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

public final class FlourishDraft {

    private final List<FlourishDesign.Layer> layers = new ArrayList<>();
    private int selected;
    private int selectedRing;
    @Nullable
    private Flourish chime;
    private FlourishDesign.Motes motes = FlourishDesign.Motes.NONE;
    private WorkbenchPage page = WorkbenchPage.LAYERS;
    @Nullable
    private WorkbenchOption openList;

    private int replays;

    public FlourishDraft() {
        load(FlourishDesign.DEFAULT);
    }

    public FlourishDesign design() {
        return new FlourishDesign(layers, chime, motes);
    }

    public void load(FlourishDesign design) {
        layers.clear();
        layers.addAll(design.layers());
        chime = design.chime();
        motes = design.motes();
        selected = Math.min(selected, layers.size() - 1);
        selectedRing = Math.max(0, Math.min(selectedRing, ringCount() - 1));
    }

    public void reset() {
        selected = 0;
        selectedRing = 0;
        load(FlourishDesign.DEFAULT);
    }

    public WorkbenchPage page() {
        return page;
    }

    public void page(WorkbenchPage page) {
        this.page = page;
        openList = null;
    }


    public int size() {
        return layers.size();
    }

    public @Nullable FlourishDesign.Layer layer(int index) {
        return index >= 0 && index < layers.size() ? layers.get(index) : null;
    }

    public int selected() {
        return selected;
    }

    public FlourishDesign.Layer selectedLayer() {
        return layers.get(selected);
    }

    public void select(int index) {
        if (index >= 0 && index < layers.size() && index != selected) {
            selected = index;
            selectedRing = 0;
            openList = null;
        }
    }

    public boolean canAdd() {
        return layers.size() < FlourishDesign.MAX_LAYERS;
    }

    public void add() {
        if (canAdd()) {
            layers.add(FlourishDesign.Layer.of(FlourishMotif.RING));
            selected = layers.size() - 1;
            selectedRing = 0;
        }
    }

    public boolean canRemove() {
        return layers.size() > 1;
    }

    public void remove() {
        if (canRemove()) {
            layers.remove(selected);
            selected = Math.min(selected, layers.size() - 1);
            selectedRing = 0;
        }
    }

    public void motif(FlourishMotif motif) {
        layers.set(selected, selectedLayer().withMotif(motif));
        selectedRing = Math.min(selectedRing, Math.max(0, ringCount() - 1));
        if (motif == FlourishMotif.FIGURE) {
            page = WorkbenchPage.FIGURE;
        }
    }

    public @Nullable Flourish chime() {
        return chime;
    }

    public int replays() {
        return replays;
    }

    public void replay() {
        replays++;
    }


    public @Nullable FlourishFigure figure() {
        return selectedLayer().figure();
    }

    public int ringCount() {
        FlourishFigure figure = figure();
        return figure != null ? figure.rings().size() : 0;
    }

    public @Nullable FlourishFigure.Ring ring(int index) {
        FlourishFigure figure = figure();
        return figure != null && index >= 0 && index < figure.rings().size() ? figure.rings().get(index) : null;
    }

    public int selectedRing() {
        return selectedRing;
    }

    public void selectRing(int index) {
        if (ring(index) != null) {
            selectedRing = index;
            openList = null;
        }
    }

    public boolean canAddRing() {
        int count = ringCount();
        return count > 0 && count < FlourishFigure.MAX_RINGS;
    }

    public void addRing() {
        if (canAddRing()) {
            int index = ringCount();
            List<FlourishFigure.Ring> rings = new ArrayList<>(figure().rings());
            rings.add(FlourishFigure.Ring.of(FlourishFigure.Shape.CIRCLE).withRadius(6).withHue(index));
            changeFigure(figure -> figure.withRings(rings));
            selectedRing = index;
        }
    }

    public boolean canRemoveRing() {
        return ringCount() > 1;
    }

    public void removeRing() {
        if (canRemoveRing()) {
            List<FlourishFigure.Ring> rings = new ArrayList<>(figure().rings());
            rings.remove(selectedRing);
            changeFigure(figure -> figure.withRings(rings));
            selectedRing = Math.min(selectedRing, rings.size() - 1);
        }
    }

    public void shape(FlourishFigure.Shape shape) {
        if (figure() == null) {
            FlourishFigure.Ring ring = FlourishFigure.Ring.of(shape).withRadius(FlourishFigure.RADII);
            layers.set(selected, selectedLayer().withFigure(FlourishFigure.DEFAULT.withRings(List.of(ring))));
            selectedRing = 0;
            return;
        }
        changeRing(ring -> ring.withShape(shape));
    }

    private void changeFigure(UnaryOperator<FlourishFigure> change) {
        FlourishFigure figure = figure();
        if (figure != null) {
            layers.set(selected, selectedLayer().withFigure(change.apply(figure)));
        }
    }

    private void changeRing(UnaryOperator<FlourishFigure.Ring> change) {
        changeFigure(figure -> figure.withRing(selectedRing, change.apply(figure.rings().get(selectedRing))));
    }


    public boolean applies(WorkbenchOption option) {
        if (option == WorkbenchOption.COUNT) {
            FlourishFigure.Ring ring = ring(selectedRing);
            return ring != null && ring.counted();
        }
        return !option.ofFigure() || figure() != null;
    }

    public void cycle(WorkbenchOption option, int step) {
        FlourishDesign.Layer layer = selectedLayer();
        switch (option) {
            case PLACEMENT -> {
                List<FlourishMotif.Placement> placements = layer.motif().placements();
                int at = placements.indexOf(layer.placement());
                layers.set(selected, layer.withPlacement(placements.get(Math.floorMod(at + step, placements.size()))));
            }
            case SIZE -> layers.set(selected, layer.withSize(Math.floorMod(layer.size() + step, FlourishDesign.SIZES)));
            case DELAY -> layers.set(selected,
                layer.withDelay(Math.floorMod(layer.delay() + step, FlourishDesign.MAX_DELAY + 1)));
            case SHADE -> layers.set(selected, layer.withShade(layer.shade() + step));
            case CHIME -> {
                int at = chime == null ? 0 : chime.ordinal() + 1;
                int next = Math.floorMod(at + step, Flourish.values().length + 1);
                chime = next == 0 ? null : Flourish.values()[next - 1];
            }
            case MOTES -> motes = step(FlourishDesign.Motes.values(), motes, step);
            case COUNT -> changeRing(ring -> ring.withCount(ring.shape().minCount()
                + Math.floorMod(ring.count() - ring.shape().minCount() + step,
                FlourishFigure.MAX_COUNT - ring.shape().minCount() + 1)));
            case FORM -> changeRing(ring -> ring.withForm(Math.floorMod(ring.form() + step, ring.shape().forms(ring.count()))));
            case RADIUS -> changeRing(ring -> ring.withRadius(1 + Math.floorMod(ring.radius() - 1 + step, FlourishFigure.RADII)));
            case WEIGHT -> changeRing(ring -> ring.withWeight(Math.floorMod(ring.weight() + step, FlourishFigure.WEIGHTS)));
            case SPIN -> changeRing(ring -> ring.withSpin(Math.floorMod(ring.spin() + FlourishFigure.MAX_SPIN + step,
                FlourishFigure.MAX_SPIN * 2 + 1) - FlourishFigure.MAX_SPIN));
            case TURN -> changeRing(ring -> ring.withTurn(ring.turn() + step));
            case HUE -> changeRing(ring -> ring.withHue(ring.hue() + step));
            case ENTRANCE -> changeFigure(figure -> figure.withEntrance(
                step(FlourishFigure.Entrance.values(), figure.entrance(), step)));
            case EXIT -> changeFigure(figure -> figure.withExit(step(FlourishFigure.Exit.values(), figure.exit(), step)));
            case TEMPO -> changeFigure(figure -> figure.withTempo(Math.floorMod(figure.tempo() + step, FlourishFigure.TEMPOS)));
        }
    }

    public int choices(WorkbenchOption option) {
        FlourishFigure.Ring ring = ring(selectedRing);
        if (option.ofFigure() && option != WorkbenchOption.HUE && ring == null) {
            return 0;
        }
        return switch (option) {
            case PLACEMENT -> selectedLayer().motif().placements().size();
            case CHIME -> Flourish.values().length + 1;
            case MOTES -> FlourishDesign.Motes.values().length;
            case SIZE -> FlourishDesign.SIZES;
            case DELAY -> FlourishDesign.MAX_DELAY + 1;
            case SHADE -> FlourishDesign.SHADES;
            case COUNT -> FlourishFigure.MAX_COUNT - ring.shape().minCount() + 1;
            case FORM -> ring.shape().forms(ring.count());
            case RADIUS -> FlourishFigure.RADII;
            case WEIGHT -> FlourishFigure.WEIGHTS;
            case SPIN -> FlourishFigure.MAX_SPIN * 2 + 1;
            case TURN -> FlourishFigure.TURNS;
            case HUE -> FlourishFigure.HUES;
            case ENTRANCE -> FlourishFigure.Entrance.values().length;
            case EXIT -> FlourishFigure.Exit.values().length;
            case TEMPO -> FlourishFigure.TEMPOS;
            default -> 0;
        };
    }

    public int choice(WorkbenchOption option) {
        FlourishDesign.Layer layer = selectedLayer();
        FlourishFigure.Ring ring = ring(selectedRing);
        if (option.ofFigure() && ring == null) {
            return 0;
        }
        return switch (option) {
            case PLACEMENT -> Math.max(0, layer.motif().placements().indexOf(layer.placement()));
            case CHIME -> chime == null ? 0 : chime.ordinal() + 1;
            case MOTES -> motes.ordinal();
            case SIZE -> layer.size();
            case DELAY -> layer.delay();
            case SHADE -> layer.shade();
            case COUNT -> ring.count() - ring.shape().minCount();
            case FORM -> ring.form();
            case RADIUS -> ring.radius() - 1;
            case WEIGHT -> ring.weight();
            case SPIN -> FlourishFigure.MAX_SPIN - ring.spin();
            case TURN -> ring.turn();
            case HUE -> ring.hue();
            case ENTRANCE -> figure().entrance().ordinal();
            case EXIT -> figure().exit().ordinal();
            case TEMPO -> figure().tempo();
            default -> 0;
        };
    }

    public void choose(WorkbenchOption option, int index) {
        int step = index - choice(option);
        if (step != 0) {
            cycle(option, option == WorkbenchOption.SPIN ? -step : step);
        }
    }

    public Component choiceName(WorkbenchOption option, int index) {
        FlourishFigure.Ring ring = ring(selectedRing);
        return switch (option) {
            case PLACEMENT -> Component.translatable(selectedLayer().motif().placements().get(index).translationKey());
            case CHIME -> index == 0
                ? Component.translatable("gui.hexwright.flourish_workbench.chime.silent")
                : Component.translatable(Flourish.values()[index - 1].nameKey());
            case MOTES -> Component.translatable(FlourishDesign.Motes.values()[index].translationKey());
            case SIZE -> Component.translatable("gui.hexwright.flourish_workbench.size." + index);
            case FORM -> ring != null ? formName(ring.shape(), index) : Component.literal("–");
            case TURN -> Component.translatable("gui.hexwright.flourish_workbench.turn." + index);
            case ENTRANCE -> Component.translatable(FlourishFigure.Entrance.values()[index].translationKey());
            case EXIT -> Component.translatable(FlourishFigure.Exit.values()[index].translationKey());
            default -> Component.literal(String.valueOf(index + 1));
        };
    }

    private static Component formName(FlourishFigure.Shape shape, int form) {
        return shape == FlourishFigure.Shape.STAR
            ? Component.translatable(shape.translationKey() + ".form", form + 2)
            : Component.translatable(shape.translationKey() + ".form." + form);
    }


    public @Nullable WorkbenchOption openList() {
        return openList;
    }

    public void openList(@Nullable WorkbenchOption option) {
        openList = option;
    }

    private static <E extends Enum<E>> E step(E[] values, E current, int step) {
        return values[Math.floorMod(current.ordinal() + step, values.length)];
    }

    public Component value(WorkbenchOption option) {
        FlourishDesign.Layer layer = selectedLayer();
        FlourishFigure figure = layer.figure();
        if (option.ofFigure() && figure == null) {
            return Component.literal("–");
        }
        FlourishFigure.Ring ring = option.ofFigure() ? figure.rings().get(selectedRing) : null;
        return switch (option) {
            case PLACEMENT -> Component.translatable(layer.placement().translationKey());
            case SIZE -> Component.translatable("gui.hexwright.flourish_workbench.size." + layer.size());
            case DELAY -> layer.delay() == 0
                ? Component.translatable("gui.hexwright.flourish_workbench.delay.none")
                : Component.translatable("gui.hexwright.flourish_workbench.delay.seconds",
                    String.format("%.1f", layer.delayTicks() / 20.0f));
            case SHADE -> Component.translatable("gui.hexwright.flourish_workbench.shade", layer.shade() + 1,
                FlourishDesign.SHADES);
            case CHIME -> chime == null
                ? Component.translatable("gui.hexwright.flourish_workbench.chime.silent")
                : Component.translatable(chime.nameKey());
            case MOTES -> Component.translatable(motes.translationKey());
            case COUNT -> count(ring);
            case FORM -> formName(ring.shape(), ring.form());
            case RADIUS -> Component.literal(ring.radius() * 100 / FlourishFigure.RADII + "%");
            case WEIGHT -> Component.translatable("gui.hexwright.flourish_workbench.weight." + ring.weight());
            case SPIN -> ring.spin() == 0
                ? Component.translatable("gui.hexwright.flourish_workbench.spin.still")
                : Component.translatable("gui.hexwright.flourish_workbench.spin." + Math.abs(ring.spin()),
                    ring.spin() > 0 ? "←" : "→");
            case TURN -> Component.translatable("gui.hexwright.flourish_workbench.turn." + ring.turn());
            case HUE -> Component.translatable("gui.hexwright.flourish_workbench.shade", ring.hue() + 1,
                FlourishFigure.HUES);
            case ENTRANCE -> Component.translatable(figure.entrance().translationKey());
            case EXIT -> Component.translatable(figure.exit().translationKey());
            case TEMPO -> Component.translatable("gui.hexwright.flourish_workbench.tempo",
                String.format("%.1f", figure.lifetimeTicks() / 20.0f));
        };
    }

    static Component count(FlourishFigure.Ring ring) {
        int marks = switch (ring.shape()) {
            case TEETH -> ring.count() * 2;
            case GLYPHS -> ring.count() * (ring.form() == 1 ? 5 : 3);
            default -> ring.count();
        };
        return Component.translatable(ring.shape().translationKey() + ".count", marks);
    }
}
