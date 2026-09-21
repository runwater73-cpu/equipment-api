package dev.equipmentstructure.api;

import dev.equipmentstructure.api.internal.ExtensionGuard;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Optional;

/** Shared item recognition and developer diagnostics; never consumes the input. */
public final class EquipmentComponentInspection {
    private static final ExtensionGuard<Item> READERS = new ExtensionGuard<>("Component item reader");
    public enum Status { MATCH, NO_MATCH, CALLBACK_FAILED, DEFINITION_CHANGED, ITEM_NOT_RESTORED,
        COMPONENT_UNREGISTERED, IDENTITY_MISMATCH }

    public record Result(Status status, Optional<ResourceLocation> adapter,
                         Optional<EquipmentComponentInstance> component) {
        public Result {
            Objects.requireNonNull(status);
            Objects.requireNonNull(adapter);
            Objects.requireNonNull(component);
        }
        static Result of(Status status, ResourceLocation adapter) {
            return new Result(status, Optional.ofNullable(adapter), Optional.empty());
        }
    }

    private EquipmentComponentInspection() {}

    /** Invokes pure readers/factories with defensive copies; callback failures are returned, not thrown. */
    public static Result inspect(ItemStack input) {
        return inspect(input, true);
    }

    /** Internal reverse check: read identity/data without invoking the factory again. */
    static Result readRestored(ItemStack input) {
        return inspect(input, false);
    }

    private static Result inspect(ItemStack input, boolean checkItem) {
        Objects.requireNonNull(input);
        if (input.isEmpty()) return Result.of(Status.NO_MATCH, null);
        if (!(input.getItem() instanceof EquipmentComponentItem)
                && !EquipmentComponentItemAdapters.hasCandidates(input.getItem())) return Result.of(Status.NO_MATCH, null);
        return READERS.call(input.getItem(), () -> {
            ItemStack stack = input.copyWithCount(1);
            var external = EquipmentComponentItemAdapters.inspect(stack, checkItem);
            if (external.status() != Status.NO_MATCH) return external;
            if (!(stack.getItem() instanceof EquipmentComponentItem reader)) return Result.of(Status.NO_MATCH, null);
            var component = Objects.requireNonNull(reader.createComponent(stack.copyWithCount(1)), "component item result");
            return validate(stack, component, null, checkItem);
        }, Result.of(Status.CALLBACK_FAILED, null));
    }

    static Result validate(ItemStack input, EquipmentComponentInstance component, ResourceLocation adapter, boolean checkItem) {
        var definition = EquipmentComponentRegistry.get(component.id()).orElse(null);
        if (definition == null) return Result.of(Status.COMPONENT_UNREGISTERED, adapter);
        if (!definition.componentType().equals(component.componentType())
                || !definition.interfaceType().equals(component.interfaceType().orElseThrow())) {
            return Result.of(Status.IDENTITY_MISMATCH, adapter);
        }
        if (checkItem) {
            var restored = EquipmentComponentRegistry.createItemStack(component).orElse(ItemStack.EMPTY);
            if (restored.isEmpty() || !ItemStack.isSameItemSameComponents(input, restored)) {
                return Result.of(Status.ITEM_NOT_RESTORED, adapter);
            }
        }
        if (!EquipmentComponentRegistry.get(component.id()).filter(definition::equals).isPresent()) {
            return Result.of(Status.DEFINITION_CHANGED, adapter);
        }
        return new Result(Status.MATCH, Optional.ofNullable(adapter), Optional.of(component));
    }

    public static boolean sameInstance(EquipmentComponentInstance first, EquipmentComponentInstance second) {
        return first.id().equals(second.id()) && first.componentType().equals(second.componentType())
                && first.interfaceType().orElseThrow().equals(second.interfaceType().orElseThrow())
                && first.data().equals(second.data());
    }

    static void clear() { READERS.clear(); }
}
