package dev.equipmentstructure.api.appearance;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Comparator;
import java.util.stream.Collectors;

/** Read-only CPU placement plan, not a GPU transaction or a second installation state. */
public final class AppearancePlan {
    public enum Status { READY, UNCONFIGURED, FALLBACK }

    public enum Reason {
        NONE,
        HOST_UNCONFIGURED, BINDING_UNCONFIGURED, COMPONENT_UNCONFIGURED,
        STALE_RESOURCES, UNSUPPORTED_ADAPTER, ASSET_NOT_READY, UNSUPPORTED_CAPABILITY,
        UNSUPPORTED_JOINT, MISSING_PORT, CYCLIC_PORT, INVALID_TRANSFORM,
        UNDECLARED_REPLACEMENT, REPLACEMENT_CONFLICT,
        MISSING_COMPONENT_SLOT, MISSING_COMPONENT_EXIT, DEPENDENCY_UNAVAILABLE, CYCLIC_CONNECTION
    }

    /** One installed slot's outcome; resource is the offending reference when one is known. */
    public record Outcome(ResourceLocation slotId, ResourceLocation componentId,
                          Optional<ResourceLocation> appearanceId, Status status, Reason reason,
                          Optional<ResourceLocation> resource) {
        public Outcome {
            Objects.requireNonNull(slotId, "slotId");
            Objects.requireNonNull(componentId, "componentId");
            Objects.requireNonNull(appearanceId, "appearanceId");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(resource, "resource");
        }
    }

    /** Slot ID distinguishes multiple instances of the same appearance. Geometry is already calibrated. */
    public record Placement(ResourceLocation slotId, ResourceLocation componentId, ResourceLocation appearanceId,
                            ResourceLocation asset, ResourceLocation seamOwner, AppearanceTransform transform,
                            Set<ResourceLocation> replacedElements,
                            AppearanceDefinitions.RenderProfile renderProfile, double scale, boolean visible,
                            boolean followsHostAnimation, Optional<AppearanceBounds> bounds,
                            Optional<AppearanceOrbitMotion> motion) {
        public Placement {
            Objects.requireNonNull(slotId, "slotId");
            Objects.requireNonNull(componentId, "componentId");
            Objects.requireNonNull(appearanceId, "appearanceId");
            Objects.requireNonNull(asset, "asset");
            Objects.requireNonNull(seamOwner, "seamOwner");
            Objects.requireNonNull(transform, "transform");
            replacedElements = Set.copyOf(replacedElements);
            Objects.requireNonNull(renderProfile, "renderProfile");
            Objects.requireNonNull(bounds, "bounds");
            Objects.requireNonNull(motion, "motion");
            if (!Double.isFinite(scale) || scale < 0.05 || scale > 2) {
                throw new IllegalArgumentException("Invalid appearance scale");
            }
        }

        public Placement(ResourceLocation slotId, ResourceLocation componentId, ResourceLocation appearanceId,
                         ResourceLocation asset, ResourceLocation seamOwner, AppearanceTransform transform,
                         Set<ResourceLocation> replacedElements, AppearanceDefinitions.RenderProfile renderProfile, double scale) {
            this(slotId, componentId, appearanceId, asset, seamOwner, transform, replacedElements, renderProfile, scale, true, true, Optional.empty(), Optional.empty());
        }

        public Placement(ResourceLocation slotId, ResourceLocation componentId, ResourceLocation appearanceId,
                         ResourceLocation asset, ResourceLocation seamOwner, AppearanceTransform transform,
                         Set<ResourceLocation> replacedElements, AppearanceDefinitions.RenderProfile renderProfile,
                         double scale, boolean visible) {
            this(slotId, componentId, appearanceId, asset, seamOwner, transform, replacedElements, renderProfile,
                    scale, visible, true, Optional.empty(), Optional.empty());
        }

        public Placement(ResourceLocation slotId, ResourceLocation componentId, ResourceLocation appearanceId,
                         ResourceLocation asset, ResourceLocation seamOwner, AppearanceTransform transform,
                         Set<ResourceLocation> replacedElements, AppearanceDefinitions.RenderProfile renderProfile,
                         double scale, boolean visible, boolean followsHostAnimation) {
            this(slotId, componentId, appearanceId, asset, seamOwner, transform, replacedElements, renderProfile,
                    scale, visible, followsHostAnimation, Optional.empty(), Optional.empty());
        }

        public Placement(ResourceLocation slotId, ResourceLocation componentId, ResourceLocation appearanceId,
                         ResourceLocation asset, ResourceLocation seamOwner, AppearanceTransform transform,
                         Set<ResourceLocation> replacedElements, AppearanceDefinitions.RenderProfile renderProfile) {
            this(slotId, componentId, appearanceId, asset, seamOwner, transform, replacedElements, renderProfile, 1);
        }

        public Placement(ResourceLocation slotId, ResourceLocation componentId, ResourceLocation appearanceId,
                         ResourceLocation asset, ResourceLocation seamOwner, AppearanceTransform transform,
                         Set<ResourceLocation> replacedElements) {
            this(slotId, componentId, appearanceId, asset, seamOwner, transform, replacedElements,
                    AppearanceDefinitions.RenderProfile.DEFAULT, 1, true, true, Optional.empty(), Optional.empty());
        }
    }

    private final long generation;
    private final ResourceLocation hostId;
    private final Optional<ResourceLocation> hostAppearanceId;
    private final List<Placement> placements;
    private final List<Placement> orderedPlacements;
    private final List<Outcome> outcomes;
    private final Set<ResourceLocation> hiddenOriginalElements;

    AppearancePlan(long generation, ResourceLocation hostId, Optional<ResourceLocation> hostAppearanceId,
                   List<Placement> placements, List<Outcome> outcomes) {
        this.generation = generation;
        this.hostId = Objects.requireNonNull(hostId, "hostId");
        this.hostAppearanceId = Objects.requireNonNull(hostAppearanceId, "hostAppearanceId");
        this.placements = List.copyOf(placements);
        this.orderedPlacements = this.placements.stream()
                .sorted(Comparator.comparingInt((Placement value) -> value.renderProfile().layer().ordinal())
                        .thenComparingInt(value -> value.renderProfile().order())
                        .thenComparing(value -> value.slotId().toString()))
                .toList();
        this.outcomes = List.copyOf(outcomes);
        // Hiding can only come from a successful placement, never an earlier binding request.
        this.hiddenOriginalElements = this.placements.stream().filter(Placement::visible).flatMap(value -> value.replacedElements().stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    public long generation() { return generation; }
    public ResourceLocation hostId() { return hostId; }
    public Optional<ResourceLocation> hostAppearanceId() { return hostAppearanceId; }
    public List<Placement> placements() { return placements; }
    /**
     * Returns the cached deterministic order required by every renderer:
     * structure, surface, effect; then author order and stable slot ID.
     */
    public List<Placement> orderedPlacements() {
        return orderedPlacements;
    }
    public List<Outcome> outcomes() { return outcomes; }
    public Set<ResourceLocation> hiddenOriginalElements() { return hiddenOriginalElements; }
    public boolean usesOriginalAppearance() { return placements.isEmpty(); }
}
