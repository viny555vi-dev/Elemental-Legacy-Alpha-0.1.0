package dev.elementallegacy.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
public final class ElementSelectionScreen extends Screen {
 public ElementSelectionScreen(){super(Component.literal("Escolha seu caminho elemental"));}
 @Override protected void init(){
  int w=190,l=width/2-w-8,r=width/2+8,t=height/2-38;
  addRenderableWidget(Button.builder(Component.literal("ÁGUA  •  Maré"),b->choose("tide")).bounds(l,t,w,28).build());
  addRenderableWidget(Button.builder(Component.literal("TERRA  •  Rocha"),b->choose("stone")).bounds(r,t,w,28).build());
  addRenderableWidget(Button.builder(Component.literal("FOGO  •  Brasa"),b->choose("ember")).bounds(l,t+36,w,28).build());
  addRenderableWidget(Button.builder(Component.literal("AR  •  Vendaval"),b->choose("gale")).bounds(r,t+36,w,28).build());
 }
 private void choose(String e){
  ElementalClient.setLocalElement(e);
  if(Minecraft.getInstance().player!=null)Minecraft.getInstance().player.connection.sendCommand("elemental choose "+e);
  onClose();
 }
 @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float partial){
  // Screen.extractRenderState already extracts the background exactly once.
  // Draw custom labels only after the superclass has rendered background/widgets.
  super.extractRenderState(g,mx,my,partial);
  g.centeredText(font,"ELEMENTAL LEGACY",width/2,height/2-105,0xF2E8D5);
  g.centeredText(font,"Escolha sua dobra. Essa decisão é permanente neste mundo.",width/2,height/2-82,0xC9D2DF);
  g.centeredText(font,"Cada caminho possui técnicas, estilo de combate e progressão próprios.",width/2,height/2-66,0xAAB6C5);
 }
 @Override public boolean shouldCloseOnEsc(){return false;}
 @Override public boolean isPauseScreen(){return false;}
}
