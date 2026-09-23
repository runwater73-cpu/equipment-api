package dev.equipmentstructure.api.smoke;

import com.xiaoyue.celestial_artifacts.data.CAModConfig;
import com.xiaoyue.celestial_core.content.generic.PlayerFlagData;
import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.compat.curios.*;
import dev.equipmentstructure.api.menu.EquipmentAssemblyMenu;
import dev.xkmc.curseofpandora.init.data.CoPConfig;
import dev.xkmc.l2hostility.compat.curios.CurioCompat;
import dev.xkmc.l2hostility.content.item.curio.core.CurseCurioItem;
import dev.xkmc.l2hostility.content.item.curio.misc.PocketOfRestoration;
import dev.xkmc.l2hostility.content.item.traits.SealedItem;
import dev.xkmc.l2hostility.init.registrate.LHItems;
import dev.xkmc.l2tabs.compat.api.AccessoriesMultiplex;
import dev.xkmc.l2tabs.compat.common.CuriosMenuPvd;
import dev.xkmc.pandora.content.base.IPandoraHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.event.CurioCanEquipEvent;
import java.util.*;

/** Real Celestial 2.0.4, Hostility 3.0.18 and Pandora 3.0.7; never included in a release JAR. */
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID)
public final class CelestialSmokeFixtures {
    static final String PREFIX = "ESA_CELESTIAL ";
    static final CuriosSlotKey BOUND = new CuriosSlotKey("catastrophe", 0, false);
    private static final Map<EquipmentSlot, ItemStack> ARMOR = new EnumMap<>(EquipmentSlot.class);
    private static ServerPlayer player;
    private static int age, wait, stage, baseHead, baseRing, bagsBeforeBreak;
    private static boolean done, denyGrant;
    private static ItemStack bag, pocket, detached;
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!Boolean.getBoolean("equipment_structure_api.celestialSmoke") || !(event.getEntity() instanceof ServerPlayer p)) return;
        player = p; age = wait = stage = 0; done = false; denyGrant = true; ARMOR.clear();
        p.setGameMode(GameType.SURVIVAL); p.getInventory().clearContent(); p.getInventory().selected = 0;
        for (var slot : EquipmentSlot.values()) p.setItemSlot(slot, ItemStack.EMPTY);
        CuriosArmorCompat.beginNativeLoad();
        try { CuriosApi.getCuriosInventory(p).orElseThrow().reset(); }
        finally { CuriosArmorCompat.endNativeLoad(); }
        CAModConfig.SERVER.misc.catastropheScrollEquipOnStart.set(true);
        CAModConfig.SERVER.misc.catastropheScrollPreventUnequip.set(true);
        CoPConfig.SERVER.curse.curseOfSpellSlot.set("head#2");
        p.serverLevel().setDayTime(6000);
    }
    @SubscribeEvent public static void deny(CurioCanEquipEvent event) {
        if (denyGrant && event.getSlotContext().entity() == player && event.getStack().is(item("celestial_artifacts:catastrophe_scroll")))
            event.setEquipResult(net.neoforged.neoforge.common.util.TriState.FALSE);
    }
    @SubscribeEvent public static void clonePlayer(PlayerEvent.Clone event) {
        if (event.getOriginal() == player && event.getEntity() instanceof ServerPlayer next) player = next;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (player == null || player.hasDisconnected() || done) return;
        if (++age > 3600) { finish("FAILED timeout stage=" + stage); return; }
        if (++wait < 40) return;
        try {
            switch (stage) {
                case 0 -> {
                    check(nativeItem("catastrophe", 0).isEmpty(), "equip veto prevents startup grant");
                    check(!PlayerFlagData.HOLDER.getOrCreate(player).hasFlag("cs"), "rejected startup must remain retryable");
                    denyGrant = false;
                }
                case 1 -> {
                    checkBound();
                    check(PlayerFlagData.HOLDER.getOrCreate(player).hasFlag("cs"), "successful startup records original flag");
                    check(PlayerBoundCurios.remove(player, BOUND.slotId()).isEmpty(), "survival removal obeys Celestial lock");
                    check(CuriosBindingActions.keys(player).equals(List.of(BOUND)), "only real player binding in covenant");
                    check(!CuriosArmorCompat.managesType(player, "pandora"), "private Pandora slot remains native");
                    baseHead = slots("head"); baseRing = slots("ring");
                    for (var position : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                        var host = new ItemStack(switch(position) {
                            case HEAD -> Items.DIAMOND_HELMET; case CHEST -> Items.DIAMOND_CHESTPLATE;
                            case LEGS -> Items.DIAMOND_LEGGINGS; default -> Items.DIAMOND_BOOTS;
                        });
                        ARMOR.put(position, host); player.setItemSlot(position, host); CuriosEquipmentTemplates.prepare(host, player);
                    }
                    verifyRouting();
                    validateCatalog();
                    install(stack("celestial_artifacts:precious_bracelet"), "bracelet", 0);
                    install(stack("celestial_artifacts:origin_etching"), "etching", 0);
                    install(stack("l2hostility:curse_of_pride"), "hostility_curse", 0);
                    install(stack("l2hostility:ring_of_life"), "ring", 0);
                    bag = stack("pandora:pandora_necklace");
                    var inventory = ((IPandoraHolder)bag.getItem()).getCap(bag).orElseThrow();
                    inventory.setStackInSlot(0, stack("curseofpandora:curse_of_spell"));
                    inventory.setStackInSlot(1, stack("curseofpandora:charm_of_health"));
                    install(bag, "necklace", 0);
                }
                case 2 -> {
                    verifyRouting();
                    check(slots("head") == baseHead + 2, "Pandora nested token expands a different armor type");
                    check(slots("ring") == baseRing + 1, "Celestial bracelet grants native extra ring");
                    check(((CuriosBindingCapacity)handler("head")).equipment$stableCapacity() == baseHead, "saved token grant is not permanent binding capacity");
                    check(CuriosBindingActions.keys(player).equals(List.of(BOUND)), "new slots stay out of covenant");
                    check(CurseCurioItem.getFromPlayer(player).size() == 2, "L2 sees both ordinary curses");
                    check(!PlayerBoundCurios.eligible(stack("l2hostility:curse_of_pride"), player), "ordinary curse is removable equipment");
                    check(CuriosApi.getCuriosInventory(player).orElseThrow().isEquipped(item("curseofpandora:charm_of_health")), "Pandora nested native isEquipped query");
                    check(player.getAttributeValue(Attributes.MAX_HEALTH) > 20, "nested native attribute active");
                    check(player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) > 4.5, "Celestial configured attribute active");
                    var restored = restored(ARMOR.get(EquipmentSlot.CHEST).copy(), "hostility_curse", 0);
                    check(restored.has(LHItems.DC_PRIDE_LEVEL), "L2 tick component persisted inside armor");
                    install(stack("l2hostility:detector_glasses"), "head", baseHead + 1);
                    bag = nativeItem("necklace", 0);
                    ((IPandoraHolder)bag.getItem()).getCap(bag).orElseThrow().setStackInSlot(0, ItemStack.EMPTY);
                    var saved = restored(ARMOR.get(EquipmentSlot.CHEST).copy(), "necklace", 0);
                    check(IPandoraHolder.getItems(saved).getFirst().isEmpty(), "nested container mutation flushes before armor copy");
                }
                case 3 -> {
                    var token = dev.xkmc.l2core.init.L2LibReg.CONDITIONAL.type().getOrCreate(player).getData(
                            ((dev.xkmc.curseofpandora.content.complex.ITokenProviderItem<?>)item("curseofpandora:curse_of_spell")).getKey());
                    EquipmentStructureApiMod.LOGGER.info("CELESTIAL_TOKEN_PROBE slots={} base={} content={} token={} modifiers={}", slots("head"), baseHead,
                            IPandoraHolder.getItems(nativeItem("necklace", 0)), token == null ? "null" : token.life, handler("head").getModifiers());
                    check(slots("head") == baseHead, "token removal withdraws cross-armor capacity");
                    check(player.getInventory().countItem(item("l2hostility:detector_glasses")) == 1, "cross-armor overflow returned exactly once");
                    check(EquipmentStructureApi.component(ARMOR.get(EquipmentSlot.HEAD), new CuriosSlotKey("head", baseHead + 1, false).slotId()).isEmpty(), "overflow has no second owner");
                    var access = Objects.requireNonNull(CurioCompat.decode("curios/ring/0", player));
                    access.set(SealedItem.sealItem(access.get(), 4));
                    check(nativeItem("ring", 0).is(LHItems.SEAL.get()), "L2 sealing transforms original armor-owned item");
                    install(stack("l2hostility:pocket_of_restoration"), "charm", 0);
                }
                case 4 -> {
                    check(nativeItem("ring", 0).is(item("l2hostility:ring_of_life")), "original pocket restores item to armor slot");
                    check(!nativeItem("charm", 0).has(LHItems.DC_SEAL_STACK), "original pocket relinquishes recovered item");
                    check(player.getInventory().countItem(item("l2hostility:ring_of_life")) == 0, "restoration does not duplicate");
                    pocket = stack("l2hostility:pocket_of_restoration");
                    var access = Objects.requireNonNull(CurioCompat.decode("curios/ring/0", player));
                    PocketOfRestoration.setData(pocket, SealedItem.sealItem(access.get(), 0), "curios/ring/0", 0);
                    access.set(ItemStack.EMPTY);
                    detached = ARMOR.get(EquipmentSlot.CHEST); player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                    ((PocketOfRestoration)pocket.getItem()).curioTick(CuriosArmorCompat.context(player, new CuriosSlotKey("charm", 0, false)), pocket);
                    check(player.getInventory().countItem(item("l2hostility:ring_of_life")) == 1, "restoration without armor recovers once to inventory");
                    check(!pocket.has(LHItems.DC_SEAL_STACK), "native pocket completes recovery without armor");
                }
                case 5 -> {
                    checkBound();
                    check(slots("ring") == baseRing, "unequipped bracelet capacity disappears");
                    check(CurseCurioItem.getFromPlayer(player).isEmpty(), "L2 ordinary curses stop when chestplate removed");
                    check(!CuriosApi.getCuriosInventory(player).orElseThrow().isEquipped(item("curseofpandora:charm_of_health")), "nested query stops without its armor");
                    check(player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) == 4.5, "Celestial attribute removed");
                    player.setItemSlot(EquipmentSlot.CHEST, detached);
                }
                case 6 -> {
                    check(player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) > 4.5, "attribute restored on rewear");
                    check(nativeItem("ring", 0).isEmpty() && player.getInventory().countItem(item("l2hostility:ring_of_life")) == 1, "recovery remains single-owner after rewear");
                    check(AccessoriesMultiplex.get().wrap(player, 0).getSize() == 0, "L2 menu hides managed slots without changing native visibility");
                    check(handler("ring").isVisible(), "addon-visible native metadata unchanged");
                    // Pandora's native startup advancement already gave another bag to inventory.
                    bagsBeforeBreak = player.getInventory().countItem(item("pandora:pandora_necklace"));
                    detached.setDamageValue(detached.getMaxDamage() - 1); detached.hurtAndBreak(2, player, EquipmentSlot.CHEST);
                }
                case 7 -> {
                    checkBound();
                    check(player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "real armor break");
                    check(player.getInventory().countItem(item("pandora:pandora_necklace")) == bagsBeforeBreak + 1, "break recovers nested bag once");
                    CAModConfig.SERVER.misc.catastropheScrollPreventUnequip.set(false);
                    var removed = PlayerBoundCurios.remove(player, BOUND.slotId());
                    check(!removed.isEmpty() && !PlayerBoundCurios.bound(removed, player), "original config permits release");
                    CAModConfig.SERVER.misc.catastropheScrollPreventUnequip.set(true);
                    player.getInventory().clearContent();
                    player.getInventory().setItem(9, removed);
                    check(dev.equipmentstructure.api.command.EquipmentStructureCommands.openForPlayer(player), "empty-hand covenant opens");
                    EquipmentStructureApiMod.LOGGER.info("CELESTIAL_SERVER_LOGIC_PASSED startup, routing, nested effects, expansion, shrink, seal, restore, break, native removal");
                }
                case 8 -> {
                    if (player.containerMenu instanceof EquipmentAssemblyMenu) return;
                    checkBound();
                    bag = stack("pandora:pandora_necklace");
                    player.getInventory().setItem(0, bag);
                    player.getInventory().setItem(9, stack("curseofpandora:charm_of_health"));
                    bag.use(player.level(), player, InteractionHand.MAIN_HAND);
                }
                case 9 -> {
                    if (player.containerMenu.getClass().getName().contains("PandoraEditMenu")) return;
                    check(IPandoraHolder.getItems(bag).stream().filter(s -> s.is(item("curseofpandora:charm_of_health"))).count() == 1,
                            "original Pandora GUI edits nested item over real network");
                    new CuriosMenuPvd(AccessoriesMultiplex.MT_CURIOS.get()).open(player);
                }
                case 10 -> {
                    if (player.containerMenu.getClass().getName().contains("CuriosListMenu")) return;
                    player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
                }
                case 11 -> {
                    if (!player.isAlive()) return;
                    checkBound();
                    check(CuriosApi.getCuriosInventory(player).orElseThrow().findCurios(item("celestial_artifacts:catastrophe_scroll")).size() == 1, "native death/respawn keeps one bound scroll");
                    player.server.getPlayerList().saveAll();
                    finish("PASSED startup veto/retry, naked binding, original removal, death, nested Pandora, native L2 restore and UIs"); return;
                }
                default -> throw new AssertionError("stage " + stage);
            }
            EquipmentStructureApiMod.LOGGER.info("CELESTIAL_STAGE_PASSED {}", stage++); wait = 0;
        } catch (Throwable failure) {
            EquipmentStructureApiMod.LOGGER.error("CELESTIAL_SMOKE_SERVER_FAILURE", failure);
            finish("FAILED stage=" + stage + " " + failure);
        }
    }
    static Item item(String id) { var key = ResourceLocation.parse(id); check(BuiltInRegistries.ITEM.containsKey(key), "real item exists " + id); return BuiltInRegistries.ITEM.get(key); }
    static ItemStack stack(String id) { return new ItemStack(item(id)); }
    private static top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler handler(String type) { return CuriosApi.getCuriosInventory(player).orElseThrow().getStacksHandler(type).orElseThrow(); }
    private static int slots(String type) { return handler(type).getSlots(); }
    private static ItemStack nativeItem(String type, int index) { return handler(type).getStacks().getStackInSlot(index); }
    private static ItemStack restored(ItemStack armor, String type, int index) { return EquipmentComponentRegistry.createItemStack(EquipmentStructureApi.component(armor, new CuriosSlotKey(type, index, false).slotId()).orElseThrow()).orElseThrow(); }
    private static void checkBound() { check(PlayerBoundCurios.item(player, BOUND.slotId()).is(item("celestial_artifacts:catastrophe_scroll")), "scroll owned by player"); }
    private static void install(ItemStack input, String type, int index) {
        var host = ARMOR.get(CuriosArmorCompat.definitions(player).profile(type).armorSlot());
        CuriosEquipmentTemplates.prepare(host, player); var key = new CuriosSlotKey(type, index, false).slotId();
        var part = EquipmentSlotItemAdapters.read(input, host, key, player).orElseThrow(() -> new AssertionError("native validation " + input + " / " + key));
        var result = EquipmentStructureApi.install(host, key, part);
        check(result == EquipmentStructureApi.InstallResult.INSTALLED, "install " + input + " = " + result);
    }
    private static void verifyRouting() {
        ARMOR.values().forEach(a -> CuriosEquipmentTemplates.prepare(a, player));
        CuriosApi.getCuriosInventory(player).orElseThrow().getCurios().forEach((type, h) -> {
            if (!CuriosArmorCompat.managesType(player, type)) return;
            for (int i = 0; i < h.getSlots(); i++) {
                var id = new CuriosSlotKey(type, i, false).slotId();
                for (var entry : ARMOR.entrySet()) check(EquipmentStructureApi.structure(entry.getValue()).orElseThrow().slot(id).isPresent()
                        == (entry.getKey() == CuriosArmorCompat.definitions(player).profile(type).armorSlot()), "type/index has one armor template " + id);
            }
        });
    }
    private static void validateCatalog() {
        int items = 0, pairs = 0;
        for (var item : BuiltInRegistries.ITEM) {
            var id = BuiltInRegistries.ITEM.getKey(item);
            if (!Set.of("celestial_artifacts", "l2hostility", "curseofpandora", "pandora").contains(id.getNamespace())) continue;
            var input = new ItemStack(item);
            if (PlayerBoundCurios.eligible(input, player)) continue;
            int accepted = 0;
            for (var type : CuriosApi.getItemStackSlots(input, player.level()).keySet()) {
                if (!CuriosArmorCompat.managesType(player, type) || slots(type) == 0) continue;
                var key = new CuriosSlotKey(type, 0, false);
                if (!PlayerBoundCurios.item(player, key.slotId()).isEmpty()) continue;
                var host = ARMOR.get(CuriosArmorCompat.definitions(player).profile(type).armorSlot());
                boolean nativeValid = CuriosArmorCompat.canEquipOriginal(CuriosArmorCompat.context(player, key), input, host);
                var part = EquipmentSlotItemAdapters.read(input, host, key.slotId(), player);
                check(part.isPresent() == nativeValid, "original validation parity " + id + "/" + type);
                if (part.isPresent()) {
                    check(ItemStack.isSameItemSameComponents(input, EquipmentComponentRegistry.createItemStack(part.get()).orElseThrow()), "item components round trip " + id);
                    accepted++;
                }
                pairs++;
            }
            if (accepted > 0) items++;
        }
        EquipmentStructureApiMod.LOGGER.info("CELESTIAL_CATALOG_PASSED {} routable items / {} item-type checks", items, pairs);
    }
    static void check(boolean test, String message) { if (!test) throw new AssertionError(message); }
    private static void finish(String result) { done = true; EquipmentStructureApiMod.LOGGER.info("CELESTIAL_SMOKE_{}", result); player.sendSystemMessage(Component.literal(PREFIX + result)); }
}
