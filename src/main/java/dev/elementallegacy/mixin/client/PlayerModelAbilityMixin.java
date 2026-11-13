package dev.elementallegacy.mixin.client;

import dev.elementallegacy.client.PlayerAbilityAnimator;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelAbilityMixin {
    @Inject(method = "setupAnim", at = @At("TAIL"))
    private void elementalLegacy$applyTechniquePose(AvatarRenderState state, CallbackInfo callback) {
        PlayerAbilityAnimator.applyModelPose(state, (PlayerModel) (Object) this);
    }
}
