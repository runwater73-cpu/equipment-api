package dev.equipmentstructure.api.attribute;

import dev.equipmentstructure.api.EquipmentStructureApiMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/** Reload-only reconciliation of transient modifiers owned by this API. */
public final class EquipmentAttributeRefresh {
    private EquipmentAttributeRefresh() {}

    static void refresh(MinecraftServer server) {
        for (var level : server.getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (entity instanceof LivingEntity living) refresh(living);
            }
        }
    }

    /** Reconcile API-owned modifiers after an author's external condition changes. Server thread only. */
    public static void refresh(LivingEntity wearer) {
        if (wearer.level().isClientSide()) return;
        // The old provider may have been deleted or changed attribute/ID; querying the old
        // ItemStack with the new catalog cannot tell vanilla which old modifiers to remove.
        var registry = wearer.registryAccess().registryOrThrow(Registries.ATTRIBUTE);
        registry.holders().forEach(holder -> {
            var instance = wearer.getAttribute(holder);
            if (instance != null) {
                for (var modifier : List.copyOf(instance.getModifiers())) {
                    if (owned(modifier.id())) instance.removeModifier(modifier.id());
                }
            }
        });
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            var stack = wearer.getItemBySlot(slot);
            if (EquipmentAttributeResolver.isBroken(stack)) continue;
            // Use the ordinary NeoForge query, preserving other mods' filtering/replacement.
            stack.forEachModifier(slot, (attribute, modifier) -> {
                if (!owned(modifier.id())) return;
                var instance = wearer.getAttribute(attribute);
                if (instance != null) {
                    instance.removeModifier(modifier.id());
                    instance.addTransientModifier(modifier);
                }
            });
        }
    }

    private static boolean owned(ResourceLocation id) {
        return id.getNamespace().equals(EquipmentStructureApiMod.MOD_ID) && id.getPath().startsWith("component/");
    }
}
