package dev.equipmentstructure.api.ui;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import java.util.Optional;

/** Client artwork and identity color only. Coordinates are in the unrotated footprint, measured in cells. */
public record GridComponentDisplay(Optional<Texture> texture, Optional<Box> box, double scale, boolean rotateWithPart,
                                   Optional<Integer> color) {
    private static final Codec<Integer> COLOR = Codec.STRING.comapFlatMap(value -> value.matches("#[0-9a-fA-F]{6}")
            ? DataResult.success(Integer.parseInt(value.substring(1), 16))
            : DataResult.error(() -> "color must be #RRGGBB"), value -> String.format(java.util.Locale.ROOT, "#%06X", value));
    private static final Codec<Double> SCALE = Codec.DOUBLE.validate(value -> Double.isFinite(value) && value > 0 && value <= 1
            ? DataResult.success(value) : DataResult.error(() -> "scale must be finite and in (0, 1]"));
    public static final Codec<GridComponentDisplay> CODEC = RecordCodecBuilder.create(i -> i.group(
            Texture.CODEC.optionalFieldOf("texture").forGetter(GridComponentDisplay::texture),
            Box.CODEC.optionalFieldOf("box").forGetter(GridComponentDisplay::box),
            SCALE.optionalFieldOf("scale", 1.0).forGetter(GridComponentDisplay::scale),
            Codec.BOOL.optionalFieldOf("rotate_with_part", false).forGetter(GridComponentDisplay::rotateWithPart),
            COLOR.optionalFieldOf("color").forGetter(GridComponentDisplay::color)
    ).apply(i, GridComponentDisplay::new));

    public GridComponentDisplay {
        java.util.Objects.requireNonNull(texture); java.util.Objects.requireNonNull(box); java.util.Objects.requireNonNull(color);
        if (!Double.isFinite(scale) || scale <= 0 || scale > 1) throw new IllegalArgumentException("Invalid artwork scale");
        if (color.filter(value -> value < 0 || value > 0xFFFFFF).isPresent()) throw new IllegalArgumentException("Color must be RGB");
    }
    public GridComponentDisplay(Optional<Texture> texture, Optional<Box> box, double scale, boolean rotateWithPart) {
        this(texture, box, scale, rotateWithPart, Optional.empty());
    }
    public static GridComponentDisplay defaults() { return new GridComponentDisplay(Optional.empty(), Optional.empty(), 1, false); }
    public GridComponentDisplay withColor(int rgb) { return new GridComponentDisplay(texture, box, scale, rotateWithPart, Optional.of(rgb)); }
    public GridComponentDisplay withTexture(Texture value) { return new GridComponentDisplay(Optional.of(value), box, scale, rotateWithPart, color); }
    public GridComponentDisplay withBox(Box value) { return new GridComponentDisplay(texture, Optional.of(value), scale, rotateWithPart, color); }
    public GridComponentDisplay withScale(double value) { return new GridComponentDisplay(texture, box, value, rotateWithPart, color); }
    public GridComponentDisplay withRotation(boolean value) { return new GridComponentDisplay(texture, box, scale, value, color); }

    /** Stable across restarts and registration order. Authors may override colors that are too similar. */
    public int resolvedColor(ResourceLocation componentId) {
        return color.orElseGet(() -> {
            int hash = componentId.toString().hashCode();
            double hue = Math.floorMod(hash, 360) / 60.0;
            double saturation = .48 + Math.floorMod(hash >>> 9, 20) / 100.0;
            double value = .82;
            double c = value * saturation, x = c * (1 - Math.abs(hue % 2 - 1)), m = value - c;
            double[] rgb = switch ((int) hue) {
                case 0 -> new double[]{c, x, 0}; case 1 -> new double[]{x, c, 0};
                case 2 -> new double[]{0, c, x}; case 3 -> new double[]{0, x, c};
                case 4 -> new double[]{x, 0, c}; default -> new double[]{c, 0, x};
            };
            return (int) Math.round((rgb[0] + m) * 255) << 16 | (int) Math.round((rgb[1] + m) * 255) << 8
                    | (int) Math.round((rgb[2] + m) * 255);
        });
    }

    public record Texture(ResourceLocation resource, int width, int height) {
        private static final Codec<ResourceLocation> PNG = ResourceLocation.CODEC.validate(id -> validPath(id)
                ? DataResult.success(id) : DataResult.error(() -> "Expected a textures/...png resource without traversal"));
        public static final Codec<Texture> CODEC = RecordCodecBuilder.create(i -> i.group(
                PNG.fieldOf("resource").forGetter(Texture::resource),
                Codec.intRange(1, 4096).fieldOf("width").forGetter(Texture::width),
                Codec.intRange(1, 4096).fieldOf("height").forGetter(Texture::height)
        ).apply(i, Texture::new));
        public Texture {
            if (!validPath(resource) || width < 1 || height < 1 || width > 4096 || height > 4096)
                throw new IllegalArgumentException("Invalid grid artwork texture");
        }
        private static boolean validPath(ResourceLocation id) {
            return id != null && id.getPath().startsWith("textures/") && id.getPath().endsWith(".png")
                    && !id.getPath().contains("..") && !id.getPath().contains("//");
        }
    }

    /** The image is centered and contained in this box. Invalid boxes for a footprint fall back to automatic placement. */
    public record Box(double x, double y, double width, double height) {
        private static final Codec<Double> COORD = Codec.DOUBLE.validate(v -> Double.isFinite(v) && v >= 0 && v <= 64
                ? DataResult.success(v) : DataResult.error(() -> "Box coordinates must be finite and in [0, 64]"));
        private static final Codec<Double> SIZE = Codec.DOUBLE.validate(v -> Double.isFinite(v) && v > 0 && v <= 64
                ? DataResult.success(v) : DataResult.error(() -> "Box dimensions must be finite and in (0, 64]"));
        public static final Codec<Box> CODEC = RecordCodecBuilder.create(i -> i.group(
                COORD.fieldOf("x").forGetter(Box::x), COORD.fieldOf("y").forGetter(Box::y),
                SIZE.fieldOf("width").forGetter(Box::width), SIZE.fieldOf("height").forGetter(Box::height)
        ).apply(i, Box::new));
        public Box {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(width) || !Double.isFinite(height)
                    || x < 0 || y < 0 || x > 64 || y > 64 || width <= 0 || height <= 0 || width > 64 || height > 64)
                throw new IllegalArgumentException("Invalid grid artwork box");
        }
    }
}
