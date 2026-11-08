package dev.elementallegacy.progression;
import dev.elementallegacy.data.ElementalWorldData;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
public final class PlayerProgressEvents {
 private PlayerProgressEvents(){}
 public static void onPlayerTick(PlayerTickEvent.Post event){
  if(!(event.getEntity() instanceof ServerPlayer p)||p.tickCount%20!=0||p.level().getServer()==null)return;
  ElementalWorldData.get(p.level().getServer()).regenerateEnergy(p.getUUID(),1.5F);
 }
}