package dev.elementallegacy.progression;

import dev.elementallegacy.data.ElementalWorldData;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class PlayerProgressEvents {
    private PlayerProgressEvents() {}

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % 20 != 0) return;
        if (player.level().getServer() == null) return;
        ElementalWorldData.get(player.level().getServer()).regenerateEnergy(player.getUUID(), 1.5F);
    }
}
