package dev.equipmentstructure.api.client.appearance;

import dev.equipmentstructure.api.appearance.AppearanceVector;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable subset of a Blockbench {@code java_block} export used by model adapters. */
@OnlyIn(Dist.CLIENT)
public record BlockbenchModelDefinition(
        String name,
        int textureWidth,
        int textureHeight,
        List<Texture> textures,
        List<Element> elements,
        Optional<ResourceLocation> modelResource
) {
    public BlockbenchModelDefinition {
        Objects.requireNonNull(name, "name");
        if (textureWidth <= 0 || textureHeight <= 0) {
            throw new IllegalArgumentException("Blockbench texture resolution must be positive");
        }
        textures = List.copyOf(textures);
        elements = List.copyOf(elements);
        Objects.requireNonNull(modelResource, "modelResource");
    }

    public BlockbenchModelDefinition(String name, int textureWidth, int textureHeight,
                                    List<Element> elements) {
        this(name, textureWidth, textureHeight, List.of(), elements, Optional.empty());
    }

    public BlockbenchModelDefinition(String name, int textureWidth, int textureHeight,
                                     List<Texture> textures, List<Element> elements) {
        this(name, textureWidth, textureHeight, textures, elements, Optional.empty());
    }

    /** Texture metadata only; decoding image bytes is owned by the client renderer. */
    public record Texture(String name, Optional<String> path, boolean embedded) {
        public Texture {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(path, "path");
        }
    }

    /** Coordinates are the original Blockbench pixel coordinates. */
    public record Element(String name, AppearanceVector from, AppearanceVector to,
                          Map<String, Face> faces, Optional<Rotation> rotation) {
        public Element {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
            faces = Map.copyOf(faces);
            Objects.requireNonNull(rotation, "rotation");
            if (!(to.x() > from.x() && to.y() > from.y() && to.z() > from.z())) {
                throw new IllegalArgumentException("Blockbench element must have positive dimensions: " + name);
            }
        }

        public Element(String name, AppearanceVector from, AppearanceVector to) {
            this(name, from, to, Map.of(), Optional.empty());
        }

        public Element(String name, AppearanceVector from, AppearanceVector to,
                       Map<String, Face> faces) {
            this(name, from, to, faces, Optional.empty());
        }
    }

    /** Optional Blockbench element rotation around a model-space origin. */
    public record Rotation(String axis, float angle, AppearanceVector origin) {
        public Rotation {
            Objects.requireNonNull(axis, "axis");
            Objects.requireNonNull(origin, "origin");
            if (!(axis.equals("x") || axis.equals("y") || axis.equals("z"))) {
                throw new IllegalArgumentException("Blockbench rotation axis must be x, y, or z");
            }
            if (!Float.isFinite(angle) || Math.abs(angle) > 360.0F) {
                throw new IllegalArgumentException("Blockbench rotation angle is outside the supported range");
            }
        }
    }

    /** One Blockbench face UV rectangle and its texture-array index. */
    public record Face(float u0, float v0, float u1, float v1, int textureIndex) {
        public Face {
            if (!Float.isFinite(u0) || !Float.isFinite(v0) || !Float.isFinite(u1) || !Float.isFinite(v1)) {
                throw new IllegalArgumentException("Blockbench UV coordinates must be finite");
            }
            if (textureIndex < -1) throw new IllegalArgumentException("Invalid Blockbench texture index");
        }
    }
}
