package dev.equipmentstructure.api.grid.space;

import dev.equipmentstructure.api.EquipmentComponentInstance;
import dev.equipmentstructure.api.EquipmentComponentRegistry;
import dev.equipmentstructure.api.grid.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Versioned lossless item storage inside the source component; unknown item NBT is retained verbatim. */
public record ComponentSpaceContents(String token, Map<ResourceLocation, Space> spaces) {
    public static final String KEY = "equipment_structure_api:spaces";
    public static final int MAX_ENTRIES = 256, MAX_BYTES = 262144;
    public record Entry(CompoundTag item, GridFootprint footprint, GridPlacement placement) {
        public Entry { item = item.copy(); Objects.requireNonNull(footprint); Objects.requireNonNull(placement); }
        @Override public CompoundTag item() { return item.copy(); }
        public ItemStack stack(HolderLookup.Provider registries) { return ItemStack.parse(registries, item).orElse(ItemStack.EMPTY); }
        public Set<GridCell> cells() { return new GridLayout.Part(footprint, placement).cells(); }
        public Entry moved(GridPlacement value) { return new Entry(item, footprint, value); }
    }
    public record Space(GridShape shape, List<Entry> entries) {
        public Space { Objects.requireNonNull(shape); entries = List.copyOf(entries); }
        public int at(GridCell cell) { for (int i = 0; i < entries.size(); i++) if (entries.get(i).cells().contains(cell)) return i; return -1; }
    }
    public ComponentSpaceContents {
        Objects.requireNonNull(token); spaces = Map.copyOf(spaces);
        if (spaces.size() > 16 || spaces.values().stream().mapToInt(s -> s.entries().size()).sum() > MAX_ENTRIES)
            throw new IllegalArgumentException("Too much component space data");
        if (!token.isEmpty()) UUID.fromString(token);
    }
    public static ComponentSpaceContents read(EquipmentComponentInstance part) {
        var data = part.data();
        if (!data.contains(KEY)) return new ComponentSpaceContents("", Map.of());
        if (!data.contains(KEY, Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Invalid space storage");
        var tag = data.getCompound(KEY);
        if (tag.getInt("version") != 1 || !tag.contains("spaces", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Unsupported space storage");
        var values = new TreeMap<ResourceLocation, Space>();
        var stored = tag.getCompound("spaces");
        for (String key : stored.getAllKeys()) {
            var space = stored.getCompound(key);
            var shape = GridCodecs.SHAPE.parse(NbtOps.INSTANCE, space.get("shape")).getOrThrow();
            if (!space.contains("entries", Tag.TAG_LIST)) throw new IllegalArgumentException("Missing space entries");
            var entries = new ArrayList<Entry>();
            var list = space.getList("entries", Tag.TAG_COMPOUND);
            if (list.size() != ((ListTag) space.get("entries")).size()) throw new IllegalArgumentException("Invalid entries");
            for (var element : list) {
                var entry = (CompoundTag) element;
                if (!entry.contains("item", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Missing stored item");
                entries.add(new Entry(entry.getCompound("item"), GridCodecs.FOOTPRINT.parse(NbtOps.INSTANCE, entry.get("footprint")).getOrThrow(),
                        GridCodecs.PLACEMENT.parse(NbtOps.INSTANCE, entry.get("placement")).getOrThrow()));
            }
            values.put(ResourceLocation.parse(key), new Space(shape, entries));
        }
        return new ComponentSpaceContents(tag.getString("token"), values);
    }
    public boolean isEmpty() { return spaces.values().stream().allMatch(s -> s.entries().isEmpty()); }
    public ComponentSpaceContents with(ResourceLocation id, Space space) {
        var changed = new HashMap<>(spaces); changed.put(id, space);
        return new ComponentSpaceContents(token.isEmpty() ? UUID.randomUUID().toString() : token, changed);
    }
    public EquipmentComponentInstance apply(EquipmentComponentInstance part) {
        var stored = new CompoundTag();
        spaces.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(pair -> {
            var space = new CompoundTag();
            space.put("shape", GridCodecs.SHAPE.encodeStart(NbtOps.INSTANCE, pair.getValue().shape()).getOrThrow());
            var entries = new ListTag();
            for (var value : pair.getValue().entries()) {
                var entry = new CompoundTag(); entry.put("item", value.item());
                entry.put("footprint", GridCodecs.FOOTPRINT.encodeStart(NbtOps.INSTANCE, value.footprint()).getOrThrow());
                entry.put("placement", GridCodecs.PLACEMENT.encodeStart(NbtOps.INSTANCE, value.placement()).getOrThrow()); entries.add(entry);
            }
            space.put("entries", entries); stored.put(pair.getKey().toString(), space);
        });
        var tag = new CompoundTag(); tag.putInt("version", 1); tag.putString("token", token); tag.put("spaces", stored);
        var previous = part.data().get(KEY);
        if (tag.sizeInBytes() > MAX_BYTES && (previous == null || tag.sizeInBytes() >= previous.sizeInBytes()))
            throw new IllegalArgumentException("Component space storage exceeds size limit");
        var data = part.data(); data.put(KEY, tag);
        return new EquipmentComponentInstance(part.id(), part.componentType(), part.interfaceType(), data);
    }
    public static long storedBytes(dev.equipmentstructure.api.EquipmentStructure structure) {
        return structure.components().values().stream().flatMap(List::stream)
                .map(part -> part.data().get(KEY)).filter(Objects::nonNull).mapToLong(Tag::sizeInBytes).sum();
    }
    /** Empty-required is enforced in every API removal path, not merely in the GUI. */
    public static boolean canRemove(EquipmentComponentInstance part, GridDefinitions definitions) {
        try {
            var stored = read(part);
            if (stored.isEmpty()) return true;
            var registered = definitions.spaces().getOrDefault(part.id(), List.of());
            for (var entry : stored.spaces().entrySet()) if (!entry.getValue().entries().isEmpty()
                    && registered.stream().noneMatch(d -> d.id().equals(entry.getKey()) && d.removal() == ComponentSpaceDefinition.Removal.KEEP_WITH_COMPONENT)) return false;
            return EquipmentComponentRegistry.createValidatedItemStack(part).isPresent();
        } catch (RuntimeException invalid) { return false; }
    }
}
