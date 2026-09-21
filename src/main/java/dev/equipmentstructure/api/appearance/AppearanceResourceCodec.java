package dev.equipmentstructure.api.appearance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Versioned, client-resource data contracts. This class only decodes data; it does not access files,
 * create GPU objects, register renderers, or mutate an item. Unknown JSON fields remain forward-compatible.
 */
public final class AppearanceResourceCodec {
    public static final int FORMAT_VERSION = 3;
    private static final Codec<Integer> FORMAT = Codec.INT.validate(value -> value == FORMAT_VERSION
            ? DataResult.success(value)
            : DataResult.error(() -> "Unsupported appearance format_version: " + value));
    private static final Codec<AppearanceVector> VECTOR = Codec.DOUBLE.listOf().comapFlatMap(values -> {
        if (values.size() != 3) return DataResult.error(() -> "A position must contain exactly 3 numbers");
        try {
            return DataResult.success(new AppearanceVector(values.get(0), values.get(1), values.get(2)));
        } catch (IllegalArgumentException error) {
            return DataResult.error(error::getMessage);
        }
    }, value -> List.of(value.x(), value.y(), value.z()));
    private static final Codec<AppearanceRotation> ROTATION = Codec.DOUBLE.listOf().comapFlatMap(values -> {
        if (values.size() != 4) return DataResult.error(() -> "A rotation must contain exactly 4 numbers");
        try {
            return DataResult.success(new AppearanceRotation(values.get(0), values.get(1), values.get(2), values.get(3)));
        } catch (IllegalArgumentException error) {
            return DataResult.error(error::getMessage);
        }
    }, value -> List.of(value.x(), value.y(), value.z(), value.w()));
    private static final Codec<AppearanceTransform> TRANSFORM = RecordCodecBuilder.create(instance -> instance.group(
            VECTOR.fieldOf("position").forGetter(AppearanceTransform::position),
            ROTATION.fieldOf("rotation").forGetter(AppearanceTransform::rotation)
    ).apply(instance, AppearanceTransform::new));
    private record StoredBounds(AppearanceVector min, AppearanceVector max, double minScale, double maxScale) {}
    private static final Codec<AppearanceBounds> BOUNDS = RecordCodecBuilder.<StoredBounds>create(instance -> instance.group(
            VECTOR.fieldOf("min").forGetter(StoredBounds::min),
            VECTOR.fieldOf("max").forGetter(StoredBounds::max),
            Codec.DOUBLE.optionalFieldOf("min_scale", 0.05D).forGetter(StoredBounds::minScale),
            Codec.DOUBLE.optionalFieldOf("max_scale", 2.0D).forGetter(StoredBounds::maxScale)
    ).apply(instance, StoredBounds::new)).comapFlatMap(value -> {
        try {
            return DataResult.success(new AppearanceBounds(value.min(), value.max(), value.minScale(), value.maxScale()));
        } catch (IllegalArgumentException error) {
            return DataResult.error(() -> "Invalid appearance bounds: " + error.getMessage());
        }
    }, value -> new StoredBounds(value.min(), value.max(), value.minScale(), value.maxScale()));
    private static final Codec<AppearanceDefinitions.RenderMode> RENDER_MODE = Codec.STRING.comapFlatMap(value -> {
        try {
            return DataResult.success(AppearanceDefinitions.RenderMode.valueOf(value.toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException error) {
            return DataResult.error(() -> "Unknown appearance render mode: " + value);
        }
    }, value -> value.name().toLowerCase(java.util.Locale.ROOT));
    private static final Codec<AppearanceDefinitions.RenderLayer> RENDER_LAYER = Codec.STRING.comapFlatMap(value -> {
        try {
            return DataResult.success(AppearanceDefinitions.RenderLayer.valueOf(value.toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException error) {
            return DataResult.error(() -> "Unknown appearance render layer: " + value);
        }
    }, value -> value.name().toLowerCase(java.util.Locale.ROOT));
    private static final Codec<AppearanceDefinitions.PlacementFreedom> PLACEMENT_FREEDOM = Codec.STRING.comapFlatMap(value -> {
        try {
            return DataResult.success(AppearanceDefinitions.PlacementFreedom.valueOf(value.toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException error) {
            return DataResult.error(() -> "Unknown placement freedom: " + value);
        }
    }, value -> value.name().toLowerCase(java.util.Locale.ROOT));
    private static final Codec<Float> DEPTH_BIAS = Codec.FLOAT.comapFlatMap(value ->
            Float.isFinite(value) && Math.abs(value) <= 16.0F
                    ? DataResult.success(value)
                    : DataResult.error(() -> "Appearance depth_bias must be finite and between -16 and 16"),
            value -> value);
    private static final Codec<AppearanceDefinitions.RenderProfile> RENDER_PROFILE = RecordCodecBuilder.create(instance -> instance.group(
            RENDER_MODE.optionalFieldOf("mode", AppearanceDefinitions.RenderMode.GEOMETRY)
                    .forGetter(value -> value.mode()),
            RENDER_LAYER.optionalFieldOf("layer", AppearanceDefinitions.RenderLayer.STRUCTURE)
                    .forGetter(value -> value.layer()),
            Codec.intRange(-1024, 1024).optionalFieldOf("order", 0).forGetter(value -> value.order()),
            DEPTH_BIAS.optionalFieldOf("depth_bias", 0.0F).forGetter(value -> value.depthBias())
    ).apply(instance, AppearanceDefinitions.RenderProfile::new));
    private static final Codec<AppearanceOrbitMotion.Direction> ORBIT_DIRECTION = Codec.STRING.comapFlatMap(value -> {
        try {
            return DataResult.success(AppearanceOrbitMotion.Direction.valueOf(value.toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException error) {
            return DataResult.error(() -> "Unknown appearance orbit direction: " + value);
        }
    }, value -> value.name().toLowerCase(java.util.Locale.ROOT));
    private record StoredOrbitMotion(String type, AppearanceVector pivot, AppearanceVector axis,
                                     double speed, AppearanceOrbitMotion.Direction direction,
                                     double phase, boolean rotateWithOrbit,
                                     AppearanceVector selfAxis, double selfSpeed,
                                     AppearanceOrbitMotion.Direction selfDirection, double selfPhase,
                                     String pivotSpace) {}
    private static final Codec<AppearanceOrbitMotion> ORBIT_MOTION = RecordCodecBuilder.<StoredOrbitMotion>create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(StoredOrbitMotion::type),
            VECTOR.optionalFieldOf("pivot", AppearanceVector.ZERO).forGetter(StoredOrbitMotion::pivot),
            VECTOR.optionalFieldOf("axis", new AppearanceVector(0, 1, 0)).forGetter(StoredOrbitMotion::axis),
            Codec.DOUBLE.optionalFieldOf("speed_degrees_per_second", 0.0D).forGetter(StoredOrbitMotion::speed),
            ORBIT_DIRECTION.optionalFieldOf("direction", AppearanceOrbitMotion.Direction.POSITIVE)
                    .forGetter(StoredOrbitMotion::direction),
            Codec.DOUBLE.optionalFieldOf("phase_degrees", 0.0D).forGetter(StoredOrbitMotion::phase),
            Codec.BOOL.optionalFieldOf("rotate_with_orbit", true).forGetter(StoredOrbitMotion::rotateWithOrbit),
            VECTOR.optionalFieldOf("self_axis", new AppearanceVector(0, 1, 0)).forGetter(StoredOrbitMotion::selfAxis),
            Codec.DOUBLE.optionalFieldOf("self_speed_degrees_per_second", 0.0D).forGetter(StoredOrbitMotion::selfSpeed),
            ORBIT_DIRECTION.optionalFieldOf("self_direction", AppearanceOrbitMotion.Direction.POSITIVE)
                    .forGetter(StoredOrbitMotion::selfDirection),
            Codec.DOUBLE.optionalFieldOf("self_phase_degrees", 0.0D).forGetter(StoredOrbitMotion::selfPhase),
            Codec.STRING.optionalFieldOf("pivot_space", "host").forGetter(StoredOrbitMotion::pivotSpace)
    ).apply(instance, StoredOrbitMotion::new)).comapFlatMap(value -> {
        if (!"orbit".equals(value.type())) return DataResult.error(() -> "Unknown appearance motion type: " + value.type());
        try {
            return DataResult.success(AppearanceOrbitMotion.of(value.pivot(), value.axis(), value.speed(),
                    value.direction(), value.phase(), value.rotateWithOrbit(), value.selfAxis(), value.selfSpeed(),
                    value.selfDirection(), value.selfPhase()).withPivotSpace(
                    AppearanceOrbitMotion.PivotSpace.valueOf(value.pivotSpace().toUpperCase(java.util.Locale.ROOT))));
        } catch (IllegalArgumentException error) {
            return DataResult.error(() -> "Invalid appearance orbit: " + error.getMessage());
        }
    }, value -> new StoredOrbitMotion("orbit", value.pivot(), value.axis(), value.speedDegreesPerSecond(),
            value.direction(), value.phaseDegrees(), value.rotateWithOrbit(), value.selfAxis(),
            value.selfSpeedDegreesPerSecond(), value.selfDirection(), value.selfPhaseDegrees(),
            value.pivotSpace().name().toLowerCase(java.util.Locale.ROOT)));
    private static final Codec<Set<ResourceLocation>> ID_SET = Codec.list(ResourceLocation.CODEC).comapFlatMap(values -> {
        Set<ResourceLocation> unique = new HashSet<>(values);
        return unique.size() == values.size()
                ? DataResult.success(Set.copyOf(unique))
                : DataResult.error(() -> "A resource ID list must not contain duplicates");
    }, value -> {
        List<ResourceLocation> sorted = new ArrayList<>(value);
        sorted.sort(Comparator.comparing(ResourceLocation::toString));
        return sorted;
    });
    private static final Codec<AppearanceDefinitions.ComponentExit> COMPONENT_EXIT = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("slot").forGetter(AppearanceDefinitions.ComponentExit::slot),
            ResourceLocation.CODEC.fieldOf("exit").forGetter(AppearanceDefinitions.ComponentExit::exit)
    ).apply(instance, AppearanceDefinitions.ComponentExit::new));
    private record StoredPort(Optional<ResourceLocation> parent, AppearanceTransform frame,
                              Optional<AppearanceDefinitions.ComponentExit> componentExit) {}
    private static final Codec<AppearanceDefinitions.Port> PORT = RecordCodecBuilder.<StoredPort>create(instance -> instance.group(
            ResourceLocation.CODEC.optionalFieldOf("parent").forGetter(StoredPort::parent),
            TRANSFORM.optionalFieldOf("frame", AppearanceTransform.IDENTITY).forGetter(StoredPort::frame),
            COMPONENT_EXIT.optionalFieldOf("component_exit").forGetter(StoredPort::componentExit)
    ).apply(instance, StoredPort::new)).comapFlatMap(value -> {
        if (value.parent().isPresent() && value.componentExit().isPresent()) {
            return DataResult.error(() -> "A port cannot have both parent and component_exit");
        }
        return DataResult.success(new AppearanceDefinitions.Port(value.parent(), value.frame(), value.componentExit()));
    }, value -> new StoredPort(value.parent(), value.localFrame(), value.componentExit()));
    private static final Codec<AppearanceDefinitions.Joint> JOINT = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("owner").forGetter(AppearanceDefinitions.Joint::owner),
            ResourceLocation.CODEC.fieldOf("strategy").forGetter(AppearanceDefinitions.Joint::strategy),
            TRANSFORM.optionalFieldOf("offset", AppearanceTransform.IDENTITY).forGetter(AppearanceDefinitions.Joint::offset)
    ).apply(instance, AppearanceDefinitions.Joint::new));
    private record StoredBinding(Optional<ResourceLocation> port, AppearanceDefinitions.Joint joint,
                                 Set<ResourceLocation> replaces, AppearanceDefinitions.PlacementFreedom placementFreedom,
                                 boolean followsHostAnimation, Optional<AppearanceBounds> bounds) {}
    private static final Codec<AppearanceDefinitions.Binding> BINDING = RecordCodecBuilder.<StoredBinding>create(instance -> instance.group(
            ResourceLocation.CODEC.optionalFieldOf("port").forGetter(StoredBinding::port),
            JOINT.fieldOf("joint").forGetter(StoredBinding::joint),
            ID_SET.optionalFieldOf("replaces", Set.of()).forGetter(StoredBinding::replaces),
            PLACEMENT_FREEDOM.optionalFieldOf("placement", AppearanceDefinitions.PlacementFreedom.ADJUSTABLE)
                    .forGetter(StoredBinding::placementFreedom),
            Codec.BOOL.optionalFieldOf("follow_host_animation", true).forGetter(StoredBinding::followsHostAnimation),
            BOUNDS.optionalFieldOf("bounds").forGetter(StoredBinding::bounds)
    ).apply(instance, StoredBinding::new)).comapFlatMap(value -> DataResult.success(new AppearanceDefinitions.Binding(
            value.port().orElse(null), value.joint(), value.replaces(), value.placementFreedom(), value.followsHostAnimation(),
            value.bounds())), value ->
            new StoredBinding(Optional.ofNullable(value.port()), value.joint(), value.replaces(), value.placementFreedom(),
                    value.followsHostAnimation(), value.bounds()));

    public static final Codec<HostResource> HOST = RecordCodecBuilder.<HostResource>create(instance -> instance.group(
            FORMAT.fieldOf("format_version").forGetter(HostResource::formatVersion),
            ResourceLocation.CODEC.fieldOf("id").forGetter(HostResource::id),
            Codec.unboundedMap(ResourceLocation.CODEC, PORT).optionalFieldOf("ports", Map.of()).forGetter(HostResource::ports),
            Codec.unboundedMap(ResourceLocation.CODEC, BINDING).optionalFieldOf("bindings", Map.of()).forGetter(HostResource::bindings),
            ID_SET.optionalFieldOf("replaceable_elements", Set.of()).forGetter(HostResource::replaceableElements)
    ).apply(instance, HostResource::new));
    public static final Codec<ComponentResource> COMPONENT = RecordCodecBuilder.<ComponentResource>create(instance -> instance.group(
            FORMAT.fieldOf("format_version").forGetter(ComponentResource::formatVersion),
            ResourceLocation.CODEC.fieldOf("id").forGetter(ComponentResource::id),
            ResourceLocation.CODEC.fieldOf("asset").forGetter(ComponentResource::asset),
            TRANSFORM.optionalFieldOf("mount", AppearanceTransform.IDENTITY).forGetter(ComponentResource::mount),
            ID_SET.optionalFieldOf("required_capabilities", Set.of()).forGetter(ComponentResource::requiredCapabilities),
            RENDER_PROFILE.optionalFieldOf("render", AppearanceDefinitions.RenderProfile.DEFAULT)
                    .forGetter(ComponentResource::renderProfile),
            Codec.unboundedMap(ResourceLocation.CODEC, TRANSFORM).optionalFieldOf("exits", Map.of())
                    .forGetter(ComponentResource::exits),
            Codec.BOOL.optionalFieldOf("follow_host_animation", true)
                    .forGetter(ComponentResource::followsHostAnimation),
            ORBIT_MOTION.optionalFieldOf("motion").forGetter(ComponentResource::motion)
    ).apply(instance, ComponentResource::new));

    private AppearanceResourceCodec() {}

    public record HostResource(int formatVersion, ResourceLocation id,
                               Map<ResourceLocation, AppearanceDefinitions.Port> ports,
                               Map<ResourceLocation, AppearanceDefinitions.Binding> bindings,
                               Set<ResourceLocation> replaceableElements) {
        public HostResource {
            Objects.requireNonNull(id, "id");
            ports = Map.copyOf(ports);
            bindings = Map.copyOf(bindings);
            replaceableElements = Set.copyOf(replaceableElements);
        }

        AppearanceDefinitions.Host toDefinition() {
            return new AppearanceDefinitions.Host(id, ports, bindings, replaceableElements);
        }
    }

    public record ComponentResource(int formatVersion, ResourceLocation id, ResourceLocation asset,
                                    AppearanceTransform mount, Set<ResourceLocation> requiredCapabilities,
                                    AppearanceDefinitions.RenderProfile renderProfile,
                                    Map<ResourceLocation, AppearanceTransform> exits,
                                    boolean followsHostAnimation, Optional<AppearanceOrbitMotion> motion) {
        public ComponentResource {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(asset, "asset");
            Objects.requireNonNull(mount, "mount");
            requiredCapabilities = Set.copyOf(requiredCapabilities);
            Objects.requireNonNull(renderProfile, "renderProfile");
            exits = Map.copyOf(exits);
            Objects.requireNonNull(motion, "motion");
        }

        public ComponentResource(int formatVersion, ResourceLocation id, ResourceLocation asset,
                                 AppearanceTransform mount, Set<ResourceLocation> requiredCapabilities,
                                 AppearanceDefinitions.RenderProfile renderProfile,
                                 Map<ResourceLocation, AppearanceTransform> exits,
                                 boolean followsHostAnimation) {
            this(formatVersion, id, asset, mount, requiredCapabilities, renderProfile, exits,
                    followsHostAnimation, Optional.empty());
        }

        public ComponentResource(int formatVersion, ResourceLocation id, ResourceLocation asset,
                                 AppearanceTransform mount, Set<ResourceLocation> requiredCapabilities,
                                 AppearanceDefinitions.RenderProfile renderProfile) {
            this(formatVersion, id, asset, mount, requiredCapabilities, renderProfile, Map.of(), true, Optional.empty());
        }

        /** Compatibility constructor for callers that already supplied exits. */
        public ComponentResource(int formatVersion, ResourceLocation id, ResourceLocation asset,
                                 AppearanceTransform mount, Set<ResourceLocation> requiredCapabilities,
                                 AppearanceDefinitions.RenderProfile renderProfile,
                                 Map<ResourceLocation, AppearanceTransform> exits) {
            this(formatVersion, id, asset, mount, requiredCapabilities, renderProfile, exits, true, Optional.empty());
        }

        public ComponentResource(int formatVersion, ResourceLocation id, ResourceLocation asset,
                                 AppearanceTransform mount, Set<ResourceLocation> requiredCapabilities) {
            this(formatVersion, id, asset, mount, requiredCapabilities,
                    AppearanceDefinitions.RenderProfile.DEFAULT, Map.of(), true, Optional.empty());
        }

        AppearanceDefinitions.Part toDefinition() {
            return new AppearanceDefinitions.Part(id, asset, mount, requiredCapabilities, renderProfile, exits,
                    followsHostAnimation, motion);
        }
    }
}
