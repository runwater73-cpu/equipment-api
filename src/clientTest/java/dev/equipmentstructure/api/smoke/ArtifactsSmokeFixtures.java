package dev.equipmentstructure.api.smoke;

import artifacts.Artifacts;
import artifacts.component.ability.DeathProtectionTeleport;
import artifacts.equipment.EquipmentHelper;
import artifacts.equipment.EquipmentSlotManager;
import artifacts.event.ArtifactHooks;
import artifacts.item.WearableArtifactItem;
import artifacts.registry.ModAttributes;
import artifacts.registry.ModDataComponents;
import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.compat.curios.*;
import dev.equipmentstructure.api.menu.EquipmentAssemblyMenu;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import top.theillusivec4.curios.api.CuriosApi;
import java.util.*;

/** Real addon acceptance fixture, excluded from normal and release builds. */
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID)
public final class ArtifactsSmokeFixtures {
    static final String PREFIX = "ESA_ARTIFACTS ";
    static final List<String> TYPES = List.of("head", "necklace", "hands", "belt", "feet");
    private static final Map<EquipmentSlot, ItemStack> ARMOR = new EnumMap<>(EquipmentSlot.class);
    private static ServerPlayer player;
    private static int age, wait, stage;
    private static boolean done;
    private static final ResourceLocation EXTRA = ResourceLocation.parse("equipment_structure_api:artifacts_test_capacity");
    private static ItemStack detached;

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!Boolean.getBoolean("equipment_structure_api.artifactsSmoke") || !(event.getEntity() instanceof ServerPlayer p)) return;
        player = p; age = wait = stage = 0; done = false; ARMOR.clear();
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.getInventory().clearContent(); p.getInventory().selected = 0;
        for (var slot : EquipmentSlot.values()) p.setItemSlot(slot, ItemStack.EMPTY);
        CuriosArmorCompat.beginNativeLoad();
        try { CuriosApi.getCuriosInventory(p).orElseThrow().reset(); }
        finally { CuriosArmorCompat.endNativeLoad(); }
        p.serverLevel().setDayTime(6000);
        p.serverLevel().setWeatherParameters(6000, 0, false, false);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (player == null || player.hasDisconnected() || done) return;
        if (++age > 3000) { finish("FAILED server timeout stage=" + stage); return; }
        if (++wait < 30) return;
        try {
            switch (stage) {
                case 0 -> {
                    check(Artifacts.CONFIG.general.slots.enableCuriosCompat.get(), "Artifacts Curios compatibility enabled");
                    for (var pos : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                        var host = new ItemStack(switch (pos) {
                            case HEAD -> Items.DIAMOND_HELMET; case CHEST -> Items.DIAMOND_CHESTPLATE;
                            case LEGS -> Items.DIAMOND_LEGGINGS; case FEET -> Items.DIAMOND_BOOTS;
                            default -> throw new AssertionError();
                        });
                        CuriosEquipmentTemplates.prepare(host, player); ARMOR.put(pos, host);
                    }
                    validateCatalog();
                    install("snorkel", "head", 0); install("cross_necklace", "necklace", 0);
                    install("power_glove", "hands", 0); install("fire_gauntlet", "hands", 1);
                    install("cloud_in_a_bottle", "belt", 0); install("aqua_dashers", "feet", 0);
                    ARMOR.forEach(player::setItemSlot);
                }
                case 1 -> {
                    for (var id : List.of("snorkel", "cross_necklace", "power_glove", "fire_gauntlet", "cloud_in_a_bottle", "aqua_dashers"))
                        check(CuriosApi.getCuriosInventory(player).orElseThrow().findCurios(item(id)).size() == 1, "native query " + id);
                    int count = EquipmentHelper.reduceEquipment(player, 0, (stack, n) -> n + (stack.getItem() instanceof WearableArtifactItem ? 1 : 0));
                    check(count == 6, "Artifacts sees six accessories on four armor items: " + count);
                    assertModifier("power_glove", Attributes.ATTACK_DAMAGE, true);
                    assertModifier("cross_necklace", ModAttributes.INVINCIBILITY_TICKS, true);
                    check(EquipmentHelper.hasAbilityActive(ModDataComponents.DOUBLE_JUMP.get(), player, true), "native double jump ability");
                    player.setSprinting(true);
                    check(ArtifactHooks.onFluidCollision(player, Fluids.WATER.defaultFluidState()), "aqua dashers native fluid collision");
                    player.setSprinting(false);
                    check(!ArtifactHooks.onFluidCollision(player, Fluids.WATER.defaultFluidState()), "fluid collision condition");
                    var mob = EntityType.ZOMBIE.create(player.serverLevel());
                    check(mob != null && EquipmentSlotManager.tryEquipAccessory(mob, new ItemStack(item("snorkel"))), "mob native auto equip remains supported");
                    check(CuriosApi.getCuriosInventory(mob).orElseThrow().findCurios(item("snorkel")).size() == 1, "mob owns auto-equipped item");
                    detached = ARMOR.get(EquipmentSlot.CHEST); player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                }
                case 2 -> {
                    var source = new ItemStack(item("golden_hook"));
                    boolean claimed = EquipmentSlotManager.tryEquipAccessory(player, source);
                    check(CuriosApi.getCuriosInventory(player).orElseThrow().findCurios(item("golden_hook")).isEmpty(), "external equip cannot bypass UI");
                    check(source.getCount() == 1, "external equip retains source");
                    EquipmentStructureApiMod.LOGGER.info("ARTIFACTS_AUTO_EQUIP_EMPTY reportedSuccess={} installed=false", claimed);
                    check(!claimed, "rejected external equip reports failure");
                    assertModifier("power_glove", Attributes.ATTACK_DAMAGE, false);
                    assertModifier("cross_necklace", ModAttributes.INVINCIBILITY_TICKS, false);
                    check(CuriosApi.getCuriosInventory(player).orElseThrow().findCurios(item("power_glove")).isEmpty(), "unworn query absent");
                    var saved = detached.save(player.registryAccess());
                    detached = ItemStack.parseOptional(player.registryAccess(), (net.minecraft.nbt.CompoundTag)saved);
                    ARMOR.put(EquipmentSlot.CHEST, detached); player.setItemSlot(EquipmentSlot.CHEST, detached);
                }
                case 3 -> {
                    assertModifier("power_glove", Attributes.ATTACK_DAMAGE, true);
                    replace("crystal_heart", "belt");
                }
                case 4 -> {
                    assertModifier("crystal_heart", Attributes.MAX_HEALTH, true);
                    check(!EquipmentHelper.hasAbilityActive(ModDataComponents.DOUBLE_JUMP.get(), player, true), "removed double jump absent");
                    replace("warp_drive", "belt");
                }
                case 5 -> {
                    assertModifier("crystal_heart", Attributes.MAX_HEALTH, false);
                    check(EquipmentHelper.hasAbilityActive(ModDataComponents.ENDER_PEARL_HUNGER_COST.get(), player, true), "warp drive hunger behavior");
                    check(EquipmentHelper.hasAbilityActive(ModDataComponents.ENDER_PEARL_DAMAGE_IMMUNITY.get(), player, true), "warp drive damage immunity");
                    replace("charm_of_shrinking", "necklace");
                }
                case 6 -> {
                    check(player.getAttributeValue(Attributes.SCALE) < 1, "native shrinking effect");
                    var live = nativeItem("necklace", 0);
                    live.set(ModDataComponents.DISABLED_BY_TOGGLE.get(), net.minecraft.util.Unit.INSTANCE);
                    // A real native mutable stack must be flushed before an armor copy/save.
                    var copied = ARMOR.get(EquipmentSlot.CHEST).copy();
                    check(restored(copied, "necklace", 0).has(ModDataComponents.DISABLED_BY_TOGGLE.get()), "native component update survives copy");
                    CuriosApi.getCuriosInventory(player).orElseThrow().getStacksHandler("hands").orElseThrow()
                            .addTransientModifier(new AttributeModifier(EXTRA, 3, AttributeModifier.Operation.ADD_VALUE));
                }
                case 7 -> {
                    check(player.getAttributeValue(Attributes.SCALE) == 1, "toggle removes Artifacts modifier through native change event");
                    nativeItem("necklace", 0).remove(ModDataComponents.DISABLED_BY_TOGGLE.get());
                    CuriosArmorCompat.flush(player); // Commit this synthetic same-tick native mutation before another transaction.
                    var h = CuriosApi.getCuriosInventory(player).orElseThrow().getStacksHandler("hands").orElseThrow();
                    for (var host : ARMOR.values()) CuriosEquipmentTemplates.prepare(host, player);
                    check(h.getSlots() == 5, "native five hands slots");
                    var last = new CuriosSlotKey("hands", 4, false).slotId();
                    for (var entry : ARMOR.entrySet()) check(EquipmentStructureApi.slots(entry.getValue()).stream().anyMatch(s -> s.id().equals(last))
                            == (entry.getKey() == EquipmentSlot.CHEST), "expanded hands stay on chest template");
                    install("golden_hook", "hands", 4);
                    check(CuriosBindingActions.keys(player).isEmpty(), "ordinary and temporary slots absent from covenant");
                }
                case 8 -> {
                    check(CuriosApi.getCuriosInventory(player).orElseThrow().findCurios(item("golden_hook")).size() == 1, "expanded item active");
                    CuriosApi.getCuriosInventory(player).orElseThrow().getStacksHandler("hands").orElseThrow().removeModifier(EXTRA);
                }
                case 9 -> {
                    check(player.getInventory().countItem(item("golden_hook")) == 1, "capacity loss recovers exactly once");
                    check(EquipmentStructureApi.component(ARMOR.get(EquipmentSlot.CHEST), new CuriosSlotKey("hands", 4, false).slotId()).isEmpty(), "overflow leaves armor");
                    replace("chorus_totem", "belt");
                }
                case 10 -> {
                    var totem = DeathProtectionTeleport.findTotem(player);
                    check(totem.is(item("chorus_totem")), "native death protection finds nested totem");
                    check(totem.get(ModDataComponents.DEATH_PROTECTION_TELEPORT.get()).teleportationChance().get() == 1, "deterministic native totem configuration");
                    player.setHealth(1); player.invulnerableTime = 0;
                    player.hurt(player.damageSources().generic(), 100);
                    check(player.isAlive() && player.getHealth() > 1, "real lethal damage triggers original totem hook");
                    CuriosArmorCompat.flush(player);
                    check(EquipmentStructureApi.component(ARMOR.get(EquipmentSlot.LEGS), new CuriosSlotKey("belt", 0, false).slotId()).isEmpty(), "consumed native stack clears armor ownership");
                    var chest = ARMOR.get(EquipmentSlot.CHEST);
                    chest.setDamageValue(chest.getMaxDamage() - 1); chest.hurtAndBreak(2, player, EquipmentSlot.CHEST);
                }
                case 11 -> {
                    check(player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "real armor durability break");
                    check(player.getInventory().countItem(item("power_glove")) == 1 && player.getInventory().countItem(item("fire_gauntlet")) == 1,
                            "broken armor returns accessories exactly once");
                    assertModifier("power_glove", Attributes.ATTACK_DAMAGE, false);
                    // Hand over an empty helmet to the real client; insertion must use menu packets.
                    player.getInventory().clearContent();
                    for (var slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) player.setItemSlot(slot, ItemStack.EMPTY);
                    player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_HELMET));
                    player.getInventory().setItem(9, new ItemStack(item("snorkel")));
                    check(dev.equipmentstructure.api.command.EquipmentStructureCommands.openForPlayer(player), "open automatic helmet template");
                    EquipmentStructureApiMod.LOGGER.info("ARTIFACTS_SERVER_PHASE_PASSED routing, attributes, abilities, unequip, serialization, toggle, expansion, shrink, consumption, break");
                }
                case 12 -> {
                    if (player.containerMenu instanceof EquipmentAssemblyMenu) return;
                    ItemStack helmet = ItemStack.EMPTY;
                    for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                        if (player.getInventory().getItem(i).is(Items.DIAMOND_HELMET)) {
                            helmet = player.getInventory().getItem(i); player.getInventory().setItem(i, ItemStack.EMPTY); break;
                        }
                    }
                    check(!helmet.isEmpty(), "menu returned helmet");
                    var part = EquipmentStructureApi.component(helmet, new CuriosSlotKey("head", 0, false).slotId()).orElseThrow();
                    check(dev.equipmentstructure.api.appearance.AppearancePoseStorage.read(part).orElseThrow().transform().position().x() == 2, "3D edit network commit");
                    player.setItemSlot(EquipmentSlot.HEAD, helmet);
                    finish("PASSED server"); return;
                }
                default -> throw new AssertionError("unexpected stage " + stage);
            }
            stage++; wait = 0;
        } catch (Throwable failure) {
            EquipmentStructureApiMod.LOGGER.error("ARTIFACTS_SMOKE_FAILED stage=" + stage, failure);
            finish("FAILED stage=" + stage + " " + failure);
        }
    }
    private static void validateCatalog() {
        var slots = CuriosApi.getEntitySlots(player);
        check(slots.get("hands").getSize() == 2, "real hands capacity");
        int tested = 0;
        for (var item : BuiltInRegistries.ITEM) if (item instanceof WearableArtifactItem) {
            int valid = 0;
            for (var type : TYPES) {
                var key = new CuriosSlotKey(type, 0, false);
                var host = ARMOR.get(CuriosArmorCompat.definitions(player).profile(type).armorSlot());
                boolean nativeValid = CuriosApi.isStackValid(CuriosArmorCompat.context(player, key), new ItemStack(item));
                var part = EquipmentSlotItemAdapters.read(new ItemStack(item), host, key.slotId(), player);
                check(part.isPresent() == nativeValid, "native tag matrix " + item + " / " + type);
                if (part.isPresent()) {
                    check(ItemStack.isSameItemSameComponents(new ItemStack(item), EquipmentComponentRegistry.createItemStack(part.get()).orElseThrow()), "lossless data components " + item);
                    valid++;
                }
            }
            check(valid > 0, "routable wearable " + item); tested++;
        }
        EquipmentStructureApiMod.LOGGER.info("ARTIFACTS_CATALOG_PASSED {} wearables across five native types", tested);
    }
    static Item item(String id) { return BuiltInRegistries.ITEM.get(ResourceLocation.parse("artifacts:" + id)); }
    private static ItemStack nativeItem(String type, int index) { return CuriosApi.getCuriosInventory(player).orElseThrow().getStacksHandler(type).orElseThrow().getStacks().getStackInSlot(index); }
    private static ItemStack restored(ItemStack host, String type, int index) {
        return EquipmentComponentRegistry.createItemStack(EquipmentStructureApi.component(host, new CuriosSlotKey(type, index, false).slotId()).orElseThrow()).orElseThrow();
    }
    private static void install(String id, String type, int index) {
        var host = ARMOR.get(CuriosArmorCompat.definitions(player).profile(type).armorSlot());
        CuriosEquipmentTemplates.prepare(host, player); var key = new CuriosSlotKey(type, index, false).slotId();
        var part = EquipmentSlotItemAdapters.read(new ItemStack(item(id)), host, key, player).orElseThrow();
        var result = EquipmentStructureApi.install(host, key, part);
        check(result == EquipmentStructureApi.InstallResult.INSTALLED, "install " + id + ": " + result);
    }
    private static void replace(String id, String type) {
        var host = ARMOR.get(CuriosArmorCompat.definitions(player).profile(type).armorSlot());
        EquipmentStructureApi.remove(host, new CuriosSlotKey(type, 0, false).slotId()); install(id, type, 0);
    }
    private static void assertModifier(String id, net.minecraft.core.Holder<Attribute> attribute, boolean exists) {
        var entry = new ItemStack(item(id)).get(ModDataComponents.ATTRIBUTE_MODIFIERS.get()).entries().stream().filter(e -> e.attribute().equals(attribute)).findFirst().orElseThrow();
        var actual = player.getAttribute(attribute).getModifier(entry.id());
        check((actual != null) == exists, "native modifier " + id + " present=" + exists);
        if (exists) check(Math.abs(actual.amount() - entry.amount().get()) < 0.00001, "original configured value " + id);
    }
    static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void finish(String result) { done = true; EquipmentStructureApiMod.LOGGER.info("ARTIFACTS_SMOKE_{}", result); player.sendSystemMessage(Component.literal(PREFIX + result)); }
}
