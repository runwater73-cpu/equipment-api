package dev.equipmentstructure.api;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 部件类型兼容关系的轻量注册入口。
 *
 * <p>部件类型可以声明为另一个类型的子类型。槽位要求父类型时，所有子类型
 * 部件都可以安装；默认没有注册关系时仍保持精确匹配。</p>
 */
public final class EquipmentComponentTypeRegistry {

    private static final Map<ResourceLocation, Set<ResourceLocation>> PARENTS =
            new ConcurrentHashMap<>();

    private EquipmentComponentTypeRegistry() {
    }

    /**
     * 注册一个部件类型的直接父类型。
     *
     * <p>例如注册 {@code example:long_blade -> api:blade} 后，要求
     * {@code api:blade} 的槽位会接受 {@code example:long_blade}。</p>
     */
    public static void registerSubtype(ResourceLocation child, ResourceLocation parent) {
        Objects.requireNonNull(child, "child");
        Objects.requireNonNull(parent, "parent");
        if (child.equals(parent)) {
            throw new IllegalArgumentException("A component type cannot extend itself: " + child);
        }
        if (isCompatible(parent, child)) {
            throw new IllegalArgumentException(
                    "Component type relationship would create a cycle: " + child + " -> " + parent
            );
        }
        PARENTS.computeIfAbsent(child, ignored -> ConcurrentHashMap.newKeySet()).add(parent);
    }

    /** 删除一个直接父类型关系，供热重载或测试环境使用。 */
    public static boolean unregisterSubtype(ResourceLocation child, ResourceLocation parent) {
        Set<ResourceLocation> parents = PARENTS.get(child);
        if (parents == null || !parents.remove(parent)) {
            return false;
        }
        if (parents.isEmpty()) {
            PARENTS.remove(child, parents);
        }
        return true;
    }

    /** 判断实际部件类型是否可以安装到要求的类型上。 */
    public static boolean isCompatible(ResourceLocation actual, ResourceLocation required) {
        Objects.requireNonNull(actual, "actual");
        Objects.requireNonNull(required, "required");
        if (actual.equals(required)) {
            return true;
        }

        Set<ResourceLocation> visited = new HashSet<>();
        ArrayDeque<ResourceLocation> pending = new ArrayDeque<>();
        pending.add(actual);
        while (!pending.isEmpty()) {
            ResourceLocation current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }
            for (ResourceLocation parent : PARENTS.getOrDefault(current, Set.of())) {
                if (parent.equals(required)) {
                    return true;
                }
                pending.addLast(parent);
            }
        }
        return false;
    }
}
