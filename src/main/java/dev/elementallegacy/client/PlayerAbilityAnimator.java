package dev.elementallegacy.client;

import dev.elementallegacy.ElementalLegacy;
import dev.elementallegacy.ability.ElementalAbility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
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
 * Drives technique-specific render-state inputs and final articulated ModelPart poses.
 * The mixin applies torso, head, arm and leg rotations at the tail of PlayerModel.setupAnim,
 * after vanilla has established its base pose, so the technique animation is not overwritten.
 */
@EventBusSubscriber(modid = ElementalLegacy.MOD_ID, value = Dist.CLIENT)
public final class PlayerAbilityAnimator {
    private static final int DURATION_TICKS = 38;
    private static final Map<Integer, ActiveAnimation> ACTIVE = new HashMap<>();
    private static final Map<Integer, ModelPose> MODEL_POSES = new HashMap<>();
    private static final Set<String> SMOKE_POSES_APPLIED = new HashSet<>();
    private static final Set<String> SMOKE_MODEL_POSES_VERIFIED = new HashSet<>();
    private static final Map<String, String> SMOKE_TORSO_SIGNATURES = new HashMap<>();
    private static final Map<String, String> SMOKE_POSE_SIGNATURES = new HashMap<>();
    private static final Set<String> SMOKE_POSE_COLLISIONS_LOGGED = new HashSet<>();
    private static PoseSnapshot SMOKE_NEUTRAL_POSE;

    private record ActiveAnimation(String abilityId, long startedAt) {}
    private record ModelPose(
            float torsoPitchDegrees, float torsoYawDegrees, float torsoRollDegrees,
            float armPitchDegrees, float armYawDegrees, float armRollDegrees, float armSwingDegrees,
            float legDriveDegrees, float legSpreadDegrees
    ) {}
    private record PoseSnapshot(
            float rightArmX, float leftArmX, float rightArmY, float leftArmY, float rightArmZ, float leftArmZ,
            float bodyX, float bodyY, float bodyZ,
            float rightLegX, float leftLegX, float rightLegY, float leftLegY, float rightLegZ, float leftLegZ
    ) {}

    private PlayerAbilityAnimator() {}

    public static void resetSmokeEvidence() {
        SMOKE_POSES_APPLIED.clear();
        SMOKE_MODEL_POSES_VERIFIED.clear();
        SMOKE_TORSO_SIGNATURES.clear();
        SMOKE_POSE_SIGNATURES.clear();
        SMOKE_POSE_COLLISIONS_LOGGED.clear();
        SMOKE_NEUTRAL_POSE = null;
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
            MODEL_POSES.remove(entityId);
            return -1.0F;
        }
        return Math.max(0.0F, value);
    }

    /** Applies animation values during NeoForge's render-state extraction phase. */
    public static void applyRenderState(AvatarRenderState state) {
        float t = progress(state.id, state.partialTick);
        if (t < 0.0F) {
            MODEL_POSES.remove(state.id);
            return;
        }
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

    /**
     * Called by the PlayerModel.setupAnim mixin after vanilla has built its base pose.
     * This is the point at which torso, head, arms and legs can be articulated without
     * the vanilla model setup overwriting the rotations.
     */
    public static void applyModelPose(AvatarRenderState state, PlayerModel model) {
        ModelPose pose = MODEL_POSES.get(state.id);
        if (pose == null) return;

        float torsoPitch = radians(pose.torsoPitchDegrees());
        float torsoYaw = radians(pose.torsoYawDegrees());
        float torsoRoll = radians(pose.torsoRollDegrees());

        model.body.xRot += torsoPitch;
        model.body.yRot += torsoYaw;
        model.body.zRot += torsoRoll;

        // Keep the head and shoulder line connected to the torso's movement.
        model.head.xRot += torsoPitch * 0.28F;
        model.head.yRot += torsoYaw * 0.30F;
        model.head.zRot += torsoRoll * 0.32F;

        model.rightArm.xRot += radians(pose.armPitchDegrees() + pose.armSwingDegrees());
        model.leftArm.xRot += radians(pose.armPitchDegrees() - pose.armSwingDegrees());
        model.rightArm.yRot += radians(pose.armYawDegrees());
        model.leftArm.yRot -= radians(pose.armYawDegrees());
        model.rightArm.zRot += radians(pose.armRollDegrees());
        model.leftArm.zRot -= radians(pose.armRollDegrees());

        model.rightLeg.xRot += radians(pose.legDriveDegrees());
        model.leftLeg.xRot += radians(pose.legDriveDegrees() * 0.72F);
        model.rightLeg.zRot += radians(pose.legSpreadDegrees());
        model.leftLeg.zRot -= radians(pose.legSpreadDegrees());
    }

    private static float radians(float degrees) {
        return degrees * (float) (Math.PI / 180.0);
    }

    @SubscribeEvent
    public static void verifyRenderedPose(net.neoforged.neoforge.client.event.RenderPlayerEvent.Post<?> event) {
        if (!Boolean.getBoolean("elemental.vfx.smoke")) return;
        AvatarRenderState state = event.getRenderState();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || state.id != minecraft.player.getId()) return;

        var model = event.getRenderer().getModel();
        PoseSnapshot current = new PoseSnapshot(
                model.rightArm.xRot, model.leftArm.xRot,
                model.rightArm.yRot, model.leftArm.yRot,
                model.rightArm.zRot, model.leftArm.zRot,
                model.body.xRot, model.body.yRot, model.body.zRot,
                model.rightLeg.xRot, model.leftLeg.xRot,
                model.rightLeg.yRot, model.leftLeg.yRot,
                model.rightLeg.zRot, model.leftLeg.zRot
        );
        ActiveAnimation active = ACTIVE.get(state.id);
        if (active == null) {
            if (SMOKE_NEUTRAL_POSE == null) {
                SMOKE_NEUTRAL_POSE = current;
                System.out.println("[ELEMENTAL_VFX_SMOKE] NEUTRAL_PLAYER_MODEL_CAPTURED");
            }
            return;
        }
        if (SMOKE_NEUTRAL_POSE == null || SMOKE_MODEL_POSES_VERIFIED.contains(active.abilityId())) return;

        // Compare after the wind-up, while the technique's full-body action is visible.
        float t = progress(state.id, state.partialTick);
        if (t < 0.48F) return;

        PoseSnapshot neutral = SMOKE_NEUTRAL_POSE;
        float armDelta = Math.max(
                Math.max(Math.abs(current.rightArmX() - neutral.rightArmX()),
                        Math.abs(current.leftArmX() - neutral.leftArmX())),
                Math.max(
                        Math.max(Math.abs(current.rightArmY() - neutral.rightArmY()),
                                Math.abs(current.leftArmY() - neutral.leftArmY())),
                        Math.max(Math.abs(current.rightArmZ() - neutral.rightArmZ()),
                                Math.abs(current.leftArmZ() - neutral.leftArmZ()))
                )
        );
        float torsoDelta = Math.max(
                Math.abs(current.bodyX() - neutral.bodyX()),
                Math.max(Math.abs(current.bodyY() - neutral.bodyY()),
                        Math.abs(current.bodyZ() - neutral.bodyZ()))
        );
        float stanceDelta = Math.max(
                Math.max(Math.abs(current.rightLegX() - neutral.rightLegX()),
                        Math.abs(current.leftLegX() - neutral.leftLegX())),
                Math.max(
                        Math.max(Math.abs(current.rightLegY() - neutral.rightLegY()),
                                Math.abs(current.leftLegY() - neutral.leftLegY())),
                        Math.max(Math.abs(current.rightLegZ() - neutral.rightLegZ()),
                                Math.abs(current.leftLegZ() - neutral.leftLegZ()))
                )
        );
        if (armDelta <= 0.12F || torsoDelta <= 0.10F || stanceDelta <= 0.05F) return;

        // Quantized torso signatures prevent 24 log entries from passing with repeated trunk poses.
        String torsoSignature = String.format(java.util.Locale.ROOT, "%.2f|%.2f|%.2f",
                current.bodyX(), current.bodyY(), current.bodyZ());
        String fullSignature = String.format(java.util.Locale.ROOT,
                "%.2f|%.2f|%.2f|%.2f|%.2f|%.2f|%.2f|%.2f|%.2f",
                current.bodyX(), current.bodyY(), current.bodyZ(),
                current.rightArmX(), current.leftArmX(), current.rightArmY(), current.leftArmY(),
                current.rightLegX(), current.leftLegX());

        String torsoCollision = SMOKE_TORSO_SIGNATURES.entrySet().stream()
                .filter(entry -> entry.getValue().equals(torsoSignature))
                .map(Map.Entry::getKey).findFirst().orElse(null);
        String poseCollision = SMOKE_POSE_SIGNATURES.entrySet().stream()
                .filter(entry -> entry.getValue().equals(fullSignature))
                .map(Map.Entry::getKey).findFirst().orElse(null);
        if (torsoCollision != null || poseCollision != null) {
            if (SMOKE_POSE_COLLISIONS_LOGGED.add(active.abilityId())) {
                System.out.println("[ELEMENTAL_VFX_SMOKE] POSE_SIGNATURE_COLLISION " + active.abilityId()
                        + " torsoMatches=" + torsoCollision + " fullPoseMatches=" + poseCollision);
            }
            return;
        }

        SMOKE_TORSO_SIGNATURES.put(active.abilityId(), torsoSignature);
        SMOKE_POSE_SIGNATURES.put(active.abilityId(), fullSignature);
        if (SMOKE_MODEL_POSES_VERIFIED.add(active.abilityId())) {
            System.out.println("[ELEMENTAL_VFX_SMOKE] PLAYER_MODEL_POSED " + active.abilityId()
                    + " atMs=" + System.currentTimeMillis()
                    + " armDelta=" + armDelta + " torsoDelta=" + torsoDelta + " stanceDelta=" + stanceDelta
                    + " torsoSignature=" + torsoSignature
                    + " rightArmX=" + current.rightArmX() + " leftArmX=" + current.leftArmX()
                    + " rightArmY=" + current.rightArmY() + " leftArmY=" + current.leftArmY()
                    + " rightArmZ=" + current.rightArmZ() + " leftArmZ=" + current.leftArmZ()
                    + " bodyX=" + current.bodyX() + " bodyY=" + current.bodyY() + " bodyZ=" + current.bodyZ()
                    + " rightLegX=" + current.rightLegX() + " leftLegX=" + current.leftLegX()
                    + " rightLegZ=" + current.rightLegZ() + " leftLegZ=" + current.leftLegZ());
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

        // Distinct body language for each family; these values are consumed by the
        // PlayerModel.setupAnim hook below, not by AvatarRenderState yaw/pitch fields.
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
            case "fire_propulsion" -> {
                bodyTurn = 5.0F * pulse;
                bodyLean = -28.0F * pulse;
                gait = 0.42F;
                crouch = false;
                flyingPose = t > 0.10F && t < 0.90F;
                mainPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
                offPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
            }
            case "air_dash" -> {
                bodyTurn = 34.0F * (float) Math.sin(t * Math.PI * 2.0F) * pulse;
                bodyLean = 4.0F * charge - 12.0F * release;
                gait = 0.32F;
                crouch = false;
                swimmingPose = true;
                mainPose = HumanoidModel.ArmPose.THROW_TRIDENT;
                offPose = HumanoidModel.ArmPose.BLOCK;
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

        // Store articulated ModelPart rotations for the tail of PlayerModel.setupAnim.
        MODEL_POSES.put(state.id, createModelPose(abilityId, t, bodyTurn, bodyLean));
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

    private static ModelPose createModelPose(String abilityId, float t, float bodyTurn, float bodyLean) {
        float charge = 1.0F - smooth(t / 0.38F);
        float release = smooth((t - 0.28F) / 0.18F) * (1.0F - smooth((t - 0.66F) / 0.34F));
        float pulse = (float) Math.sin(Math.PI * clamp(t));
        float sweep = (float) Math.sin(t * Math.PI * 2.0F);
        float impact = (float) Math.sin(t * Math.PI * 4.0F);

        float roll = 0.0F;
        float armPitch = 0.0F;
        float armYaw = 0.0F;
        float armRoll = 0.0F;
        float armSwing = 0.0F;
        float legDrive = 8.0F * pulse;
        float legSpread = 5.0F;

        switch (abilityId) {
            case "flame_projectile" -> {
                roll = -7.0F * release;
                armPitch = 10.0F * release; armYaw = -6.0F * release;
                armRoll = 5.0F * release; armSwing = 18.0F * release;
                legDrive = 11.0F * release; legSpread = 6.0F;
            }
            case "flame_burst" -> {
                roll = 12.0F * pulse;
                armPitch = -7.0F * release; armYaw = 9.0F * pulse;
                armRoll = 13.0F * pulse;
                legDrive = 18.0F * pulse; legSpread = 12.0F;
            }
            case "fire_whip" -> {
                roll = 18.0F * sweep;
                armPitch = 9.0F * pulse; armYaw = 12.0F * pulse;
                armRoll = 22.0F * sweep; armSwing = 14.0F * sweep;
                legDrive = 13.0F * pulse; legSpread = 14.0F;
            }
            case "focused_explosion" -> {
                roll = -10.0F * release;
                armPitch = 12.0F * charge - 9.0F * release;
                armRoll = 8.0F * charge; armSwing = 7.0F * release;
                legDrive = 24.0F * charge - 10.0F * release; legSpread = 16.0F;
            }
            case "fire_propulsion" -> {
                roll = -14.0F * pulse;
                armPitch = -18.0F * pulse; armYaw = 8.0F * pulse;
                armRoll = 12.0F * pulse;
                legDrive = -12.0F * pulse; legSpread = 11.0F;
            }
            case "fire_dragon" -> {
                roll = 14.0F * pulse;
                armPitch = -12.0F * pulse; armYaw = 6.0F * pulse;
                armRoll = 14.0F * pulse; armSwing = 9.0F * pulse;
                legDrive = 14.0F * pulse; legSpread = 13.0F;
            }
            case "water_projectile" -> {
                roll = 5.0F * release;
                armPitch = 8.0F * release; armYaw = -9.0F * release;
                armRoll = 6.0F * release; armSwing = 14.0F * release;
                legDrive = 9.0F * release; legSpread = 7.0F;
            }
            case "water_whip" -> {
                roll = -20.0F * sweep;
                armPitch = 12.0F * pulse; armYaw = 11.0F * pulse;
                armRoll = -18.0F * sweep; armSwing = 8.0F * sweep;
                legDrive = 15.0F * pulse; legSpread = 14.0F;
            }
            case "ice_bind" -> {
                roll = 13.0F * release;
                armPitch = -12.0F * charge; armYaw = -8.0F * charge;
                armRoll = 8.0F * charge;
                legDrive = 22.0F * charge; legSpread = 10.0F;
            }
            case "ice_blades" -> {
                roll = 16.0F * sweep;
                armPitch = -8.0F * pulse; armYaw = -9.0F * pulse;
                armRoll = 18.0F * sweep; armSwing = 12.0F * sweep;
                legDrive = 18.0F * pulse; legSpread = 13.0F;
            }
            case "water_shield" -> {
                roll = 7.0F * pulse;
                armPitch = -7.0F * pulse; armYaw = 12.0F * pulse;
                armRoll = 10.0F * pulse;
                legDrive = 8.0F * pulse; legSpread = 9.0F;
            }
            case "great_wave" -> {
                roll = -13.0F * release;
                armPitch = -15.0F * charge + 12.0F * release;
                armYaw = 9.0F * charge; armRoll = -8.0F * release;
                armSwing = 6.0F * release;
                legDrive = 28.0F * charge - 15.0F * release; legSpread = 17.0F;
            }
            case "rock_throw" -> {
                roll = 11.0F * release;
                armPitch = 12.0F * release; armYaw = -11.0F * release;
                armRoll = 12.0F * release; armSwing = 18.0F * release;
                legDrive = 20.0F * charge - 8.0F * release; legSpread = 12.0F;
            }
            case "stone_wall" -> {
                armPitch = -10.0F * charge; armRoll = 7.0F * charge;
                legDrive = 24.0F * charge - 7.0F * release; legSpread = 14.0F;
            }
            case "rock_cage" -> {
                roll = 8.0F * pulse;
                armPitch = -6.0F * pulse; armYaw = 8.0F * pulse;
                armRoll = -8.0F * pulse;
                legDrive = 11.0F * pulse; legSpread = 11.0F;
            }
            case "seismic_wave" -> {
                roll = -8.0F * release;
                armPitch = -14.0F * charge + 11.0F * release;
                armYaw = -7.0F * charge; armRoll = 10.0F * release;
                armSwing = 6.0F * release;
                legDrive = 27.0F * charge - 16.0F * release; legSpread = 16.0F;
            }
            case "stone_armor" -> {
                roll = 6.0F * pulse;
                armPitch = -4.0F * pulse; armYaw = 4.0F * pulse;
                armRoll = 6.0F * pulse;
                legDrive = 6.0F * pulse; legSpread = 7.0F;
            }
            case "earthquake" -> {
                roll = 10.0F * impact;
                armPitch = -20.0F * charge + 16.0F * release;
                armYaw = 4.0F * impact; armRoll = 14.0F * impact;
                armSwing = 8.0F * impact;
                legDrive = 30.0F * charge - 18.0F * release; legSpread = 17.0F;
            }
            case "air_blast" -> {
                roll = -6.0F * release;
                armPitch = 9.0F * release; armYaw = 7.0F * release;
                armRoll = -6.0F * release; armSwing = 14.0F * release;
                legDrive = 11.0F * release; legSpread = 6.0F;
            }
            case "air_push" -> {
                roll = -9.0F * release;
                armPitch = -9.0F * release; armYaw = -11.0F * release;
                armRoll = 8.0F * release;
                legDrive = 20.0F * release; legSpread = 10.0F;
            }
            case "air_shield" -> {
                roll = -9.0F * pulse;
                armPitch = -8.0F * pulse; armYaw = 15.0F * pulse;
                armRoll = -11.0F * pulse;
                legDrive = 9.0F * pulse; legSpread = 12.0F;
            }
            case "air_jump" -> {
                armPitch = -18.0F * pulse; armYaw = 8.0F * pulse;
                armRoll = 6.0F * pulse;
                legDrive = -20.0F * pulse; legSpread = 14.0F;
            }
            case "air_dash" -> {
                roll = 12.0F * sweep;
                armPitch = 10.0F * pulse; armYaw = 10.0F * pulse;
                armRoll = 18.0F * sweep; armSwing = 18.0F * sweep;
                legDrive = 12.0F * pulse; legSpread = 11.0F;
            }
            case "cyclone" -> {
                roll = 18.0F * sweep;
                armPitch = -6.0F * pulse; armYaw = -8.0F * pulse;
                armRoll = 24.0F * sweep; armSwing = 12.0F * sweep;
                legDrive = 16.0F * pulse; legSpread = 15.0F;
            }
            default -> {
                roll = 8.0F * pulse;
                armPitch = 8.0F * pulse;
                legDrive = 12.0F * pulse;
                legSpread = 8.0F;
            }
        }

        return new ModelPose(bodyLean, bodyTurn, roll, armPitch, armYaw, armRoll,
                armSwing, legDrive, legSpread);
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