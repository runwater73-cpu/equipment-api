package dev.equipmentstructure.api;

import dev.equipmentstructure.api.internal.ExtensionGuard;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Opt-in component bindings for existing items, without subclassing or modifying their owning mod.
 * Register on both logical sides during common setup, after the concrete component definition.
 * The definition's item factory remains the sole reverse conversion, including after save/reload.
 */
public final class EquipmentComponentItemAdapters {
    private static final Comparator<Entry> ORDER = Comparator.comparingInt(Entry::priority).reversed()
            .thenComparing(entry -> entry.id().toString());
    private static final ExtensionGuard<ResourceLocation> GUARD = new ExtensionGuard<>("Component item adapter");
    private static volatile Snapshot snapshot = new Snapshot(Map.of(), Map.of(), List.of());

    private EquipmentComponentItemAdapters() {}

    /**
     * Returns saved component data for one input, or empty when this binding does not apply.
     * The reader receives a defensive count-one copy. It must be deterministic and side-effect free;
     * it may inspect tags/components, but must not read client-only state or mutate the world.
     */
    @FunctionalInterface
    public interface Reader {
        Optional<CompoundTag> read(ItemStack stack);
    }

    /** Default priority is zero. Higher priority wins; ties use the namespaced adapter ID. */
    public static Registration register(ResourceLocation adapterId, Item item,
                                        ResourceLocation componentId, Reader reader) {
        return register(adapterId, item, componentId, 0, reader);
    }

    /**
     * Registers one exact item candidate. Conditions belong in the reader; a tag alone never
     * supplies a component identity or an inverse conversion. Conflicting IDs fail at registration.
     */
    public static Registration register(ResourceLocation adapterId, Item item,
                                                     ResourceLocation componentId, int priority, Reader reader) {
        return register(adapterId, Objects.requireNonNull(item, "item"), null, componentId, priority, reader);
    }

    /** Registers every current member of an item tag; membership follows vanilla tag reload/sync. */
    public static Registration registerTag(ResourceLocation adapterId, TagKey<Item> tag,
                                           ResourceLocation componentId, Reader reader) {
        return registerTag(adapterId, tag, componentId, 0, reader);
    }

    /**
     * Tag and exact-item candidates share priority and stable-ID ordering. No specificity bonus is
     * implied. A tag supplies eligibility only: the reader and registered reverse factory must still
     * preserve the exact source item and its data. Empty/missing tags invoke no callbacks.
     */
    public static Registration registerTag(ResourceLocation adapterId, TagKey<Item> tag,
                                           ResourceLocation componentId, int priority, Reader reader) {
        Objects.requireNonNull(tag, "tag");
        if (!tag.isFor(Registries.ITEM)) throw new IllegalArgumentException("Expected an item tag: " + tag);
        return register(adapterId, null, tag, componentId, priority, reader);
    }

    private static synchronized Registration register(ResourceLocation adapterId, Item item, TagKey<Item> tag,
                                                       ResourceLocation componentId, int priority, Reader reader) {
        Objects.requireNonNull(adapterId, "adapterId");
        Objects.requireNonNull(componentId, "componentId");
        Objects.requireNonNull(reader, "reader");
        var definition = EquipmentComponentRegistry.get(componentId)
                .orElseThrow(() -> new IllegalArgumentException("Register the component before its item adapter: " + componentId));
        if (definition.itemFactory().isEmpty()) {
            throw new IllegalArgumentException("An item adapter requires a reverse item factory: " + componentId);
        }
        if (snapshot.byId().containsKey(adapterId)) {
            throw new IllegalStateException("Component item adapter already registered: " + adapterId);
        }
        var entry = new Entry(adapterId, item, tag, definition, priority, reader);
        var entries = new HashMap<>(snapshot.byId());
        entries.put(adapterId, entry);
        snapshot = Snapshot.of(entries);
        return new Registration(entry);
    }

    /** Development/test lifecycle only. Saved components and their item factories are untouched. */
    public static synchronized void clear() {
        snapshot = new Snapshot(Map.of(), Map.of(), List.of());
        GUARD.clear();
    }

    /** External bindings run before the item's native EquipmentComponentItem implementation. */
    static boolean hasCandidates(Item item) {
        var current = snapshot;
        return current.byItem().containsKey(item) || !current.tags().isEmpty();
    }

    /** Metadata only: conditional bindings may overlap intentionally, so priorities are not conflicts. */
    public record Binding(ResourceLocation id, Item item, ResourceLocation componentId, int priority,
                          boolean definitionCurrent) {}

    /** Exact-item registrations; use tagBindings() for tag registrations. Neither invokes callbacks. */
    public static List<Binding> bindings() {
        return snapshot.byId().values().stream().filter(entry -> entry.item() != null).sorted(ORDER)
                .map(entry -> new Binding(entry.id(), entry.item(),
                entry.definition().id(), entry.priority(),
                EquipmentComponentRegistry.get(entry.definition().id()).filter(entry.definition()::equals).isPresent())).toList();
    }

    /** Tag registration metadata, independent of the currently loaded tag members. */
    public record TagBinding(ResourceLocation id, TagKey<Item> tag, ResourceLocation componentId, int priority,
                             boolean definitionCurrent) {}

    public static List<TagBinding> tagBindings() {
        return snapshot.tags().stream().map(entry -> new TagBinding(entry.id(), entry.tag(),
                entry.definition().id(), entry.priority(),
                EquipmentComponentRegistry.get(entry.definition().id()).filter(entry.definition()::equals).isPresent())).toList();
    }

    /** Same selection order and failure boundary as read; only an explicit developer probe calls this. */
    static EquipmentComponentInspection.Result inspect(ItemStack stack, boolean checkItem) {
        var current = snapshot;
        var exact = current.byItem().getOrDefault(stack.getItem(), List.of());
        var tags = current.tags();
        int exactIndex = 0, tagIndex = 0;
        // Merge registration-time sorted lists; never cache tag members across reloads/worlds.
        while (exactIndex < exact.size() || tagIndex < tags.size()) {
            Entry entry;
            if (tagIndex == tags.size() || (exactIndex < exact.size()
                    && ORDER.compare(exact.get(exactIndex), tags.get(tagIndex)) < 0)) {
                entry = exact.get(exactIndex++);
            } else {
                entry = tags.get(tagIndex++);
                if (!stack.is(entry.tag())) continue;
            }
            var result = GUARD.call(entry.id(), () -> inspect(entry, stack, checkItem),
                    EquipmentComponentInspection.Result.of(EquipmentComponentInspection.Status.CALLBACK_FAILED, entry.id()));
            if (result.status() != EquipmentComponentInspection.Status.NO_MATCH) return result;
        }
        return EquipmentComponentInspection.Result.of(EquipmentComponentInspection.Status.NO_MATCH, null);
    }

    private static EquipmentComponentInspection.Result inspect(Entry entry, ItemStack stack, boolean checkItem) {
        var data = Objects.requireNonNull(entry.reader().read(stack.copyWithCount(1)), "adapter reader result");
        if (data.isEmpty()) return EquipmentComponentInspection.Result.of(EquipmentComponentInspection.Status.NO_MATCH, entry.id());
        var definition = EquipmentComponentRegistry.get(entry.definition().id()).orElse(null);
        if (!entry.definition().equals(definition)) {
            return EquipmentComponentInspection.Result.of(EquipmentComponentInspection.Status.DEFINITION_CHANGED, entry.id());
        }
        var component = definition.createInstance(data.get());
        return EquipmentComponentInspection.validate(stack, component, entry.id(), checkItem);
    }

    private static synchronized boolean unregister(Entry entry) {
        if (snapshot.byId().get(entry.id()) != entry) return false;
        var entries = new HashMap<>(snapshot.byId());
        entries.remove(entry.id());
        snapshot = Snapshot.of(entries);
        GUARD.forget(entry.id());
        return true;
    }

    /** Owns exactly one registration; an old handle cannot unregister a newer binding with the same ID. */
    public static final class Registration implements AutoCloseable {
        private final Entry entry;

        private Registration(Entry entry) { this.entry = entry; }

        public boolean unregister() { return EquipmentComponentItemAdapters.unregister(entry); }

        public boolean isActive() { return snapshot.byId().get(entry.id()) == entry; }

        @Override
        public void close() { unregister(); }
    }

    // Exactly one selector (item or tag) is supplied by the public registration methods.
    private record Entry(ResourceLocation id, Item item, TagKey<Item> tag, EquipmentComponentDefinition definition,
                         int priority, Reader reader) {}

    /** Indexing/sorting happen on registration, never per inventory hover or menu click. */
    private record Snapshot(Map<ResourceLocation, Entry> byId, Map<Item, List<Entry>> byItem, List<Entry> tags) {
        private static Snapshot of(Map<ResourceLocation, Entry> entries) {
            Map<Item, List<Entry>> grouped = new HashMap<>();
            entries.values().stream().filter(entry -> entry.item() != null).forEach(entry ->
                    grouped.computeIfAbsent(entry.item(), ignored -> new ArrayList<>()).add(entry));
            grouped.replaceAll((item, values) -> values.stream().sorted(ORDER).toList());
            return new Snapshot(Map.copyOf(entries), Map.copyOf(grouped),
                    entries.values().stream().filter(entry -> entry.tag() != null).sorted(ORDER).toList());
        }
    }
}
