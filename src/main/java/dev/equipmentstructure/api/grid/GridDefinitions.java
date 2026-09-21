package dev.equipmentstructure.api.grid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.equipmentstructure.api.EquipmentComponentRegistry;
import dev.equipmentstructure.api.EquipmentStructure;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.Comparator;

/** One immutable server definition generation. Client receipt never mutates Java registrations. */
public record GridDefinitions(Map<ResourceLocation, GridBoard> hosts,
                              Map<ResourceLocation, GridFootprint> components,
                              Map<ResourceLocation, java.util.List<dev.equipmentstructure.api.grid.space.ComponentSpaceDefinition>> spaces,
                              Map<ResourceLocation, dev.equipmentstructure.api.grid.synergy.GridRuleDefinition> rules,
                              Map<ResourceLocation, Map<ResourceLocation, dev.equipmentstructure.api.grid.synergy.GridRegion>> regions) {
    public static final GridDefinitions EMPTY = new GridDefinitions(Map.of(), Map.of());
    public static final int MAX_DEFINITIONS = 4096;
    public static final Codec<GridDefinitions> CODEC = RecordCodecBuilder.create(i -> i.group(
            GridCodecs.boundedMap(GridCodecs.BOARD, MAX_DEFINITIONS).fieldOf("hosts").forGetter(GridDefinitions::hosts),
            GridCodecs.boundedMap(GridCodecs.FOOTPRINT, MAX_DEFINITIONS).fieldOf("components").forGetter(GridDefinitions::components),
            GridCodecs.boundedMap(dev.equipmentstructure.api.grid.space.ComponentSpaceDefinition.CODEC.listOf(0, 16), MAX_DEFINITIONS)
                    .optionalFieldOf("spaces", Map.of()).forGetter(GridDefinitions::spaces),
            GridCodecs.boundedMap(dev.equipmentstructure.api.grid.synergy.GridRuleDefinition.CODEC, MAX_DEFINITIONS)
                    .optionalFieldOf("rules", Map.of()).forGetter(GridDefinitions::rules),
            GridCodecs.boundedMap(GridCodecs.boundedMap(dev.equipmentstructure.api.grid.synergy.GridRegion.CODEC, 256), MAX_DEFINITIONS)
                    .optionalFieldOf("regions", Map.of()).forGetter(GridDefinitions::regions)
    ).apply(i, GridDefinitions::new));

    private static volatile GridDefinitions registeredSnapshot;
    private static final ThreadLocal<dev.equipmentstructure.api.internal.SnapshotCache<GridDefinitions, EquipmentStructure, String>> FINGERPRINTS =
            ThreadLocal.withInitial(() -> new dev.equipmentstructure.api.internal.SnapshotCache<>(32));

    public GridDefinitions {
        hosts = ordered(hosts);
        components = ordered(components);
        var spaceCopy = new java.util.HashMap<ResourceLocation, java.util.List<dev.equipmentstructure.api.grid.space.ComponentSpaceDefinition>>();
        spaces.forEach((id, list) -> {
            if (list.size() > 16 || list.stream().map(dev.equipmentstructure.api.grid.space.ComponentSpaceDefinition::id).distinct().count() != list.size())
                throw new IllegalArgumentException("Invalid component spaces");
            spaceCopy.put(id, java.util.List.copyOf(list));
        });
        spaces = ordered(spaceCopy);
        rules = ordered(rules);
        rules.forEach((id, rule) -> { if (!id.equals(rule.id())) throw new IllegalArgumentException("Rule ID mismatch"); });
        var regionCopy = new java.util.HashMap<ResourceLocation, Map<ResourceLocation, dev.equipmentstructure.api.grid.synergy.GridRegion>>();
        regions.forEach((id, value) -> { if (value.size() > 256) throw new IllegalArgumentException("Too many regions"); regionCopy.put(id, ordered(value)); });
        regions = ordered(regionCopy);
        if (hosts.size() > MAX_DEFINITIONS || components.size() > MAX_DEFINITIONS || spaces.size() > MAX_DEFINITIONS
                || rules.size() > MAX_DEFINITIONS || regions.size() > MAX_DEFINITIONS)
            throw new IllegalArgumentException("Too many grid definitions");
    }
    public GridDefinitions(Map<ResourceLocation, GridBoard> hosts, Map<ResourceLocation, GridFootprint> components) {
        this(hosts, components, Map.of(), Map.of(), Map.of());
    }
    public GridDefinitions(Map<ResourceLocation, GridBoard> hosts, Map<ResourceLocation, GridFootprint> components,
            Map<ResourceLocation, java.util.List<dev.equipmentstructure.api.grid.space.ComponentSpaceDefinition>> spaces) {
        this(hosts, components, spaces, Map.of(), Map.of());
    }

    public static GridDefinitions registered() {
        var snapshot = registeredSnapshot;
        if (snapshot != null) return snapshot;
        synchronized (GridDefinitions.class) {
            if (registeredSnapshot == null) registeredSnapshot = captureRegistered();
            return registeredSnapshot;
        }
    }

    /** Registration changes publish a new immutable generation; existing readers retain their snapshot. */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static synchronized void invalidateRegistered() { registeredSnapshot = null; }

    private static GridDefinitions captureRegistered() {
        var footprints = new java.util.HashMap<ResourceLocation, GridFootprint>();
        EquipmentComponentRegistry.definitions().forEach((id, part) -> footprints.put(id, part.footprint()));
        return new GridDefinitions(EquipmentGridRegistry.hosts(), footprints, dev.equipmentstructure.api.grid.space.ComponentSpaceRegistry.definitions(),
                dev.equipmentstructure.api.grid.synergy.GridRuleRegistry.definitions(), dev.equipmentstructure.api.grid.synergy.GridRuleRegistry.regions());
    }

    /** Complete generation token for client requests, including candidates not yet installed. */
    public String fingerprint() {
        var cache = FINGERPRINTS.get();
        String cached = cache.get(this, null);
        return cached != null ? cached : cache.put(this, null, hash(CODEC.encodeStart(JsonOps.INSTANCE, this).getOrThrow().toString()));
    }

    /** Unrelated components do not invalidate an already saved equipment layout. */
    public String fingerprint(EquipmentStructure structure) {
        var cache = FINGERPRINTS.get();
        String cached = cache.get(this, structure);
        if (cached != null) return cached;
        var body = hosts.get(structure.hostId());
        if (body == null) throw new IllegalArgumentException("Missing host grid: " + structure.hostId());
        StringBuilder data = new StringBuilder(structure.hostId().toString()).append('\n').append(structure.version())
                .append('\n').append(GridCodecs.BOARD.encodeStart(JsonOps.INSTANCE, body).getOrThrow());
        ordered(structure.components()).forEach((slot, parts) -> {
            var part = parts.getFirst();
            var footprint = components.get(part.id());
            if (footprint == null) throw new IllegalArgumentException("Missing component grid: " + part.id());
            data.append('\n').append(slot).append('\n').append(part.id()).append('\n').append(part.componentType())
                    .append('\n').append(part.interfaceType()).append('\n')
                    .append(GridCodecs.FOOTPRINT.encodeStart(JsonOps.INSTANCE, footprint).getOrThrow());
            var componentSpaces = spaces.getOrDefault(part.id(), java.util.List.of());
            if (!componentSpaces.isEmpty()) data.append('\n').append(dev.equipmentstructure.api.grid.space.ComponentSpaceDefinition.CODEC.listOf()
                    .encodeStart(JsonOps.INSTANCE, componentSpaces).getOrThrow());
        });
        return cache.put(this, structure, hash(data.toString()));
    }

    private static <T> Map<ResourceLocation, T> ordered(Map<ResourceLocation, T> source) {
        var result = new TreeMap<ResourceLocation, T>(Comparator.comparing(ResourceLocation::toString));
        source.forEach((id, value) -> result.put(java.util.Objects.requireNonNull(id), java.util.Objects.requireNonNull(value)));
        return java.util.Collections.unmodifiableMap(result);
    }

    private static String hash(String data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
