package dev.equipmentstructure.api;

import dev.equipmentstructure.api.appearance.AppearancePartPresentation;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-owned player interaction rules, synchronized by NeoForge. Existing data is retained. */
public final class EquipmentFeatureConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue QUICK_INSTALL, POSITION_EDITING, HIDE_ORIGINAL, HIDE_ATTACHMENTS;
    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("assembly");
        QUICK_INSTALL = builder.comment("Allow Shift-click to install one component into a compatible empty attachment position.")
                .define("quickInstall", true);
        builder.pop().push("appearance");
        POSITION_EDITING = builder.comment("Allow players to save position, rotation and scale edits. Existing poses remain intact.")
                .define("positionEditing", true);
        HIDE_ORIGINAL = builder.comment("Allow players to hide the original equipment model. Showing a hidden model remains allowed.")
                .define("hideOriginal", true);
        HIDE_ATTACHMENTS = builder.comment("Allow players to hide attachments. Showing a hidden attachment remains allowed.")
                .define("hideAttachments", true);
        builder.pop();
        SPEC = builder.build();
    }
    private EquipmentFeatureConfig() {}
    public static boolean quickInstall() { return !SPEC.isLoaded() || QUICK_INSTALL.get(); }
    public static Rules rules() {
        return new Rules(!SPEC.isLoaded() || POSITION_EDITING.get(), !SPEC.isLoaded() || HIDE_ORIGINAL.get(),
                !SPEC.isLoaded() || HIDE_ATTACHMENTS.get());
    }

    public record Rules(boolean positionEditing, boolean hideOriginal, boolean hideAttachments) {
        public boolean allowsOriginal(boolean current, boolean next) {
            return hideOriginal || next || current == next;
        }
        public boolean allowsPart(AppearancePartPresentation current, AppearancePartPresentation next) {
            return (positionEditing || current.pose().equals(next.pose()) && current.motion().equals(next.motion()))
                    && (hideAttachments || next.visible() || current.visible() == next.visible());
        }
    }
}
