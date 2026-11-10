package dev.elementallegacy.client;

import dev.elementallegacy.ElementalLegacy;
import dev.elementallegacy.ability.ElementalAbility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderArmEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Applies the animation to render-state inputs BEFORE vanilla builds the player model.
 * RenderPlayerEvent.Pre runs before AvatarRenderer calls the normal model setup,
 * so mutating ModelPart rotations in that event gets overwritten. We instead adjust
 * AvatarRenderState (arm poses, crouch/swim/fall-flying and locomotion values) which
 * is modified by NeoForge's RegisterRenderStateModifiersEvent before model submission.
 * PlayerModel.setupAnim consumes immediately afterwards.
 */
@EventBusSubscriber(modid = ElementalLegacy.MOD_ID, value = Dist.CLIENT)
public final class PlayerAbilityAnimator {
    private static final int DURATION_TICKS = 38;
    private static final Map<Integer, ActiveAnimation> ACTIVE = new HashMap<>();
    private static final Set<String> SMOKE_POSES_APPLIED = new HashSet<>();
    private static final Set<String> SMOKE_MODEL_POSES_VERIFIED = new HashSet<>();

    private record ActiveAnimation(String abilityId, long startedAt) {}

    private PlayerAbilityAnimator() {}

    public static void resetSmokeEvidence() {
        SMOKE_POSES_APPLIED.clear();
        SMOKE_MODEL_POSES_VERIFIED.clear();
    }

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
        float value = (float) ((minecraft.level.getGameTime() + partialTick - animation.startedAt()) / DURATION_TICKS);
        if (value >= 1.0F) {
            ACTIVE.remove(entityId);
            return -1.0F;
        }
        return Math.max(0.0F, value);
    }

    /** Applies animation values during NeoForge's render-state extraction phase. */
    public static void applyRenderState(AvatarRenderState state) {
        float t = progress(state.id, state.partialTick);
        if (t < 0.0F) return;
        ActiveAnimation active = ACTIVE.get(state.id);
        if (active == null) return;
        animateRenderState(state, active.abilityId(), t);
        if (Boolean.getBoolean("elemental.vfx.smoke") && SMOKE_POSES_APPLIED.add(active.abilityId())) {
            System.out.println("[ELEMENTAL_VFX_SMOKE] PLAYER_STATE_MODIFIED " + active.abilityId()
                    + " bodyRot=" + state.bodyRot
                    + " crouch=" + state.isCrouching
                    + " rightArm=" + state.rightArmPose
                    + " leftArm=" + state.leftArmPose);
        }
    }

    @SubscribeEvent
    public static void verifyRenderedPose(net.neoforged.neoforge.client.event.RenderPlayerEvent.Post<?> event) {
        if (!Boolean.getBoolean("elemental.vfx.smoke")) return;
        AvatarRenderState state = event.getRenderState();
        ActiveAnimation active = ACTIVE.get(state.id);
        if (active == null || SMOKE_MODEL_POSES_VERIFIED.contains(active.abilityId())) return;
        var model = event.getRenderer().getModel();
        float rightArmX = model.rightArm.xRot;
        float leftArmX = model.leftArm.xRot;
        float bodyX = model.body.xRot;
        float rightArmZ = model.rightArm.zRot;
        float leftArmZ = model.leftArm.zRot;
        boolean visiblyPosed = Math.abs(rightArmX) > 0.30F || Math.abs(leftArmX) > 0.30F
                || Math.abs(bodyX) > 0.20F || Math.abs(rightArmZ) > 0.20F || Math.abs(leftArmZ) > 0.20F;
        if (visiblyPosed && SMOKE_MODEL_POSES_VERIFIED.add(active.abilityId())) {
            System.out.println("[ELEMENTAL_VFX_SMOKE] PLAYER_MODEL_POSED " + active.abilityId()
                    + " rightArmX=" + rightArmX + " leftArmX=" + leftArmX
                    + " bodyX=" + bodyX + " rightArmZ=" + rightArmZ + " leftArmZ=" + leftArmZ);
        }
    }

    @SubscribeEvent
    public static void renderFirstPersonArm(RenderArmEvent event) {
        var avatarState = event.getPlayerRenderState().avatarRenderState;
        if (avatarState == null) return;
        float t = progress(avatarState.id, avatarState.partialTick);
        if (t < 0.0F) return;
        ActiveAnimation active = ACTIVE.get(avatarState.id);
        if (active != null) {
            animateArm(event.getArmPart(), active.abilityId(), t,
                    event.getArm() == HumanoidArm.RIGHT ? 1.0F : -1.0F);
        }
    }

    private static void animateRenderState(AvatarRenderState state, String abilityId, float t) {
        float charge = 1.0F - smooth(t / 0.38F);
        float release = smooth((t - 0.28F) / 0.18F) * (1.0F - smooth((t - 0.66F) / 0.34F));
        float recovery = smooth((t - 0.66F) / 0.34F);
        float pulse = (float) Math.sin(Math.PI * clamp(t));

        float bodyTurn = 0.0F;
        float bodyLean = 0.0F;
        float gait = 0.48F + 0.30F * pulse;
        boolean crouch = charge > 0.28F;
        boolean flyingPose = false;
        boolean swimmingPose = false;
        HumanoidModel.ArmPose mainPose = HumanoidModel.ArmPose.THROW_TRIDENT;
        HumanoidModel.ArmPose offPose = HumanoidModel.ArmPose.EMPTY;

        // Distinct body language for each family; all values feed the actual vanilla model setup.
        switch (abilityId) {
            case "flame_projectile" -> {
                bodyTurn = -19.0F * charge + 11.0F * release;
                bodyLean = 8.0F * release;
                mainPose = HumanoidModel.ArmPose.THROW_TRIDENT;
                offPose = HumanoidModel.ArmPose.BLOCK;
            }
            case "flame_burst" -> {
                bodyTurn = 12.0F * pulse;
                bodyLean = -13.0F * release;
                gait = 0.95F;
                mainPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
                offPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
            }
            case "fire_whip" -> {
                bodyTurn = -31.0F * pulse;
                bodyLean = 6.0F * release;
                gait = 0.78F;
                mainPose = HumanoidModel.ArmPose.SPEAR;
                offPose = HumanoidModel.ArmPose.BLOCK;
            }
            case "focused_explosion" -> {
                bodyTurn = 16.0F * charge;
                bodyLean = 17.0F * charge - 19.0F * release;
                crouch = charge > 0.12F;
                gait = 0.84F;
                mainPose = HumanoidModel.ArmPose.TOOT_HORN;
                offPose = HumanoidModel.ArmPose.TOOT_HORN;
            }
            case "fire_propulsion", "air_dash" -> {
                bodyTurn = 5.0F * pulse;
                bodyLean = -22.0F * pulse;
                gait = 1.0F;
                crouch = false;
                flyingPose = t > 0.20F && t < 0.88F;
                mainPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
                offPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
            }
            case "fire_dragon" -> {
                bodyTurn = 28.0F * pulse;
                bodyLean = 11.0F * charge - 5.0F * release;
                gait = 0.80F;
                mainPose = HumanoidModel.ArmPose.TOOT_HORN;
                offPose = HumanoidModel.ArmPose.TOOT_HORN;
            }
            case "water_projectile" -> {
                bodyTurn = 15.0F * charge - 9.0F * release;
                bodyLean = 7.0F * release;
                mainPose = HumanoidModel.ArmPose.SPEAR;
                offPose = HumanoidModel.ArmPose.BLOCK;
            }
            case "water_whip" -> {
                bodyTurn = 29.0F * pulse;
                bodyLean = 5.0F * pulse;
                gait = 0.72F;
                swimmingPose = true;
                mainPose = HumanoidModel.ArmPose.SPEAR;
                offPose = HumanoidModel.ArmPose.EMPTY;
            }
            case "ice_bind" -> {
                bodyTurn = -12.0F * charge;
                bodyLean = 12.0F * charge - 9.0F * release;
                crouch = charge > 0.10F;
                gait = 0.72F;
                mainPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
                offPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
            }
            case "ice_blades" -> {
                bodyTurn = -23.0F * pulse;
                bodyLean = 8.0F * release;
                gait = 0.88F;
                swimmingPose = true;
                mainPose = HumanoidModel.ArmPose.SPEAR;
                offPose = HumanoidModel.ArmPose.SPEAR;
            }
            case "water_shield" -> {
                bodyTurn = 8.0F * pulse;
                bodyLean = -4.0F * pulse;
                crouch = false;
                mainPose = HumanoidModel.ArmPose.BLOCK;
                offPose = HumanoidModel.ArmPose.BLOCK;
            }
            case "great_wave" -> {
                bodyTurn = -16.0F * charge + 15.0F * release;
                bodyLean = 18.0F * charge - 16.0F * release;
                gait = 0.96F;
                swimmingPose = true;
                mainPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
                offPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
            }
            case "rock_throw" -> {
                bodyTurn = -26.0F * charge + 16.0F * release;
                bodyLean = 9.0F * charge - 5.0F * release;
                gait = 0.74F;
                mainPose = HumanoidModel.ArmPose.THROW_TRIDENT;
                offPose = HumanoidModel.ArmPose.BLOCK;
            }
            case "stone_wall" -> {
                bodyTurn = 0.0F;
                bodyLean = 18.0F * charge - 8.0F * release;
                crouch = charge > 0.15F;
                gait = 0.68F;
                mainPose = HumanoidModel.ArmPose.TOOT_HORN;
                offPose = HumanoidModel.ArmPose.TOOT_HORN;
            }
            case "rock_cage" -> {
                bodyTurn = 12.0F * pulse;
                bodyLean = -8.0F * pulse;
                crouch = false;
                gait = 0.65F;
                mainPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
                offPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
            }
            case "seismic_wave" -> {
                bodyTurn = -7.0F * pulse;
                bodyLean = 21.0F * charge - 20.0F * release;
                crouch = charge > 0.08F;
                gait = 0.92F;
                mainPose = HumanoidModel.ArmPose.TOOT_HORN;
                offPose = HumanoidModel.ArmPose.TOOT_HORN;
            }
            case "stone_armor" -> {
                bodyTurn = 7.0F * pulse;
                bodyLean = 6.0F * pulse;
                crouch = false;
                gait = 0.52F;
                mainPose = HumanoidModel.ArmPose.BLOCK;
                offPose = HumanoidModel.ArmPose.BLOCK;
            }
            case "earthquake" -> {
                bodyTurn = 4.0F * (float) Math.sin(t * Math.PI * 4.0F);
                bodyLean = 24.0F * charge - 27.0F * release;
                crouch = charge > 0.05F;
                gait = 1.0F;
                mainPose = HumanoidModel.ArmPose.TOOT_HORN;
                offPose = HumanoidModel.ArmPose.TOOT_HORN;
            }
            case "air_blast" -> {
                bodyTurn = 18.0F * charge - 12.0F * release;
                bodyLean = 4.0F * release;
                mainPose = HumanoidModel.ArmPose.THROW_TRIDENT;
                offPose = HumanoidModel.ArmPose.BLOCK;
            }
            case "air_push" -> {
                bodyTurn = 0.0F;
                bodyLean = -9.0F * release;
                gait = 0.82F;
                mainPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
                offPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
            }
            case "air_shield" -> {
                bodyTurn = 17.0F * pulse;
                bodyLean = 0.0F;
                crouch = false;
                mainPose = HumanoidModel.ArmPose.BLOCK;
                offPose = HumanoidModel.ArmPose.BLOCK;
            }
            case "air_jump" -> {
                bodyTurn = 0.0F;
                bodyLean = -18.0F * pulse;
                crouch = false;
                flyingPose = pulse > 0.18F;
                gait = 0.90F;
                mainPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
                offPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
            }
            case "cyclone" -> {
                bodyTurn = 36.0F * (float) Math.sin(t * Math.PI * 2.0F);
                bodyLean = 9.0F * pulse;
                crouch = false;
                gait = 0.88F;
                mainPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
                offPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
            }
            default -> {
                bodyTurn = 12.0F * pulse;
                bodyLean = 8.0F * release;
            }
        }

        // The player model consumes these render-state values immediately after RenderPlayerEvent.Pre.
        state.bodyRot += bodyTurn;
        state.yRot -= bodyTurn * 0.28F;
        state.xRot += bodyLean;
        state.isCrouching = state.isCrouching || crouch;
        state.isFallFlying = state.isFallFlying || flyingPose;
        if (flyingPose) state.fallFlyingTimeInTicks = Math.max(state.fallFlyingTimeInTicks, t * DURATION_TICKS);
        if (swimmingPose) {
            state.isVisuallySwimming = true;
            state.swimAmount = Math.max(state.swimAmount, pulse * 0.78F);
        }
        state.walkAnimationSpeed = Math.max(state.walkAnimationSpeed, gait * pulse);
        state.walkAnimationPos += t * (7.5F + gait * 4.0F);

        if (state.mainArm == HumanoidArm.LEFT) {
            state.leftArmPose = mainPose;
            state.rightArmPose = offPose;
        } else {
            state.rightArmPose = mainPose;
            state.leftArmPose = offPose;
        }
    }

    private static void animateArm(ModelPart arm, String abilityId, float t, float side) {
        float charge = 1.0F - smooth(t / 0.38F);
        float release = smooth((t - 0.28F) / 0.18F) * (1.0F - smooth((t - 0.66F) / 0.34F));
        float pulse = (float) Math.sin(Math.PI * clamp(t));

        // First-person rendering has its own hook after the arm part is prepared,
        // so these rotations are applied directly at the correct time.
        arm.xRot -= 1.05F * charge;
        arm.xRot += 1.45F * release;
        arm.yRot += side * 0.42F * pulse;
        arm.zRot += side * 0.58F * pulse;

        if (abilityId.equals("fire_whip") || abilityId.equals("water_whip")
                || abilityId.equals("ice_blades") || abilityId.equals("cyclone")) {
            arm.zRot += side * 0.85F * (float) Math.sin(t * Math.PI * 2.0F);
        }
        if (abilityId.equals("air_jump") || abilityId.equals("fire_dragon")
                || abilityId.equals("great_wave") || abilityId.equals("stone_wall")) {
            arm.xRot -= 0.72F * pulse;
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