package dev.equipmentstructure.api.smoke;

import auviotre.enigmatic.legacy.handlers.EnigmaticHandler;
import auviotre.enigmatic.legacy.registries.EnigmaticItems;
import com.illusivesoulworks.caelus.api.CaelusApi;
import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.appearance.AppearancePoseStorage;
import dev.equipmentstructure.api.compat.curios.*;
import dev.equipmentstructure.api.menu.EquipmentAssemblyMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import top.theillusivec4.curios.api.CuriosApi;

/** Opt-in acceptance tests against local, unmodified third-party JARs. Never packaged. */
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID)
public final class EnigmaticSmokeFixtures {
    private static ServerPlayer player;
    private static int age, step, wait;
    private static boolean done;
    private static ItemStack armor = ItemStack.EMPTY;
    private static ItemStack helmet = ItemStack.EMPTY;
    private static long curseTime;
    private static boolean ritualObserved;
    @SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) {
        if (!Boolean.getBoolean("equipment_structure_api.bindingSmoke")) return;
        event.enqueueWork(() -> {
            // Fixture-only: another declared player binding, plus a cross-armor destination.
            CuriosCompatibility.registerPlayerBoundItem(ResourceLocation.parse("enigmaticlegacyplus:iron_ring"), true);
            CuriosCompatibility.registerSlot("charm", new CuriosSlotProfile(EquipmentSlot.HEAD, dev.equipmentstructure.api.grid.GridFootprint.SINGLE_CELL));
        });
    }
    public static final String PREFIX = "ESA_ENIGMATIC_";
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!Boolean.getBoolean("equipment_structure_api.enigmaticSmoke") || !(event.getEntity() instanceof ServerPlayer p)) return;
        player = p; age = step = wait = 0; done = false;
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.serverLevel().setDayTime(6000);
        p.server.setDifficulty(net.minecraft.world.Difficulty.PEACEFUL, true);
        if (Boolean.getBoolean("equipment_structure_api.boundResume")) { step = 30; return; }
        if (Boolean.getBoolean("equipment_structure_api.enigmaticResume")) { step = 10; return; }
        p.getInventory().clearContent(); p.getInventory().selected = 0;
        for (String path : java.util.List.of("discover_scroll", "discover_spellstone")) {
            var advancement = p.server.getAdvancements().get(ResourceLocation.parse("enigmaticlegacyplus:main/" + path));
            for (var criterion : p.getAdvancements().getOrStartProgress(advancement).getCompletedCriteria()) p.getAdvancements().revoke(advancement, criterion);
        }
        CuriosArmorCompat.beginNativeLoad();
        try { CuriosApi.getCuriosInventory(p).orElseThrow().reset(); }
        finally { CuriosArmorCompat.endNativeLoad(); }
        if (Boolean.getBoolean("equipment_structure_api.unbindSmoke")) { step = 60; return; }
        if (Boolean.getBoolean("equipment_structure_api.bindingSmoke")) {
            step = 40;
            p.getInventory().setItem(9, new ItemStack(EnigmaticItems.CURSED_RING.get()));
            p.getInventory().setItem(10, new ItemStack(EnigmaticItems.IRON_RING.get()));
            return;
        }
        p.getInventory().setItem(0, new ItemStack(Items.DIAMOND_CHESTPLATE));
        var items = java.util.List.of(EnigmaticItems.IRON_RING.get(), EnigmaticItems.GOLDEN_RING.get(),
                EnigmaticItems.ENIGMATIC_AMULET_RED.get(), EnigmaticItems.MINING_CHARM.get(), EnigmaticItems.MAJESTIC_ELYTRA.get());
        for (int i = 0; i < items.size(); i++) p.getInventory().setItem(9 + i, new ItemStack(items.get(i)));
    }
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void clonePlayer(PlayerEvent.Clone event) {
        if (event.getOriginal() == player && event.getEntity() instanceof ServerPlayer clone) {
            if (step == 65) ritualObserved = EnigmaticHandler.getPersistedData(event.getOriginal()).getBoolean("DestroyedCursedRing");
            player = clone;
        }
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (player == null || player.hasDisconnected() || done) return;
        if (++age > 2400) { finish("FAILED timeout step=" + step); return; }
        if (++wait < 45) return;
        try {
            switch (step) {
                case 0 -> { open(); }
                case 1 -> {
                    if (player.containerMenu instanceof EquipmentAssemblyMenu) return;
                    equipFromInventory(); check(count() == 5, "five accessories installed through UI");
                }
                case 2 -> {
                    checkEffects(true);
                    var nativeInventory = CuriosApi.getCuriosInventory(player).orElseThrow();
                    check(nativeInventory.getStacksHandler("scroll").orElseThrow().getSlots() == 0, "scroll starts locked");
                    check(nativeInventory.getStacksHandler("spellstone").orElseThrow().getSlots() == 0, "spellstone starts locked");
                    check(EquipmentStructureApi.slots(armor).stream().noneMatch(s -> BuiltinEquipmentSlots.all().contains(s.id())), "no built-in armor slots");
                    check(EnigmaticHandler.hasCurio(player, EnigmaticItems.MAJESTIC_ELYTRA), "mod's own capability query sees elytra");
                    player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                }
                case 3 -> { checkEffects(false); player.setItemSlot(EquipmentSlot.CHEST, armor); }
                case 4 -> {
                    checkEffects(true);
                    for (String path : java.util.List.of("discover_scroll", "discover_spellstone")) {
                        var advancement = player.server.getAdvancements().get(ResourceLocation.parse("enigmaticlegacyplus:main/" + path));
                        for (var criterion : player.getAdvancements().getOrStartProgress(advancement).getRemainingCriteria()) player.getAdvancements().award(advancement, criterion);
                    }
                }
                case 5 -> {
                    var inv = CuriosApi.getCuriosInventory(player).orElseThrow();
                    check(inv.getStacksHandler("scroll").orElseThrow().getSlots() == 1, "original advancement unlocks scroll");
                    check(inv.getStacksHandler("spellstone").orElseThrow().getSlots() == 1, "original advancement unlocks spellstone");
                    player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                    player.getInventory().setItem(0, armor);
                    player.getInventory().setItem(9, new ItemStack(EnigmaticItems.XP_SCROLL.get()));
                    player.getInventory().setItem(10, new ItemStack(EnigmaticItems.OCEAN_STONE.get()));
                    open();
                }
                case 6 -> {
                    if (player.containerMenu instanceof EquipmentAssemblyMenu) return;
                    equipFromInventory(); check(count() == 7, "unlocked accessories install through UI");
                }
                case 7 -> {
                    checkEffects(true);
                    check(EnigmaticHandler.hasCurio(player, EnigmaticItems.XP_SCROLL), "native scroll query");
                    check(EnigmaticHandler.hasCurio(player, EnigmaticItems.OCEAN_STONE), "native spellstone query");
                    var back = EquipmentStructureApi.component(armor, new CuriosSlotKey("back", 0, false).slotId()).orElseThrow();
                    check(AppearancePoseStorage.read(back).orElseThrow().transform().position().x() == 2, "3D pose saved by server");
                    player.server.getPlayerList().saveAll();
                    finish("PHASE1_PASSED UI7, attributes, unequip, Caelus, progression slots, pose and save"); return;
                }
                case 10 -> {
                    armor = player.getItemBySlot(EquipmentSlot.CHEST);
                    check(count() == 7, "real server restart retained seven original accessories");
                    checkEffects(true);
                    check(EnigmaticHandler.hasCurio(player, EnigmaticItems.OCEAN_STONE), "restart retained unlocked slot");
                    armor.setDamageValue(armor.getMaxDamage() - 1);
                    armor.hurtAndBreak(2, player, EquipmentSlot.CHEST);
                    check(armor.isEmpty(), "armor broke");
                    for (var item : java.util.List.of(EnigmaticItems.IRON_RING.get(), EnigmaticItems.GOLDEN_RING.get(), EnigmaticItems.ENIGMATIC_AMULET_RED.get(),
                            EnigmaticItems.MINING_CHARM.get(), EnigmaticItems.MAJESTIC_ELYTRA.get(), EnigmaticItems.XP_SCROLL.get(), EnigmaticItems.OCEAN_STONE.get()))
                        check(player.getInventory().countItem(item) == 1, "break returns exactly one " + item);
                }
                case 11 -> {
                    checkEffects(false);
                    EquipmentStructureApiMod.LOGGER.info("ENIGMATIC_SMOKE_RESTART_BREAK_PASSED seven accessories saved and recovered exactly once");
                    player.getInventory().clearContent(); player.getInventory().selected = 0;
                    player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_CHESTPLATE));
                    player.getInventory().setItem(9, new ItemStack(EnigmaticItems.CURSED_RING.get()));
                    check(!EnigmaticHandler.tryForceEquip(player, new ItemStack(EnigmaticItems.GOLDEN_RING.get())), "ordinary forced equip is disabled");
                    open(); step = 19;
                }
                case 20 -> {
                    if (player.containerMenu instanceof EquipmentAssemblyMenu) return;
                    equipFromInventory();
                }
                case 21 -> {
                    checkBound();
                    check(EquipmentStructureApi.structure(armor).orElseThrow().components().isEmpty(), "bound ring leaves equipment storage");
                    player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                }
                case 22 -> {
                    checkBound(); check(CuriosArmorCompat.canRemoveArmor(armor, player), "binding must not lock old armor");
                    player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                }
                case 23 -> {
                    checkBound();
                    var replacement = player.getItemBySlot(EquipmentSlot.CHEST);
                    replacement.setDamageValue(replacement.getMaxDamage() - 1);
                    replacement.hurtAndBreak(2, player, EquipmentSlot.CHEST);
                    checkBound();
                    check(PlayerBoundCurios.remove(player, new CuriosSlotKey("ring", 0, false).slotId()).isEmpty(), "survival cannot remove seven curses");
                    player.hurt(player.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
                }
                case 24 -> {
                    if (!player.isAlive()) return;
                    checkBound();
                    player.getInventory().setItem(0, new ItemStack(Items.DIAMOND_CHESTPLATE));
                    player.getInventory().selected = 0; open();
                }
                case 25 -> {
                    if (player.containerMenu instanceof EquipmentAssemblyMenu) return;
                    checkBound(); player.server.getPlayerList().saveAll();
                    finish("PHASE2_PASSED restart, break recovery, UI curse binding, armor swap, naked effects and real death/respawn"); return;
                }
                case 30 -> {
                    checkBound();
                    check(player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "binding survives restart while naked");
                    check(timer() >= player.getPersistentData().getLong("esa:test_curse_timer"), "original curse timer survives restart");
                    player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                    player.getInventory().setItem(0, ItemStack.EMPTY);
                    player.getInventory().selected = 0; open();
                }
                case 31 -> {
                    if (CuriosApi.getCuriosInventory(player).orElseThrow().findFirstCurio(EnigmaticItems.CURSED_RING.get()).isPresent()) return;
                    check(player.getInventory().countItem(EnigmaticItems.CURSED_RING.get()) == 1, "creative UI removal returns one original ring");
                    check(!EnigmaticHandler.isTheCursedOne(player), "original curse ends after allowed removal");
                    finish("PHASE3_PASSED naked bound save/reload and original creative unbinding through UI"); return;
                }
                case 40 -> { open(); check(((EquipmentAssemblyMenu)player.containerMenu).equipmentStack().isEmpty(), "empty hand opens integrated assembly"); }
                case 41 -> {
                    if (player.containerMenu instanceof EquipmentAssemblyMenu) return;
                    checkBound();
                    check(!PlayerBoundCurios.item(player, new CuriosSlotKey("ring", 1, false).slotId()).isEmpty(), "second declared binding coexists");
                    check(!PlayerBoundCurios.install(player, new CuriosSlotKey("ring", 1, false), new ItemStack(EnigmaticItems.CURSED_RING.get())), "occupied shared capacity rejected");
                    curseTime = timer();
                }
                case 42 -> {
                    check(timer() >= curseTime + 40, "native wear timer advances without armor");
                    check(PlayerBoundCurios.remove(player, new CuriosSlotKey("ring", 0, false).slotId()).isEmpty(), "survival original removal veto");
                    var ringHandler = CuriosApi.getCuriosInventory(player).orElseThrow().getStacksHandler("ring").orElseThrow();
                    ringHandler.grow(1);
                    check(!PlayerBoundCurios.install(player, new CuriosSlotKey("ring", 2, false), new ItemStack(EnigmaticItems.REDEMPTION_RING.get())), "original mutually exclusive redemption rule");
                    ringHandler.shrink(1);
                    clearBinding(0); clearBinding(1);
                    auviotre.enigmatic.legacy.contents.item.rings.CursedRing.autoEquip.set(true);
                    player.getInventory().setItem(9, new ItemStack(EnigmaticItems.CURSED_RING.get()));
                }
                case 43 -> {
                    checkBound();
                    auviotre.enigmatic.legacy.contents.item.rings.CursedRing.autoEquip.set(false);
                    clearBinding(0);
                    var data = EnigmaticHandler.getPersistedData(player); data.remove("CursedRingGift");
                    auviotre.enigmatic.legacy.contents.item.rings.CursedRing.ultraHardcore.set(true);
                    try {
                        var method = auviotre.enigmatic.legacy.contents.item.rings.CursedRing.Events.class.getDeclaredMethod("onPlayerJoin", PlayerEvent.PlayerLoggedInEvent.class);
                        method.setAccessible(true); method.invoke(null, new PlayerEvent.PlayerLoggedInEvent(player));
                    } finally { auviotre.enigmatic.legacy.contents.item.rings.CursedRing.ultraHardcore.set(false); }
                    checkBound();
                    check(data.getBoolean("CursedRingGift"), "original mandatory starter listener completed");
                    armor = new ItemStack(Items.DIAMOND_CHESTPLATE); helmet = new ItemStack(Items.DIAMOND_HELMET);
                    installPart(armor, "amulet", 0, EnigmaticItems.ASCENSION_AMULET.get());
                    player.setItemSlot(EquipmentSlot.CHEST, armor);
                }
                case 44 -> {
                    check(charms() == 2, "real chest amulet adds one native charm slot");
                    CuriosEquipmentTemplates.prepare(helmet, player);
                    check(EquipmentStructureApi.slots(helmet).stream().filter(s -> CuriosSlotKey.parse(s.id()).map(k -> k.type().equals("charm") && !k.cosmetic()).orElse(false)).count() == 2, "cross-armor grant appears entirely on helmet");
                    check(EquipmentStructureApi.slots(armor).stream().noneMatch(s -> CuriosSlotKey.parse(s.id()).map(k -> k.type().equals("charm")).orElse(false)), "no charm slots duplicated on chest");
                    installPart(helmet, "charm", 0, EnigmaticItems.ENCHANTER_PEARL.get());
                    check(EquipmentSlotItemAdapters.read(new ItemStack(EnigmaticItems.ENCHANTER_PEARL.get()), helmet, new CuriosSlotKey("charm", 1, false).slotId(), player).isEmpty(), "duplicate validation while helmet is unworn");
                    player.setItemSlot(EquipmentSlot.HEAD, helmet);
                }
                case 45 -> {
                    check(charms() == 3, "amulet and pearl grants stack through original Curios modifiers");
                    installPart(helmet, "charm", 2, EnigmaticItems.MINING_CHARM.get());
                }
                case 46 -> {
                    check(charms() == 3 && EnigmaticHandler.hasCurio(player, EnigmaticItems.MINING_CHARM), "new slot item ticks normally");
                    player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
                }
                case 47 -> {
                    check(charms() == 2, "removing remote grant shrinks helmet capacity");
                    check(player.getInventory().countItem(EnigmaticItems.MINING_CHARM.get()) == 1, "overflow returned exactly once");
                    check(EquipmentStructureApi.component(helmet, new CuriosSlotKey("charm", 2, false).slotId()).isEmpty(), "overflow removed from helmet ownership");
                    player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                }
                case 48 -> {
                    check(charms() == 1, "self-type pearl grant stops with helmet");
                    check(EquipmentStructureApi.component(helmet, new CuriosSlotKey("charm", 0, false).slotId()).isPresent(), "unequipping preserves source item");
                    player.setItemSlot(EquipmentSlot.HEAD, helmet);
                }
                case 49 -> {
                    check(charms() == 2, "re-equipping applies same modifier once; actual=" + charms()
                            + "; pearl=" + EnigmaticHandler.hasCurio(player, EnigmaticItems.ENCHANTER_PEARL)
                            + "; cursed=" + EnigmaticHandler.isTheCursedOne(player)
                            + "; parts=" + EquipmentStructureApi.structure(helmet).orElseThrow().components()
                            + "; modifiers=" + CuriosApi.getCuriosInventory(player).orElseThrow().getStacksHandler("charm").orElseThrow().getModifiers());
                    check(timer() > curseTime, "native curse timer remains monotonic across equipment changes");
                    helmet.setDamageValue(helmet.getMaxDamage() - 1); helmet.hurtAndBreak(2, player, EquipmentSlot.HEAD);
                }
                case 50 -> {
                    check(charms() == 1, "breaking grant owner removes added slot");
                    check(player.getInventory().countItem(EnigmaticItems.ENCHANTER_PEARL.get()) == 1, "broken owner returns original source item once");
                    checkBound(); player.hurt(player.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
                }
                case 51 -> {
                    if (!player.isAlive()) return;
                    checkBound(); player.getInventory().selected = 0; player.getInventory().setItem(0, ItemStack.EMPTY); open();
                }
                case 52 -> {
                    if (player.containerMenu instanceof EquipmentAssemblyMenu) return;
                    checkBound(); player.getPersistentData().putLong("esa:test_curse_timer", timer()); player.server.getPlayerList().saveAll();
                    finish("PHASE4_PASSED integrated empty-hand UI, two bindings, autoEquip, ultraHardcore starter, native curse timer, mutual exclusion, cross-armor and self-type grants, overflow, break, death"); return;
                }
                case 60 -> {
                    check(PlayerBoundCurios.install(player, new CuriosSlotKey("ring", 0, false), new ItemStack(EnigmaticItems.CURSED_RING.get())), "curse fixture binding");
                    armor = new ItemStack(Items.DIAMOND_CHESTPLATE);
                    installPart(armor, "charm", 0, EnigmaticItems.ENCHANTER_PEARL.get());
                    player.setItemSlot(EquipmentSlot.CHEST, armor);
                }
                case 61 -> {
                    checkBound();
                    check(EnigmaticHandler.hasCurio(player, EnigmaticItems.ENCHANTER_PEARL), "original cursed accessory active before conversion");
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(EnigmaticItems.BLESS_STONE.get()));
                    player.hurt(player.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
                }
                case 62 -> {
                    if (!player.isAlive()) return;
                    var inventory = CuriosApi.getCuriosInventory(player).orElseThrow();
                    check(inventory.findCurios(EnigmaticItems.CURSED_RING.get()).isEmpty(), "survival bless stone consumes Seven Curses");
                    check(inventory.findCurios(EnigmaticItems.REDEMPTION_RING.get()).size() == 1, "original death conversion creates one redemption ring");
                    check(PlayerBoundCurios.item(player, new CuriosSlotKey("ring", 0, false).slotId()).is(EnigmaticItems.REDEMPTION_RING.get()), "replacement remains player-owned while naked");
                    check(!EnigmaticHandler.isTheCursedOne(player) && EnigmaticHandler.isTheBlessedOne(player), "native curse and blessing states updated");
                    check(EquipmentStructureApi.component(armor, new CuriosSlotKey("charm", 0, false).slotId()).isEmpty(), "native conversion ejects incompatible cursed component from original armor");
                    check(PlayerBoundCurios.remove(player, new CuriosSlotKey("ring", 0, false).slotId()).isEmpty(), "new redemption ring applies its own survival lock");
                    curseTime = timer();
                }
                case 63 -> {
                    check(timer() == curseTime, "curse wear timer stops after native conversion");
                    clearBinding(0); player.getInventory().clearContent();
                }
                case 64 -> {
                    ritualObserved = false;
                    check(PlayerBoundCurios.install(player, new CuriosSlotKey("ring", 0, false), new ItemStack(EnigmaticItems.CURSED_RING.get())), "second curse for original lava ritual");
                    var nether = player.server.getLevel(net.minecraft.world.level.Level.NETHER);
                    var center = new net.minecraft.core.BlockPos(1024, 80, 1024);
                    for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) for (int y = -3; y <= 2; y++) {
                        boolean inside = Math.abs(x) <= 2 && Math.abs(z) <= 2 && y >= -2;
                        var block = inside ? y <= 0 ? net.minecraft.world.level.block.Blocks.LAVA : net.minecraft.world.level.block.Blocks.AIR
                                : net.minecraft.world.level.block.Blocks.NETHERRACK;
                        nether.setBlock(center.offset(x, y, z), block.defaultBlockState(), 2);
                    }
                    player.teleportTo(nether, center.getX() + .5, center.getY() + .2, center.getZ() + .5, 0, 0);
                    player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(EnigmaticItems.CURSED_STONE.get()));
                    EnigmaticHandler.getPersistedData(player).remove("DestroyedCursedRing");
                    player.hurt(player.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
                }
                case 65 -> {
                    if (!player.isAlive()) return;
                    check(CuriosApi.getCuriosInventory(player).orElseThrow().findCurios(EnigmaticItems.CURSED_RING.get()).isEmpty(), "native lava ritual destroys ring through original setter");
                    check(PlayerBoundCurios.item(player, new CuriosSlotKey("ring", 0, false).slotId()).isEmpty(), "no ghost player binding after ritual");
                    // EnigmaticEye consumes this one-shot marker during PlayerRespawnEvent.
                    check(ritualObserved, "original ritual completion marker before native respawn dialogue consumes it");
                    check(!EnigmaticHandler.isTheCursedOne(player), "original curse state ends after ritual");
                    player.getInventory().setItem(0, ItemStack.EMPTY); player.getInventory().selected = 0; open();
                }
                case 66 -> {
                    if (player.containerMenu instanceof EquipmentAssemblyMenu) return;
                    player.server.getPlayerList().saveAll();
                    finish("PHASE5_PASSED survival bless-stone death conversion, naked redemption ownership, original removal lock, curse timer stop, cursed-component ejection, Nether lava ritual destruction and empty panel sync"); return;
                }
                default -> throw new AssertionError("invalid stage " + step);
            }
            step++; wait = 0;
        } catch (Throwable failure) {
            EquipmentStructureApiMod.LOGGER.error("Enigmatic acceptance failure", failure);
            finish("FAILED step=" + step + " " + failure);
        }
    }
    private static void open() { check(dev.equipmentstructure.api.command.EquipmentStructureCommands.openForPlayer(player), "automatic chestplate template"); }
    private static long timer() { return player.getData(auviotre.enigmatic.legacy.registries.EnigmaticAttachments.ENIGMATIC_DATA).getTimeWithCurses(); }
    private static int charms() { return CuriosApi.getCuriosInventory(player).orElseThrow().getStacksHandler("charm").orElseThrow().getSlots(); }
    private static void clearBinding(int index) {
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        check(!PlayerBoundCurios.remove(player, new CuriosSlotKey("ring", index, false).slotId()).isEmpty(), "fixture creative release");
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
    }
    private static void installPart(ItemStack host, String type, int index, Item item) {
        CuriosEquipmentTemplates.prepare(host, player); var key = new CuriosSlotKey(type, index, false).slotId();
        var part = EquipmentSlotItemAdapters.read(new ItemStack(item), host, key, player).orElseThrow();
        check(EquipmentStructureApi.install(host, key, part) == EquipmentStructureApi.InstallResult.INSTALLED, "install actual addon " + item);
    }
    private static void equipFromInventory() {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var item = player.getInventory().getItem(i);
            if (item.is(Items.DIAMOND_CHESTPLATE)) {
                armor = item; player.getInventory().setItem(i, ItemStack.EMPTY); player.setItemSlot(EquipmentSlot.CHEST, item); return;
            }
        }
        throw new AssertionError("lost chestplate");
    }
    private static int count() { return EquipmentStructureApi.structure(armor).orElseThrow().components().size(); }
    private static void checkBound() {
        check(EnigmaticHandler.isTheCursedOne(player), "seven curses remain active");
        var inv = CuriosApi.getCuriosInventory(player).orElseThrow();
        check(inv.findCurios(EnigmaticItems.CURSED_RING.get()).size() == 1, "exactly one native bound ring");
        check(!PlayerBoundCurios.item(player, new CuriosSlotKey("ring", 0, false).slotId()).isEmpty(), "player owns bound ring");
        check(player.getInventory().countItem(EnigmaticItems.CURSED_RING.get()) == 0, "no loose duplicate of bound ring");
    }
    private static void checkEffects(boolean equipped) {
        check((player.getAttribute(Attributes.ARMOR).getModifier(ResourceLocation.parse("enigmaticlegacyplus:iron_ring")) != null) == equipped, "iron ring armor modifier=" + equipped);
        check((player.getAttribute(Attributes.LUCK).getModifier(ResourceLocation.parse("enigmaticlegacyplus:golden_ring")) != null) == equipped, "gold ring luck modifier=" + equipped);
        check(EnigmaticHandler.hasCurio(player, EnigmaticItems.IRON_RING) == equipped, "original mod sees iron ring=" + equipped);
        check((player.getAttribute(CaelusApi.getInstance().getFallFlyingAttribute()).getModifier(ResourceLocation.parse("enigmaticlegacyplus:elytra")) != null) == equipped,
                "Caelus elytra modifier=" + equipped + "; actual=" + player.getAttribute(CaelusApi.getInstance().getFallFlyingAttribute()).getModifiers());
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void finish(String result) {
        done = true; EquipmentStructureApiMod.LOGGER.info("ENIGMATIC_SMOKE_{}", result);
        player.sendSystemMessage(Component.literal(PREFIX + result));
    }
}
