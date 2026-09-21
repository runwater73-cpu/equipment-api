package dev.equipmentstructure.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import dev.equipmentstructure.api.internal.ExtensionGuard;

/** 装备承载体 Provider 的统一注册入口。 */
public final class EquipmentHostProviders {

    private static final ConcurrentHashMap<Item, FixedProvider> ITEM_PROVIDERS =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<TagKey<Item>, TagProvider> TAG_PROVIDERS =
            new ConcurrentHashMap<>();
    private static final ExtensionGuard<Long> GUARD = new ExtensionGuard<>("Equipment host provider");
    private static final CopyOnWriteArrayList<RegisteredProvider> PROVIDERS =
            new CopyOnWriteArrayList<>();
    private static final CopyOnWriteArrayList<RegisteredProvider> FALLBACK_PROVIDERS =
            new CopyOnWriteArrayList<>();
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final Comparator<RegisteredProvider> PROVIDER_ORDER =
            Comparator.comparingInt(RegisteredProvider::priority).reversed()
                    .thenComparingLong(RegisteredProvider::sequence);

    private EquipmentHostProviders() {
    }

    /**
     * 为一个物品注册固定宿主模板。
     *
     * <p>这是装备模组或独立整合模组必须显式调用的入口；API 不会根据
     * Item 继承关系自动推断宿主槽位。重复注册会覆盖旧模板。</p>
     */
    public static Registration register(Item item, ResourceLocation hostId) {
        Item checkedItem = Objects.requireNonNull(item, "item");
        ResourceLocation checkedHost = Objects.requireNonNull(hostId, "hostId");
        FixedProvider entry = new FixedProvider(checkedHost);
        ITEM_PROVIDERS.put(checkedItem, entry);
        return new Registration(() -> ITEM_PROVIDERS.remove(checkedItem, entry));
    }

    /** Binds every item in an item tag to one host template. */
    public static Registration registerTag(TagKey<Item> tag, ResourceLocation hostId) {
        TagKey<Item> checkedTag = Objects.requireNonNull(tag, "tag");
        ResourceLocation checkedHost = Objects.requireNonNull(hostId, "hostId");
        TagProvider entry = new TagProvider(checkedHost, SEQUENCE.getAndIncrement());
        TAG_PROVIDERS.put(checkedTag, entry);
        return new Registration(() -> TAG_PROVIDERS.remove(checkedTag, entry));
    }

    /** Convenience overload for a namespaced item tag. */
    public static Registration registerTag(ResourceLocation tag, ResourceLocation hostId) {
        return registerTag(TagKey.create(Registries.ITEM, Objects.requireNonNull(tag, "tag")), hostId);
    }

    /** 注册一个按 ItemStack 自定义判断的宿主 Provider，默认优先级为 0。 */
    public static Registration register(EquipmentHostProvider provider) {
        return register(provider, 0);
    }

    /** 优先级越高越先尝试；同优先级按注册先后顺序执行。 */
    public static Registration register(EquipmentHostProvider provider, int priority) {
        RegisteredProvider entry = new RegisteredProvider(
                Objects.requireNonNull(provider, "provider"),
                priority,
                SEQUENCE.getAndIncrement()
        );
        PROVIDERS.add(entry);
        PROVIDERS.sort(PROVIDER_ORDER);
        return new Registration(() -> { PROVIDERS.remove(entry); GUARD.forget(entry.sequence()); });
    }

    /**
     * Registers a last-resort provider. Fallbacks run after data-pack bindings
     * when a RegistryAccess is available, so built-in defaults never block an
     * integration from replacing a template.
     */
    public static Registration registerFallback(EquipmentHostProvider provider) {
        return registerFallback(provider, 0);
    }

    /** Registers a prioritized last-resort provider. */
    public static Registration registerFallback(EquipmentHostProvider provider, int priority) {
        RegisteredProvider entry = new RegisteredProvider(
                Objects.requireNonNull(provider, "provider"),
                priority,
                SEQUENCE.getAndIncrement()
        );
        FALLBACK_PROVIDERS.add(entry);
        FALLBACK_PROVIDERS.sort(PROVIDER_ORDER);
        return new Registration(() -> { FALLBACK_PROVIDERS.remove(entry); GUARD.forget(entry.sequence()); });
    }

    /** 注册一个带明确判断条件的模板 Provider。 */
    public static Registration register(Predicate<ItemStack> predicate, ResourceLocation hostId) {
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(hostId, "hostId");
        return register(stack -> predicate.test(stack) ? Optional.of(hostId) : Optional.empty());
    }

    /**
     * Resolves a template without RegistryAccess. Fixed mappings, regular
     * providers, tag mappings and finally fallback providers are checked in
     * that order. Data-pack item bindings are unavailable without a registry
     * access and are therefore skipped by this overload.
     */
    public static Optional<ResourceLocation> resolve(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        FixedProvider direct = ITEM_PROVIDERS.get(stack.getItem());
        if (direct != null) {
            return Optional.of(direct.hostId);
        }
        Optional<ResourceLocation> regular = resolveProviders(PROVIDERS, stack, "provider result");
        if (regular.isPresent()) {
            return regular;
        }
        Optional<ResourceLocation> tag = resolveTags(stack);
        if (tag.isPresent()) return tag;
        Optional<ResourceLocation> fallback = resolveProviders(
                FALLBACK_PROVIDERS, stack, "fallback provider result");
        if (fallback.isPresent()) {
            return fallback;
        }
        return Optional.empty();
    }

    /**
     * 在 Java Provider 之后查询数据包物品绑定。
     * 数据包绑定只负责映射物品 ID，模板本身仍由 host_definition Registry 提供。
     */
    public static Optional<ResourceLocation> resolve(ItemStack stack, RegistryAccess registries) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(registries, "registries");
        FixedProvider direct = ITEM_PROVIDERS.get(stack.getItem());
        if (direct != null) {
            return Optional.of(direct.hostId);
        }
        Optional<ResourceLocation> regular = resolveProviders(PROVIDERS, stack, "provider result");
        if (regular.isPresent()) {
            return regular;
        }
        Optional<ResourceLocation> tag = resolveTags(stack);
        if (tag.isPresent()) return tag;
        Optional<ResourceLocation> bindingResult = Optional.ofNullable(
                stack.getItemHolder().getData(EquipmentStructureRegistries.HOST_BINDING)
        ).map(EquipmentHostBinding::host);
        if (bindingResult.isPresent()) {
            return bindingResult;
        }
        return resolveProviders(FALLBACK_PROVIDERS, stack, "fallback provider result");
    }

    /** 清空注册内容，主要用于测试和开发环境热重载。 */
    public static void clear() {
        ITEM_PROVIDERS.clear();
        TAG_PROVIDERS.clear();
        PROVIDERS.clear();
        FALLBACK_PROVIDERS.clear();
        GUARD.clear();
    }

    /** Fixed mappings only. Dynamic providers cannot be enumerated without concrete input stacks. */
    public static java.util.Map<Item, ResourceLocation> fixedBindings() {
        var result = new java.util.HashMap<Item, ResourceLocation>();
        ITEM_PROVIDERS.forEach((item, provider) -> result.put(item, provider.hostId));
        return java.util.Map.copyOf(result);
    }

    private static Optional<ResourceLocation> resolveProviders(
            Iterable<RegisteredProvider> providers,
            ItemStack stack,
            String resultName
    ) {
        for (RegisteredProvider entry : providers) {
            Optional<ResourceLocation> result = GUARD.call(entry.sequence(), () -> Objects.requireNonNull(
                    entry.provider().resolve(stack.copy()), resultName), Optional.empty());
            if (result.isPresent()) return result;
        }
        return Optional.empty();
    }

    private static Optional<ResourceLocation> resolveTags(ItemStack stack) {
        return TAG_PROVIDERS.entrySet().stream()
                .filter(entry -> stack.is(entry.getKey()))
                .sorted(Comparator.comparingLong(entry -> entry.getValue().sequence()))
                .map(entry -> entry.getValue().hostId())
                .findFirst();
    }

    private record RegisteredProvider(EquipmentHostProvider provider, int priority, long sequence) {
    }

    private record TagProvider(ResourceLocation hostId, long sequence) {
    }

    /** Identity, not host-ID equality, owns a fixed registration's lifetime. */
    private static final class FixedProvider {
        private final ResourceLocation hostId;
        private FixedProvider(ResourceLocation hostId) { this.hostId = hostId; }
    }

    /** 可保存的注册句柄；重复调用 unregister 不会产生副作用。 */
    public static final class Registration {
        private final Runnable remover;
        private boolean active = true;

        private Registration(Runnable remover) {
            this.remover = remover;
        }

        public synchronized boolean unregister() {
            if (!active) {
                return false;
            }
            active = false;
            remover.run();
            return true;
        }

        public synchronized boolean isActive() {
            return active;
        }
    }
}
