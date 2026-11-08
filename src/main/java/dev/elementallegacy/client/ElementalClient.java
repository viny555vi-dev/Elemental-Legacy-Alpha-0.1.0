package dev.elementallegacy.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.elementallegacy.ElementalLegacy;
import dev.elementallegacy.ability.ElementalAbility;
import dev.elementallegacy.element.Element;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid=ElementalLegacy.MOD_ID,value=Dist.CLIENT)
public final class ElementalClient {
 private static final KeyMapping POWER_ONE=new KeyMapping("key.elemental_legacy.power_one",InputConstants.KEY_Z,KeyMapping.Category.MISC);
 private static final KeyMapping POWER_TWO=new KeyMapping("key.elemental_legacy.power_two",InputConstants.KEY_X,KeyMapping.Category.MISC);
 private static final KeyMapping OPEN_SELECTION=new KeyMapping("key.elemental_legacy.open_selection",InputConstants.KEY_R,KeyMapping.Category.MISC);
 private static final String SYNC_PREFIX="ELEMENTAL_SYNC|";
 private static String localElement="";
 private static boolean openedForConnection;
 private static boolean awaitingSync;
 private ElementalClient(){}
 @SubscribeEvent public static void registerKeys(RegisterKeyMappingsEvent e){e.register(POWER_ONE);e.register(POWER_TWO);e.register(OPEN_SELECTION);}
 @SubscribeEvent public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e){
  localElement="";
  openedForConnection=false;
  awaitingSync=false;
 }
 @SubscribeEvent public static void onClientTick(ClientTickEvent.Post e){
  Minecraft mc=Minecraft.getInstance();
  if(mc.player==null||mc.level==null)return;

  // Ask the active world's server-side save whether this player has chosen an element.
  // This avoids using a client config key that can collide when worlds share a name.
  if(!openedForConnection){
   if(mc.gui.screen()!=null)return;
   openedForConnection=true;
   awaitingSync=true;
   mc.player.connection.sendCommand("elemental sync");
   return;
  }
  if(awaitingSync||mc.gui.screen()!=null)return;
  if(OPEN_SELECTION.consumeClick()){mc.gui.setScreen(new ElementalSkillScreen());return;}
  if(!localElement.isBlank()){
   if(POWER_ONE.consumeClick())useAbility(0);
   if(POWER_TWO.consumeClick())useAbility(1);
  }else{
   POWER_ONE.consumeClick();
   POWER_TWO.consumeClick();
  }
 }
 @SubscribeEvent public static void onSystemChat(ClientChatReceivedEvent.System event){
  String message=event.getMessage().getString();
  if(!message.startsWith(SYNC_PREFIX))return;
  event.setCanceled(true);
  String payload=message.substring(SYNC_PREFIX.length());
  Minecraft mc=Minecraft.getInstance();
  awaitingSync=false;
  if(payload.startsWith("chosen|")){
   String id=payload.substring("chosen|".length());
   if(Element.byId(id).isPresent()){
    localElement=id;
    return;
   }
  }
  if(payload.equals("unchosen")){
   localElement="";
   if(mc.player!=null&&mc.level!=null&&mc.gui.screen()==null){
    mc.gui.setScreen(new ElementSelectionScreen());
   }
   return;
  }
  // An invalid or unexpected response must not silently assign an element.
  localElement="";
  if(mc.player!=null&&mc.level!=null&&mc.gui.screen()==null){
   mc.gui.setScreen(new ElementSelectionScreen());
  }
 }
 private static void useAbility(int index){
  if(Minecraft.getInstance().player==null)return;
  Element element=Element.byId(localElement).orElse(null);if(element==null)return;
  var list=ElementalAbility.forElement(element);if(index>=list.size())return;
  Minecraft.getInstance().player.connection.sendCommand("elemental use "+list.get(index).id());
 }
 public static String getLocalElement(){return localElement;}
 public static void setLocalElement(String e){localElement=e;}
 @SubscribeEvent public static void renderHud(RenderGuiEvent.Post e){
  Minecraft mc=Minecraft.getInstance();if(mc.player==null||localElement.isBlank())return;
  var g=e.getGuiGraphics();int x=10,y=10;g.fill(x-4,y-4,x+220,y+48,0x990D1420);
  g.text(mc.font,"ELEMENTAL LEGACY",x,y,0xFFE9D8,false);
  String name=Element.byId(localElement).map(Element::displayName).orElse(localElement);
  g.text(mc.font,Component.literal("Dobra: "+name),x,y+13,0xFFFFFFFF,false);
  g.text(mc.font,"[Z] Técnica 1   [X] Técnica 2   [R] Técnicas",x,y+27,0xFFB9C7D8,false);
 }
}
