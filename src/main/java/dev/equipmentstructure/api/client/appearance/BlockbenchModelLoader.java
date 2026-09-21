package dev.equipmentstructure.api.client.appearance;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.equipmentstructure.api.appearance.AppearanceVector;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Safe reader for the portable subset exported by Blockbench's {@code java_block} format.
 * It only decodes geometry metadata; GPU buffers and textures remain the responsibility of
 * the content mod's renderer.
 */
@OnlyIn(Dist.CLIENT)
public final class BlockbenchModelLoader {
    private static final double COORDINATE_LIMIT = 4096.0;

    private BlockbenchModelLoader() {}

    /** Loads one resource from a resource manager without logging or mutating global state. */
    public static Optional<BlockbenchModelDefinition> load(ResourceManager manager, ResourceLocation resource) {
        Objects.requireNonNull(manager, "manager");
        Objects.requireNonNull(resource, "resource");
        try {
            return manager.getResource(resource)
                    .flatMap(value -> {
                        try (var reader = value.openAsReader()) {
                            return decode(resource, JsonParser.parseReader(reader));
                        } catch (IOException | JsonParseException | IllegalArgumentException ignored) {
                            return Optional.empty();
                        }
                    });
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    /** Decodes and validates one Blockbench JSON object. */
    public static Optional<BlockbenchModelDefinition> decode(ResourceLocation resource, JsonElement json) {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(json, "json");
        try {
            if (!json.isJsonObject()) return Optional.empty();
            JsonObject root = json.getAsJsonObject();
            if (root.has("meta") && root.get("meta").isJsonObject()) {
                return "java_block".equals(string(root.getAsJsonObject("meta"), "model_format"))
                        ? decodeProject(resource, root) : Optional.empty();
            }
            return decodeJavaModel(resource, root);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<BlockbenchModelDefinition> decodeProject(ResourceLocation resource, JsonObject root) {
        try {
            JsonObject resolution = object(root, "resolution");
            int width = positiveInt(resolution, "width");
            int height = positiveInt(resolution, "height");
            List<BlockbenchModelDefinition.Texture> textures = projectTextures(root);
            JsonArray rawElements = array(root, "elements");
            List<BlockbenchModelDefinition.Element> elements = new ArrayList<>(rawElements.size());
            for (int index = 0; index < rawElements.size(); index++) {
                JsonObject element = rawElements.get(index).getAsJsonObject();
                String name = element.has("name") ? element.get("name").getAsString() : "element_" + index;
                AppearanceVector from = vector(element, "from");
                AppearanceVector to = vector(element, "to");
                elements.add(new BlockbenchModelDefinition.Element(name, from, to, faces(element, java.util.Map.of()), rotation(element)));
            }
            String name = root.has("name") ? root.get("name").getAsString() : resource.toString();
            return Optional.of(new BlockbenchModelDefinition(name, width, height, textures, elements,
                    Optional.of(resource)));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<BlockbenchModelDefinition> decodeJavaModel(ResourceLocation resource, JsonObject root) {
        try {
            if (!root.has("elements") || !root.get("elements").isJsonArray()) return Optional.empty();
            int width = 16;
            int height = 16;
            if (root.has("texture_size")) {
                JsonArray textureSize = root.getAsJsonArray("texture_size");
                if (textureSize.size() != 2) throw new IllegalArgumentException("Texture size must contain two values");
                width = positiveInt(textureSize.get(0));
                height = positiveInt(textureSize.get(1));
            }
            TextureTable textures = javaTextures(root);
            JsonArray rawElements = root.getAsJsonArray("elements");
            List<BlockbenchModelDefinition.Element> elements = new ArrayList<>(rawElements.size());
            for (int index = 0; index < rawElements.size(); index++) {
                JsonObject element = rawElements.get(index).getAsJsonObject();
                String name = element.has("name") ? element.get("name").getAsString() : "element_" + index;
                elements.add(new BlockbenchModelDefinition.Element(name, vector(element, "from"), vector(element, "to"),
                        javaFaces(element, textures.indices(), width, height), rotation(element)));
            }
            return Optional.of(new BlockbenchModelDefinition(resource.toString(), width, height, textures.entries(),
                    elements, Optional.of(resource)));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    /**
     * Resolves a Blockbench texture declaration to a Minecraft texture resource.
     * Relative paths use the model namespace and are rooted at {@code textures/}.
     * Embedded data is deliberately not decoded by this API.
     */
    public static Optional<ResourceLocation> resolveTexture(ResourceLocation modelResource,
                                                            BlockbenchModelDefinition.Texture texture) {
        Objects.requireNonNull(modelResource, "modelResource");
        Objects.requireNonNull(texture, "texture");
        if (texture.embedded() || texture.path().isEmpty()) return Optional.empty();
        String raw = texture.path().get().replace('\\', '/');
        if (raw.isBlank() || raw.contains("..") || raw.startsWith("/") || raw.contains("//")) {
            return Optional.empty();
        }
        try {
            ResourceLocation parsed = raw.indexOf(':') >= 0 ? ResourceLocation.tryParse(raw) : null;
            String namespace = parsed == null ? modelResource.getNamespace() : parsed.getNamespace();
            String path = parsed == null ? raw : parsed.getPath();
            if (namespace == null || namespace.isBlank() || path.isBlank()) return Optional.empty();
            if (!path.startsWith("textures/")) path = "textures/" + path;
            if (!path.endsWith(".png")) path += ".png";
            return Optional.of(ResourceLocation.fromNamespaceAndPath(namespace, path));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    /** Returns the resolved texture only when it is present in the active resource manager. */
    public static Optional<ResourceLocation> findTexture(ResourceManager manager,
                                                         ResourceLocation modelResource,
                                                         BlockbenchModelDefinition.Texture texture) {
        Objects.requireNonNull(manager, "manager");
        return resolveTexture(modelResource, texture).filter(id -> manager.getResource(id).isPresent());
    }

    /**
     * Returns whether the decoded model contains at least one face whose texture is
     * actually present in the active resource pack. A decoded JSON document with no
     * drawable face is not a prepared appearance asset; callers can then fall back to
     * the original equipment appearance instead of reporting a misleading READY plan.
     */
    public static boolean hasDrawableFace(ResourceManager manager, BlockbenchModelDefinition model) {
        Objects.requireNonNull(manager, "manager");
        Objects.requireNonNull(model, "model");
        ResourceLocation modelResource = model.modelResource().orElse(null);
        if (modelResource == null || model.textures().isEmpty()) return false;
        for (var element : model.elements()) {
            for (var face : element.faces().values()) {
                int index = face.textureIndex();
                if (index < 0 || index >= model.textures().size()) continue;
                if (findTexture(manager, modelResource, model.textures().get(index)).isPresent()) return true;
            }
        }
        return false;
    }

    private static List<BlockbenchModelDefinition.Texture> projectTextures(JsonObject root) {
        if (!root.has("textures")) return List.of();
        JsonArray raw = array(root, "textures");
        List<BlockbenchModelDefinition.Texture> result = new ArrayList<>(raw.size());
        for (int index = 0; index < raw.size(); index++) {
            JsonObject texture = raw.get(index).getAsJsonObject();
            String name = texture.has("name") ? texture.get("name").getAsString() : "texture_" + index;
            Optional<String> path = texture.has("path")
                    ? Optional.of(texture.get("path").getAsString()) : Optional.empty();
            boolean embedded = texture.has("source") && texture.get("source").isJsonPrimitive()
                    && texture.get("source").getAsString().startsWith("data:");
            result.add(new BlockbenchModelDefinition.Texture(name, path, embedded));
        }
        return List.copyOf(result);
    }

    private static TextureTable javaTextures(JsonObject root) {
        if (!root.has("textures") || !root.get("textures").isJsonObject()) return new TextureTable(List.of(), java.util.Map.of());
        var declarations = new LinkedHashMap<String, String>();
        for (var entry : root.getAsJsonObject("textures").entrySet()) {
            if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isString()) {
                declarations.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        var entries = new ArrayList<BlockbenchModelDefinition.Texture>();
        var indices = new LinkedHashMap<String, Integer>();
        for (String key : declarations.keySet()) {
            if ("particle".equals(key)) continue;
            String path = resolveJavaTexture(key, declarations, new HashSet<>());
            if (path == null || path.isBlank()) continue;
            int index = entries.size();
            entries.add(new BlockbenchModelDefinition.Texture(key, Optional.of(path), false));
            indices.put(key, index);
            indices.put("#" + key, index);
            indices.put(path, index);
        }
        return new TextureTable(List.copyOf(entries), java.util.Map.copyOf(indices));
    }

    private static String resolveJavaTexture(String key, java.util.Map<String, String> declarations, Set<String> seen) {
        if (!seen.add(key)) return null;
        String value = declarations.get(key);
        if (value == null) return null;
        if (!value.startsWith("#")) return value;
        return resolveJavaTexture(value.substring(1), declarations, seen);
    }

    /** Java exports use a 0..16 UV grid regardless of PNG resolution; projects store pixel UVs. */
    private static java.util.Map<String, BlockbenchModelDefinition.Face> javaFaces(JsonObject element,
            java.util.Map<String, Integer> textureIndices, int width, int height) {
        var result = new LinkedHashMap<String, BlockbenchModelDefinition.Face>();
        faces(element, textureIndices).forEach((direction, face) -> result.put(direction,
                new BlockbenchModelDefinition.Face(face.u0() * width / 16.0F, face.v0() * height / 16.0F,
                        face.u1() * width / 16.0F, face.v1() * height / 16.0F, face.textureIndex())));
        return java.util.Map.copyOf(result);
    }

    private static java.util.Map<String, BlockbenchModelDefinition.Face> faces(JsonObject element,
                                                                                  java.util.Map<String, Integer> textureIndices) {
        if (!element.has("faces") || !element.get("faces").isJsonObject()) return java.util.Map.of();
        JsonObject rawFaces = element.getAsJsonObject("faces");
        var result = new LinkedHashMap<String, BlockbenchModelDefinition.Face>();
        for (var entry : rawFaces.entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject face = entry.getValue().getAsJsonObject();
            JsonArray uv = face.has("uv") && face.get("uv").isJsonArray() ? face.getAsJsonArray("uv") : null;
            if (uv == null || uv.size() != 4) throw new IllegalArgumentException("Face UV must contain four values");
            int texture = -1;
            if (face.has("texture") && face.get("texture").isJsonPrimitive()) {
                var rawTexture = face.get("texture").getAsJsonPrimitive();
                texture = rawTexture.isNumber() ? rawTexture.getAsInt()
                        : textureIndices.getOrDefault(rawTexture.getAsString(), -1);
            }
            result.put(entry.getKey(), new BlockbenchModelDefinition.Face(
                    uv.get(0).getAsFloat(), uv.get(1).getAsFloat(),
                    uv.get(2).getAsFloat(), uv.get(3).getAsFloat(), texture));
        }
        return java.util.Map.copyOf(result);
    }

    private static Optional<BlockbenchModelDefinition.Rotation> rotation(JsonObject element) {
        if (!element.has("rotation")) return Optional.empty();
        if (element.get("rotation").isJsonArray()) {
            JsonArray values = element.getAsJsonArray("rotation");
            if (values.size() != 3) throw new IllegalArgumentException("Element rotation must contain three values");
            float rx = values.get(0).getAsFloat(), ry = values.get(1).getAsFloat(), rz = values.get(2).getAsFloat();
            int nonZero = (rx == 0.0F ? 0 : 1) + (ry == 0.0F ? 0 : 1) + (rz == 0.0F ? 0 : 1);
            if (nonZero == 0) return Optional.empty();
            if (nonZero != 1) throw new IllegalArgumentException("java_block element rotation must use one axis");
            String axis = rx != 0.0F ? "x" : ry != 0.0F ? "y" : "z";
            float angle = rx != 0.0F ? rx : ry != 0.0F ? ry : rz;
            AppearanceVector origin = element.has("origin") ? vector(element, "origin")
                    : new AppearanceVector(8, 8, 8);
            return Optional.of(new BlockbenchModelDefinition.Rotation(axis, angle, origin));
        }
        if (!element.get("rotation").isJsonObject()) throw new IllegalArgumentException("Element rotation must be an object or array");
        JsonObject raw = element.getAsJsonObject("rotation");
        String axis = raw.has("axis") ? raw.get("axis").getAsString().toLowerCase(java.util.Locale.ROOT) : "";
        float angle = raw.has("angle") ? raw.get("angle").getAsFloat() : Float.NaN;
        AppearanceVector origin = vector(raw, "origin");
        return Optional.of(new BlockbenchModelDefinition.Rotation(axis, angle, origin));
    }

    private static JsonObject object(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException("Missing object: " + key);
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonArray()) throw new IllegalArgumentException("Missing array: " + key);
        return value.getAsJsonArray();
    }

    private static String string(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Missing string: " + key);
        }
        return value.getAsString();
    }

    private static int positiveInt(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonPrimitive()) throw new IllegalArgumentException("Missing integer: " + key);
        int result = value.getAsInt();
        if (result <= 0 || result > 4096) throw new IllegalArgumentException("Invalid resolution: " + key);
        return result;
    }

    private static int positiveInt(JsonElement value) {
        if (value == null || !value.isJsonPrimitive()) throw new IllegalArgumentException("Missing integer");
        int result = value.getAsInt();
        if (result <= 0 || result > 4096) throw new IllegalArgumentException("Invalid resolution");
        return result;
    }

    private static AppearanceVector vector(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().size() != 3) {
            throw new IllegalArgumentException("Vector must contain three values: " + key);
        }
        double x = value.getAsJsonArray().get(0).getAsDouble();
        double y = value.getAsJsonArray().get(1).getAsDouble();
        double z = value.getAsJsonArray().get(2).getAsDouble();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x) > COORDINATE_LIMIT || Math.abs(y) > COORDINATE_LIMIT
                || Math.abs(z) > COORDINATE_LIMIT) {
            throw new IllegalArgumentException("Blockbench coordinate is outside the supported range");
        }
        return new AppearanceVector(x, y, z);
    }

    private record TextureTable(List<BlockbenchModelDefinition.Texture> entries,
                                java.util.Map<String, Integer> indices) {}
}
