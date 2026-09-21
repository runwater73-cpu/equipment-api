package dev.equipmentstructure.api.client;

import dev.equipmentstructure.api.EquipmentStructureApiMod;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** Configurable key binding shared by all mods using Equipment Structure API. */
@EventBusSubscriber(
        modid = EquipmentStructureApiMod.MOD_ID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD
)
public final class EquipmentAssemblyKeyMappings {
    private static final net.neoforged.neoforge.client.settings.IKeyConflictContext EDITOR =
            new net.neoforged.neoforge.client.settings.IKeyConflictContext() {
                @Override public boolean isActive() {
                    return net.minecraft.client.Minecraft.getInstance().screen instanceof EquipmentAppearancePlacementScreen;
                }
                @Override public boolean conflicts(net.neoforged.neoforge.client.settings.IKeyConflictContext other) {
                    return other == this || other == net.neoforged.neoforge.client.settings.KeyConflictContext.GUI;
                }
            };
    public static final KeyMapping RESET_PART = editorKey("reset_part", GLFW.GLFW_KEY_R);
    public static final KeyMapping RESET_VIEW = editorKey("reset_view", GLFW.GLFW_KEY_HOME);
    public static final KeyMapping FOCUS_PART = editorKey("focus_part", GLFW.GLFW_KEY_F);
    public static final KeyMapping TOGGLE_SNAP = editorKey("toggle_snap", GLFW.GLFW_KEY_V);

    private static KeyMapping editorKey(String key, int defaultKey) {
        return new KeyMapping("key.equipment_structure_api." + key, EDITOR,
                com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, defaultKey, "key.categories.equipment_structure_api");
    }

    public static final KeyMapping OPEN_ASSEMBLY = new KeyMapping(
            "key.equipment_structure_api.open_assembly",
            GLFW.GLFW_KEY_G,
            "key.categories.equipment_structure_api"
    );

    private EquipmentAssemblyKeyMappings() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_ASSEMBLY);
        event.register(RESET_PART);
        event.register(RESET_VIEW);
        event.register(FOCUS_PART);
        event.register(TOGGLE_SNAP);
    }
}
