package dev.elementallegacy.client;

import dev.elementallegacy.ElementalLegacy;
import dev.elementallegacy.ability.ElementalAbility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Client-side skeletal choreography for the real player model.
 * Server packets carry the caster entity id and technique so nearby clients
 * animate the same avatar at the same time.
 */
@EventBusSubscriber(modid = ElementalLegacy.MOD_ID, value = Dist.CLIENT)
public final class PlayerAbilityAnimator {
    private static final int DURATION_TICKS = 38;
    private static final Map<Integer, ActiveAnimation> ACTIVE = new HashMap<>();

    private record ActiveAnimation(String abilityId, long startedAt) {}

    private PlayerAbilityAnimator() {}

    public static void start(int entityId, String abilityId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || ElementalAbility.byId(abilityId).isEmpty()) return;
        ACTIVE.put(entityId, new ActiveAnimation(abilityId, minecraft.level.getGameTime()));
    }

    private static float progress(int entityId, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return -1.0F;
        ActiveAnimation animation = ACTIVE.get(entityId);
        if (animation == null) return -1.0F;
        float t = (float) ((minecraft.level.getGameTime() + partialTick - animation.startedAt()) / DURATION_TICKS);
        if (t >= 1.0F) {
            ACTIVE.remove(entityId);
            return -1.0F;
        }
        return Math.max(0.0F, t);
    }

    @SubscribeEvent
    public static void renderThirdPerson(RenderPlayerEvent.Pre<?> event) {
        int entityId = event.getRenderState().id;
        float t = progress(entityId, event.getPartialTick());
        if (t < 0.0F) return;
        ActiveAnimation active = ACTIVE.get(entityId);
        if (active == null) return;
        animate(event.getRenderer().getModel(), active.abilityId(), t);
    }

    @SubscribeEvent
    public static void renderFirstPersonArm(RenderArmEvent event) {
        var avatarState = event.getPlayerRenderState().avatarRenderState;
        if (avatarState == null) return;
        int entityId = avatarState.id;
        float t = progress(entityId, avatarState.partialTick);
        if (t < 0.0F) return;
        ActiveAnimation active = ACTIVE.get(entityId);
        if (active == null) return;
        animateArm(event.getArmPart(), active.abilityId(), t,
                event.getArm() == HumanoidArm.RIGHT ? 1.0F : -1.0F);
    }

    private static void animate(PlayerModel model, String abilityId, float t) {
        float charge = 1.0F - smooth(t / 0.38F);
        float strike = smooth((t - 0.28F) / 0.18F) * (1.0F - smooth((t - 0.62F) / 0.38F));
        float recovery = smooth((t - 0.62F) / 0.38F);
        float pulse = (float) Math.sin(Math.PI * clamp(t));

        ModelPart body = model.body;
        ModelPart head = model.head;
        ModelPart rightArm = model.rightArm;
        ModelPart leftArm = model.leftArm;
        ModelPart rightLeg = model.rightLeg;
        ModelPart leftLeg = model.leftLeg;

        // Every technique starts with a readable weight shift and finishes with a recovery,
        // instead of leaving the avatar in its vanilla idle pose.
        body.xRot += 0.10F * pulse;
        head.xRot -= 0.05F * pulse;
        switch (abilityId) {
            case "flame_projectile" -> {
                body.yRot += -0.30F * charge + 0.20F * strike;
                body.xRot += 0.18F * strike;
                rightArm.xRot += -1.25F * charge + 1.72F * strike - 0.20F * recovery;
                rightArm.yRot += -0.32F * charge;
                rightArm.zRot += -0.18F * charge;
                leftArm.xRot += -0.62F * charge + 0.38F * strike;
                leftArm.yRot += 0.35F;
                rightLeg.xRot -= 0.54F * strike;
                leftLeg.xRot += 0.30F * strike;
            }
            case "flame_burst" -> {
                body.xRot += 0.32F * strike;
                body.yRot += 0.12F * pulse;
                rightArm.xRot += -1.45F * charge + 1.10F * strike;
                leftArm.xRot += -1.35F * charge + 1.15F * strike;
                rightArm.zRot -= 0.42F * strike;
                leftArm.zRot += 0.42F * strike;
                rightLeg.xRot -= 0.42F * strike;
                leftLeg.xRot += 0.42F * strike;
            }
            case "fire_whip" -> {
                body.yRot += -0.55F * pulse;
                body.zRot += 0.28F * strike;
                rightArm.xRot += -2.35F * charge + 1.0F * strike;
                rightArm.zRot += -0.70F * charge + 0.85F * strike;
                leftArm.xRot += -0.42F * charge;
                leftArm.zRot += 0.22F;
                leftLeg.xRot += 0.34F * strike;
                rightLeg.xRot -= 0.24F * strike;
            }
            case "focused_explosion" -> {
                body.xRot += 0.46F * charge - 0.58F * strike;
                body.yRot += 0.24F * charge;
                rightArm.xRot += -0.25F - 1.55F * charge + 1.50F * strike;
                leftArm.xRot += -0.25F - 1.55F * charge + 1.50F * strike;
                rightArm.zRot -= 0.82F * charge;
                leftArm.zRot += 0.82F * charge;
                rightLeg.xRot += 0.55F * charge - 0.32F * strike;
                leftLeg.xRot += 0.55F * charge - 0.32F * strike;
            }
            case "fire_propulsion" -> {
                body.xRot += 0.68F * pulse;
                body.yRot += 0.12F * strike;
                rightArm.xRot += 1.35F * pulse;
                leftArm.xRot += 1.35F * pulse;
                rightArm.zRot -= 0.42F;
                leftArm.zRot += 0.42F;
                rightLeg.xRot -= 0.95F * strike;
                leftLeg.xRot += 0.48F * strike;
            }
            case "fire_dragon" -> {
                body.xRot -= 0.24F * charge;
                body.yRot += 0.25F * pulse;
                rightArm.xRot += -2.10F * charge + 0.65F * strike;
                leftArm.xRot += -2.10F * charge + 0.65F * strike;
                rightArm.zRot -= 0.65F * (1.0F - recovery);
                leftArm.zRot += 0.65F * (1.0F - recovery);
                rightLeg.xRot -= 0.25F * strike;
                leftLeg.xRot -= 0.25F * strike;
            }
            case "water_projectile" -> {
                body.yRot += 0.24F * charge - 0.15F * strike;
                rightArm.xRot += -1.10F * charge + 1.35F * strike;
                leftArm.xRot += -0.85F * charge + 0.60F * strike;
                rightArm.yRot -= 0.30F * charge;
                leftArm.yRot += 0.28F * charge;
                rightLeg.xRot -= 0.36F * strike;
                leftLeg.xRot += 0.20F * strike;
            }
            case "water_whip" -> {
                body.yRot += 0.48F * pulse;
                body.zRot -= 0.18F * pulse;
                rightArm.xRot += -1.70F * charge + 0.95F * strike;
                leftArm.xRot += -0.55F + 0.30F * strike;
                rightArm.zRot += 0.95F * pulse;
                leftArm.zRot += -0.20F * pulse;
                leftLeg.xRot += 0.34F * strike;
            }
            case "ice_bind" -> {
                body.xRot += 0.22F * charge - 0.34F * strike;
                rightArm.xRot += -2.0F * charge + 1.25F * strike;
                leftArm.xRot += -2.0F * charge + 1.25F * strike;
                rightArm.zRot -= 0.20F;
                leftArm.zRot += 0.20F;
                rightLeg.xRot += 0.28F * strike;
                leftLeg.xRot += 0.28F * strike;
            }
            case "ice_blades" -> {
                body.yRot += -0.38F * charge + 0.55F * strike;
                rightArm.xRot += -1.55F * charge + 1.35F * strike;
                leftArm.xRot += -1.55F * charge + 0.50F * strike;
                rightArm.zRot -= 0.95F * pulse;
                leftArm.zRot += 0.95F * pulse;
                rightLeg.xRot -= 0.28F * strike;
            }
            case "water_shield" -> {
                body.yRot += 0.24F * pulse;
                rightArm.xRot += -1.0F * pulse - 0.25F;
                leftArm.xRot += -1.0F * pulse - 0.25F;
                rightArm.zRot -= 0.82F * pulse;
                leftArm.zRot += 0.82F * pulse;
                rightLeg.xRot += 0.14F * pulse;
                leftLeg.xRot -= 0.14F * pulse;
            }
            case "great_wave" -> {
                body.xRot += 0.30F * strike;
                body.yRot += 0.18F * pulse;
                rightArm.xRot += -2.15F * charge + 1.60F * strike;
                leftArm.xRot += -2.15F * charge + 1.60F * strike;
                rightArm.zRot -= 0.28F * strike;
                leftArm.zRot += 0.28F * strike;
                rightLeg.xRot -= 0.58F * strike;
                leftLeg.xRot += 0.38F * strike;
            }
            case "rock_throw" -> {
                body.yRot += -0.48F * charge + 0.30F * strike;
                body.xRot += 0.20F * charge;
                rightArm.xRot += -1.55F * charge + 1.55F * strike;
                leftArm.xRot += -0.45F * charge + 0.25F * strike;
                rightArm.zRot -= 0.35F * charge;
                rightLeg.xRot -= 0.42F * strike;
                leftLeg.xRot += 0.30F * strike;
            }
            case "stone_wall" -> {
                body.xRot += 0.48F * charge - 0.24F * strike;
                rightArm.xRot += -0.75F * charge + 1.50F * strike;
                leftArm.xRot += -0.75F * charge + 1.50F * strike;
                rightArm.zRot -= 0.25F;
                leftArm.zRot += 0.25F;
                rightLeg.xRot += 0.40F * charge - 0.25F * strike;
                leftLeg.xRot += 0.40F * charge - 0.25F * strike;
            }
            case "rock_cage" -> {
                body.yRot += 0.30F * pulse;
                rightArm.xRot += -0.85F - 0.70F * charge + 0.90F * strike;
                leftArm.xRot += -0.85F - 0.70F * charge + 0.90F * strike;
                rightArm.zRot += 0.62F * (1.0F - recovery);
                leftArm.zRot -= 0.62F * (1.0F - recovery);
                rightLeg.xRot += 0.25F * strike;
                leftLeg.xRot += 0.25F * strike;
            }
            case "seismic_wave" -> {
                body.xRot += 0.38F * charge - 0.68F * strike;
                rightArm.xRot += -0.60F * charge + 1.80F * strike;
                leftArm.xRot += -0.60F * charge + 1.80F * strike;
                rightLeg.xRot += 0.82F * strike;
                leftLeg.xRot -= 0.12F * strike;
            }
            case "stone_armor" -> {
                body.xRot -= 0.22F * pulse;
                rightArm.xRot += -1.25F * pulse - 0.25F;
                leftArm.xRot += -1.25F * pulse - 0.25F;
                rightArm.zRot -= 0.52F * (1.0F - recovery);
                leftArm.zRot += 0.52F * (1.0F - recovery);
                rightLeg.xRot += 0.12F * pulse;
                leftLeg.xRot -= 0.12F * pulse;
            }
            case "earthquake" -> {
                body.xRot += 0.60F * charge - 0.82F * strike;
                body.zRot += 0.08F * (float) Math.sin(t * Math.PI * 8.0F) * (1.0F - recovery);
                rightArm.xRot += -0.80F * charge + 1.90F * strike;
                leftArm.xRot += -0.80F * charge + 1.90F * strike;
                rightLeg.xRot += 0.90F * strike;
                leftLeg.xRot += 0.90F * strike;
            }
            case "air_blast" -> {
                body.yRot += 0.24F * charge - 0.20F * strike;
                rightArm.xRot += -1.25F * charge + 1.45F * strike;
                leftArm.xRot += -0.65F * charge + 0.55F * strike;
                rightArm.zRot -= 0.30F * charge;
                rightLeg.xRot -= 0.36F * strike;
                leftLeg.xRot += 0.24F * strike;
            }
            case "air_push" -> {
                body.xRot += 0.10F * strike;
                rightArm.xRot += -1.95F * charge + 1.30F * strike;
                leftArm.xRot += -1.95F * charge + 1.30F * strike;
                rightArm.zRot -= 0.75F * (1.0F - recovery);
                leftArm.zRot += 0.75F * (1.0F - recovery);
                rightLeg.xRot -= 0.20F * strike;
                leftLeg.xRot -= 0.20F * strike;
            }
            case "air_shield" -> {
                body.yRot += 0.38F * pulse;
                rightArm.xRot += -1.15F - 0.70F * pulse;
                leftArm.xRot += -1.15F - 0.70F * pulse;
                rightArm.zRot -= 1.10F * pulse;
                leftArm.zRot += 1.10F * pulse;
                rightLeg.xRot += 0.16F * pulse;
                leftLeg.xRot -= 0.16F * pulse;
            }
            case "air_jump" -> {
                body.xRot -= 0.28F * pulse;
                rightArm.xRot += -2.20F * pulse;
                leftArm.xRot += -2.20F * pulse;
                rightLeg.xRot += 0.80F * pulse;
                leftLeg.xRot += 0.80F * pulse;
            }
            case "air_dash" -> {
                body.xRot += 0.70F * pulse;
                rightArm.xRot += 1.20F * pulse;
                leftArm.xRot += 1.20F * pulse;
                rightArm.zRot -= 0.28F;
                leftArm.zRot += 0.28F;
                rightLeg.xRot -= 0.80F * strike;
                leftLeg.xRot += 0.50F * strike;
            }
            case "cyclone" -> {
                body.yRot += 0.75F * (float) Math.sin(t * Math.PI * 2.0F);
                body.zRot += 0.24F * pulse;
                rightArm.xRot += -2.10F * (1.0F - recovery);
                leftArm.xRot += -2.10F * (1.0F - recovery);
                rightArm.zRot -= 0.45F * (float) Math.sin(t * Math.PI * 2.0F);
                leftArm.zRot += 0.45F * (float) Math.sin(t * Math.PI * 2.0F);
                rightLeg.xRot -= 0.28F * pulse;
                leftLeg.xRot += 0.28F * pulse;
            }
            default -> {
                rightArm.xRot += -1.3F * charge + 1.2F * strike;
                leftArm.xRot += -0.5F * charge;
                body.xRot += 0.24F * strike;
                rightLeg.xRot -= 0.28F * strike;
            }
        }
    }

    private static void animateArm(ModelPart arm, String abilityId, float t, float side) {
        float charge = 1.0F - smooth(t / 0.38F);
        float strike = smooth((t - 0.28F) / 0.18F) * (1.0F - smooth((t - 0.62F) / 0.38F));
        float pulse = (float) Math.sin(Math.PI * clamp(t));
        // The first-person hand uses a stronger amplitude because only the forearm is visible.
        float emphasis = 1.35F;
        arm.xRot += emphasis * (-0.95F * charge + 1.25F * strike);
        arm.yRot += side * 0.32F * pulse;
        arm.zRot += side * 0.50F * pulse;
        if (abilityId.equals("fire_whip") || abilityId.equals("water_whip")
                || abilityId.equals("ice_blades") || abilityId.equals("cyclone")) {
            arm.zRot += side * 0.65F * (float) Math.sin(t * Math.PI * 2.0F);
        }
        if (abilityId.equals("air_jump") || abilityId.equals("fire_dragon") || abilityId.equals("great_wave")) {
            arm.xRot -= 0.70F * pulse;
        }
    }

    private static float smooth(float value) {
        float x = clamp(value);
        return x * x * (3.0F - 2.0F * x);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}