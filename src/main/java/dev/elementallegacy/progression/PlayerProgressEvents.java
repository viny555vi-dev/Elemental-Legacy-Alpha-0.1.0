package dev.elementallegacy.progression;

import dev.elementallegacy.ability.ElementalAbilityService;
import dev.elementallegacy.data.ElementalWorldData;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class PlayerProgressEvents {
    private PlayerProgressEvents() {}

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().getServer() == null) return;

        // Visual timelines advance every tick; gameplay regeneration remains once per second.
        ElementalAbilityService.tick(player);
        if (player.tickCount % 20 != 0 || ElementalAbilityService.isRegenerationPaused(player)) return;

        // Combat recovery is deliberately slower than exploration recovery.
        float regeneration = player.hurtTime > 0 ? 2.0F : 4.0F;
        ElementalWorldData.get(player.level().getServer()).regenerateEnergy(player.getUUID(), regeneration);
    }
}
