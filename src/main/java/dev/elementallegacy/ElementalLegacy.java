package dev.elementallegacy;
import dev.elementallegacy.command.ElementalCommands;
import dev.elementallegacy.progression.PlayerProgressEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
@Mod(ElementalLegacy.MOD_ID)
public final class ElementalLegacy {
 public static final String MOD_ID="elemental_legacy";
 public ElementalLegacy(IEventBus modEventBus) {
  NeoForge.EVENT_BUS.addListener(ElementalCommands::register);
  NeoForge.EVENT_BUS.addListener(PlayerProgressEvents::onPlayerTick);
 }
}