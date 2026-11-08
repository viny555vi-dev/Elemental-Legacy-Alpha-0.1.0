package dev.elementallegacy.client;
import dev.elementallegacy.ability.ElementalAbility;
import dev.elementallegacy.element.Element;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;
public final class ElementalSkillScreen extends Screen {
 private final Element element;
 private final List<ElementalAbility> abilities;
 public ElementalSkillScreen(){
  super(Component.literal("Técnicas elementais"));
  this.element=Element.byId(ElementalClient.getLocalElement()).orElse(null);
  this.abilities=element==null?List.of():ElementalAbility.forElement(element);
 }
 @Override protected void init(){
  int w=260,x=width/2-w/2,y=height/2-24;
  for(int i=0;i<abilities.size();i++){
   ElementalAbility ability=abilities.get(i);
   String key=i==0?"Z":"X";
   addRenderableWidget(Button.builder(Component.literal("["+key+"]  "+ability.displayName()+"  •  "+ability.energyCost()+" energia"),
    b->use(ability)).bounds(x,y+i*36,w,28).build());
  }
  addRenderableWidget(Button.builder(Component.literal("Fechar"),b->onClose()).bounds(width/2-60,y+abilities.size()*36+8,120,20).build());
 }
 private void use(ElementalAbility ability){
  if(Minecraft.getInstance().player!=null)Minecraft.getInstance().player.connection.sendCommand("elemental use "+ability.id());
  onClose();
 }
 @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float partial){
  // Screen.extractRenderState already extracts the background exactly once.
  // Draw custom labels after the superclass has rendered the background and widgets.
  super.extractRenderState(g,mx,my,partial);
  String name=element==null?"Sem elemento":element.displayName();
  g.centeredText(font,"TÉCNICAS DE "+name.toUpperCase(),width/2,height/2-78,0xFFE9D8);
  g.centeredText(font,"Use Z ou X durante o combate para ativar diretamente.",width/2,height/2-58,0xC9D2DF);
 }
 @Override public boolean isPauseScreen(){return false;}
}