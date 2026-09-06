package com.bluup.hexwright.server.fluid;

import com.bluup.hexwright.common.remnant.Remnant;
import com.bluup.hexwright.common.remnant.RemnantType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TankRemnants {

    public static final TankRemnants EMPTY = new TankRemnants(new EnumMap<>(RemnantType.class));

    public static final double MIN_DRAMS = 1.0e-4;

    private final Map<RemnantType, Double> drams;

    private TankRemnants(Map<RemnantType, Double> drams) {
        this.drams = drams;
    }

    public boolean isEmpty() {
        return drams.isEmpty();
    }

    public int kinds() {
        return drams.size();
    }

    public double total() {
        double sum = 0.0;
        for (double amount : drams.values()) {
            sum += amount;
        }
        return sum;
    }

    public double drams(RemnantType type) {
        return drams.getOrDefault(type, 0.0);
    }

    public boolean has(RemnantType type) {
        return drams.containsKey(type);
    }

    public List<Remnant> contents() {
        List<Remnant> list = new ArrayList<>(drams.size());
        drams.forEach((type, amount) -> list.add(new Remnant(type, amount)));
        return list;
    }

    public Set<RemnantType> types() {
        return Collections.unmodifiableSet(drams.keySet());
    }

    public @Nullable RemnantType largest() {
        RemnantType best = null;
        double most = 0.0;
        for (Map.Entry<RemnantType, Double> entry : drams.entrySet()) {
            if (entry.getValue() > most) {
                most = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }


    public TankRemnants plus(@Nullable Remnant remnant) {
        if (remnant == null || remnant.drams() < MIN_DRAMS) {
            return this;
        }
        Map<RemnantType, Double> next = copy();
        next.merge(remnant.type(), remnant.drams(), Double::sum);
        return new TankRemnants(next);
    }

    public TankRemnants plusAll(TankRemnants other) {
        if (other.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return other;
        }
        Map<RemnantType, Double> next = copy();
        other.drams.forEach((type, amount) -> next.merge(type, amount, Double::sum));
        return new TankRemnants(next);
    }

    public TankRemnants minus(RemnantType type, double amount) {
        double held = drams(type);
        if (held <= 0.0 || amount <= 0.0) {
            return this;
        }
        Map<RemnantType, Double> next = copy();
        double left = held - amount;
        if (left < MIN_DRAMS) {
            next.remove(type);
        } else {
            next.put(type, left);
        }
        return next.isEmpty() ? EMPTY : new TankRemnants(next);
    }

    public TankRemnants minusAll(TankRemnants other) {
        if (other.isEmpty() || isEmpty()) {
            return this;
        }
        Map<RemnantType, Double> next = copy();
        other.drams.forEach((type, amount) -> {
            double left = next.getOrDefault(type, 0.0) - amount;
            if (left < MIN_DRAMS) {
                next.remove(type);
            } else {
                next.put(type, left);
            }
        });
        return next.isEmpty() ? EMPTY : new TankRemnants(next);
    }

    public TankRemnants portion(double fraction) {
        if (fraction >= 1.0) {
            return this;
        }
        if (fraction <= 0.0 || isEmpty()) {
            return EMPTY;
        }
        Map<RemnantType, Double> next = new EnumMap<>(RemnantType.class);
        drams.forEach((type, amount) -> {
            double part = amount * fraction;
            if (part >= MIN_DRAMS) {
                next.put(type, part);
            }
        });
        return next.isEmpty() ? EMPTY : new TankRemnants(next);
    }

    public TankRemnants cappedTo(double cap) {
        if (cap <= 0.0) {
            return EMPTY;
        }
        double total = total();
        return total <= cap ? this : portion(cap / total);
    }

    private Map<RemnantType, Double> copy() {
        return new EnumMap<>(drams);
    }


    private static final String TAG_TYPE = "Type";
    private static final String TAG_DRAMS = "Drams";

    public ListTag save() {
        ListTag list = new ListTag();
        drams.forEach((type, amount) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString(TAG_TYPE, type.name());
            entry.putDouble(TAG_DRAMS, amount);
            list.add(entry);
        });
        return list;
    }

    public static TankRemnants load(@Nullable Tag tag) {
        if (!(tag instanceof ListTag list) || list.isEmpty()) {
            return EMPTY;
        }
        Map<RemnantType, Double> drams = new EnumMap<>(RemnantType.class);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            RemnantType type = RemnantType.byName(entry.getString(TAG_TYPE));
            double amount = entry.getDouble(TAG_DRAMS);
            if (type != null && amount >= MIN_DRAMS) {
                drams.merge(type, amount, Double::sum);
            }
        }
        return drams.isEmpty() ? EMPTY : new TankRemnants(drams);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof TankRemnants that && drams.equals(that.drams));
    }

    @Override
    public int hashCode() {
        return drams.hashCode();
    }

    @Override
    public String toString() {
        return "TankRemnants" + drams;
    }
}
