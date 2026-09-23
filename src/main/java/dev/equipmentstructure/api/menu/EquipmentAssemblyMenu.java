package dev.equipmentstructure.api.menu;

import dev.equipmentstructure.api.EquipmentComponentInstance;
import dev.equipmentstructure.api.EquipmentComponentRegistry;
import dev.equipmentstructure.api.EquipmentSlotItemAdapters;
import dev.equipmentstructure.api.EquipmentHostProviders;
import dev.equipmentstructure.api.EquipmentSlotDefinition;
import dev.equipmentstructure.api.EquipmentStructureApi;
import dev.equipmentstructure.api.EquipmentStructureRegistries;
import dev.equipmentstructure.api.EquipmentFeatureConfig;
import dev.equipmentstructure.api.appearance.AppearancePartPresentation;
import dev.equipmentstructure.api.appearance.AppearanceVisibilityStorage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import dev.equipmentstructure.api.appearance.AppearancePose;

/** Generic server-authoritative assembly menu driven by the equipment slot definitions. */
public final class EquipmentAssemblyMenu extends AbstractContainerMenu {

    public static final int EQUIPMENT_SLOT = 0;
    public static final int PART_SLOT_START = 1;
    /** One physical staging slot is reused for whichever logical interface is selected. */
    private static final int STAGING_SLOT_COUNT = 1;

    private final Container inventory;
    private final Inventory playerInventory;
    private List<EquipmentSlotDefinition> slotDefinitions;
    /** Stable interface identity selected for the staging slot. */
    private ResourceLocation selectedInterfaceId;
    /** Interface identity represented by the mirrored item in the staging slot. */
    private ResourceLocation mirroredInterfaceId;
    private EquipmentComponentInstance mirroredComponent;
    /** Component item mirrors used only for rendering and direct player removal. */
    private final Set<Integer> installedMirrorSlots = new HashSet<>();

    public EquipmentAssemblyMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf data) {
        this(containerId, playerInventory, readMenuData(data));
    }

    private EquipmentAssemblyMenu(int containerId, Inventory playerInventory, MenuData data) {
        this(containerId, playerInventory, new SimpleContainer(containerSize()),
                data.definitions());
    }

    public EquipmentAssemblyMenu(
            int containerId,
            Inventory playerInventory,
            Container inventory,
            List<EquipmentSlotDefinition> initialDefinitions
    ) {
        super(EquipmentAssemblyMenus.ASSEMBLY.get(), containerId);
        this.slotDefinitions = List.copyOf(initialDefinitions);
        checkContainerSize(inventory, containerSize());
        this.inventory = inventory;
        this.playerInventory = playerInventory;
        addSlot(new Slot(inventory, EQUIPMENT_SLOT,
                EquipmentAssemblyLayout.equipmentSlotX(), EquipmentAssemblyLayout.equipmentSlotY()) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return isSupportedEquipment(stack);
            }

            @Override
            public boolean mayPickup(Player player) {
                return true;
            }

            @Override
                public void setByPlayer(ItemStack stack) {
                    // A different equipment item can have identical interface definitions.
                    // Release the previous projection before replacing its owner.
                    returnPendingComponents();
                    ItemStack prepared = stack.copy();
                if (!prepared.isEmpty() && !EquipmentStructureApi.hasStructure(prepared)) {
                    boolean initialized = EquipmentStructureApi.initializeFromProvider(
                            prepared, playerInventory.player.level().registryAccess());
                    // The client may not have received a newly loaded host template yet,
                    // but the server must never accept an uninitialized equipment stack.
                    if (!playerInventory.player.level().isClientSide() && !initialized) {
                        return;
                    }
                }
                super.setByPlayer(prepared);
                refreshDefinitions();
            }

            @Override
            public void onTake(Player player, ItemStack stack) {
                // The equipment stack carries the installed components with it.
                // Clear the staging projection before the old stack can be
                // replaced or the menu can be closed, otherwise the projection
                // would still refer to the previous equipment.
                returnPendingComponents();
                super.onTake(player, stack);
                refreshDefinitions();
            }
        });
        for (int index = 0; index < STAGING_SLOT_COUNT; index++) {
            final int stagingIndex = index;
            addSlot(new Slot(inventory, PART_SLOT_START + index,
                    EquipmentAssemblyLayout.COMPONENT_SLOT_X, EquipmentAssemblyLayout.COMPONENT_SLOT_Y) {
                @Override
                public int getMaxStackSize() {
                    return 1;
                }

                @Override
                public boolean mayPickup(Player player) {
                    if (!isActive()) {
                        return false;
                    }
                    if (!installedMirrorSlots.contains(stagingIndex)) {
                        return true;
                    }
                    return mirroredInterfaceId != null && EquipmentStructureApi.checkRemove(
                            inventory.getItem(EQUIPMENT_SLOT), mirroredInterfaceId).isAllowed();
                }

                @Override
                public boolean isActive() {
                    return !equipmentStack().isEmpty() && selectedInterface().isPresent();
                }

                @Override
                public boolean mayPlace(ItemStack stack) {
                    // Each visible slot represents one physical interface unit.
                    // An installed mirror must be removed before another part can
                    // occupy the same unit; allowing replacement would orphan the
                    // previously installed component in the equipment data.
                    if (!isActive() || hasItem()) {
                        return false;
                    }
                    refreshDefinitions();
                    if (selectedInterfaceId == null) {
                        return false;
                    }
                    EquipmentSlotDefinition definition = slotDefinitions.stream()
                            .filter(value -> value.id().equals(selectedInterfaceId)).findFirst().orElse(null);
                    if (definition == null) {
                        return false;
                    }
                    ItemStack equipment = inventory.getItem(EQUIPMENT_SLOT);
                    return !equipment.isEmpty() && readComponent(stack, definition.id())
                            .filter(component -> EquipmentStructureApi.checkInstall(
                                    equipment, definition.id(), component).isAllowed()).isPresent();
                }

                @Override
                public ItemStack safeInsert(ItemStack stack, int increment) {
                    // Vanilla passes the complete carried stack for a primary
                    // click. The logical staging point accepts exactly one
                    // component, so split one item here and return the rest to
                    // AbstractContainerMenu, which keeps it carried by the
                    // player instead of silently consuming the whole stack.
                    if (stack.isEmpty() || increment <= 0 || !mayPlace(stack)) {
                        return stack;
                    }
                    setByPlayer(stack.split(1));
                    return stack;
                }

                @Override
                public void setByPlayer(ItemStack stack) {
                    // Vanilla transfers ownership of this input to the slot;
                    // successful installation replaces it with a projection.
                    if (stack.isEmpty()) {
                        super.setByPlayer(ItemStack.EMPTY);
                        return;
                    }
                    if (!installComponent(selectedInterfaceId, stack)) {
                        // Keep the item in the slot when an event or a server-side
                        // validation rejects the installation. The normal menu
                        // click flow will then return it safely on close.
                        super.setByPlayer(stack);
                        return;
                    }
                    ItemStack mirror = stack.copyWithCount(1);
                    // Vanilla has already transferred ownership of this single
                    // input. Do not consume it again in the slot callback.
                    installedMirrorSlots.add(stagingIndex);
                    mirroredInterfaceId = selectedInterfaceId;
                    mirroredComponent = EquipmentStructureApi.component(equipmentStack(), selectedInterfaceId)
                            .orElseThrow();
                    super.setByPlayer(mirror);
                }

                @Override
                public void onTake(Player player, ItemStack stack) {
                    // Installed mirrors are removed transactionally by the
                    // menu's clicked() implementation. Keeping this callback
                    // side-effect free is important: vanilla invokes onTake
                    // after it has already moved the item to the carried stack,
                    // so an event cancellation here could not reliably restore
                    // every PICKUP/SWAP/PICKUP_ALL path.
                    super.onTake(player, stack);
                }
            });
        }
        addPlayerInventory(playerInventory);
    }

    public static int containerSize() {
        return PART_SLOT_START + STAGING_SLOT_COUNT;
    }


    public static int stagingSlotCount() {
        return STAGING_SLOT_COUNT;
    }


    public int structureSlotCount() {
        refreshDefinitions();
        return slotDefinitions.size();
    }

    public int visibleSlotCount() {
        refreshDefinitions();
        return slotDefinitions.size();
    }

    public List<EquipmentSlotDefinition> slotDefinitions() {
        refreshDefinitions();
        return slotDefinitions;
    }

    public EquipmentSlotDefinition definitionForVisibleSlot(int index) {
        refreshDefinitions();
        if (index < 0 || index >= visibleSlotCount()) {
            throw new IndexOutOfBoundsException("visible slot index: " + index);
        }
        return slotDefinitions.get(index);
    }

    /** Returns the currently selected interface, if it still exists. */
    public java.util.Optional<EquipmentSlotDefinition> selectedInterface() {
        refreshDefinitions();
        return slotDefinitions.stream()
                .filter(value -> value.id().equals(selectedInterfaceId))
                .findFirst();
    }

    /** Compatibility helper sharing the same transaction as selection by stable ID. */
    public void selectInterface(int index) {
        refreshDefinitions();
        selectInterface(index >= 0 && index < slotDefinitions.size()
                ? slotDefinitions.get(index).id() : null);
    }

    public void selectInterface(ResourceLocation interfaceId) {
        refreshDefinitions();
        ResourceLocation next = interfaceId != null && slotDefinitions.stream()
                .anyMatch(value -> value.id().equals(interfaceId)) ? interfaceId : null;
        if (!EquipmentAssemblyLimits.supports(slotDefinitions.size())) next = null;
        if (!java.util.Objects.equals(selectedInterfaceId, next)) {
            // The reusable staging slot belongs to the previous logical
            // interface. Return pending input before changing selection.
            returnPendingComponents();
            installedMirrorSlots.clear();
            mirroredInterfaceId = null;
        }
        selectedInterfaceId = next;
        broadcastChanges();
    }

    /** Whether the current equipment and pending input can be returned as a valid item. */
    public boolean canAssemble() {
        refreshDefinitions();
        ItemStack equipment = inventory.getItem(EQUIPMENT_SLOT);
        return !equipment.isEmpty()
                && EquipmentStructureApi.validate(equipment).valid()
                && (inventory.getItem(PART_SLOT_START).isEmpty() || installedMirrorSlots.contains(0));
    }

    public int playerInventoryY() {
        return EquipmentAssemblyLayout.PLAYER_INVENTORY_Y;
    }

    public int imageHeight() {
        return EquipmentAssemblyLayout.GUI_HEIGHT;
    }

    public int rows() {
        return 1;
    }

    public ItemStack equipmentStack() {
        return inventory.getItem(EQUIPMENT_SLOT);
    }

    private dev.equipmentstructure.api.network.GridActionResultPayload lastGridResult;

    public void receiveGridResult(dev.equipmentstructure.api.network.GridActionResultPayload result) {
        if (result.containerId() == containerId) lastGridResult = result;
    }

    public java.util.Optional<dev.equipmentstructure.api.network.GridActionResultPayload> lastGridResult() {
        return java.util.Optional.ofNullable(lastGridResult);
    }

    /** One server transaction owns both the equipment snapshot and the carried item transfer. */
    public boolean applySpaceAction(dev.equipmentstructure.api.network.SpaceActionPayload request, Player player) {
        java.util.function.BooleanSupplier guard = () -> player == playerInventory.player && !player.level().isClientSide()
                && player.containerMenu == this && stillValid(player) && request.containerId() == containerId
                && request.stateId() == getStateId() && ItemStack.matches(equipmentStack(), request.expectedEquipment())
                && ItemStack.matches(getCarried(), request.expectedCarried())
                && request.definitions().equals(dev.equipmentstructure.api.grid.GridDefinitions.registered().fingerprint());
        if (!guard.getAsBoolean() || !inventory.getItem(PART_SLOT_START).isEmpty() && !installedMirrorSlots.contains(0)) return false;
        var result = dev.equipmentstructure.api.grid.space.ComponentSpaceTransactions.execute(equipmentStack(), getCarried(),
                request.source(), request.space(), request.token(), request.action(), request.entry(), request.target(), request.single(),
                player.registryAccess(), guard, this::setCarried);
        dev.equipmentstructure.api.EquipmentStructureApiMod.LOGGER.info("[Component space] request={} source={} space={} result={}",
                request.requestId(), request.source(), request.space(), result.reason());
        if (result.applied()) { inventory.setChanged(); loadSelectedComponent(); }
        return result.applied();
    }

    public boolean applyGridAction(dev.equipmentstructure.api.network.GridActionPayload request, Player player) {
        if (!gridRequestCurrent(request, player)) return false;
        refreshDefinitions();
        // A pending, uninstalled vanilla staging item must be returned before grid item actions.
        if (!inventory.getItem(PART_SLOT_START).isEmpty() && !installedMirrorSlots.contains(0)) return false;
        var equipment = equipmentStack();
        var previous = EquipmentStructureApi.structure(equipment).orElse(null);
        if (previous == null) return false;
        java.util.function.BooleanSupplier guard = () -> equipmentStack() == equipment && gridRequestCurrent(request, player);
        boolean applied;
        if (request.action() == dev.equipmentstructure.api.network.GridActionPayload.Action.INITIALIZE) {
            applied = EquipmentStructureApi.initializeGridTransaction(equipment, guard);
        } else if (request.action() == dev.equipmentstructure.api.network.GridActionPayload.Action.MOVE) {
            applied = EquipmentStructureApi.setGridLayoutTransaction(equipment, previous, request.placements(), guard);
        } else {
            ResourceLocation slot = request.slotId();
            if (previous.slot(slot).isEmpty()) return false;
            if (previous.component(slot).isPresent()
                    && !EquipmentSlotItemAdapters.canRemove(equipment, slot, player)) return false;
            var carried = getCarried().copy();
            if (request.action() == dev.equipmentstructure.api.network.GridActionPayload.Action.REMOVE
                    || request.action() == dev.equipmentstructure.api.network.GridActionPayload.Action.QUICK_REMOVE) {
                if (!carried.isEmpty()) return false;
                var restored = previous.component(slot).flatMap(EquipmentComponentRegistry::createValidatedItemStack);
                if (previous.component(slot).isEmpty() && guard.getAsBoolean()) {
                    var personal = EquipmentSlotItemAdapters.removePlayerOwnedItem(equipment, slot, player);
                    if (personal.isEmpty()) return false;
                    if (request.action() == dev.equipmentstructure.api.network.GridActionPayload.Action.QUICK_REMOVE)
                        player.getInventory().placeItemBackInInventory(personal);
                    else setCarried(personal);
                    broadcastChanges();
                    return true;
                }
                if (restored.isEmpty()) return false;
                Runnable destination = request.action() == dev.equipmentstructure.api.network.GridActionPayload.Action.QUICK_REMOVE
                        ? () -> player.getInventory().placeItemBackInInventory(restored.get())
                        : () -> setCarried(restored.get());
                applied = EquipmentStructureApi.removeTransaction(equipment, slot, guard,
                        destination).isPresent();
            } else {
                var component = readComponent(carried, slot);
                if (component.isEmpty()) return false;
                var placement = java.util.Optional.of(request.placements().get(slot));
                if (request.action() == dev.equipmentstructure.api.network.GridActionPayload.Action.REPLACE) {
                    // The cursor is also the return destination. A stacked input cannot hold the old item.
                    if (carried.getCount() != 1) return false;
                    var restored = previous.component(slot).flatMap(EquipmentComponentRegistry::createValidatedItemStack);
                    if (restored.isEmpty()) return false;
                    applied = EquipmentStructureApi.replaceTransaction(equipment, slot, component.get(), placement, guard,
                            () -> setCarried(restored.get())).isPresent();
                } else {
                    ItemStack remainder = carried.copy();
                    remainder.shrink(1);
                    applied = EquipmentStructureApi.installTransaction(equipment, slot, component.get(), placement, guard,
                            () -> setCarried(remainder)) == EquipmentStructureApi.InstallResult.INSTALLED;
                }
            }
        }
        if (applied) {
            inventory.setChanged();
            loadSelectedComponent();
        }
        return applied;
    }

    private boolean gridRequestCurrent(dev.equipmentstructure.api.network.GridActionPayload request, Player player) {
        return player == playerInventory.player && !player.level().isClientSide() && player.containerMenu == this
                && stillValid(player) && request.containerId() == containerId && request.stateId() == getStateId()
                && !request.expectedEquipment().isEmpty() && ItemStack.matches(equipmentStack(), request.expectedEquipment())
                && ItemStack.matches(getCarried(), request.expectedCarried())
                && request.definitions().equals(dev.equipmentstructure.api.grid.GridDefinitions.registered().fingerprint());
    }

    /** Applies a client-confirmed visual pose using the server's current item. */
    public boolean applyAppearancePose(ResourceLocation slotId, ItemStack expectedEquipment, AppearancePose pose, Player player) {
        // Only the equipment snapshot participates, not unrelated inventory/mirror updates.
        // The supplied snapshot is compared, never installed as an output item.
        if (expectedEquipment.isEmpty() || !ItemStack.matches(equipmentStack(), expectedEquipment)) return false;
        return applyAppearancePose(slotId, pose, player);
    }

    public boolean applyAppearancePose(ResourceLocation slotId, AppearancePose pose, Player player) {
        if (player != playerInventory.player || player.level().isClientSide() || !stillValid(player)) return false;
        refreshDefinitions();
        if (slotId == null || slotDefinitions.stream().noneMatch(value -> value.id().equals(slotId))) return false;
        if (EquipmentStructureApi.component(equipmentStack(), slotId).isEmpty()) return false;
        var current = AppearancePartPresentation.read(EquipmentStructureApi.component(equipmentStack(), slotId).orElseThrow());
        if (!EquipmentFeatureConfig.rules().allowsPart(current, current.withPose(pose))) return false;
        boolean changed = EquipmentStructureApi.setAppearancePose(equipmentStack(), slotId, pose);
        if (changed) inventory.setChanged();
        return changed;
    }

    public boolean applyAppearancePresentation(ResourceLocation slotId, ItemStack expectedEquipment, AppearancePose pose,
            dev.equipmentstructure.api.appearance.AppearanceVisibility visibility, Player player) {
        if (expectedEquipment.isEmpty() || !ItemStack.matches(equipmentStack(), expectedEquipment)) return false;
        if (player != playerInventory.player || player.level().isClientSide() || !stillValid(player)) return false;
        refreshDefinitions();
        if (slotId == null || slotDefinitions.stream().noneMatch(value -> value.id().equals(slotId))) return false;
        if (EquipmentStructureApi.component(equipmentStack(), slotId).isEmpty()) return false;
        var current = AppearancePartPresentation.read(EquipmentStructureApi.component(equipmentStack(), slotId).orElseThrow());
        if (!allowsAppearanceEdits(java.util.Map.of(slotId, current.withPose(pose).withVisible(visibility.componentVisible())),
                visibility.originalVisible())) return false;
        boolean changed = EquipmentStructureApi.setAppearancePresentation(equipmentStack(), slotId, pose, visibility);
        if (changed) inventory.setChanged();
        return changed;
    }

    /** Validates ownership and the complete item snapshot before a batch appearance save. */
    public boolean applyAppearanceLayout(ItemStack expectedEquipment,
            java.util.Map<ResourceLocation, dev.equipmentstructure.api.appearance.AppearancePartPresentation> edits,
            boolean originalVisible, Player player) {
        if (player != playerInventory.player || player.level().isClientSide() || player.containerMenu != this
                || !stillValid(player) || expectedEquipment.isEmpty()
                || !ItemStack.matches(equipmentStack(), expectedEquipment)) return false;
        refreshDefinitions();
        if (edits.keySet().stream().anyMatch(id -> slotDefinitions.stream().noneMatch(slot -> slot.id().equals(id)))) return false;
        if (!allowsAppearanceEdits(edits, originalVisible)) return false;
        boolean saved = EquipmentStructureApi.setAppearancePresentations(equipmentStack(), edits, originalVisible);
        if (saved) inventory.setChanged();
        return saved;
    }

    private boolean allowsAppearanceEdits(java.util.Map<ResourceLocation, AppearancePartPresentation> edits, boolean originalVisible) {
        var rules = EquipmentFeatureConfig.rules();
        if (!rules.allowsOriginal(AppearanceVisibilityStorage.originalVisible(equipmentStack()), originalVisible)) return false;
        return edits.entrySet().stream().allMatch(entry -> EquipmentStructureApi.component(equipmentStack(), entry.getKey())
                .map(AppearancePartPresentation::read).filter(current -> rules.allowsPart(current, entry.getValue())).isPresent());
    }

    /** Host identity used by client UI metadata; it is read from the item structure. */
    public java.util.Optional<ResourceLocation> hostId() {
        return EquipmentStructureApi.structure(equipmentStack()).map(value -> value.hostId());
    }

    private boolean installComponent(ResourceLocation interfaceId, ItemStack stack) {
        if (playerInventory.player.level().isClientSide()) {
            return true;
        }
        refreshDefinitions();
        if (interfaceId == null) {
            return false;
        }
        ItemStack equipment = inventory.getItem(EQUIPMENT_SLOT);
        if (equipment.isEmpty() || !EquipmentStructureApi.hasStructure(equipment)) {
            return false;
        }
        EquipmentSlotDefinition definition = slotDefinitions.stream()
                .filter(value -> value.id().equals(interfaceId)).findFirst().orElse(null);
        if (definition == null) {
            return false;
        }
        var structure = EquipmentStructureApi.structure(equipment).orElseThrow();
        var resolved = readComponent(stack, interfaceId)
                .filter(component -> definition.accepts(structure.equipmentType(), component));
        if (resolved.isEmpty()) {
            return false;
        }
        EquipmentComponentInstance component = resolved.get();
        boolean installed = EquipmentStructureApi.install(equipment, definition.id(), component)
                == EquipmentStructureApi.InstallResult.INSTALLED;
        if (installed) {
            inventory.setChanged();
        }
        return installed;
    }

    private boolean removeInstalledComponent(ResourceLocation interfaceId) {
        refreshDefinitions();
        if (interfaceId == null) {
            return false;
        }
        ItemStack equipment = inventory.getItem(EQUIPMENT_SLOT);
        if (!EquipmentSlotItemAdapters.canRemove(equipment, interfaceId, playerInventory.player)) return false;
        if (equipment.isEmpty() || !EquipmentStructureApi.hasStructure(equipment)) {
            return false;
        }
        var installed = EquipmentStructureApi.component(equipment, interfaceId);
        if (installed.isEmpty() || !installed.get().equals(mirroredComponent)
                || EquipmentComponentRegistry.createValidatedItemStack(installed.get())
                .filter(restored -> ItemStack.isSameItemSameComponents(restored,
                        inventory.getItem(PART_SLOT_START))).isEmpty()) return false;
        boolean removed = EquipmentStructureApi.remove(equipment, interfaceId).isPresent();
        return removed;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive() && !player.isRemoved();
    }

    private boolean isSupportedEquipment(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (EquipmentStructureApi.hasStructure(stack)) {
            return EquipmentAssemblyLimits.supports(EquipmentStructureApi.slots(stack).size());
        }
        var registries = playerInventory.player.level().registryAccess();
        var host = EquipmentHostProviders.resolve(stack, registries);
        boolean hasTemplate = host
                .flatMap(id -> registries.registry(EquipmentStructureRegistries.HOST_DEFINITION)
                        .flatMap(registry -> registry.getOptional(id)))
                .filter(definition -> EquipmentAssemblyLimits.supports(definition.slots().size()))
                .isPresent();
        // Slot hit testing also runs on the client. A host provider can be known on
        // the client before its datapack registry has finished syncing; allow the
        // optimistic click and let the authoritative server perform initialization.
        return hasTemplate || playerInventory.player.level().isClientSide() && host.isPresent();
    }

    private void refreshDefinitions() {
        ItemStack equipment = inventory.getItem(EQUIPMENT_SLOT);
        EquipmentSlotItemAdapters.prepare(equipment, playerInventory.player.registryAccess(), playerInventory.player);
        List<EquipmentSlotDefinition> resolved = EquipmentStructureApi.slots(equipment);
        if (resolved.equals(slotDefinitions)) {
            return;
        }

        // A component slot is only meaningful for the equipment currently in the
        // center slot. When the equipment changes, discard the old mapping and
        // return pending component items instead of letting them be interpreted as
        // slots belonging to the new item.
        returnPendingComponents();
        slotDefinitions = List.copyOf(resolved);
        if (!EquipmentAssemblyLimits.supports(slotDefinitions.size())
                || selectedInterfaceId != null && slotDefinitions.stream()
                .noneMatch(value -> value.id().equals(selectedInterfaceId))) {
            selectedInterfaceId = null;
        }
    }

    private void returnPendingComponents() {
        if (playerInventory.player.level().isClientSide()) {
            return;
        }
        for (int index = 0; index < STAGING_SLOT_COUNT; index++) {
            ItemStack pending = inventory.getItem(PART_SLOT_START + index);
            if (!pending.isEmpty()) {
                if (!installedMirrorSlots.remove(index)) {
                    playerInventory.player.getInventory().placeItemBackInInventory(pending.copy());
                }
                inventory.setItem(PART_SLOT_START + index, ItemStack.EMPTY);
            }
        }
        installedMirrorSlots.clear();
        mirroredInterfaceId = null;
        mirroredComponent = null;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        // Button IDs are reserved for selecting a logical interface. No item or
        // structure mutation occurs here; subsequent slot interaction is validated
        // against the selected interface on the server.
        refreshDefinitions();
        if (id < 0 || id >= slotDefinitions.size()) {
            return false;
        }
        selectInterface(slotDefinitions.get(id).id());
        return true;
    }

    private void loadSelectedComponent() {
        if (playerInventory.player.level().isClientSide()) return;
        var installed = selectedInterfaceId == null
                ? java.util.Optional.<EquipmentComponentInstance>empty()
                : EquipmentStructureApi.component(equipmentStack(), selectedInterfaceId);
        // Integrations can update an open equipment through the public API.
        // A projection must never outlive the exact component it represents.
        if (installedMirrorSlots.contains(0) && (!java.util.Objects.equals(selectedInterfaceId, mirroredInterfaceId)
                || !java.util.Objects.equals(installed.orElse(null), mirroredComponent))) {
            returnPendingComponents();
        }
        if (!inventory.getItem(PART_SLOT_START).isEmpty()) return;
        installed.flatMap(EquipmentComponentRegistry::createValidatedItemStack)
                .ifPresent(stack -> {
                    inventory.setItem(PART_SLOT_START, stack.copyWithCount(1));
                    installedMirrorSlots.add(0);
                    mirroredInterfaceId = selectedInterfaceId;
                    mirroredComponent = installed.orElseThrow();
                    inventory.setChanged();
                });
    }

    @Override
    public void broadcastChanges() {
        refreshDefinitions();
        loadSelectedComponent();
        super.broadcastChanges();
    }

    /** Keeps cursor, slot snapshots and the menu revision in the same vanilla packet. */
    @Override
    public void broadcastFullState() {
        refreshDefinitions();
        loadSelectedComponent();
        super.broadcastFullState();
    }

    public Component statusMessage() {
        ItemStack equipment = inventory.getItem(EQUIPMENT_SLOT);
        if (equipment.isEmpty()) {
            return Component.translatable("gui.equipment_structure_api.assembly.empty");
        }
        return Component.translatable("gui.equipment_structure_api.assembly.ready");
    }

    /**
     * Handles removal of the mirrored component before vanilla mutates the slot.
     * Vanilla calls Slot#onTake after moving the item to the carried stack; an
     * event cancellation at that point cannot be rolled back for every click
     * type (especially SWAP), so installed mirrors are handled transactionally.
     */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        refreshDefinitions();
        loadSelectedComponent();
        // Vanilla repeats quickMoveStack while the source still matches. Auto-targeting must
        // install exactly one attachment per gesture, never silently fill every compatible slot.
        if (clickType == ClickType.QUICK_MOVE && slotId >= containerSize() && slotId < slots.size()) {
            if (button == 0 || button == 1) quickMoveStack(player, slotId);
            return;
        }
        if (slotId == PART_SLOT_START && (player.level().isClientSide()
                || !slots.get(PART_SLOT_START).isActive())) {
            // The logical part transaction is predicted only by the server.
            // Vanilla slot/carried synchronization supplies the result.
            return;
        }
        if (!player.level().isClientSide()
                && slotId == PART_SLOT_START
                && installedMirrorSlots.contains(0)) {
            if (clickType == ClickType.QUICK_MOVE) {
                quickMoveStack(player, slotId);
                return;
            }
            if (clickType == ClickType.PICKUP && (button == 0 || button == 1)) {
                if (!getCarried().isEmpty()) {
                    return;
                }
                ItemStack removed = takeInstalledMirror();
                if (!removed.isEmpty()) {
                    setCarried(removed);
                }
                return;
            }
            if (clickType == ClickType.SWAP && (button >= 0 && button < 9 || button == 40)) {
                ItemStack hotbar = player.getInventory().getItem(button);
                if (!hotbar.isEmpty()) {
                    return;
                }
                ItemStack removed = takeInstalledMirror();
                if (!removed.isEmpty()) {
                    player.getInventory().setItem(button, removed);
                }
                return;
            }
            if (clickType == ClickType.PICKUP_ALL) {
                // Vanilla's pickup-all loop calls Slot#onTake repeatedly. An
                // installed projection is a single logical component, so leave
                // this action to an explicit PICKUP instead of allowing a
                // partial vanilla transfer to bypass the transactional path.
                return;
            }
            if (clickType == ClickType.THROW && getCarried().isEmpty()) {
                ItemStack removed = takeInstalledMirror();
                if (!removed.isEmpty()) {
                    if (button == 0) {
                        removed.setCount(1);
                    }
                    player.drop(removed, true);
                }
                return;
            }
            // Quick-craft, clone and other non-removal actions are delegated to
            // vanilla. The occupied staging slot has mayPlace=false, so these
            // paths cannot mutate the mirror or the equipment structure.
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        // A component staging slot represents exactly one logical component;
        // quick-craft must not allocate a stack count across that slot.
        return slot.index < PART_SLOT_START || slot.index >= PART_SLOT_START + STAGING_SLOT_COUNT;
    }

    /** Applies a workspace click to its explicit interface, independent of preceding selection packets. */
    public boolean clickInterface(ResourceLocation interfaceId, int button, int action, Player player) {
        if (player != playerInventory.player || player.level().isClientSide() || !stillValid(player)) return false;
        ClickType[] types = ClickType.values();
        if (action < 0 || action >= types.length) return false;
        ClickType type = types[action];
        boolean allowed = switch (type) {
            case PICKUP, QUICK_MOVE, THROW -> button == 0 || button == 1;
            case SWAP -> button >= 0 && button < 9 || button == 40;
            default -> false; // No clone, pickup-all or quick-craft of logical projections.
        };
        if (!allowed) return false;
        refreshDefinitions();
        if (!EquipmentAssemblyLimits.supports(slotDefinitions.size()) || slotDefinitions.stream()
                .noneMatch(definition -> definition.id().equals(interfaceId))) return false;
        if (!getCarried().isEmpty()) {
            EquipmentSlotDefinition definition = slotDefinitions.stream()
                    .filter(value -> value.id().equals(interfaceId)).findFirst().orElseThrow();
            // Resolve the carried item before selection changes. An unresolved or
            // incompatible component is a no-op: preserve the current selection,
            // staging mirror and carried stack exactly as they are.
            var carriedComponent = readComponent(getCarried(), interfaceId);
            if (carriedComponent.isEmpty()
                    || !EquipmentStructureApi.checkInstall(equipmentStack(), interfaceId,
                            carriedComponent.get()).isAllowed()) {
                return false;
            }
        }
        selectInterface(interfaceId);
        dev.equipmentstructure.api.EquipmentStructureApiMod.LOGGER.debug(
                "Workspace click menu={} interface={} action={} button={} carriedBefore={}",
                containerId, interfaceId, type, button, getCarried().getCount());
        clicked(PART_SLOT_START, button, type, player);
        dev.equipmentstructure.api.EquipmentStructureApiMod.LOGGER.debug(
                "Workspace result menu={} carriedAfter={} mirrored={}",
                containerId, getCarried().getCount(), installedMirrorSlots.contains(0));
        broadcastChanges();
        return true;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        // PICKUP_ALL scans every slot, even when double-click started in the
        // player inventory. Never let this scan transfer the logical projection.
        return slot.index != PART_SLOT_START && super.canTakeItemForPickAll(stack, slot);
    }

    private ItemStack takeInstalledMirror() {
        if (mirroredInterfaceId == null) {
            return ItemStack.EMPTY;
        }
        ItemStack mirror = inventory.getItem(PART_SLOT_START).copy();
        if (mirror.isEmpty() || !removeInstalledComponent(mirroredInterfaceId)) {
            return ItemStack.EMPTY;
        }
        inventory.setItem(PART_SLOT_START, ItemStack.EMPTY);
        installedMirrorSlots.remove(0);
        mirroredInterfaceId = null;
        mirroredComponent = null;
        inventory.setChanged();
        broadcastChanges();
        return mirror;
    }

    public Component slotName(EquipmentSlotDefinition definition) {
        String path = definition.id().getPath().replace('/', '.');
        return Component.translatable("slot." + definition.id().getNamespace() + "." + path);
    }

    public int installedCount(EquipmentSlotDefinition definition) {
        ItemStack equipment = inventory.getItem(EQUIPMENT_SLOT);
        return equipment.isEmpty() ? 0 : EquipmentStructureApi.components(equipment, definition.id()).size();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (player.level().isClientSide() || index < EQUIPMENT_SLOT || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        refreshDefinitions();
        loadSelectedComponent();
        Slot slot = slots.get(index);
        if (!slot.isActive() || !slot.hasItem() || !slot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack source = slot.getItem();
        ItemStack original = source.copy();
        int containerSize = containerSize();
        if (index >= containerSize && net.neoforged.fml.ModList.get().isLoaded("curios")
                && dev.equipmentstructure.api.compat.curios.CuriosArmorCompat.definitions(player).enabled()
                && dev.equipmentstructure.api.compat.curios.PlayerBoundCurios.eligible(source, player)) {
            if (!dev.equipmentstructure.api.compat.curios.CuriosBindingActions.quickInstall(player, source)) return ItemStack.EMPTY;
            slot.setChanged(); broadcastChanges(); return original;
        }
        var inputComponent = index >= containerSize
                ? firstComponent(source)
                : java.util.Optional.<EquipmentComponentInstance>empty();
        if (index < containerSize) {
            int componentIndex = index - PART_SLOT_START;
            if (componentIndex >= 0 && componentIndex < STAGING_SLOT_COUNT
                    && installedMirrorSlots.contains(componentIndex)) {
                // A mirrored component is already part of the equipment. It
                // must be uninstalled before it can be moved to the player's
                // inventory; moving the mirror directly would leave stale data
                // in the equipment and make the component impossible to manage.
                if (!removeInstalledComponent(mirroredInterfaceId)) {
                    return ItemStack.EMPTY;
                }
                installedMirrorSlots.remove(componentIndex);
                mirroredInterfaceId = null;
                mirroredComponent = null;
                inventory.setItem(index, ItemStack.EMPTY);
                ItemStack returned = source.copy();
                if (!player.getInventory().add(returned)) {
                    player.drop(returned, false);
                }
                inventory.setChanged();
                broadcastChanges();
                return original;
            }
            if (!moveItemStackTo(source, containerSize, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (isSupportedEquipment(source) && (equipmentStack().isEmpty() || inputComponent.isEmpty())) {
            // With an empty center, a dual-role item is equipment input. With an occupied center,
            // its explicit component binding wins and is checked against compatible empty positions.
            if (!EquipmentStructureApi.hasStructure(source)) {
                EquipmentStructureApi.initializeFromProvider(
                        source, player.level().registryAccess());
            }
            if (!EquipmentStructureApi.hasStructure(source)
                    || !moveItemStackTo(source, EQUIPMENT_SLOT, EQUIPMENT_SLOT + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            if (inputComponent.isPresent()) {
                // Resolve once per shift-click. Direct slot transfer would bypass installation;
                // native readers and external adapters share this server-authoritative path.
                if (!installFromQuickMove(source, inputComponent.get())) return ItemStack.EMPTY;
            } else {
                // Standard main-inventory <-> hotbar transfer, excluding the source region.
                int hotbarStart = containerSize + 27;
                if (index < hotbarStart
                        ? !moveItemStackTo(source, hotbarStart, slots.size(), false)
                        : !moveItemStackTo(source, containerSize, hotbarStart, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }
        if (source.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        if (source.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        slot.onTake(player, source);
        refreshDefinitions();
        return original;
    }

    private boolean installFromQuickMove(ItemStack source, EquipmentComponentInstance component) {
        if (playerInventory.player.level().isClientSide() || source.isEmpty() || !EquipmentFeatureConfig.quickInstall()) {
            return false;
        }
        refreshDefinitions();
        ItemStack equipment = inventory.getItem(EQUIPMENT_SLOT);
        if (equipment.isEmpty() || !EquipmentStructureApi.hasStructure(equipment)
                || !EquipmentAssemblyLimits.supports(slotDefinitions.size())) {
            return false;
        }
        if (!inventory.getItem(PART_SLOT_START).isEmpty() && !installedMirrorSlots.contains(0)) return false;
        var structure = EquipmentStructureApi.structure(equipment).orElseThrow();
        var candidates = new java.util.ArrayList<EquipmentSlotDefinition>();
        for (var value : slotDefinitions) {
            if (structure.component(value.id()).isEmpty()) candidates.add(value);
        }
        // Full equipment is a cheap rejection: no placement search, rule evaluation or install callbacks.
        if (candidates.isEmpty()) return false;
        candidates.sort(java.util.Comparator.comparing(value -> !value.id().equals(selectedInterfaceId)));
        var gridDefinitions = dev.equipmentstructure.api.grid.GridDefinitions.registered();
        var grid = dev.equipmentstructure.api.grid.GridTransactions.resolve(structure, gridDefinitions);
        if (!grid.allowed()) return false;
        java.util.Optional<dev.equipmentstructure.api.grid.GridPlacement> placement = java.util.Optional.empty();
        var geometry = new java.util.HashMap<ResourceLocation, java.util.Optional<dev.equipmentstructure.api.grid.GridPlacement>>();
        EquipmentSlotDefinition definition = null;
        for (var candidate : candidates) {
            var resolved = readComponent(source, candidate.id());
            if (resolved.isEmpty() || !candidate.accepts(structure.equipmentType(), resolved.get())) continue;
            component = resolved.get();
            if (grid.layout().isPresent()) {
                var footprint = gridDefinitions.components().get(component.id());
                if (footprint == null) continue;
                var shapeId = component.id();
                placement = geometry.computeIfAbsent(shapeId, ignored -> dev.equipmentstructure.api.grid.GridTransactions.firstFit(
                        grid.layout().get(), candidate.id(), footprint, gridDefinitions.spaces().getOrDefault(shapeId, java.util.List.of())));
                if (placement.isEmpty()) continue;
            }
            boolean allowed = EquipmentStructureApi.checkInstallAt(equipment, candidate.id(), component, placement).isAllowed();
            if (EquipmentStructureApi.structure(equipment).orElse(null) != structure
                    || gridDefinitions != dev.equipmentstructure.api.grid.GridDefinitions.registered()) return false;
            if (allowed) { definition = candidate; break; }
        }
        if (definition == null) return false;
        // A cancelled installation is not retried on another target.
        if (EquipmentStructureApi.installAt(equipment, definition.id(), component, placement)
                != EquipmentStructureApi.InstallResult.INSTALLED) return false;
        ItemStack mirror = source.copyWithCount(1);
        source.shrink(1);
        selectInterface(definition.id());
        inventory.setItem(PART_SLOT_START, mirror);
        installedMirrorSlots.add(0);
        mirroredInterfaceId = selectedInterfaceId;
        mirroredComponent = component;
        inventory.setChanged();
        broadcastChanges();
        if (playerInventory.player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(serverPlayer,
                    new dev.equipmentstructure.api.network.AssemblySelectionPayload(containerId, definition.id()));
        }
        return true;
    }

    /** The destination chooses the integration; ordinary item adapters retain their original meaning. */
    public java.util.Optional<EquipmentComponentInstance> readComponent(ItemStack stack, ResourceLocation slot) {
        return EquipmentSlotItemAdapters.read(stack, equipmentStack(), slot, playerInventory.player);
    }

    private java.util.Optional<EquipmentComponentInstance> firstComponent(ItemStack stack) {
        for (var slot : slotDefinitions) {
            if (EquipmentStructureApi.component(equipmentStack(), slot.id()).isPresent()) continue;
            var part = readComponent(stack, slot.id());
            if (part.isPresent()) return part;
        }
        return EquipmentComponentRegistry.fromItemStack(stack);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player.level().isClientSide()) {
            return;
        }
        for (int index = 0; index < containerSize(); index++) {
            ItemStack stack = inventory.removeItemNoUpdate(index);
            if (!stack.isEmpty() && !installedMirrorSlots.remove(index - PART_SLOT_START)) {
                player.getInventory().placeItemBackInInventory(stack);
            }
        }
        installedMirrorSlots.clear();
        mirroredInterfaceId = null;
        mirroredComponent = null;
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int row = 0; row < EquipmentAssemblyLayout.PLAYER_INVENTORY_ROWS; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        EquipmentAssemblyLayout.playerSlotX(column),
                        EquipmentAssemblyLayout.playerSlotY(row)));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column,
                    EquipmentAssemblyLayout.playerSlotX(column),
                    EquipmentAssemblyLayout.playerSlotY(3)));
        }
    }

    private static MenuData readMenuData(RegistryFriendlyByteBuf data) {
        int count = data.readVarInt();
        if (!EquipmentAssemblyLimits.supports(count)) {
            throw new IllegalArgumentException("Invalid assembly slot count: " + count);
        }
        List<EquipmentSlotDefinition> definitions = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            definitions.add(new EquipmentSlotDefinition(
                    data.readResourceLocation(),
                    data.readResourceLocation(),
                    data.readResourceLocation()
            ));
        }
        return new MenuData(definitions);
    }

    public static void writeDefinitions(RegistryFriendlyByteBuf data,
                                        List<EquipmentSlotDefinition> definitions) {
        if (definitions == null) {
            throw new IllegalArgumentException("Assembly slot definitions cannot be null");
        }
        if (!EquipmentAssemblyLimits.supports(visibleSlotCount(definitions))) {
            throw new IllegalArgumentException("Invalid assembly slot count: " + definitions.size());
        }
        data.writeVarInt(definitions.size());
        for (EquipmentSlotDefinition definition : definitions) {
            data.writeResourceLocation(definition.id());
            data.writeResourceLocation(definition.interfaceType());
            data.writeResourceLocation(definition.componentType());
        }
    }

    private record MenuData(List<EquipmentSlotDefinition> definitions) {
    }

    public static int visibleSlotCount(List<EquipmentSlotDefinition> definitions) {
        int count = definitions.size();
        if (!EquipmentAssemblyLimits.supports(count)) {
            throw new IllegalArgumentException("Invalid assembly interface count: " + count);
        }
        return count;
    }
}
