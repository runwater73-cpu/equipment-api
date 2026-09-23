package dev.equipmentstructure.api.compat.curios;

import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.grid.GridDefinitions;
import dev.equipmentstructure.api.grid.GridDefinitionSync;
import dev.equipmentstructure.api.network.GridDefinitionsPayload;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/** Optional entry point: loaded only when Curios is present. Native Curios owns gameplay callbacks. */
public final class CuriosArmorCompat {
    private static volatile CuriosDefinitions server = CuriosDefinitions.javaDefaults();
    private static volatile CuriosDefinitions client;
    private static Supplier<RegistryAccess> clientRegistries = () -> null;
    private static final Set<ResourceLocation> GENERATED = new HashSet<>();
    private record Generation(CuriosDefinitions definitions, java.util.Map<String, ResourceLocation> slots,
                              Set<ResourceLocation> equipmentTypes) {}
    private static Generation generation;
    private static final ThreadLocal<Boolean> PREPARING = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Integer> LOADING = ThreadLocal.withInitial(() -> 0);
    private CuriosArmorCompat() {}
    public static boolean nativeLoading() { return LOADING.get() > 0; }
    public static void beginNativeLoad() { LOADING.set(LOADING.get() + 1); }
    public static void endNativeLoad() { LOADING.set(LOADING.get() - 1); }

    public static void initialize(IEventBus bus) {
        EquipmentSlotItemAdapters.register(new CuriosComponentItems());
        EquipmentHostProviders.registerFallback(stack -> {
            var settings = client != null && (ServerLifecycleHooks.getCurrentServer() == null
                    || !ServerLifecycleHooks.getCurrentServer().isSameThread()) ? client : server;
            if (!settings.enabled() || settings.excluded().contains(BuiltInRegistries.ITEM.getKey(stack.getItem()))) return Optional.empty();
            var slot = CuriosEquipmentTemplates.position(stack);
            if (slot == null) return Optional.empty();
            return Optional.of(CuriosEquipmentTemplates.template(slot));
        }, -100);
        bus.addListener(RegisterPayloadHandlersEvent.class, event -> event.registrar(EquipmentStructureApiMod.NETWORK_VERSION)
                .playToClient(CuriosDefinitionsPayload.TYPE, CuriosDefinitionsPayload.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> {
                            client = payload.definitions();
                            generate(context.player().registryAccess(), client, payload.slots());
                        }))
                .playToServer(CuriosBindingActionPayload.TYPE, CuriosBindingActionPayload.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> {
                            if (context.player().containerMenu instanceof dev.equipmentstructure.api.menu.EquipmentAssemblyMenu menu)
                                CuriosBindingActions.act(menu, payload, context.player());
                        }))
                .playToServer(CuriosSlotClonePayload.TYPE, CuriosSlotClonePayload.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> payload.apply(context.player()))));
        NeoForge.EVENT_BUS.addListener(AddReloadListenerEvent.class, event -> event.addListener(new CuriosDefinitionLoader()));
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, event -> generate(event.getServer().registryAccess(), server, nativeSlots()));
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, OnDatapackSyncEvent.class, event -> {
            var slots = nativeSlots();
            generate(event.getPlayerList().getServer().registryAccess(), server, slots);
            event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, new CuriosDefinitionsPayload(server, slots)));
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityTickEvent.Post.class, event -> {
            if (event.getEntity() instanceof Player player && !player.level().isClientSide()) flush(player);
        });
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> {
            server = CuriosDefinitions.javaDefaults(); client = null;
            ArmorCurioStackHandler.clearTracking(); CuriosEquipmentTemplates.clear();
            for (var id : GENERATED) EquipmentComponentRegistry.unregister(id);
            GENERATED.clear(); generation = null;
        });
    }

    public static void clientRegistries(Supplier<RegistryAccess> supplier) { clientRegistries = supplier; }
    public static RegistryAccess registries() {
        var current = ServerLifecycleHooks.getCurrentServer();
        return current != null && current.isSameThread() ? current.registryAccess() : clientRegistries.get();
    }
    static CuriosDefinitions serverDefinitions() { return server; }
    static void replaceServerDefinitions(CuriosDefinitions definitions) { server = definitions; }
    public static CuriosDefinitions definitions(LivingEntity entity) {
        return entity.level().isClientSide() && client != null ? client : server;
    }
    public static boolean manages(SlotContext context) {
        return context != null && context.entity() instanceof Player && definitions(context.entity()).enabled()
                && managesType(context.entity(), context.identifier());
    }
    public static boolean managesType(LivingEntity entity, String type) {
        if (type == null || type.isBlank() || type.length() > 128) return false;
        var settings = definitions(entity);
        if (!settings.enabled() || !settings.profile(type).enabled()) return false;
        // Invisible native slots may be an addon's private inventory. Require explicit routing.
        return settings.slots().containsKey(type) || settings.slots().containsKey("*")
                || CuriosApi.getSlot(type, entity.level()).map(slot -> slot.useNativeGui()).orElse(false);
    }
    public static SlotContext context(LivingEntity entity, CuriosSlotKey key) {
        boolean render = key.cosmetic() || CuriosApi.getCuriosInventory(entity).flatMap(inv -> inv.getStacksHandler(key.type()))
                .filter(h -> key.index() < h.getRenders().size()).map(h -> h.getRenders().get(key.index())).orElse(true);
        return new SlotContext(key.type(), entity, key.index(), key.cosmetic(), render);
    }
    public static java.util.Comparator<String> slotOrder(LivingEntity entity) {
        var types = CuriosApi.getEntitySlots(entity);
        return java.util.Comparator.<String>comparingInt(id -> types.containsKey(id) ? types.get(id).getOrder() : Integer.MAX_VALUE)
                .thenComparing(java.util.Comparator.naturalOrder());
    }
    public static boolean canEquipOriginal(SlotContext context, ItemStack stack) {
        var probe = new top.theillusivec4.curios.common.inventory.DynamicStackHandler(1, ignored -> context);
        return probe.isItemValid(0, stack);
    }
    public static boolean canEquipOriginal(SlotContext context, ItemStack stack, ItemStack equipment) {
        return CuriosValidationView.validate(context, equipment, () -> canEquipOriginal(context, stack));
    }
    public static boolean canRemove(ItemStack armor, CuriosSlotKey key, LivingEntity actor) {
        if (actor == null) return false;
        var part = EquipmentStructureApi.component(armor, key.slotId()).orElse(null);
        if (part == null) return true;
        var item = CuriosComponentItems.decode(part, actor.registryAccess());
        var context = context(actor, key);
        var probe = new top.theillusivec4.curios.common.inventory.DynamicStackHandler(1, ignored -> context);
        probe.setStackInSlot(0, item);
        return item.isEmpty() || !probe.extractItem(0, 1, true).isEmpty();
    }
    public static boolean canRemoveArmor(ItemStack armor, LivingEntity actor) {
        var structure = EquipmentStructureApi.structure(armor).orElse(null);
        if (structure == null) return true;
        for (var id : structure.components().keySet()) {
            var key = CuriosSlotKey.parse(id);
            if (key.isPresent() && !EquipmentSlotItemAdapters.canRemove(armor, id, actor)) return false;
        }
        return true;
    }

    public static ItemStack owner(SlotContext context) {
        if (!manages(context) || context.index() >= 128) return ItemStack.EMPTY;
        var entity = context.entity();
        var profile = definitions(entity).profile(context.identifier());
        var owner = entity.getItemBySlot(profile.armorSlot());
        if (owner.isEmpty() || definitions(entity).excluded().contains(BuiltInRegistries.ITEM.getKey(owner.getItem()))) return ItemStack.EMPTY;
        if (!entity.level().isClientSide() && !PREPARING.get()) {
            PREPARING.set(true);
            try { CuriosEquipmentTemplates.prepare(owner, entity); }
            finally { PREPARING.set(false); }
        }
        var structure = EquipmentStructureApi.structure(owner).orElse(null);
        if (structure == null || profile.template().filter(id -> !id.equals(structure.hostId())).isPresent()) return ItemStack.EMPTY;
        return structure.slot(new CuriosSlotKey(context.identifier(), context.index(), context.cosmetic()).slotId()).isPresent()
                ? owner : ItemStack.EMPTY;
    }

    public static void flush(LivingEntity entity) {
        CuriosApi.getCuriosInventory(entity).ifPresent(inv -> inv.getCurios().values().forEach(handler -> {
            if (handler.getStacks() instanceof ArmorCurioStackHandler stacks) stacks.flush();
            if (handler.getCosmeticStacks() instanceof ArmorCurioStackHandler stacks) stacks.flush();
        }));
    }

    private static java.util.Map<String, ResourceLocation> nativeSlots() {
        var slots = new java.util.TreeMap<String, ResourceLocation>();
        CuriosApi.getSlots(false).forEach((id, type) -> {
            if (!id.isBlank() && id.length() <= 128) slots.put(id, type.getIcon());
        });
        return java.util.Map.copyOf(slots);
    }
    static synchronized void generate(RegistryAccess registries, CuriosDefinitions definitions, java.util.Map<String, ResourceLocation> slots) {
        var types = new HashSet<ResourceLocation>(Set.of(BuiltinEquipmentTypes.HELMET, BuiltinEquipmentTypes.CHESTPLATE,
                BuiltinEquipmentTypes.LEGGINGS, BuiltinEquipmentTypes.BOOTS));
        registries.registry(EquipmentStructureRegistries.HOST_DEFINITION)
                .ifPresent(registry -> registry.forEach(host -> types.add(host.equipmentType())));
        var nextGeneration = new Generation(definitions, slots, Set.copyOf(types));
        if (nextGeneration.equals(generation)) return;
        var wanted = new java.util.LinkedHashMap<ResourceLocation, EquipmentComponentDefinition>();
        for (var type : slots.keySet().stream().sorted().toList()) {
            var key = new CuriosSlotKey(type, 0, false);
            var profile = definitions.profile(type);
            var all = new java.util.LinkedHashMap<ResourceLocation, dev.equipmentstructure.api.grid.GridFootprint>();
            all.put(key.defaultComponent(), profile.footprint());
            definitions.items().forEach((item, shape) -> all.put(key.itemComponent(item), shape));
            for (var entry : all.entrySet()) {
                var definition = EquipmentComponentDefinition.builder(entry.getKey(), key.interfaceId(), key.interfaceId())
                        .suitableFor(types.toArray(ResourceLocation[]::new)).footprint(entry.getValue())
                        .itemFactory(part -> {
                            var access = registries();
                            return access == null ? ItemStack.EMPTY : CuriosComponentItems.decode(part, access);
                        }).build();
                wanted.put(definition.id(), definition);
            }
        }
        if (wanted.size() + EquipmentComponentRegistry.definitions().size() - GENERATED.size() > GridDefinitions.MAX_DEFINITIONS)
            throw new IllegalStateException("Curios compatibility exceeds the grid definition limit; reduce item overrides");
        for (var id : Set.copyOf(GENERATED)) EquipmentComponentRegistry.unregister(id);
        GENERATED.clear();
        wanted.values().forEach(definition -> { EquipmentComponentRegistry.register(definition); GENERATED.add(definition.id()); });
        for (var entry : slots.entrySet()) {
            var id = new CuriosSlotKey(entry.getKey(), 0, false).interfaceId();
            dev.equipmentstructure.api.ui.EquipmentStructureUiRegistry.unregisterInterface(id);
            dev.equipmentstructure.api.ui.EquipmentStructureUiRegistry.registerInterface(id,
                    new dev.equipmentstructure.api.ui.EquipmentSlotDisplay(Optional.of("curios.identifier." + entry.getKey()),
                            Optional.of(entry.getValue()), java.util.List.of()));
        }
        generation = nextGeneration;
        GridDefinitionSync.loadTemplates(registries);
    }
}
