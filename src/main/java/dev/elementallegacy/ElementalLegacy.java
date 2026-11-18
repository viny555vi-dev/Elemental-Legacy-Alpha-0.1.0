package dev.elementallegacy;
import dev.elementallegacy.command.ElementalCommands;
import dev.elementallegacy.progression.PlayerProgressEvents;
import dev.elementallegacy.ability.StoneArmorService;
import dev.elementallegacy.network.AbilityAnimationPayload;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
@Mod(ElementalLegacy.MOD_ID)
public final class ElementalLegacy {
 public static final String MOD_ID="elemental_legacy";
 public ElementalLegacy(IEventBus modEventBus) {
  modEventBus.addListener(AbilityAnimationPayload::register);
  NeoForge.EVENT_BUS.addListener(ElementalCommands::register);
  NeoForge.EVENT_BUS.addListener(PlayerProgressEvents::onPlayerTick);
  NeoForge.EVENT_BUS.addListener(StoneArmorService::onIncomingDamage);
  NeoForge.EVENT_BUS.addListener(StoneArmorService::onKnockBack);
  NeoForge.EVENT_BUS.addListener(StoneArmorService::onJump);
  NeoForge.EVENT_BUS.addListener(StoneArmorService::onLogout);
 }
}