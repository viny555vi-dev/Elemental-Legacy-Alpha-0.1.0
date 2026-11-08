package dev.elementallegacy.client;
import dev.elementallegacy.ElementalLegacy;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
@Mod(value=ElementalLegacy.MOD_ID,dist=Dist.CLIENT)
public final class ElementalLegacyClient {
 public ElementalLegacyClient(IEventBus modEventBus){modEventBus.addListener(ElementalClient::registerKeys);}
}