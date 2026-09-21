package dev.equipmentstructure.api.appearance;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Calibrated appearance resources and explicit connections; no loading, drawing or gameplay rules. */
public final class AppearanceDefinitions {
    public static final ResourceLocation ANCHOR_ONLY = ResourceLocation.fromNamespaceAndPath("equipment_structure_api", "anchor_only");

    private AppearanceDefinitions() {}

    /** Rendering role used to order independent assets without assigning them a body-specific meaning. */
    public enum RenderMode { GEOMETRY, SURFACE, EFFECT }

    public enum RenderLayer {
        STRUCTURE,
        SURFACE,
        EFFECT
    }

    /** Author-declared freedom for the player placement editor. */
    public enum PlacementFreedom {
        ADJUSTABLE,
        FIXED,
        FREE
    }

    /** Stable draw metadata. It never changes installation or attribute semantics. */
    public record RenderProfile(RenderMode mode, RenderLayer layer, int order, float depthBias) {
        public static final RenderProfile DEFAULT = new RenderProfile(RenderMode.GEOMETRY,
                RenderLayer.STRUCTURE, 0, 0.0F);

        public RenderProfile {
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(layer, "layer");
            if (order < -1024 || order > 1024) {
                throw new IllegalArgumentException("Appearance render order must be between -1024 and 1024");
            }
            if (!Float.isFinite(depthBias) || Math.abs(depthBias) > 16.0F) {
                throw new IllegalArgumentException("Appearance depth bias must be finite and bounded");
            }
        }
    }

    /** A stable installed slot and a named exit in that part's model-local coordinates. */
    public record ComponentExit(ResourceLocation slot, ResourceLocation exit) {
        public ComponentExit {
            Objects.requireNonNull(slot, "slot");
            Objects.requireNonNull(exit, "exit");
        }
    }

    /** A host-local frame, relative to at most one host port OR installed part exit. */
    public record Port(Optional<ResourceLocation> parent, AppearanceTransform localFrame,
                       Optional<ComponentExit> componentExit) {
        public Port {
            Objects.requireNonNull(parent, "parent");
            Objects.requireNonNull(localFrame, "localFrame");
            Objects.requireNonNull(componentExit, "componentExit");
            if (parent.isPresent() && componentExit.isPresent()) {
                throw new IllegalArgumentException("A port cannot have both parent and component_exit");
            }
        }

        public Port(Optional<ResourceLocation> parent, AppearanceTransform localFrame) {
            this(parent, localFrame, Optional.empty());
        }

        public Port(AppearanceTransform localFrame) { this(Optional.empty(), localFrame); }

        public static Port onComponent(ResourceLocation slot, ResourceLocation exit, AppearanceTransform localFrame) {
            return new Port(Optional.empty(), localFrame, Optional.of(new ComponentExit(slot, exit)));
        }
    }

    /** Explicit seam responsibility. Only ANCHOR_ONLY is executed by the current core. */
    public record Joint(ResourceLocation owner, ResourceLocation strategy, AppearanceTransform offset) {
        public Joint {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(strategy, "strategy");
            Objects.requireNonNull(offset, "offset");
        }
    }

    /** A logical slot binds to an optional host port and explicitly names the original elements it replaces. */
    public record Binding(ResourceLocation port, Joint joint, Set<ResourceLocation> replaces,
                          PlacementFreedom placementFreedom, boolean followsHostAnimation,
                          Optional<AppearanceBounds> bounds) {
        public Binding {
            Objects.requireNonNull(joint, "joint");
            replaces = Set.copyOf(replaces);
            Objects.requireNonNull(placementFreedom, "placementFreedom");
            Objects.requireNonNull(bounds, "bounds");
        }

        /** Creates an adjustable binding. */
        public Binding(ResourceLocation port, Joint joint, Set<ResourceLocation> replaces) {
            this(port, joint, replaces, PlacementFreedom.ADJUSTABLE, true, Optional.empty());
        }

        public Binding(ResourceLocation port, Joint joint, Set<ResourceLocation> replaces,
                       PlacementFreedom placementFreedom) {
            this(port, joint, replaces, placementFreedom, true, Optional.empty());
        }

        /** Creates a binding that is intentionally independent of a host connection point. */
        public static Binding free(Joint joint) {
            return new Binding(null, joint, Set.of(), PlacementFreedom.FREE, true, Optional.empty());
        }

        public static Binding free(Joint joint, boolean followsHostAnimation) {
            return new Binding(null, joint, Set.of(), PlacementFreedom.FREE, followsHostAnimation, Optional.empty());
        }

        public static Binding fixed(ResourceLocation port, Joint joint, Set<ResourceLocation> replaces) {
            return new Binding(port, joint, replaces, PlacementFreedom.FIXED, true, Optional.empty());
        }

        /**
         * Creates the normal additive equipment binding: the host model remains visible
         * and the part is drawn at the named port.
         */
        public static Binding additive(ResourceLocation port, Joint joint) {
            return new Binding(port, joint, Set.of(), PlacementFreedom.ADJUSTABLE, true, Optional.empty());
        }

        public static Binding additive(ResourceLocation port, Joint joint, boolean followsHostAnimation) {
            return new Binding(port, joint, Set.of(), PlacementFreedom.ADJUSTABLE,
                    followsHostAnimation, Optional.empty());
        }

        /**
         * Creates an explicit replacement binding. Replacement is opt-in; callers
         * should name every host element that the part is allowed to replace.
         */
        public static Binding replacement(ResourceLocation port, Joint joint,
                                          Set<ResourceLocation> replaces) {
            return new Binding(port, joint, replaces, PlacementFreedom.ADJUSTABLE, true, Optional.empty());
        }

        /** Rendering and editing share the same constraint policy; stored data is never changed here. */
        public AppearancePose constrain(AppearancePose pose) {
            if (placementFreedom == PlacementFreedom.FIXED) return AppearancePose.IDENTITY;
            return bounds.map(value -> value.clamp(pose)).orElse(pose);
        }
    }

    /** Map keys are port IDs and logical slot IDs respectively. IDs have no hard-coded body meaning. */
    public record Host(ResourceLocation id, Map<ResourceLocation, Port> ports,
                       Map<ResourceLocation, Binding> bindings, Set<ResourceLocation> replaceableElements) {
        public Host {
            Objects.requireNonNull(id, "id");
            ports = Map.copyOf(ports);
            bindings = Map.copyOf(bindings);
            replaceableElements = Set.copyOf(replaceableElements);
        }

        /** Returns the declared binding; absent bindings remain an explicit configuration error. */
        public Optional<Binding> bindingOptional(ResourceLocation slot) {
            return Optional.ofNullable(bindings.get(slot));
        }
    }

    /** One calibrated asset per part. Mount and named exits share its model-local coordinate system. */
    public record Part(ResourceLocation id, ResourceLocation asset, AppearanceTransform mount,
                       Set<ResourceLocation> requiredCapabilities, RenderProfile renderProfile,
                       Map<ResourceLocation, AppearanceTransform> exits, boolean followsHostAnimation,
                       Optional<AppearanceOrbitMotion> motion) {
        public Part {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(asset, "asset");
            Objects.requireNonNull(mount, "mount");
            requiredCapabilities = Set.copyOf(requiredCapabilities);
            Objects.requireNonNull(renderProfile, "renderProfile");
            exits = Map.copyOf(exits);
            Objects.requireNonNull(motion, "motion");
        }

        public Part(ResourceLocation id, ResourceLocation asset, AppearanceTransform mount,
                    Set<ResourceLocation> requiredCapabilities, RenderProfile renderProfile,
                    Map<ResourceLocation, AppearanceTransform> exits, boolean followsHostAnimation) {
            this(id, asset, mount, requiredCapabilities, renderProfile, exits, followsHostAnimation, Optional.empty());
        }

        public Part(ResourceLocation id, ResourceLocation asset, AppearanceTransform mount,
                    Set<ResourceLocation> requiredCapabilities, RenderProfile renderProfile,
                    Map<ResourceLocation, AppearanceTransform> exits) {
            this(id, asset, mount, requiredCapabilities, renderProfile, exits, true, Optional.empty());
        }

        public Part(ResourceLocation id, ResourceLocation asset, AppearanceTransform mount,
                    Set<ResourceLocation> requiredCapabilities, RenderProfile renderProfile) {
            this(id, asset, mount, requiredCapabilities, renderProfile, Map.of());
        }

        public Part(ResourceLocation id, ResourceLocation asset, AppearanceTransform mount,
                    Set<ResourceLocation> requiredCapabilities) {
            this(id, asset, mount, requiredCapabilities, RenderProfile.DEFAULT);
        }
    }
}
