package dev.elementallegacy;

import dev.elementallegacy.command.ElementalCommands;
import dev.elementallegacy.progression.PlayerProgressEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(ElementalLegacy.MOD_ID)
public final class ElementalLegacy {
    public static final String MOD_ID = "elemental_legacy";

    public ElementalLegacy() {
        IEventBus gameBus = NeoForge.EVENT_BUS;
        gameBus.addListener(ElementalCommands::register);
        gameBus.addListener(PlayerProgressEvents::onPlayerTick);
    }
}
