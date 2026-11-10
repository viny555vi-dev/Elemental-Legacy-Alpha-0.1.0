package dev.elementallegacy.client;

import dev.elementallegacy.ElementalLegacy;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

/**
 * Player poses must be written after vanilla extracts the avatar render state
 * and before LivingEntityRenderer submits the player model.
 */
@EventBusSubscriber(
        modid = ElementalLegacy.MOD_ID,
        value = Dist.CLIENT
)
public final class PlayerAbilityRenderStateModifiers {
    private PlayerAbilityRenderStateModifiers() {}

    @SubscribeEvent
    public static void register(RegisterRenderStateModifiersEvent event) {
        event.registerAvatarEntityModifier(new AvatarRenderStateModifier() {
            @Override
            public <T extends Avatar & ClientAvatarEntity> void accept(T avatar, AvatarRenderState state) {
                PlayerAbilityAnimator.applyRenderState(state);
            }
        });
    }
}
