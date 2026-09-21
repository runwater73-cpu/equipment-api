package dev.equipmentstructure.api.appearance;

import dev.equipmentstructure.api.EquipmentSlotDefinition;
import dev.equipmentstructure.api.EquipmentStructure;
import dev.equipmentstructure.api.client.appearance.AppearancePlanCache;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AppearancePlanCacheTest {
    private static final ResourceLocation HOST = ResourceLocation.fromNamespaceAndPath("test", "host");
    private static final ResourceLocation SLOT = ResourceLocation.fromNamespaceAndPath("test", "slot");

    @Test
    void computesOnceForEqualImmutableSnapshots() {
        var cache = new AppearancePlanCache(4);
        var structure = structure();
        var support = new AppearanceSupport(3, Set.of(), Set.of(), true);
        var calls = new AtomicInteger();
        var first = cache.getOrCompute(structure, support,
                () -> { calls.incrementAndGet(); return plan(3); });
        var second = cache.getOrCompute(structure, support,
                () -> { calls.incrementAndGet(); return plan(3); });
        assertSame(first, second);
        assertEquals(1, calls.get());
    }

    @Test
    void boundsEntriesAndDropsOldGenerations() {
        var cache = new AppearancePlanCache(1);
        var support1 = new AppearanceSupport(1, Set.of(), Set.of(), true);
        var support2 = new AppearanceSupport(2, Set.of(), Set.of(), true);
        cache.getOrCompute(structure(), support1, () -> plan(1));
        cache.getOrCompute(structure(), support2, () -> plan(2));
        assertEquals(1, cache.size());
        cache.invalidateGeneration(2);
        assertEquals(1, cache.size());
        cache.invalidateGeneration(1);
        assertEquals(0, cache.size());
    }

    @Test
    void mismatchedPlanGenerationIsNotCached() {
        var cache = new AppearancePlanCache(4);
        var support = new AppearanceSupport(7, Set.of(), Set.of(), true);
        AtomicInteger calls = new AtomicInteger();
        cache.getOrCompute(structure(), support, () -> { calls.incrementAndGet(); return plan(6); });
        cache.getOrCompute(structure(), support, () -> { calls.incrementAndGet(); return plan(6); });
        assertEquals(2, calls.get());
        assertEquals(0, cache.size());
    }

    private static EquipmentStructure structure() {
        return new EquipmentStructure(HOST, HOST, List.of(new EquipmentSlotDefinition(
                SLOT, SLOT, SLOT)), Map.of());
    }

    private static AppearancePlan plan(long generation) {
        return new AppearancePlan(generation, HOST, java.util.Optional.empty(), List.of(), List.of());
    }
}
