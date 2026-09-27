package com.bluup.hexwright.client.journal;

import com.bluup.hexwright.common.staff_assembly.calc.IngredientCategory;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public final class ClientInvestigations {

    private static volatile Set<String> completed = Set.of();

    private static volatile int version;

    private ClientInvestigations() {
    }

    public static void set(Collection<String> ids) {
        completed = Set.copyOf(ids);
        version++;
    }

    public static Set<String> completed() {
        return completed;
    }

    public static boolean has(String investigationId) {
        return completed.contains(investigationId);
    }

    public static int version() {
        return version;
    }

    private static volatile Set<String> artifacts = Set.of();

    public static void setArtifacts(Collection<String> ids) {
        artifacts = Set.copyOf(ids);
        version++;
    }

    public static Set<String> artifacts() {
        return artifacts;
    }

    private static volatile Map<IngredientCategory, Set<ResourceLocation>> essenceSources = Map.of();

    public static void setEssenceSources(Map<IngredientCategory, Set<ResourceLocation>> sources) {
        essenceSources = Map.copyOf(sources);
        version++;
    }

    public static Map<IngredientCategory, Set<ResourceLocation>> essenceSources() {
        return essenceSources;
    }
}
