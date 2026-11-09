package dev.elementallegacy.client;

import dev.elementallegacy.ElementalLegacy;
import dev.elementallegacy.network.AbilityAnimationPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

@EventBusSubscriber(modid = ElementalLegacy.MOD_ID, value = Dist.CLIENT)
public final class AbilityAnimationClientPayloads {
    private AbilityAnimationClientPayloads() {}

    @SubscribeEvent
    public static void register(RegisterClientPayloadHandlersEvent event) {
        event.register(AbilityAnimationPayload.TYPE, (payload, context) ->
                context.enqueueWork(() -> PlayerAbilityAnimator.start(payload.entityId(), payload.abilityId())));
    }
}