package dev.equipmentstructure.api;

import dev.equipmentstructure.api.event.EquipmentStructureChangedEvent;
import dev.equipmentstructure.api.event.EquipmentStructureInstallEvent;
import dev.equipmentstructure.api.event.EquipmentStructureRemoveEvent;
import dev.equipmentstructure.api.event.EquipmentStructureExtensionEvent;
import dev.equipmentstructure.api.event.EquipmentStructureMigrationEvent;
import dev.equipmentstructure.api.attribute.EquipmentAttributeResolver;
import dev.equipmentstructure.api.attribute.EquipmentAttributeValue;
import dev.equipmentstructure.api.attribute.EquipmentAttributeSnapshot;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.RegistryAccess;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
import dev.equipmentstructure.api.appearance.AppearancePose;
import dev.equipmentstructure.api.appearance.AppearancePoseStorage;
import dev.equipmentstructure.api.appearance.AppearanceVisibility;
import dev.equipmentstructure.api.appearance.AppearanceVisibilityStorage;
import java.util.Map;
import dev.equipmentstructure.api.grid.*;

/**
 * Equipment Structure API 的公共读写入口。
 *
 * <p>所有结构修改都应在服务端所属游戏线程调用，并通过 ItemStack Data Component 保存。
 * 属性层只解释由部件作者注册的属性贡献，不绑定材料、熔炼或具体装备内容。</p>
 * <p>可取消事件用于检查或拒绝操作。若回调替换了当前结构，外层操作返回冲突而不覆盖
 * 新快照；这不是跨线程事务，也不会回滚作者回调产生的其他副作用。</p>
 */
public final class EquipmentStructureApi {

    private EquipmentStructureApi() {
    }

    public static Optional<EquipmentStructure> structure(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        return Optional.ofNullable(stack.get(EquipmentStructureDataComponents.EQUIPMENT_STRUCTURE.get()));
    }

    public static boolean hasStructure(ItemStack stack) {
        return structure(stack).isPresent();
    }

    /**
     * 从当前世界的模板 Registry 创建结构并写入装备。
     *
     * <p>仅初始化没有结构的非空装备。已有结构或模板不存在时返回 false，保留全部数据。
     * 更新已有装备请使用 migrate；主动重建结构使用底层 setStructure。</p>
     */
    public static boolean initialize(
            ItemStack stack,
            RegistryAccess registries,
            ResourceLocation hostId
    ) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(registries, "registries");
        Objects.requireNonNull(hostId, "hostId");
        if (stack.isEmpty() || hasStructure(stack)) {
            return false;
        }
        return registries.registry(EquipmentStructureRegistries.HOST_DEFINITION)
                .flatMap(registry -> registry.getOptional(hostId))
                .filter(definition -> definition.id().equals(hostId))
                .map(definition -> {
                    GridDefinitionSync.loadTemplates(registries);
                    var plan = GridTransactions.initializeOrRefresh(definition.createStructure(), GridDefinitions.registered());
                    if (!plan.allowed()) return false;
                    setStructure(stack, plan.structure());
                    return true;
                })
                .orElse(false);
    }

    /** 根据已注册 Provider 自动识别装备模板并初始化结构。 */
    public static boolean initializeFromProvider(
            ItemStack stack,
            RegistryAccess registries
    ) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(registries, "registries");
        if (stack.isEmpty() || hasStructure(stack)) {
            return false;
        }
        return EquipmentHostProviders.resolve(stack, registries)
                .map(hostId -> initialize(stack, registries, hostId))
                .orElse(false);
    }

    /** 返回装备当前的槽位快照，适合客户端界面和属性系统读取。 */
    public static List<EquipmentSlotDefinition> slots(ItemStack stack) {
        return structure(stack).map(EquipmentStructure::slots).orElse(List.of());
    }

    /** 返回指定槽位已安装部件的只读快照。 */
    public static List<EquipmentComponentInstance> components(
            ItemStack stack,
            ResourceLocation slotId
    ) {
        Objects.requireNonNull(slotId, "slotId");
        return structure(stack).map(value -> value.components(slotId)).orElse(List.of());
    }

    /** 查询一个接口当前安装的唯一部件。未知或空接口返回 empty。 */
    public static Optional<EquipmentComponentInstance> component(ItemStack stack, ResourceLocation slotId) {
        Objects.requireNonNull(slotId, "slotId");
        return structure(stack).flatMap(value -> value.component(slotId));
    }

    /**
     * Computes one final equipment attribute using the same resolver used by
     * NeoForge's item attribute event and the assembly GUI.
     */
    public static double attributeValue(ItemStack stack, Holder<Attribute> attribute,
                                         double baseValue, EquipmentSlot slot) {
        return EquipmentAttributeResolver.value(stack, attribute, baseValue, slot);
    }

    /** Main-hand convenience overload for weapon and tool integrations. */
    public static double mainHandAttributeValue(ItemStack stack, Holder<Attribute> attribute,
                                                double baseValue) {
        return EquipmentAttributeResolver.mainHandValue(stack, attribute, baseValue);
    }

    /** Returns the final value and active component contributions for UI/tooltips. */
    public static EquipmentAttributeValue attributeResult(ItemStack stack, Holder<Attribute> attribute,
                                                          double baseValue, EquipmentSlot slot) {
        return EquipmentAttributeResolver.result(stack, attribute, baseValue, slot);
    }

    /** Main-hand result overload for weapon and tool screens. */
    public static EquipmentAttributeValue mainHandAttributeResult(ItemStack stack,
                                                                  Holder<Attribute> attribute,
                                                                  double baseValue) {
        return attributeResult(stack, attribute, baseValue, EquipmentSlot.MAINHAND);
    }

    /** Resolves a group of attributes using one stable structure snapshot. */
    public static EquipmentAttributeSnapshot attributeSnapshot(
            ItemStack stack, Map<Holder<Attribute>, Double> baseValues, EquipmentSlot slot) {
        return EquipmentAttributeResolver.snapshot(stack, baseValues, slot);
    }

    /**
     * Returns the active attribute contributions from one installed component.
     * This is the same resolved view used by the assembly screen and combat
     * calculation, making it suitable for tooltips and addon UIs.
     */
    public static List<dev.equipmentstructure.api.attribute.EquipmentAttributeContribution>
    attributeContributions(ItemStack stack, ResourceLocation slotId) {
        return EquipmentAttributeResolver.contributions(stack, slotId);
    }

    /** Returns only the contributions effective for one concrete vanilla equipment slot. */
    public static List<dev.equipmentstructure.api.attribute.EquipmentAttributeContribution>
    attributeContributions(ItemStack stack, ResourceLocation slotId, EquipmentSlot equipmentSlot) {
        return EquipmentAttributeResolver.contributions(stack, slotId, equipmentSlot);
    }

    /**
     * Returns active component contributions together with their source
     * component and concrete installation point.
     */
    public static List<dev.equipmentstructure.api.attribute.EquipmentAttributeDetail>
    attributeDetails(ItemStack stack) {
        return EquipmentAttributeResolver.details(stack);
    }

    /** Returns source-aware contributions from one concrete installation point. */
    public static List<dev.equipmentstructure.api.attribute.EquipmentAttributeDetail>
    attributeDetails(ItemStack stack, ResourceLocation slotId) {
        return EquipmentAttributeResolver.details(stack, slotId);
    }

    /** Returns source-aware contributions effective for one concrete vanilla equipment slot. */
    public static List<dev.equipmentstructure.api.attribute.EquipmentAttributeDetail>
    attributeDetails(ItemStack stack, ResourceLocation slotId, EquipmentSlot equipmentSlot) {
        return EquipmentAttributeResolver.details(stack, slotId, equipmentSlot);
    }

    /** 整条纯迁移链成功且事件允许后才写入。失败不写入部分结果，也不回滚回调的副作用。 */
    public static MigrationResult migrate(ItemStack stack, int targetVersion) {
        Objects.requireNonNull(stack, "stack");
        if (targetVersion <= 0) {
            throw new IllegalArgumentException("Target structure version must be positive");
        }
        Optional<EquipmentStructure> current = structure(stack);
        if (current.isEmpty()) {
            return MigrationResult.NO_STRUCTURE;
        }
        EquipmentStructure value = current.get();
        if (value.version() == targetVersion) {
            return MigrationResult.UNCHANGED;
        }
        EquipmentStructure changed = EquipmentStructureMigrations.migrate(value, targetVersion);
        var gridDefinitions = GridDefinitions.registered();
        var gridPlan = GridTransactions.initializeOrRefresh(changed, gridDefinitions);
        if (!gridPlan.allowed()) return MigrationResult.GRID_REJECTED;
        changed = gridPlan.structure();
        if (!isCurrentStructure(stack, value)) {
            return MigrationResult.CONFLICT;
        }
        EquipmentStructureMigrationEvent before = new EquipmentStructureMigrationEvent(stack, value, changed);
        if (NeoForge.EVENT_BUS.post(before).isCanceled()) {
            return MigrationResult.CANCELED;
        }
        if (!isCurrentStructure(stack, value) || !gridDefinitions.equals(GridDefinitions.registered())) {
            return MigrationResult.CONFLICT;
        }
        setStructure(stack, changed);
        NeoForge.EVENT_BUS.post(new EquipmentStructureChangedEvent(
                stack, value, changed, EquipmentStructureChangedEvent.ChangeType.MIGRATED
        ));
        return MigrationResult.MIGRATED;
    }

    /**
     * Low-level complete replacement, intentionally bypassing mutation events and migration rules.
     * The caller owns preservation/return of existing components and synchronization of its container.
     * Prefer initialize, addSlot, extend, install, remove or migrate for ordinary changes.
     */
    public static void setStructure(ItemStack stack, EquipmentStructure structure) {
        Objects.requireNonNull(stack, "stack");
        stack.set(EquipmentStructureDataComponents.EQUIPMENT_STRUCTURE.get(),
                Objects.requireNonNull(structure, "structure"));
    }

    /** Low-level removal of all structure data. Does not return parts or fire mutation events. */
    public static void clearStructure(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        stack.remove(EquipmentStructureDataComponents.EQUIPMENT_STRUCTURE.get());
    }

    /**
     * 向装备追加一个动态槽位。
     *
     * <p>适用于材料、升级或能力在装备生成后提供额外接口的场景。重复槽位或
     * 没有结构时不会修改装备。</p>
     */
    public static SlotMutationResult addSlot(ItemStack stack, EquipmentSlotDefinition slot) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(slot, "slot");
        Optional<EquipmentStructure> current = structure(stack);
        if (current.isEmpty()) {
            return SlotMutationResult.NO_STRUCTURE;
        }
        EquipmentStructure value = current.get();
        if (value.slot(slot.id()).isPresent()) {
            return SlotMutationResult.ALREADY_EXISTS;
        }
        EquipmentStructure changed = value.addSlot(slot);
        EquipmentStructureExtensionEvent before =
                new EquipmentStructureExtensionEvent(stack, value, changed);
        if (NeoForge.EVENT_BUS.post(before).isCanceled()) {
            return SlotMutationResult.CANCELED;
        }
        if (!isCurrentStructure(stack, value)) {
            return SlotMutationResult.CONFLICT;
        }
        setStructure(stack, changed);
        NeoForge.EVENT_BUS.post(new EquipmentStructureChangedEvent(
                stack, value, changed, EquipmentStructureChangedEvent.ChangeType.EXTENDED
        ));
        return SlotMutationResult.ADDED;
    }

    /**
     * 使用一个纯结构变换器扩展装备结构。
     *
     * <p>只允许在现有接口列表末尾追加空接口；主体 ID、模式版本、原有接口定义/顺序
     * 和已装部件必须保持。返回原结构表示无需更新。变换器应为纯函数。</p>
     */
    public static ExtensionResult extend(
            ItemStack stack,
            UnaryOperator<EquipmentStructure> transformer
    ) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(transformer, "transformer");
        Optional<EquipmentStructure> current = structure(stack);
        if (current.isEmpty()) {
            return ExtensionResult.NO_STRUCTURE;
        }
        EquipmentStructure value = current.get();
        EquipmentStructure changed = Objects.requireNonNull(
                transformer.apply(value), "transformer result"
        );
        if (!value.hostId().equals(changed.hostId())) {
            throw new IllegalArgumentException("A structure extension cannot change the host ID");
        }
        if (!value.equipmentType().equals(changed.equipmentType())) {
            throw new IllegalArgumentException("A structure extension cannot change the equipment type");
        }
        if (changed.version() < value.version()) {
            throw new IllegalArgumentException("A structure extension cannot lower the version");
        }
        if (changed.version() != value.version()) {
            throw new IllegalArgumentException("Use migrations to advance the schema version");
        }
        if (changed.slots().size() < value.slots().size()
                || !changed.slots().subList(0, value.slots().size()).equals(value.slots())) {
            throw new IllegalArgumentException("An extension must preserve existing interface definitions and order");
        }
        if (!changed.components().equals(value.components())) {
            throw new IllegalArgumentException("An extension must preserve components and append only empty interfaces");
        }
        if (!changed.grid().equals(value.grid())) {
            throw new IllegalArgumentException("An extension must preserve the grid layout");
        }
        if (!isCurrentStructure(stack, value)) {
            return ExtensionResult.CONFLICT;
        }
        // Everything else is already validated equal; only newly appended slots can differ.
        if (value.slots().size() == changed.slots().size()) {
            return ExtensionResult.UNCHANGED;
        }
        EquipmentStructureExtensionEvent before =
                new EquipmentStructureExtensionEvent(stack, value, changed);
        if (NeoForge.EVENT_BUS.post(before).isCanceled()) {
            return ExtensionResult.CANCELED;
        }
        if (!isCurrentStructure(stack, value)) {
            return ExtensionResult.CONFLICT;
        }
        setStructure(stack, changed);
        NeoForge.EVENT_BUS.post(new EquipmentStructureChangedEvent(
                stack, value, changed, EquipmentStructureChangedEvent.ChangeType.EXTENDED
        ));
        return ExtensionResult.EXTENDED;
    }

    /** 尝试把一个部件安装到指定槽位。失败时不改变 ItemStack。 */
    public static InstallResult install(
            ItemStack stack,
            ResourceLocation slotId,
            EquipmentComponentInstance component
    ) {
        return installAt(stack, slotId, component, Optional.empty());
    }

    /** Explicit placement, or deterministic first fit when absent. Never rearranges other parts. */
    public static InstallResult installAt(ItemStack stack, ResourceLocation slotId,
            EquipmentComponentInstance component, Optional<GridPlacement> placement) {
        return installTransaction(stack, slotId, component, placement, () -> true, () -> {});
    }

    /** Internal menu commit hook. Guard is read-only; transfer must be a non-throwing owned-container write. */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static InstallResult installTransaction(ItemStack stack, ResourceLocation slotId,
            EquipmentComponentInstance component, Optional<GridPlacement> placement,
            java.util.function.BooleanSupplier guard, Runnable transfer) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(slotId, "slotId");
        Objects.requireNonNull(component, "component");

        EquipmentStructure value = structure(stack).orElse(null);
        var definitions = GridDefinitions.registered();
        var componentDefinition = EquipmentComponentRegistry.get(component.id());
        InstallCheck check = checkInstallAt(stack, slotId, component, placement);
        if (!check.isAllowed()) {
            return check.status().installResult();
        }
        if (!isCurrentStructure(stack, value)) return InstallResult.CONFLICT;
        var plan = GridTransactions.install(value, slotId, component, placement, definitions);
        if (!plan.allowed()) return InstallResult.GRID_REJECTED;
        EquipmentSlotDefinition definition = value.slot(slotId).orElseThrow();
        EquipmentStructureInstallEvent before =
                new EquipmentStructureInstallEvent(stack, value, slotId, component);
        if (NeoForge.EVENT_BUS.post(before).isCanceled()) {
            return InstallResult.CANCELED;
        }
        if (!guard.getAsBoolean() || !isCurrentStructure(stack, value)
                || !componentDefinition.equals(EquipmentComponentRegistry.get(component.id()))
                || !definitions.equals(GridDefinitions.registered())) {
            return InstallResult.CONFLICT;
        }
        EquipmentStructure changed = plan.structure();
        setStructure(stack, changed);
        transfer.run();
        var installedContext = new EquipmentComponentContext(stack, changed, definition, component);
        NeoForge.EVENT_BUS.post(new EquipmentStructureChangedEvent(
                stack, value, changed, EquipmentStructureChangedEvent.ChangeType.INSTALLED
        ));
        EquipmentComponentBehaviorRegistry.installed(installedContext);
        return InstallResult.INSTALLED;
    }

    /**
     * 在不修改装备的前提下检查部件是否可以安装。
     *
     * <p>这个入口用于装配界面、配方查看器和其他模组的兼容性预览。返回的
     * 槽位快照来自检查时的结构；调用方仍应以 {@link #install} 的服务端结果
     * 作为最终依据。</p>
     */
    public static InstallCheck checkInstall(
            ItemStack stack,
            ResourceLocation slotId,
            EquipmentComponentInstance component
    ) {
        return checkInstallAt(stack, slotId, component, Optional.empty());
    }

    public static InstallCheck checkInstallAt(ItemStack stack, ResourceLocation slotId,
            EquipmentComponentInstance component, Optional<GridPlacement> placement) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(slotId, "slotId");
        Objects.requireNonNull(component, "component");

        Optional<EquipmentStructure> current = structure(stack);
        if (current.isEmpty()) {
            return new InstallCheck(InstallCheckStatus.NO_STRUCTURE, slotId, Optional.empty());
        }

        EquipmentStructure value = current.get();
        Optional<EquipmentSlotDefinition> slot = value.slot(slotId);
        if (slot.isEmpty()) {
            return new InstallCheck(InstallCheckStatus.UNKNOWN_SLOT, slotId, Optional.empty());
        }

        EquipmentSlotDefinition definition = slot.get();
        if (!definition.accepts(value.equipmentType(), component)) {
            return new InstallCheck(InstallCheckStatus.INCOMPATIBLE_TYPE, slotId, slot);
        }
        if (value.component(slotId).isPresent()) {
            return new InstallCheck(InstallCheckStatus.SLOT_FULL, slotId, slot);
        }
        if (!GridTransactions.install(value, slotId, component, placement, GridDefinitionSync.current()).allowed()) {
            return new InstallCheck(InstallCheckStatus.GRID_REJECTED, slotId, slot);
        }
        if (!EquipmentComponentBehaviorRegistry.canInstall(
                new EquipmentComponentContext(stack, value, definition, component))) {
            return new InstallCheck(InstallCheckStatus.BEHAVIOR_REJECTED, slotId, slot);
        }
        return new InstallCheck(InstallCheckStatus.AVAILABLE, slotId, slot);
    }

    /**
     * Updates only the visual pose stored in one installed component. The
     * component identity, gameplay data and all other interfaces remain intact.
     */
    public static boolean setAppearancePose(ItemStack stack, ResourceLocation slotId, AppearancePose pose) {
        var component = component(stack, slotId);
        if (component.isEmpty()) return false;
        return setAppearancePresentation(stack, slotId, pose, AppearanceVisibilityStorage.read(stack, component.get()));
    }

    /** Atomically saves the selected part's pose/visibility and the equipment's original-model visibility. */
    public static boolean setAppearancePresentation(ItemStack stack, ResourceLocation slotId, AppearancePose pose,
                                                     AppearanceVisibility visibility) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(slotId, "slotId");
        Objects.requireNonNull(pose, "pose");
        Objects.requireNonNull(visibility, "visibility");
        Optional<EquipmentStructure> current = structure(stack);
        if (current.isEmpty() || current.get().component(slotId).isEmpty()) return false;
        EquipmentStructure previous = current.get();
        EquipmentComponentInstance component = previous.component(slotId).orElseThrow();
        EquipmentComponentInstance changedComponent = AppearanceVisibilityStorage.withComponentVisible(
                AppearancePoseStorage.with(component, pose), visibility.componentVisible());
        if (changedComponent.equals(component)
                && visibility.originalVisible() == AppearanceVisibilityStorage.originalVisible(stack)) return true;
        if (!isCurrentStructure(stack, previous)) return false;
        EquipmentStructure changed = previous.withComponent(slotId, changedComponent);
        setStructure(stack, changed);
        AppearanceVisibilityStorage.setOriginalVisible(stack, visibility.originalVisible());
        NeoForge.EVENT_BUS.post(new EquipmentStructureChangedEvent(
                stack, previous, changed, EquipmentStructureChangedEvent.ChangeType.APPEARANCE_UPDATED));
        return true;
    }

    /** Validates every target before atomically saving a whole editor session. */
    public static boolean setAppearancePresentations(ItemStack stack,
            java.util.Map<ResourceLocation, dev.equipmentstructure.api.appearance.AppearancePartPresentation> edits,
            boolean originalVisible) {
        Objects.requireNonNull(stack, "stack");
        var snapshot = java.util.Map.copyOf(edits);
        var previous = structure(stack).orElse(null);
        if (previous == null || snapshot.keySet().stream().anyMatch(slot -> previous.component(slot).isEmpty())) return false;
        var parts = new java.util.HashMap<>(previous.components());
        snapshot.forEach((slot, edit) -> parts.put(slot, java.util.List.of(edit.apply(previous.component(slot).orElseThrow()))));
        var changed = new EquipmentStructure(previous.hostId(), previous.equipmentType(),
                previous.slots(), parts, previous.version(), previous.grid());
        if (changed.equals(previous) && originalVisible == AppearanceVisibilityStorage.originalVisible(stack)) return true;
        if (!isCurrentStructure(stack, previous)) return false;
        setStructure(stack, changed);
        AppearanceVisibilityStorage.setOriginalVisible(stack, originalVisible);
        NeoForge.EVENT_BUS.post(new EquipmentStructureChangedEvent(
                stack, previous, changed, EquipmentStructureChangedEvent.ChangeType.APPEARANCE_UPDATED));
        return true;
    }

    /** 通过接口 ID 移除唯一部件；受现有可取消事件控制。 */
    public static Optional<EquipmentComponentInstance> remove(ItemStack stack, ResourceLocation slotId) {
        return removeTransaction(stack, slotId, () -> true, () -> {});
    }

    /** Read-only removal preview. Does not fire events or transfer any items. */
    public static RemoveCheck checkRemove(ItemStack stack, ResourceLocation slotId) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(slotId, "slotId");
        Optional<EquipmentStructure> current = structure(stack);
        if (current.isEmpty()) {
            return new RemoveCheck(RemoveCheckStatus.NO_STRUCTURE, Optional.empty());
        }
        if (current.get().slot(slotId).isEmpty()) {
            return new RemoveCheck(RemoveCheckStatus.UNKNOWN_SLOT, Optional.empty());
        }
        Optional<EquipmentComponentInstance> component = current.get().component(slotId);
        if (component.isEmpty()) {
            return new RemoveCheck(RemoveCheckStatus.EMPTY_SLOT, component);
        }
        if (!dev.equipmentstructure.api.grid.space.ComponentSpaceContents.canRemove(component.get(), GridDefinitionSync.current()))
            return new RemoveCheck(RemoveCheckStatus.BEHAVIOR_REJECTED, component);
        boolean removable = EquipmentComponentRegistry.get(component.get().id())
                .map(EquipmentComponentDefinition::removable).orElse(true);
        if (!removable) return new RemoveCheck(RemoveCheckStatus.NOT_REMOVABLE, component);
        if (!EquipmentComponentBehaviorRegistry.canRemove(new EquipmentComponentContext(
                stack, current.get(), current.get().slot(slotId).orElseThrow(), component.get()))) {
            return new RemoveCheck(RemoveCheckStatus.BEHAVIOR_REJECTED, component);
        }
        return new RemoveCheck(RemoveCheckStatus.AVAILABLE, component);
    }

    /** Internal menu commit hook; see installTransaction. */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static Optional<EquipmentComponentInstance> removeTransaction(ItemStack stack, ResourceLocation slotId,
            java.util.function.BooleanSupplier guard, Runnable transfer) {
        EquipmentStructure value = structure(stack).orElse(null);
        var gridDefinitions = GridDefinitions.registered();
        RemoveCheck check = checkRemove(stack, slotId);
        if (!check.isAllowed()) {
            return Optional.empty();
        }
        if (!isCurrentStructure(stack, value)) return Optional.empty();
        EquipmentComponentInstance target = check.component().orElseThrow();
        var componentDefinition = EquipmentComponentRegistry.get(target.id());
        EquipmentStructureRemoveEvent before =
                new EquipmentStructureRemoveEvent(stack, value, slotId, target);
        if (NeoForge.EVENT_BUS.post(before).isCanceled()) {
            return Optional.empty();
        }
        if (!guard.getAsBoolean() || !isCurrentStructure(stack, value)
                || !gridDefinitions.equals(GridDefinitions.registered())
                || !componentDefinition.equals(EquipmentComponentRegistry.get(target.id()))) {
            return Optional.empty();
        }
        EquipmentStructure changed = GridTransactions.remove(value, slotId, GridDefinitions.registered());
        setStructure(stack, changed);
        transfer.run();
        var removedContext = new EquipmentComponentContext(stack, value, value.slot(slotId).orElseThrow(), target);
        NeoForge.EVENT_BUS.post(new EquipmentStructureChangedEvent(
                stack, value, changed, EquipmentStructureChangedEvent.ChangeType.REMOVED
        ));
        EquipmentComponentBehaviorRegistry.removed(removedContext);
        return Optional.of(target);
    }

    /**
     * Compatibility entry into the automatic equipment behavior dispatcher.
     * The stack must be the live item in the specified entity equipment slot.
     * Reconciles all equipped components, then ticks this location at most once
     * per server game tick, shared with automatic dispatch. Most integrations
     * should only register their behavior and let the API drive it.
     */
    public static void tickComponents(ItemStack stack, LivingEntity wearer, EquipmentSlot equipmentSlot) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(wearer, "wearer");
        Objects.requireNonNull(equipmentSlot, "equipmentSlot");
        if (wearer.level().isClientSide() || stack.isEmpty() || wearer.getItemBySlot(equipmentSlot) != stack) return;
        EquipmentComponentRuntime.tick(wearer, java.util.EnumSet.of(equipmentSlot));
    }

    /**
     * Updates one installed part's data without reinstalling it. Server thread
     * only. The expected instance must be the current snapshot obtained from
     * component() or a tick context, so delayed updates cannot alter a replacement.
     * The transformer receives a defensive data copy and must be pure. Exceptions
     * propagate without writing a partial result. Identity and other parts remain intact.
     */
    public static ComponentDataResult updateComponentData(ItemStack stack, ResourceLocation slotId,
            EquipmentComponentInstance expected,
            UnaryOperator<net.minecraft.nbt.CompoundTag> transformer) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(slotId, "slotId");
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(transformer, "transformer");
        var previous = structure(stack).orElse(null);
        if (previous == null) return ComponentDataResult.NO_STRUCTURE;
        if (previous.slot(slotId).isEmpty()) return ComponentDataResult.UNKNOWN_SLOT;
        var target = previous.component(slotId).orElse(null);
        if (target == null) return ComponentDataResult.EMPTY_SLOT;
        if (target != expected) return ComponentDataResult.CONFLICT;
        var data = Objects.requireNonNull(transformer.apply(target.data()), "transformer result");
        // Container contents have their own atomic transfer/consume API and cannot be silently erased by a data callback.
        var spaceKey = dev.equipmentstructure.api.grid.space.ComponentSpaceContents.KEY;
        if (!Objects.equals(target.data().get(spaceKey), data.get(spaceKey))) return ComponentDataResult.CONFLICT;
        if (!isCurrentStructure(stack, previous)) return ComponentDataResult.CONFLICT;
        var updated = new EquipmentComponentInstance(target.id(), target.componentType(), target.interfaceType(), data);
        if (updated.equals(target)) return ComponentDataResult.UNCHANGED;
        // Editing component data must not rerun installation side effects.
        var parts = new java.util.HashMap<>(previous.components());
        parts.put(slotId, List.of(updated));
        var changed = new EquipmentStructure(previous.hostId(), previous.equipmentType(),
                previous.slots(), parts, previous.version(), previous.grid());
        setStructure(stack, changed);
        NeoForge.EVENT_BUS.post(new EquipmentStructureChangedEvent(
                stack, previous, changed, EquipmentStructureChangedEvent.ChangeType.COMPONENT_DATA_UPDATED));
        return ComponentDataResult.UPDATED;
    }

    /** 检查装备是否包含结构，以及当前网格状态是否合法。空安装点是合法状态。 */
    public static ValidationResult validate(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        Optional<EquipmentStructure> current = structure(stack);
        if (current.isEmpty()) {
            return new ValidationResult(false, ValidationStatus.NO_STRUCTURE);
        }
        if (!GridTransactions.resolve(current.get(), GridDefinitionSync.current()).allowed()) {
            return new ValidationResult(false, ValidationStatus.INVALID_GRID);
        }
        return new ValidationResult(true, ValidationStatus.VALID);
    }

    private static boolean isCurrentStructure(ItemStack stack, EquipmentStructure expected) {
        // Immutable Data Components are stable references until replaced; no full NBT comparison per click.
        return !stack.isEmpty()
                && stack.get(EquipmentStructureDataComponents.EQUIPMENT_STRUCTURE.get()) == expected;
    }

    /** Read-only geometry resolved against the calling logical side's definitions. */
    public static Optional<GridTransactions.View> gridLayout(ItemStack stack) {
        return structure(stack).map(value -> GridTransactions.resolve(value, GridDefinitionSync.current()));
    }

    /** Explicit initialization or definition refresh. Existing saved origins are never rearranged. */
    public static boolean initializeGrid(ItemStack stack) {
        return initializeGridTransaction(stack, () -> true);
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public static boolean initializeGridTransaction(ItemStack stack, java.util.function.BooleanSupplier guard) {
        var previous = structure(stack).orElse(null);
        if (previous == null) return false;
        var definitions = GridDefinitions.registered();
        var plan = GridTransactions.initializeOrRefresh(previous, definitions);
        return commitGrid(stack, previous, plan, definitions, guard);
    }

    /** Server thread, compare-and-set complete structure snapshot; movement never fires install/remove hooks. */
    public static boolean setGridLayout(ItemStack stack, EquipmentStructure expected,
            Map<ResourceLocation, GridPlacement> edits) {
        return setGridLayoutTransaction(stack, expected, edits, () -> true);
    }

    @org.jetbrains.annotations.ApiStatus.Internal
    public static boolean setGridLayoutTransaction(ItemStack stack, EquipmentStructure expected,
            Map<ResourceLocation, GridPlacement> edits, java.util.function.BooleanSupplier guard) {
        if (!isCurrentStructure(stack, expected) || expected == null) return false;
        var definitions = GridDefinitions.registered();
        var plan = GridTransactions.move(expected, Map.copyOf(edits), definitions);
        return commitGrid(stack, expected, plan, definitions, guard);
    }

    private static boolean commitGrid(ItemStack stack, EquipmentStructure previous,
            GridTransactions.Plan plan, GridDefinitions definitions, java.util.function.BooleanSupplier guard) {
        if (plan.status() != GridTransactions.Status.VALID) return false;
        if (plan.structure().equals(previous)) return guard.getAsBoolean() && isCurrentStructure(stack, previous);
        var event = new dev.equipmentstructure.api.event.EquipmentGridChangeEvent(stack, previous, plan.structure());
        if (NeoForge.EVENT_BUS.post(event).isCanceled() || !guard.getAsBoolean() || !isCurrentStructure(stack, previous)
                || !definitions.equals(GridDefinitions.registered())) return false;
        setStructure(stack, plan.structure());
        NeoForge.EVENT_BUS.post(new EquipmentStructureChangedEvent(stack, previous, plan.structure(),
                EquipmentStructureChangedEvent.ChangeType.GRID_UPDATED));
        return true;
    }

    /** Replaces one component atomically. The caller owns its incoming item and the returned old instance. */
    public static Optional<EquipmentComponentInstance> replace(ItemStack stack, ResourceLocation slotId,
            EquipmentComponentInstance component, Optional<GridPlacement> placement) {
        return replaceTransaction(stack, slotId, component, placement, () -> true, () -> {});
    }

    /** Internal menu commit hook; see installTransaction. */
    @org.jetbrains.annotations.ApiStatus.Internal
    public static Optional<EquipmentComponentInstance> replaceTransaction(ItemStack stack, ResourceLocation slotId,
            EquipmentComponentInstance component, Optional<GridPlacement> placement,
            java.util.function.BooleanSupplier guard, Runnable transfer) {
        var previous = structure(stack).orElse(null);
        if (previous == null) return Optional.empty();
        var definition = previous.slot(slotId).orElse(null);
        if (definition == null || !definition.accepts(previous.equipmentType(), component)
                || !checkRemove(stack, slotId).isAllowed()) return Optional.empty();
        var old = previous.component(slotId).orElseThrow();
        var definitions = GridDefinitions.registered();
        var oldDefinition = EquipmentComponentRegistry.get(old.id());
        var newDefinition = EquipmentComponentRegistry.get(component.id());
        var plan = GridTransactions.install(previous, slotId, component, placement, definitions);
        if (!plan.allowed() || !EquipmentComponentBehaviorRegistry.canInstall(
                new EquipmentComponentContext(stack, previous, definition, component)) || !isCurrentStructure(stack, previous))
            return Optional.empty();
        if (NeoForge.EVENT_BUS.post(new EquipmentStructureRemoveEvent(stack, previous, slotId, old)).isCanceled()
                || !isCurrentStructure(stack, previous)) return Optional.empty();
        if (NeoForge.EVENT_BUS.post(new EquipmentStructureInstallEvent(stack, previous, slotId, component)).isCanceled()
                || !guard.getAsBoolean() || !isCurrentStructure(stack, previous)
                || !oldDefinition.equals(EquipmentComponentRegistry.get(old.id()))
                || !newDefinition.equals(EquipmentComponentRegistry.get(component.id()))
                || !definitions.equals(GridDefinitions.registered())) return Optional.empty();
        setStructure(stack, plan.structure());
        transfer.run();
        NeoForge.EVENT_BUS.post(new EquipmentStructureChangedEvent(stack, previous, plan.structure(),
                EquipmentStructureChangedEvent.ChangeType.REPLACED));
        EquipmentComponentBehaviorRegistry.removed(new EquipmentComponentContext(stack, previous, definition, old));
        EquipmentComponentBehaviorRegistry.installed(new EquipmentComponentContext(stack, plan.structure(), definition, component));
        return Optional.of(old);
    }

    public enum InstallResult {
        INSTALLED,
        CANCELED,
        BEHAVIOR_REJECTED,
        NO_STRUCTURE,
        UNKNOWN_SLOT,
        INCOMPATIBLE_TYPE,
        SLOT_FULL,
        GRID_REJECTED,
        CONFLICT
    }

    public enum RemoveCheckStatus {
        AVAILABLE,
        NO_STRUCTURE,
        UNKNOWN_SLOT,
        EMPTY_SLOT,
        NOT_REMOVABLE,
        BEHAVIOR_REJECTED
    }

    public enum ComponentDataResult {
        UPDATED, UNCHANGED, NO_STRUCTURE, UNKNOWN_SLOT, EMPTY_SLOT, CONFLICT
    }

    public record RemoveCheck(RemoveCheckStatus status, Optional<EquipmentComponentInstance> component) {
        public RemoveCheck {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(component, "component");
        }

        public boolean isAllowed() {
            return status == RemoveCheckStatus.AVAILABLE;
        }
    }

    /** 只读安装预检状态，不会触发事件或写入 ItemStack。 */
    public enum InstallCheckStatus {
        AVAILABLE(null),
        BEHAVIOR_REJECTED(InstallResult.BEHAVIOR_REJECTED),
        NO_STRUCTURE(InstallResult.NO_STRUCTURE),
        UNKNOWN_SLOT(InstallResult.UNKNOWN_SLOT),
        INCOMPATIBLE_TYPE(InstallResult.INCOMPATIBLE_TYPE),
        SLOT_FULL(InstallResult.SLOT_FULL),
        GRID_REJECTED(InstallResult.GRID_REJECTED);

        private final InstallResult installResult;

        InstallCheckStatus(InstallResult installResult) {
            this.installResult = installResult;
        }

        private InstallResult installResult() {
            return installResult;
        }
    }

    public record InstallCheck(
            InstallCheckStatus status,
            ResourceLocation slotId,
            Optional<EquipmentSlotDefinition> slot
    ) {
        public InstallCheck {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(slotId, "slotId");
            Objects.requireNonNull(slot, "slot");
        }

        public boolean isAllowed() {
            return status == InstallCheckStatus.AVAILABLE;
        }
    }

    public enum SlotMutationResult {
        ADDED,
        CANCELED,
        NO_STRUCTURE,
        ALREADY_EXISTS,
        CONFLICT
    }

    public enum ExtensionResult {
        EXTENDED,
        CANCELED,
        UNCHANGED,
        NO_STRUCTURE,
        CONFLICT
    }

    public enum MigrationResult {
        MIGRATED,
        UNCHANGED,
        NO_STRUCTURE,
        CANCELED,
        CONFLICT,
        GRID_REJECTED
    }

    public enum ValidationStatus {
        VALID,
        NO_STRUCTURE,
        INVALID_GRID
    }

    public record ValidationResult(
            boolean valid,
            ValidationStatus status
    ) {
        public ValidationResult {
            Objects.requireNonNull(status, "status");
        }
    }
}
