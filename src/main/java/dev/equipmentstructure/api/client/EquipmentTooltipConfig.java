package dev.equipmentstructure.api.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client preferences do not affect stored equipment or server rules. */
public final class EquipmentTooltipConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue ENABLED;
    private static final ModConfigSpec.BooleanValue OPEN_KEY_HINT;
    private static final ModConfigSpec.BooleanValue EDITOR_GUIDES;
    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("tooltip");
        ENABLED = builder.comment("Show the API-owned tooltip section. Other mods' tooltips are untouched.").define("enabled", true);
        OPEN_KEY_HINT = builder.comment("Show the configured assembly key when the server supports the API.").define("showOpenKey", true);
        builder.pop();
        builder.push("editor");
        EDITOR_GUIDES = builder.comment("Show and enable the editor axis handles, orbit trajectories and author anchor marker.")
                .define("showGuides", true);
        builder.pop();
        SPEC = builder.build();
    }
    private EquipmentTooltipConfig() {}
    public static boolean enabled() { return !SPEC.isLoaded() || ENABLED.get(); }
    public static boolean showOpenKey() { return !SPEC.isLoaded() || OPEN_KEY_HINT.get(); }
    public static boolean showEditorGuides() { return !SPEC.isLoaded() || EDITOR_GUIDES.get(); }
}
