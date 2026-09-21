package dev.equipmentstructure.api.test;

import dev.equipmentstructure.api.*;
import dev.equipmentstructure.api.attribute.*;
import dev.equipmentstructure.api.event.*;
import dev.equipmentstructure.api.grid.*;
import dev.equipmentstructure.api.grid.space.*;
import dev.equipmentstructure.api.grid.synergy.*;
import dev.equipmentstructure.api.menu.EquipmentAssemblyMenu;
import dev.equipmentstructure.api.network.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder(EquipmentStructureApiMod.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = EquipmentStructureApiMod.MOD_ID)
public final class EquipmentSpatialGameTests {
    private static final ResourceLocation HOST=id("host"), A=id("a"), B=id("b"), TYPE=id("type"), OWNER=id("owner"), PEER=id("peer"), REGION=id("region"), PANEL=id("panel"), RULE=id("contact");
    private static final List<EquipmentSlotDefinition> SLOTS=List.of(EquipmentSlotDefinition.of(A,TYPE),EquipmentSlotDefinition.of(B,TYPE));
    private static final ComponentSpaceDefinition ATTACHED=ComponentSpaceDefinition.attached(REGION,GridShape.mask("##","#."),new GridCell(1,0)).acceptsItems(ResourceLocation.parse("minecraft:coal")).withStackLimit(4);
    private static final ComponentSpaceDefinition CHILD=ComponentSpaceDefinition.panel(PANEL,GridShape.mask("###","#.#","###")).acceptsItems(ResourceLocation.parse("minecraft:iron_ingot"),OWNER).withRemoval(ComponentSpaceDefinition.Removal.KEEP_WITH_COMPONENT);
    private static Item ownerItem,peerItem;
    @EventBusSubscriber(modid=EquipmentStructureApiMod.MOD_ID) public static final class Fixtures {
        @SubscribeEvent public static void items(RegisterEvent event) { event.register(Registries.ITEM,h->{ownerItem=new Part(OWNER);peerItem=new Part(PEER);h.register(OWNER,ownerItem);h.register(PEER,peerItem);}); }
    }
    @SubscribeEvent public static void register(RegisterGameTestsEvent event) {
        event.register(EquipmentSpatialGameTests.class);
        EquipmentGridRegistry.registerHost(HOST,new GridBoard(GridShape.rectangle(8,6),GridShape.rectangle(1,1),new GridPlacement(7,5)));
        for(var pair:Map.of(OWNER,ownerItem,PEER,peerItem).entrySet()) EquipmentComponentRegistry.register(GameTestFixtures.component(pair.getKey(),TYPE,TYPE,true,part->{var item=new ItemStack(pair.getValue());if(!part.data().isEmpty())item.set(DataComponents.CUSTOM_DATA,CustomData.of(part.data()));return item;}).withFootprint(GridFootprint.freelyRotating(GridShape.rectangle(1,1))));
        ComponentSpaceRegistry.register(OWNER,ATTACHED,CHILD);
        GridRuleRegistry.register(new GridRuleDefinition(RULE,GridSelector.components(OWNER),GridCondition.neighbors(GridSelector.components(PEER),1)).inHost(HOST));
        EquipmentAttributeRegistry.register(OWNER,EquipmentAttributeContribution.add(Attributes.ATTACK_DAMAGE,id("bonus"),2).whenGridRule(RULE));
    }
    private static final class Part extends Item implements EquipmentComponentItem {
        private final ResourceLocation id; Part(ResourceLocation id){super(new Properties());this.id=id;}
        public EquipmentComponentInstance createComponent(ItemStack item){return new EquipmentComponentInstance(id,TYPE,TYPE,item.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag());}
    }
    private record Test(Player player,EquipmentAssemblyMenu menu) {
        ItemStack equipment(){return menu.equipmentStack();}
        EquipmentStructure structure(){return EquipmentStructureApi.structure(equipment()).orElseThrow();}
        ComponentSpaceContents contents(){return ComponentSpaceContents.read(structure().component(A).orElseThrow());}
    }
    private static Test setup(GameTestHelper h){var player=h.makeMockPlayer(GameType.SURVIVAL);var inventory=new SimpleContainer(EquipmentAssemblyMenu.containerSize());var equipment=new ItemStack(Items.IRON_SWORD);EquipmentStructureApi.setStructure(equipment,new EquipmentHostDefinition(HOST,BuiltinEquipmentTypes.SWORD,SLOTS).createStructure());inventory.setItem(0,equipment);var menu=new EquipmentAssemblyMenu(27,player.getInventory(),inventory,SLOTS);player.containerMenu=menu;var t=new Test(player,menu);h.assertTrue(EquipmentStructureApi.installAt(equipment,A,new EquipmentComponentInstance(OWNER,TYPE,TYPE),Optional.of(new GridPlacement(0,0)))==EquipmentStructureApi.InstallResult.INSTALLED,"source installed");return t;}
    private static SpaceActionPayload request(Test t,ResourceLocation space,int x,int y,boolean single){return new SpaceActionPayload(t.menu.containerId,t.menu.getStateId(),42,t.equipment(),t.menu.getCarried(),GridDefinitions.registered().fingerprint(),A,space,t.contents().token(),ComponentSpaceTransactions.Action.CLICK,-1,new GridPlacement(x,y),single);}
    private static boolean click(Test t,ResourceLocation space,int x,int y,boolean single){return t.menu.applySpaceAction(request(t,space,x,y,single),t.player);}
    private static int count(Test t,ResourceLocation space){return t.contents().spaces().get(space).entries().stream().mapToInt(e->e.stack(t.player.registryAccess()).getCount()).sum();}

    @GameTest(template="empty",timeoutTicks=40) public static void bothSpacesUseRealItemsAndPreserveNamesAcrossSave(GameTestHelper h){
        var t=setup(h);var item=new ItemStack(Items.COAL,7);item.set(DataComponents.CUSTOM_NAME,Component.literal("kept"));t.menu.setCarried(item);
        h.assertTrue(click(t,REGION,0,0,false)&&t.menu.getCarried().getCount()==3&&count(t,REGION)==4,"partial insertion");
        var saved=t.equipment().save(h.getLevel().registryAccess());var restored=ItemStack.parse(h.getLevel().registryAccess(),saved).orElseThrow();
        h.assertTrue(ItemStack.matches(t.equipment(),restored),"host storage roundtrip");
        t.menu.setCarried(ItemStack.EMPTY);h.assertTrue(click(t,REGION,0,0,true)&&t.menu.getCarried().getCount()==2&&count(t,REGION)==2,"half extraction");
        h.assertTrue(t.menu.getCarried().getHoverName().getString().equals("kept"),"item name retained");
        t.menu.setCarried(new ItemStack(ownerItem));h.assertTrue(click(t,PANEL,0,0,false),"modded item allowed without installing it");
        h.assertTrue(t.structure().components().size()==1,"stored part is not another installed component");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40) public static void filtersHolesAndFailedSwapsLeaveBothSidesUntouched(GameTestHelper h){
        var t=setup(h);t.menu.setCarried(new ItemStack(Items.DIAMOND,3));var before=t.equipment().copy();
        h.assertTrue(!click(t,REGION,0,0,false)&&ItemStack.matches(before,t.equipment())&&t.menu.getCarried().getCount()==3,"filter rejection");
        t.menu.setCarried(new ItemStack(Items.IRON_INGOT,2));h.assertTrue(!click(t,PANEL,1,1,false),"hole cannot store");
        h.assertTrue(click(t,PANEL,0,0,false),"fill child");t.menu.setCarried(new ItemStack(Items.DIAMOND));before=t.equipment().copy();
        h.assertTrue(!click(t,PANEL,0,0,false)&&ItemStack.matches(before,t.equipment())&&t.menu.getCarried().is(Items.DIAMOND),"rejected replacement preserves old item");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40) public static void nonemptyRequiredSpaceBlocksAllRemovalAndReplacementPaths(GameTestHelper h){
        var t=setup(h);t.menu.setCarried(new ItemStack(Items.COAL));h.assertTrue(click(t,REGION,0,0,false),"fill");
        h.assertTrue(!EquipmentStructureApi.checkRemove(t.equipment(),A).isAllowed(),"API removal policy");
        h.assertTrue(EquipmentStructureApi.remove(t.equipment(),A).isEmpty(),"direct API blocked");
        h.assertTrue(EquipmentStructureApi.replace(t.equipment(),A,new EquipmentComponentInstance(PEER,TYPE,TYPE),Optional.empty()).isEmpty(),"replacement blocked");
        t.menu.setCarried(ItemStack.EMPTY);h.assertTrue(click(t,REGION,0,0,false),"empty source first");h.assertTrue(EquipmentStructureApi.remove(t.equipment(),A).isPresent(),"now removable");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40) public static void keepWithComponentRequiresLosslessItemRoundtrip(GameTestHelper h){
        var t=setup(h);t.menu.setCarried(new ItemStack(Items.IRON_INGOT,5));h.assertTrue(click(t,PANEL,0,0,false),"fill portable panel");
        var part=t.structure().component(A).orElseThrow();var restored=EquipmentComponentRegistry.createValidatedItemStack(part).orElseThrow();
        h.assertTrue(EquipmentStructureApi.remove(t.equipment(),A).isPresent(),"portable source removal");
        var read=EquipmentComponentRegistry.fromItemStack(restored).orElseThrow();h.assertTrue(read.equals(part),"contents and token on returned source");
        h.assertTrue(EquipmentStructureApi.installAt(t.equipment(),A,read,Optional.of(new GridPlacement(0,0)))==EquipmentStructureApi.InstallResult.INSTALLED&&count(t,PANEL)==5,"reinstallation retains child items");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40) public static void cancellationAndConflictingSnapshotsDoNotConsumeItems(GameTestHelper h){
        var t=setup(h);t.menu.setCarried(new ItemStack(Items.COAL,3));var previous=t.structure();
        Consumer<EquipmentSpaceChangeEvent> cancel=e->{if(e.previous()==previous)e.setCanceled(true);};NeoForge.EVENT_BUS.addListener(EquipmentSpaceChangeEvent.class,cancel);
        try{h.assertTrue(!click(t,REGION,0,0,false)&&t.structure()==previous&&t.menu.getCarried().getCount()==3,"cancel atomic");}finally{NeoForge.EVENT_BUS.unregister(cancel);}
        var stale=request(t,REGION,0,0,false);t.menu.setCarried(new ItemStack(Items.COAL,2));h.assertTrue(!t.menu.applySpaceAction(stale,t.player),"stale carried snapshot");
        h.assertTrue(click(t,REGION,0,0,false),"current action accepted");h.assertTrue(!t.menu.applySpaceAction(stale,t.player)&&count(t,REGION)==2,"replay rejected");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40) public static void attachedRegionsRejectOverlapsAndFailedMovementPreservesContents(GameTestHelper h){
        var t=setup(h);t.menu.setCarried(new ItemStack(Items.COAL,2));h.assertTrue(click(t,REGION,0,0,false),"fill");
        h.assertTrue(EquipmentStructureApi.installAt(t.equipment(),B,new EquipmentComponentInstance(PEER,TYPE,TYPE),Optional.of(new GridPlacement(1,0)))==EquipmentStructureApi.InstallResult.GRID_REJECTED,"exclusive region blocks unrelated part");
        var before=t.structure();h.assertTrue(!EquipmentStructureApi.setGridLayout(t.equipment(),before,Map.of(A,new GridPlacement(7,0)))&&t.structure()==before&&count(t,REGION)==2,"overflow rollback");
        h.assertTrue(EquipmentStructureApi.setGridLayout(t.equipment(),before,Map.of(A,new GridPlacement(3,2,GridRotation.CLOCKWISE_90)))&&count(t,REGION)==2,"rotate whole source and storage");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40) public static void removedDefinitionRetainsItemsAndAllowsRecoveryOnly(GameTestHelper h){
        var t=setup(h);t.menu.setCarried(new ItemStack(Items.COAL,2));h.assertTrue(click(t,REGION,0,0,false),"fill");ComponentSpaceRegistry.unregister(OWNER);
        try{t.menu.setCarried(new ItemStack(Items.COAL));h.assertTrue(!click(t,REGION,0,0,false)&&count(t,REGION)==2,"no insertion while undefined");t.menu.setCarried(ItemStack.EMPTY);h.assertTrue(click(t,REGION,0,0,false)&&t.menu.getCarried().getCount()==2,"recover actual items");}finally{ComponentSpaceRegistry.register(OWNER,ATTACHED,CHILD);}h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40) public static void exactConsumptionAndGenericDataEditsCannotBypassOwnership(GameTestHelper h){
        var t=setup(h);t.menu.setCarried(new ItemStack(Items.COAL,4));h.assertTrue(click(t,REGION,0,0,false),"fill");var before=t.structure();var part=before.component(A).orElseThrow();
        h.assertTrue(EquipmentStructureApi.updateComponentData(t.equipment(),A,part,data->{data.remove(ComponentSpaceContents.KEY);return data;})==EquipmentStructureApi.ComponentDataResult.CONFLICT,"generic callback cannot erase container");
        h.assertTrue(ComponentSpaceTransactions.consume(t.equipment(),before,A,REGION,0,2,h.getLevel().registryAccess(),()->true).applied()&&count(t,REGION)==2,"exact consumption");
        h.assertTrue(!ComponentSpaceTransactions.consume(t.equipment(),before,A,REGION,0,2,h.getLevel().registryAccess(),()->true).applied()&&count(t,REGION)==2,"stale consume rejected");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40) public static void namedRuleDrivesAttributesOnlyFromCommittedLayout(GameTestHelper h){
        var t=setup(h);h.assertTrue(EquipmentAttributeResolver.contributions(t.equipment(),A).isEmpty(),"no neighbor no bonus");
        h.assertTrue(EquipmentStructureApi.installAt(t.equipment(),B,new EquipmentComponentInstance(PEER,TYPE,TYPE),Optional.of(new GridPlacement(0,1)))==EquipmentStructureApi.InstallResult.INSTALLED,"peer touches actual source");
        h.assertTrue(EquipmentAttributeResolver.contributions(t.equipment(),A).size()==1,"one contribution per source");var before=t.structure();
        var preview=GridTransactions.move(before,Map.of(B,new GridPlacement(5,3)),GridDefinitions.registered());h.assertTrue(preview.allowed()&&!GridRuleRegistry.evaluate(preview.structure(),GridDefinitions.registered(),true).get(new GridRuleRegistry.Key(RULE,A)).active(),"preview reports lost link");
        h.assertTrue(EquipmentAttributeResolver.contributions(t.equipment(),A).size()==1,"preview does not alter live attributes");
        h.assertTrue(EquipmentStructureApi.setGridLayout(t.equipment(),before,Map.of(B,new GridPlacement(5,3)))&&EquipmentAttributeResolver.contributions(t.equipment(),A).isEmpty(),"commit revokes contribution");h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=40) public static void spacePacketRoundtripAndSizeLimitAreChecked(GameTestHelper h){
        var t=setup(h);t.menu.setCarried(new ItemStack(Items.COAL));var packet=request(t,REGION,0,0,false);var buffer=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        try{SpaceActionPayload.STREAM_CODEC.encode(buffer,packet);var decoded=SpaceActionPayload.STREAM_CODEC.decode(buffer);h.assertTrue(t.menu.applySpaceAction(decoded,t.player),"decoded intent executes once");}finally{buffer.release();}
        var tag=new net.minecraft.nbt.CompoundTag();tag.putString("large","x".repeat(ComponentSpaceContents.MAX_BYTES));var item=new ItemStack(Items.COAL);item.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));t.menu.setCarried(item);var before=t.equipment().copy();
        h.assertTrue(!click(t,REGION,1,0,false)&&ItemStack.matches(before,t.equipment())&&ItemStack.matches(item,t.menu.getCarried()),"oversize input retained");h.succeed();
    }
    private static ResourceLocation id(String name){return ResourceLocation.parse("equipment_structure_api:spatial_test/"+name);}
    @GameTest(template="empty",timeoutTicks=40) public static void combinedHostStorageIsBoundedAcrossMultipleSourceComponents(GameTestHelper h){
        var t=setup(h);h.assertTrue(EquipmentStructureApi.installAt(t.equipment(),B,new EquipmentComponentInstance(OWNER,TYPE,TYPE),Optional.of(new GridPlacement(4,0)))==EquipmentStructureApi.InstallResult.INSTALLED,"second source");
        var tag=new net.minecraft.nbt.CompoundTag();tag.putString("payload","x".repeat(80000));var item=new ItemStack(Items.IRON_INGOT);item.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        t.menu.setCarried(item.copy());h.assertTrue(click(t,PANEL,0,0,false),"first source below limit");var before=t.equipment().copy();t.menu.setCarried(item.copy());
        var result=ComponentSpaceTransactions.execute(t.equipment(),t.menu.getCarried(),B,PANEL,"",ComponentSpaceTransactions.Action.CLICK,-1,new GridPlacement(0,0),false,h.getLevel().registryAccess(),()->true,t.menu::setCarried);
        h.assertTrue(!result.applied()&&result.reason().equals("host_storage_limit")&&ItemStack.matches(before,t.equipment())&&ItemStack.matches(item,t.menu.getCarried()),"combined limit preserves both sides");
        h.assertTrue(EquipmentStructureApi.replace(t.equipment(),B,t.structure().component(A).orElseThrow(),Optional.empty()).isEmpty()&&ItemStack.matches(before,t.equipment()),"portable source replacement cannot bypass the host limit");h.succeed();
    }
}
