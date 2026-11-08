package dev.elementallegacy.client;
import dev.elementallegacy.ElementalLegacy;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
@EventBusSubscriber(modid=ElementalLegacy.MOD_ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ElementalClientModEvents {
 private ElementalClientModEvents(){}
 @SubscribeEvent public static void registerKeys(RegisterKeyMappingsEvent event){ElementalClient.registerKeys(event);}
}