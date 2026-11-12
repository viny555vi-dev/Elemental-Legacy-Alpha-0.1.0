package dev.elementallegacy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.elementallegacy.ElementalLegacy;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Procedural, mod-owned 3D ability geometry for Minecraft 26.3.
 *
 * The effect is submitted through NeoForge's SubmitCustomGeometryEvent and the
 * SubmitNodeCollector pipeline. It deliberately does not mutate vanilla particles
 * or use the player's held-item pose as a substitute for a spell model.
 */
@EventBusSubscriber(modid = ElementalLegacy.MOD_ID, value = Dist.CLIENT)
public final class TechniqueVisualRenderer {
    private static final int DURATION_TICKS = 48;
    private static final Identifier FIRE_TEXTURE = Identifier.fromNamespaceAndPath(ElementalLegacy.MOD_ID, "textures/vfx/fire.png");
    private static final Identifier WATER_TEXTURE = Identifier.fromNamespaceAndPath(ElementalLegacy.MOD_ID, "textures/vfx/water.png");
    private static final Identifier ICE_TEXTURE = Identifier.fromNamespaceAndPath(ElementalLegacy.MOD_ID, "textures/vfx/ice.png");
    private static final Identifier STONE_TEXTURE = Identifier.fromNamespaceAndPath(ElementalLegacy.MOD_ID, "textures/vfx/stone.png");
    private static final Identifier AIR_TEXTURE = Identifier.fromNamespaceAndPath(ElementalLegacy.MOD_ID, "textures/vfx/air.png");

    private static RenderType materialType(String abilityId) {
        if (abilityId.startsWith("flame_") || abilityId.startsWith("fire_") || abilityId.equals("focused_explosion")) {
            return RenderTypes.entityTranslucent(FIRE_TEXTURE);
        }
        if (abilityId.startsWith("ice_")) return RenderTypes.entityTranslucent(ICE_TEXTURE);
        if (abilityId.startsWith("water_") || abilityId.equals("great_wave")) return RenderTypes.entityTranslucent(WATER_TEXTURE);
        if (abilityId.startsWith("stone_") || abilityId.startsWith("rock_")
                || abilityId.equals("seismic_wave") || abilityId.equals("earthquake")) {
            return RenderTypes.entityCutout(STONE_TEXTURE);
        }
        return RenderTypes.entityTranslucent(AIR_TEXTURE);
    }
    private static int durationTicksFor(String abilityId) {
        return switch (abilityId) {
            case "stone_wall" -> 180;
            case "ice_bind" -> 140;
            case "rock_cage" -> 120;
            case "stone_armor" -> 220;
            case "water_shield", "air_shield" -> 120;
            case "earthquake" -> 96;
            case "seismic_wave" -> 72;
            default -> DURATION_TICKS;
        };
    }
    private static final Map<Integer, ActiveEffect> ACTIVE = new HashMap<>();
    private static final Set<String> SMOKE_MESHES_SUBMITTED = new HashSet<>();
    private static final String[] SMOKE_ABILITIES = {
            "flame_projectile", "flame_burst", "fire_whip", "focused_explosion", "fire_propulsion", "fire_dragon",
            "water_projectile", "water_whip", "ice_bind", "ice_blades", "water_shield", "great_wave",
            "rock_throw", "stone_wall", "rock_cage", "seismic_wave", "stone_armor", "earthquake",
            "air_blast", "air_push", "air_shield", "air_jump", "air_dash", "cyclone"
    };

    private record ActiveEffect(String abilityId, Vec3 origin, Vec3 forward, Vec3 right, long startedAt, int durationTicks) {}
    private static boolean smokeStarted;
    private static boolean smokeOriginalHudHidden;
    private static int smokeNextTick;
    private static int smokeIndex;
    private static String pendingSmokeAbility;
    private static boolean smokeFinishedLogged;
    private static boolean submitLogged;

    private TechniqueVisualRenderer() {}

    public static void start(int entityId, String abilityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity entity = mc.level.getEntity(entityId);
        if (entity == null) return;

        Vec3 forward = entity.getLookAngle().normalize();
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        if (right.lengthSqr() < 0.0001) right = new Vec3(1.0, 0.0, 0.0);
        else right = right.normalize();
        Vec3 origin = entity.position().add(0.0, Math.max(0.8, entity.getBbHeight() * 0.62), 0.0);
        ACTIVE.put(entityId, new ActiveEffect(abilityId, origin, forward, right, mc.level.getGameTime(), durationTicksFor(abilityId)));
    }

    @SubscribeEvent
    public static void submit(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ACTIVE.isEmpty()) return;
        if (Boolean.getBoolean("elemental.vfx.smoke") && !submitLogged) {
            submitLogged = true;
            System.out.println("[ELEMENTAL_VFX_SMOKE] SubmitCustomGeometryEvent is rendering custom meshes.");
        }

        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        long gameTime = event.getLevelRenderState().gameTime;
        float partial = event.getLevelRenderState().worldPartialTicks;
        Iterator<Map.Entry<Integer, ActiveEffect>> iterator = ACTIVE.entrySet().iterator();

        while (iterator.hasNext()) {
            ActiveEffect effect = iterator.next().getValue();
            float progress = (float) ((gameTime + partial - effect.startedAt()) / effect.durationTicks());
            if (progress >= 1.0F) {
                iterator.remove();
                continue;
            }
            if (progress < 0.0F) continue;

            PoseStack poseStack = event.getPoseStack();
            poseStack.pushPose();
            poseStack.translate(
                    effect.origin().x - camera.x,
                    effect.origin().y - camera.y,
                    effect.origin().z - camera.z
            );
            ActiveEffect current = effect;
            float time = clamp(progress);
            if (Boolean.getBoolean("elemental.vfx.smoke") && SMOKE_MESHES_SUBMITTED.add(effect.abilityId())) {
                System.out.println("[ELEMENTAL_VFX_SMOKE] MESH_SUBMIT " + effect.abilityId());
            }
            event.getSubmitNodeCollector().submitCustomGeometry(
                    poseStack,
                    materialType(current.abilityId()),
                    (pose, buffer) -> render(current, time, pose, buffer)
            );
            poseStack.popPose();
        }
    }

    /**
     * CI-only in-game smoke run: plays every technique in sequence after a client
     * joins a world. This exercises the same renderer used by real payloads.
     */
    @SubscribeEvent
    public static void smokeTest(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("elemental.vfx.smoke")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            smokeStarted = false;
            return;
        }
        if (!smokeStarted) {
            smokeStarted = true;
            smokeOriginalHudHidden = mc.gui.hud.isHidden();
            if (!smokeOriginalHudHidden) mc.gui.hud.toggle();
            mc.gui.toastManager().clear();
            System.out.println("[ELEMENTAL_VFX_SMOKE] HUD_HIDDEN " + mc.gui.hud.isHidden());
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            SMOKE_MESHES_SUBMITTED.clear();
            PlayerAbilityAnimator.resetSmokeEvidence();
            smokeIndex = 0;
            pendingSmokeAbility = null;
            smokeFinishedLogged = false;
            submitLogged = false;
            smokeNextTick = mc.player.tickCount + 80;
        }
        if (smokeIndex >= SMOKE_ABILITIES.length) {
            if (!smokeFinishedLogged) {
                smokeFinishedLogged = true;
                if (mc.gui.hud.isHidden() != smokeOriginalHudHidden) mc.gui.hud.toggle();
                System.out.println("[ELEMENTAL_VFX_SMOKE] HUD_RESTORED " + mc.gui.hud.isHidden());
                System.out.println("[ELEMENTAL_VFX_SMOKE] COMPLETED: all 24 techniques rendered.");
            }
            return;
        }
        if (mc.player.tickCount < smokeNextTick) return;

        if (pendingSmokeAbility == null) {
            pendingSmokeAbility = SMOKE_ABILITIES[smokeIndex];
            CameraType cameraType = smokeCameraFor(pendingSmokeAbility);
            if (mc.options.getCameraType() != cameraType) {
                mc.options.setCameraType(cameraType);
            }
            System.out.println("[ELEMENTAL_VFX_SMOKE] CAMERA " + cameraType.name()
                    + " for " + pendingSmokeAbility);
            smokeNextTick = mc.player.tickCount + 4;
            return;
        }
        if (mc.player.tickCount < smokeNextTick) return;

        String ability = pendingSmokeAbility;
        pendingSmokeAbility = null;
        smokeIndex++;
        start(mc.player.getId(), ability);
        PlayerAbilityAnimator.start(mc.player.getId(), ability);
        System.out.println("[ELEMENTAL_VFX_SMOKE] " + ability + " (" + smokeIndex + "/24)");
        smokeNextTick = mc.player.tickCount + durationTicksFor(ability) + 5;
    }

    private static CameraType smokeCameraFor(String abilityId) {
        // Keep the caster visible while effects travel forward into the scene.
        return CameraType.THIRD_PERSON_BACK;
    }

    private static void render(ActiveEffect fx, float t, PoseStack.Pose pose, VertexConsumer out) {
        String id = fx.abilityId();
        switch (id) {
            // EMBER — distinct, connected flame meshes rather than a trail of vanilla particles.
            case "flame_projectile" -> projectile(fx, t, pose, out, 0xFFFF4B1E, 0xFFFFC14B, 0.34);
            case "flame_burst" -> coneBurst(fx, t, pose, out, 0xFFFF5A20, 0xFFFFD16A);
            case "fire_whip" -> whip(fx, t, pose, out, 0xFFFF5A22, 0xFFFFD36B, 2.9);
            case "focused_explosion" -> explosion(fx, t, pose, out, 0xFFFF4B1E, 0xFFFFD06A);
            case "fire_propulsion" -> firePropulsion(fx, t, pose, out, 0xFFFF501F, 0xFFFFC94A);
            case "fire_dragon" -> dragon(fx, t, pose, out);

            // TIDE — a continuous moving water sheet, ribbons and ice geometry.
            case "water_projectile" -> projectile(fx, t, pose, out, 0xFF168DE0, 0xFFB9F5FF, 0.42);
            case "water_whip" -> whip(fx, t, pose, out, 0xFF168DD8, 0xFFB8F5FF, 2.5);
            case "ice_bind" -> iceCage(fx, t, pose, out);
            case "ice_blades" -> iceBlades(fx, t, pose, out);
            case "water_shield" -> shield(fx, t, pose, out, 0xFF168FD8, 0xFFB8F4FF);
            case "great_wave" -> greatWave(fx, t, pose, out);

            // STONE — animated meshes rise from the floor and build defensive forms.
            case "rock_throw" -> rockProjectiles(fx, t, pose, out);
            case "stone_wall" -> stoneWall(fx, t, pose, out);
            case "rock_cage" -> rockCage(fx, t, pose, out);
            case "seismic_wave" -> seismic(fx, t, pose, out, false);
            case "stone_armor" -> stoneArmor(fx, t, pose, out);
            case "earthquake" -> seismic(fx, t, pose, out, true);

            // GALE — pressure surfaces, rotating helixes and displacement trails.
            case "air_blast" -> projectile(fx, t, pose, out, 0xFF55CFB8, 0xFFE2FFFF, 0.30);
            case "air_push" -> airPush(fx, t, pose, out);
            case "air_shield" -> airShield(fx, t, pose, out);
            case "air_jump" -> airJump(fx, t, pose, out);
            case "air_dash" -> airDash(fx, t, pose, out, 0xFF52CDB8, 0xFFE2FFFF);
            case "cyclone" -> cyclone(fx, t, pose, out);
            default -> projectile(fx, t, pose, out, 0xFFFFFFFF, 0xFFB8E8FF, 0.3);
        }
    }

    private static Vec3 point(ActiveEffect fx, double side, double up, double forward) {
        return fx.right().scale(side).add(0.0, up, 0.0).add(fx.forward().scale(forward));
    }

    private static void projectile(ActiveEffect fx, float t, PoseStack.Pose pose, VertexConsumer out,
                                   int outer, int core, double radius) {
        // Launch from just beyond the casting hand and keep the core outside the
        // avatar's silhouette in third-person view, rather than drawing it directly
        // behind the player's torso along the camera axis.
        float travel = ease(clamp((t - 0.04F) / 0.72F));
        double z = 0.85 + travel * 3.25;
        double side = 0.58 + Math.sin(t * Math.PI * 5.0) * 0.08;
        double height = 0.24 + Math.sin(t * Math.PI * 6.0) * 0.10;
        List<Vec3> trail = List.of(
                point(fx, side - 0.16, height - 0.08, 0.28),
                point(fx, side - 0.02, height + 0.12, z - 0.82),
                point(fx, side + 0.10, height + 0.02, z - 0.20)
        );
        ribbon(out, pose, trail, radius * 1.02, outer);
        ribbon(out, pose, trail, radius * 0.42, core);
        Vec3 center = point(fx, side, height, z);
        orb(out, pose, center, radius * 1.68, outer, 10, 18);
        orb(out, pose, center, radius * 0.94, core, 8, 16);
        ring(out, pose, fx, z - 0.08, radius * 2.18, core, 36, t * 2.8);
        ring(out, pose, fx, z - 0.10, radius * 2.55, outer, 32, -t * 2.1);
    }

    private static void coneBurst(ActiveEffect fx, float t, PoseStack.Pose pose, VertexConsumer out,
                                  int outer, int inner) {
        float extend = ease(clamp((t - 0.10F) / 0.48F));
        for (int lane = -5; lane <= 5; lane++) {
            double spread = lane * 0.52 * extend;
            double z = 0.82 + extend * 4.25;
            double lift = 0.26 + Math.abs(lane) * 0.055;
            List<Vec3> path = List.of(
                    point(fx, lane * 0.035, 0.20, 0.34),
                    point(fx, spread * 0.62, 0.42 + lift * 0.35, z * 0.54),
                    point(fx, spread * 1.18, lift, z)
            );
            ribbon(out, pose, path, 0.15 + (5 - Math.abs(lane)) * 0.022, outer);
            ribbon(out, pose, path, 0.055, inner);
        }
        ring(out, pose, fx, 0.82 + extend * 4.25, 1.65 * extend + 0.12, inner, 42, t);
    }

    private static void whip(ActiveEffect fx, float t, PoseStack.Pose pose, VertexConsumer out,
                             int outer, int inner, double reach) {
        float sweep = ease(clamp((t - 0.10F) / 0.62F));
        for (int strand = 0; strand < 3; strand++) {
            List<Vec3> path = new ArrayList<>();
            for (int i = 0; i <= 26; i++) {
                double u = i / 26.0;
                double angle = -1.55 + 3.1 * u * sweep;
                double side = Math.sin(angle) * reach * (0.74 + strand * 0.09);
                double depth = 0.65 + Math.cos(angle) * reach * 0.48;
                double height = 0.2 + Math.sin(u * Math.PI) * (0.85 + strand * 0.16);
                path.add(point(fx, side, height, depth));
            }
            ribbon(out, pose, path, strand == 0 ? 0.16 : 0.075, outer);
        }
        List<Vec3> core = new ArrayList<>();
        for (int i = 0; i <= 26; i++) {
            double u = i / 26.0;
            double angle = -1.55 + 3.1 * u * sweep;
            core.add(point(fx, Math.sin(angle) * reach * 0.76, 0.25 + Math.sin(u * Math.PI) * 0.86,
                    0.65 + Math.cos(angle) * reach * 0.48));
        }
        ribbon(out, pose, core, 0.035, inner);
    }

    private static void explosion(ActiveEffect fx, float t, PoseStack.Pose pose, VertexConsumer out,
                                  int outer, int inner) {
        float charge = 1.0F - ease(clamp(t / 0.38F));
        float release = ease(clamp((t - 0.30F) / 0.17F));
        double radius = 0.3 + release * 3.1;
        orb(out, pose, point(fx, 0, 0, 2.6), 0.2 + charge * 0.58, inner, 8, 14);
        ring(out, pose, fx, 2.4, radius, outer, 42, t * 3.0);
        ring(out, pose, fx, 2.4, radius * 0.74, inner, 42, -t * 2.0);
        ringAt(out, pose, fx, 0.1 + release * 0.85, radius * 0.9, outer, 48, false, t);
    }

    private static void firePropulsion(ActiveEffect fx, float t, PoseStack.Pose pose, VertexConsumer out,
                                       int outer, int inner) {
        float thrust = ease(clamp((t - 0.04F) / 0.18F))
                * (1.0F - ease(clamp((t - 0.76F) / 0.24F)));
        for (int jet = 0; jet < 5; jet++) {
            double phase = jet * Math.PI * 2.0 / 5.0;
            List<Vec3> plume = new ArrayList<>();
            for (int i = 0; i <= 22; i++) {
                double u = i / 22.0;
                double radius = 0.16 + u * 0.52;
                double flicker = Math.sin(u * Math.PI * 4.0 + t * 13.0 + phase) * 0.09;
                double side = Math.cos(phase) * radius + flicker;
                double up = -0.52 + Math.sin(phase) * radius * 0.65 + flicker * 0.55;
                double back = -u * 4.2 * thrust;
                plume.add(point(fx, side, up, back));
            }
            ribbon(out, pose, plume, jet == 0 ? 0.17 : 0.105, outer);
            if (jet % 2 == 0) ribbon(out, pose, plume, 0.035, inner);
        }

        for (int sideSign : new int[]{-1, 1}) {
            List<Vec3> footJet = new ArrayList<>();
            for (int i = 0; i <= 16; i++) {
                double u = i / 16.0;
                footJet.add(point(fx,
                        sideSign * (0.24 + u * 0.16),
                        -0.72 + Math.sin(u * Math.PI * 2.0 + t * 15.0) * 0.08,
                        0.05 - u * 3.7 * thrust));
            }
            ribbon(out, pose, footJet, 0.13, outer);
            ribbon(out, pose, footJet, 0.045, inner);
        }
        ringAt(out, pose, fx, -0.82, 0.38 + thrust * 0.72, inner, 44, true, t * 2.6);
    }

    private static void airDash(ActiveEffect fx, float t, PoseStack.Pose pose, VertexConsumer out,
                                int outer, int inner) {
        float dash = ease(clamp((t - 0.05F) / 0.16F))
                * (1.0F - ease(clamp((t - 0.64F) / 0.30F)));
        double length = 0.35 + dash * 6.0;
        for (int lane = -3; lane <= 3; lane++) {
            List<Vec3> trail = new ArrayList<>();
            for (int i = 0; i <= 24; i++) {
                double u = i / 24.0;
                double wave = Math.sin(u * Math.PI * 2.0 + t * 8.0 + lane * 0.4);
                double side = lane * 0.15 + wave * 0.12;
                double up = -0.05 + Math.cos(u * Math.PI * 2.0 + t * 7.0) * 0.10;
                trail.add(point(fx, side, up, 0.45 - u * length));
            }
            ribbon(out, pose, trail, lane == 0 ? 0.14 : 0.075, outer);
            if (lane == 0) ribbon(out, pose, trail, 0.04, inner);
        }

        for (int sideSign : new int[]{-1, 1}) {
            List<Vec3> crescent = new ArrayList<>();
            for (int i = 0; i <= 20; i++) {
                double u = i / 20.0;
                crescent.add(point(fx,
                        sideSign * (1.8 - u * 1.45),
                        0.12 + Math.sin(u * Math.PI) * 0.78,
                        0.35 + u * 1.8 * dash));
            }
            ribbon(out, pose, crescent, 0.075, inner);
        }
        ringAt(out, pose, fx, -0.10, 0.42 + dash * 0.82, inner, 40, true, t * 7.0);
        ringAt(out, pose, fx, 0.55, 0.24 + dash * 0.56, outer, 36, true, -t * 5.0);
    }

    private static void dragon(ActiveEffect fx, float t, PoseStack.Pose pose, VertexConsumer out) {
        float reach = ease(clamp((t - 0.06F) / 0.55F));
        double length = 3.05 * reach;

        // Four overlapping, sinuous body ribbons create a readable serpentine silhouette.
        for (int strand = 0; strand < 4; strand++) {
            List<Vec3> path = new ArrayList<>();
            for (int i = 0; i <= 36; i++) {
                double u = i / 36.0;
                double wave = Math.sin(u * Math.PI * 5.2 - t * 5.0);
                double side = wave * (0.34 + strand * 0.07);
                double up = 0.72 + Math.sin(u * Math.PI * 2.1 - t * 3.8) * 0.22
                        + Math.sin(u * Math.PI) * 0.25;
                double z = 0.30 + u * length;
                path.add(point(fx, side, up, z));
            }
            int color = strand == 3 ? 0xFFFFE7A1 : (strand == 0 ? 0xFFFF6428 : 0xFFFF8A32);
            ribbon(out, pose, path, strand == 0 ? 0.25 : 0.13, color);
        }

        if (reach > 0.10F) {
            Vec3 head = point(fx, Math.sin(t * 5.5) * 0.18,
                    0.72 + Math.sin(t * 3.8) * 0.22, 0.30 + length);
            orb(out, pose, head, 0.50, 0xFFFF7A2D, 10, 18);
            orb(out, pose, head, 0.26, 0xFFFFE9A6, 8, 16);

            for (int sideSign : new int[]{-1, 1}) {
                Vec3 horn = head.add(fx.right().scale(sideSign * 0.22)).add(0.0, 0.18, 0.0);
                spike(out, pose, horn, 0.11, 0.25, 0xFFFFD16A);
                Vec3 eye = head.add(fx.right().scale(sideSign * 0.24)).add(0.0, 0.07, 0.12);
                orb(out, pose, eye, 0.045, 0xFFFFF5C7, 5, 8);

                List<Vec3> wing = List.of(
                        point(fx, sideSign * 0.14, 0.92, 0.82),
                        point(fx, sideSign * 0.58, 1.18, 1.22),
                        point(fx, sideSign * 0.92, 0.82, 1.62),
                        point(fx, sideSign * 0.30, 0.64, 1.34)
                );
                ribbon(out, pose, wing, 0.10, 0xFFFFB347);
            }
        }
    }

    private static void greatWave(ActiveEffect fx, float t, PoseStack.Pose pose, VertexConsumer out) {
        float travel = ease(clamp((t - 0.08F) / 0.68F));
        double width = 0.8 + 3.2 * travel;
        double depth = 1.3 + 5.2 * travel;
        int columns = 22;
        int rows = 7;
        for (int x = 0; x < columns; x++) {
            double u0 = x / (double) columns;
            double u1 = (x + 1) / (double) columns;
            for (int y = 0; y < rows; y++) {
                double v0 = y / (double) rows;
                double v1 = (y + 1) / (double) rows;
                Vec3 a = wavePoint(fx, u0, v0, width, depth, t);
                Vec3 b = wavePoint(fx, u1, v0, width, depth, t);
                Vec3 c = wavePoint(fx, u1, v1, width, depth, t);
                Vec3 d = wavePoint(fx, u0, v1, width, depth, t);
                quad(out, pose, a, b, c, d, y > 4 ? 0xFF8DEBFF : 0xFF168FCF);
            }
        }
        for (int strand = 0; strand < 4; strand++) {
            List<Vec3> crest = new ArrayList<>();
            for (int x = 0; x <= 24; x++) {
                double u = x / 24.0;
                crest.add(wavePoint(fx, u, 0.92 - strand * 0.08, width, depth, t).add(0, 0.08, 0));
            }
            ribbon(out, pose, crest, 0.045 + strand * 0.018, strand == 0 ? 0xFFD7FBFF : 0xFF5ED8F5);
        }
        ring(out, pose, fx, depth, width * 0.74, 0xFFBDF7FF, 48, t * 1.5);
    }

    private static Vec3 wavePoint(ActiveEffect fx, double u, double v, double width, double depth, float t) {
        double side = (u - 0.5) * width * 2.0;
        double curl = Math.sin(u * Math.PI * 2.6 + t * 7.0) * (0.22 + v * 0.18);
        double height = v * (1.1 + 1.9 * Math.sin(Math.PI * u)) + curl;
        return point(fx, side, height, depth + Math.sin(u * Math.PI * 2.0 + t * 5.0) * 0.18);
    }

    /**
     * A full glacial prison: twelve rising faceted pillars, three ice bands,
     * a cross-braced lattice and a frosted crown. The cage samples the terrain
     * below every pillar so it grows out of uneven ground instead of floating.
     */
    private static void iceCage(ActiveEffect fx, float t, PoseStack.Pose pose, VertexConsumer out) {
        float rise = ease(clamp(t / 0.17F));
        float release = 1.0F - ease(clamp((t - 0.86F) / 0.14F));
        float formed = rise * release;
        double radius = 1.52;
        double centerForward = 3.25;
        List<Vec3> crown = new ArrayList<>();
        List<Vec3> waist = new ArrayList<>();

        for (int i = 0; i < 12; i++) {
            double angle = Math.PI * 2.0 * i / 12.0;
            double side = Math.cos(angle) * radius;
            double forward = centerForward + Math.sin(angle) * radius;
            double base = terrainOffset(fx, side, forward);
            double height = (3.25 + (i % 3) * 0.20) * formed;
            Vec3 center = point(fx, side, base + height * 0.48, forward);

            orientedBox(out, pose, fx, center, 0.30, Math.max(0.04, height * 0.92), 0.34,
                    i % 3 == 0 ? 0xFFB8F5FF : 0xFF63BBDD);
            orientedBox(out, pose, fx,
                    point(fx, side, base + height * 0.53, forward + 0.015),
                    0.13, Math.max(0.03, height * 0.78), 0.035, 0xFFE5FCFF);
            if (height > 0.2) {
                spike(out, pose, point(fx, side, base + height - 0.32, forward),
                        0.23, 0.62 * formed, 0xFFD7F8FF);
                List<Vec3> vein = List.of(
                        point(fx, side - 0.045, base + 0.18, forward + 0.18),
                        point(fx, side + 0.025, base + height * 0.48, forward + 0.18),
                        point(fx, side - 0.025, base + height * 0.88, forward + 0.18));
                ribbon(out, pose, vein, 0.026, 0xFFF0FFFF);
            }
            crown.add(point(fx, side, base + height, forward));
            waist.add(point(fx, side, base + height * 0.52, forward));
        }

        for (int band = 0; band < 3; band++) {
            double lift = band == 0 ? 0.16 : (band == 1 ? 1.66 : 3.15);
            groundRing(fx, pose, out, centerForward, radius * (band == 1 ? 0.94 : 1.0),
                    lift * formed, band == 1 ? 0xFF8DE4FA : 0xFFE8FDFF, 48);
        }
        for (int i = 0; i < 12; i++) {
            int next = (i + 1) % 12;
            if ((i & 1) == 0) {
                ribbon(out, pose, List.of(waist.get(i), crown.get(next)), 0.055, 0xFF8DE4FA);
                ribbon(out, pose, List.of(crown.get(i), waist.get(next)), 0.035, 0xFFDDFBFF);
            }
        }
        groundRing(fx, pose, out, centerForward, radius + 0.42, 0.035,
                0xFFB8F4FF, 56);
        orb(out, pose, point(fx, 0, terrainOffset(fx, 0, centerForward) + 1.58 * formed,
                centerForward), 0.20 * formed, 0xFFB9F3FF, 8, 14);
    }

    private static void iceBlades(ActiveEffect fx, float t, PoseStack.Pose pose, VertexConsumer out) {
        float open = ease(clamp((t - 0.10F) / 0.48F));
        for (int i = -3; i <= 3; i++) {
            double side = i * 0.42 * open;
            double depth = 1.5 + Math.abs(i) * 0.28 + open * 3.4;
            Vec3 at = point(fx, side, 0.2 + Math.abs(i) * 0.08, depth);
            double width = 0.12 + 0.02 * (3 - Math.abs(i));
            spike(out, pose, at, width, 0.95, 0xFF8CE5F8);
            ribbon(out, pose, List.of(at.add(-width * 0.2, 0.65, 0), at.add(0, 0.95, 0), at.add(width * 0.2, 0.65, 0)), 0.035, 0xFFE5FCFF);
        }
    }

    private static void shield(ActiveEffect fx, float t, PoseStack.Pose pose, VertexConsumer out,
                               int outer, int inner) {
        float pulse = 0.9F + (float) Math.sin(t * Math.PI * 5.0) * 0.08F;
        for (int y = 0; y < 5; y++) {
            double h = -0.75 + y * 0.42;
            ringAt(out, pose, fx, h, 1.05 * pulse, y % 2 == 0 ? outer : inner, 40, true, t + y * 0.12);
        }
        for (int i = 0; i < 6; i++) {
            List<Vec3> spiral = new ArrayList<>();
            for (int n = 0; n <= 18; n++) {
                double u = n / 18.0;
                double a = u * Math.PI * 2.0 + i * Math.PI / 3.0 + t * 3.0;
                spiral.add(point(fx, Math.cos(a) * 1.08, -0.85 + u * 1.9, Math.sin(a) * 1.08));
            }
            ribbon(out, pose, spiral, 0.045, inner);
        }
    }

    private static void rockProjectiles(ActiveEffect fx, float t, PoseStack.Pose pose, VertexConsumer out) {
        float travel = ease(clamp((t - 0.04F) / 0.62F));
        for (int i = 0; i < 3; i++) {
            double z = 1.00 + travel * 3.00 + i * 0.24;
            double side = (i - 1) * 0.92 + Math.sin(t * 8.0 + i) * 0.12;
            double height = 0.42 + Math.sin(t * 11.0 + i) * 0.12;
            Vec3 rock = point(fx, side, height, z);
            List<Vec3> trail = List.of(
                    point(fx, side * 0.78, height * 0.72, z - 0.72),
                    point(fx, side, height + 0.10, z - 0.28),
                    rock
            );
            ribbon(out, pose, trail, 0.17, 0xFFB79A72);
            spike(out, pose, rock, 0.48, 0.98, 0xFF8E765B);
            orb(out, pose, rock.add(0, -0.08, 0), 0.22, 0xFFD9BD91, 7, 10);
        }
    }

    /**
     * A broad rampart grown from the actual block surface: layered stone courses,
     * a raised foundation, buttresses, irregular merlons and visible fracture seams.
     */
    /**
     * A monumental masonry rampart assembled from staggered stone courses.
     * Every block samples the local terrain height, so the foundation follows
     * slopes instead of hovering above the world. Alternating joints, buttresses
     * and crenellations give the wall a recognisable fortress silhouette.
     */
    private static void stoneWall(ActiveEffect fx, float t, PoseStack.Pose pose, VertexConsumer out) {
        float build = ease(clamp(t / 0.20F));
        float retract = 1.0F - ease(clamp((t - 0.91F) / 0.09F));
        float formed = build * retract;
        final double forward = 3.8;
        final double courseHeight = 0.69;
        List<Vec3> crownLine = new ArrayList<>();
        List<Vec3> foundation = new ArrayList<>();

        for (int row = 0; row < 6; row++) {
            double stagger = (row & 1) == 0 ? 0.0 : 0.31;
            for (int column = -7; column <= 7; column++) {
                double side = column * 0.62 + stagger;
                if (Math.abs(side) > 4.45) continue;

                double ground = terrainOffset(fx, side, forward);
                double variation = Math.sin(column * 1.31 + row * 0.77) * 0.035;
                double width = (0.58 + variation) * formed;
                double height = (0.65 + Math.cos(column * 0.83 + row) * 0.025) * formed;
                double depth = 0.78 * formed;
                double centerY = ground + (row + 0.5) * courseHeight * formed;
                int shade = Math.floorMod(column + row * 2, 4);
                int tone = switch (shade) {
                    case 0 -> 0xFFE0E3E7;
                    case 1 -> 0xFFC4CAD1;
                    case 2 -> 0xFFADB5BE;
                    default -> 0xFFD2D7DD;
                };

                // Each course is offset half a block, breaking up the old vertical-log appearance.
                orientedBox(out, pose, fx, point(fx, side, centerY, forward),
                        width, height, depth, tone);

                // Slightly raised face-stone gives the rampart a layered, chipped masonry surface.
                if ((column + row) % 3 == 0) {
                    orientedBox(out, pose, fx,
                            point(fx, side - 0.015, centerY + 0.035 * formed, forward + 0.405 * formed),
                            width * 0.82, height * 0.72, 0.045 * formed, 0xFFE9ECEF);
                }

                // Taller merlons on alternating top blocks form a strong castle-wall silhouette.
                if (row == 5 && (column & 1) == 0) {
                    orientedBox(out, pose, fx,
                            point(fx, side, ground + 4.43 * formed, forward),
                            0.43 * formed, 0.48 * formed, 0.84 * formed, 0xFFBFC6CE);
                }
                if (row == 0) {
                    foundation.add(point(fx, side, ground + 0.06 * formed, forward + 0.43 * formed));
                }
                if (row == 5) crownLine.add(point(fx, side, ground + 4.14 * formed, forward));
            }
        }

        // Broad corner buttresses lock the wall visually into the terrain.
        for (int sideSign : new int[]{-1, 1}) {
            double side = sideSign * 3.95;
            double ground = terrainOffset(fx, side, forward);
            for (int course = 0; course < 4; course++) {
                orientedBox(out, pose, fx,
                        point(fx, side, ground + (course + 0.5) * 0.91 * formed, forward - 0.08 * formed),
                        0.88 * formed, 0.86 * formed, 1.02 * formed,
                        (course & 1) == 0 ? 0xFFB9C1C9 : 0xFFD4D9DE);
            }
        }

        // Fine recessed mortar joints use a cool mineral shadow instead of the old brown tint.
        for (int row = 1; row < 6; row++) {
            List<Vec3> seam = new ArrayList<>();
            for (int x = -14; x <= 14; x++) {
                double side = x * 0.31;
                double ground = terrainOffset(fx, side, forward);
                seam.add(point(fx, side, ground + row * courseHeight * formed, forward + 0.405 * formed));
            }
            ribbon(out, pose, seam, 0.018 * formed, 0xFF626A73);
        }

        ribbon(out, pose, crownLine, 0.065 * formed, 0xFFE9ECEF);
        ribbon(out, pose, foundation, 0.085 * formed, 0xFF777F88);
        groundRing(fx, pose, out, forward, 4.35, 0.045, 0xFFB9C1C9, 56);
    }

    private static void rockCage(ActiveEffect fx, float t, PoseStack.Pose pose, VertexConsumer out) {
        float rise = ease(clamp((t - 0.05F) / 0.46F));
        double radius = 1.62;
        double centerForward = 3.35;
        List<Vec3> crown = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            double a = Math.PI * 2.0 * i / 10.0;
            double side = Math.cos(a) * radius;
            double forward = centerForward + Math.sin(a) * radius;
            Vec3 base = point(fx, side, 0, forward);
            spike(out, pose, base.add(0, 1.75 * rise, 0), 0.38, 2.05 * rise, 0xFF887052);
            if ((i & 1) == 0) {
                box(out, pose, base.add(0, 1.30 * rise, 0), 0.34, 0.30, 0.34, 0xFFB89A70);
            }
            crown.add(point(fx, side, 2.05 * rise, forward));
        }
        for (int i = 0; i < crown.size(); i++) {
            ribbon(out, pose, List.of(crown.get(i), crown.get((i + 1) % crown.size())),
                    0.075, 0xFFD5B88B);
        }
        groundRing(fx, pose, out, centerForward, radius + 0.18, 0.04, 0xFFB99A70, 56);
    }

    /**
     * The ground is the main character of this technique: terrain-following cracks,
     * lifted stone plates, debris spires and expanding fault rings make the impact
     * read as a real earthquake rather than a row of floating spikes.
     */
    private static void seismic(ActiveEffect fx, float t, PoseStack.Pose pose, VertexConsumer out, boolean wide) {
        float wave = ease(clamp(t / 0.28F));
        float decay = 1.0F - ease(clamp((t - 0.82F) / 0.18F));
        double reach = (wide ? 8.0 : 5.6) * wave;
        double centerForward = 2.6;
        int rows = wide ? 5 : 3;
        int columns = wide ? 9 : 7;

        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < columns; col++) {
                double side = (col - (columns - 1) * 0.5) * (wide ? 1.18 : 0.92);
                double forward = centerForward + row * (wide ? 1.35 : 1.15) + 0.25 * Math.sin(col * 1.4 + row);
                double ground = terrainOffset(fx, side, forward);
                double phase = t * 17.0 + row * 1.9 + col * 1.23;
                double lift = (0.08 + Math.max(0.0, Math.sin(phase)) * (wide ? 0.72 : 0.46)) * wave * decay;
                double tilt = 0.08 * Math.sin(phase * 0.65);
                int rockColor = ((row + col) & 1) == 0 ? 0xFFBFC6CD : 0xFF89939D;
                orientedBox(out, pose, fx, point(fx, side, ground + 0.10 + lift, forward),
                        wide ? 0.92 : 0.72, 0.20 + lift * 0.42, wide ? 1.10 : 0.92, rockColor);
                if ((row + col) % 4 == 0) {
                    spike(out, pose, point(fx, side + tilt, ground + 0.18 + lift, forward),
                            wide ? 0.30 : 0.22, (wide ? 0.95 : 0.65) * wave * decay, 0xFFD1D7DD);
                }
            }
        }

        int fissures = wide ? 13 : 8;
        for (int crack = 0; crack < fissures; crack++) {
            double angle = Math.PI * 2.0 * crack / fissures + 0.11 * Math.sin(crack * 2.1);
            List<Vec3> line = new ArrayList<>();
            List<Vec3> inner = new ArrayList<>();
            for (int n = 0; n <= 18; n++) {
                double u = n / 18.0;
                double distance = 0.6 + u * reach;
                double jagged = Math.sin(n * 1.83 + crack * 2.7) * (0.12 + u * 0.20);
                double side = Math.cos(angle) * distance + Math.cos(angle + 1.2) * jagged;
                double forward = centerForward + Math.sin(angle) * distance + Math.sin(angle + 1.2) * jagged;
                Vec3 crackPoint = groundPoint(fx, side, 0.035, forward);
                line.add(crackPoint);
                inner.add(crackPoint.add(0, 0.012, 0));
            }
            ribbon(out, pose, line, wide ? 0.095 : 0.065, 0xFF3D454E);
            ribbon(out, pose, inner, wide ? 0.027 : 0.020, 0xFFE0E5EA);
        }

        groundRing(fx, pose, out, centerForward, 2.2 + reach * 0.32, 0.055,
                0xFFD7DDE3, 64);
        if (wide) {
            groundRing(fx, pose, out, centerForward, 4.4 + reach * 0.40, 0.035,
                    0xFF737D87, 72);
            groundRing(fx, pose, out, centerForward, 6.2 + reach * 0.18, 0.025,
                    0xFFBFC7CF, 72);
        }
    }

    /**
     * Defensive stone shell fitted around the player: front/back torso plates,
     * articulated shoulder and arm guards, and segmented leg greaves.
     */
    /**
     * Segmented stone armor fitted to the vanilla player proportions. Narrow plates
     * follow the torso, shoulders, forearms and greaves without enclosing the avatar
     * in a single oversized cuboid. The shell remains active for the defensive duration.
     */
    private static void stoneArmor(ActiveEffect fx, float t, PoseStack.Pose pose, VertexConsumer out) {
        float form = ease(clamp(t / 0.13F));
        float sustain = 1.0F - ease(clamp((t - 0.91F) / 0.09F));
        float armor = form * sustain;
        int dark = 0xFF555D66;
        int mid = 0xFF8D969F;
        int light = 0xFFC1C8CF;
        int edge = 0xFFE7EBEF;

        // Thin torso shell with separate chest/back plates and a waist guard.
        orientedBox(out, pose, fx, point(fx, 0, -0.01, 0.00),
                0.58 * armor, 0.70 * armor, 0.31 * armor, mid);
        orientedBox(out, pose, fx, point(fx, 0, 0.00, 0.176),
                0.50 * armor, 0.53 * armor, 0.045 * armor, light);
        orientedBox(out, pose, fx, point(fx, 0, 0.00, -0.176),
                0.50 * armor, 0.53 * armor, 0.045 * armor, dark);
        orientedBox(out, pose, fx, point(fx, 0, -0.34, 0.00),
                0.60 * armor, 0.14 * armor, 0.34 * armor, dark);
        orientedBox(out, pose, fx, point(fx, 0, 0.34, 0.00),
                0.60 * armor, 0.10 * armor, 0.34 * armor, edge);

        for (int sideSign : new int[]{-1, 1}) {
            double side = sideSign;
            // Shoulder plates sit over the model's shoulder pivot.
            orientedBox(out, pose, fx, point(fx, side * 0.33, 0.27, 0.00),
                    0.29 * armor, 0.22 * armor, 0.34 * armor, light);
            // Two articulated arm segments, leaving a visible elbow break.
            orientedBox(out, pose, fx, point(fx, side * 0.38, -0.01, 0.00),
                    0.21 * armor, 0.35 * armor, 0.25 * armor, mid);
            orientedBox(out, pose, fx, point(fx, side * 0.38, -0.32, 0.00),
                    0.22 * armor, 0.28 * armor, 0.26 * armor, light);

            // Separate thigh and shin plates stop at the avatar's feet.
            orientedBox(out, pose, fx, point(fx, side * 0.16, -0.58, 0.00),
                    0.23 * armor, 0.31 * armor, 0.25 * armor, mid);
            orientedBox(out, pose, fx, point(fx, side * 0.16, -0.89, 0.00),
                    0.22 * armor, 0.29 * armor, 0.24 * armor, light);
            orientedBox(out, pose, fx, point(fx, side * 0.16, -1.08, 0.035),
                    0.24 * armor, 0.12 * armor, 0.29 * armor, dark);
        }

        // A raised chest crest and fine mineral seam create the defensive focal point.
        orientedBox(out, pose, fx, point(fx, 0, 0.40, 0.00),
                0.38 * armor, 0.10 * armor, 0.30 * armor, edge);
        List<Vec3> chestSigil = List.of(
                point(fx, -0.17 * armor, 0.20 * armor, 0.204 * armor),
                point(fx, 0, -0.02 * armor, 0.207 * armor),
                point(fx, 0.17 * armor, 0.20 * armor, 0.204 * armor));
        ribbon(out, pose, chestSigil, 0.022 * armor, edge);
        ringAt(out, pose, fx, -0.30, 0.62 * armor, 0xFFD5DCE2, 40, true, t * 0.16);
    }

    

    private static void airPush(ActiveEffect fx, float t, PoseStack.Pose pose, VertexConsumer out) {
        float expand = ease(clamp((t - 0.08F) / 0.56F));
        for (int i = 0; i < 4; i++) {
            double radius = 0.45 + expand * (1.1 + i * 0.62);
            ring(out, pose, fx, 1.0 + i * 0.12, radius, 0xFF79E9D8, 56, t * (i % 2 == 0 ? 2 : -2));
        }
        for (int i = -2; i <= 2; i++) {
            List<Vec3> gust = List.of(point(fx, i * 0.18, 0.1, 0.4),
                    point(fx, i * 0.48, 0.38, 2.0 + expand * 3.4),
                    point(fx, i * 0.8, 0.16, 4.0 + expand * 2.2));
            ribbon(out, pose, gust, 0.055, 0xFFD1FFF7);
        }
    }

    private static void airShield(ActiveEffect fx, float t, PoseStack.Pose pose, VertexConsumer out) {
        for (int strand = 0; strand < 6; strand++) {
            List<Vec3> helix = new ArrayList<>();
            for (int i = 0; i <= 26; i++) {
                double u = i / 26.0;
                double a = u * Math.PI * 2.0 + strand * Math.PI / 3.0 + t * 4.0;
                helix.add(point(fx, Math.cos(a) * 1.0, -0.95 + u * 1.95, Math.sin(a) * 1.0));
            }
            ribbon(out, pose, helix, 0.05, 0xFF79E9D8);
        }
        ringAt(out, pose, fx, -0.88, 1.05, 0xFFC9FFF5, 48, true, t);
        ringAt(out, pose, fx, 1.0, 0.8, 0xFFC9FFF5, 48, true, -t);
    }

    private static void airJump(ActiveEffect fx, float t, PoseStack.Pose pose, VertexConsumer out) {
        float lift = ease(clamp((t - 0.06F) / 0.45F));
        for (int i = 0; i < 5; i++) {
            double y = -0.8 + i * 0.42 + lift * 1.4;
            ringAt(out, pose, fx, y, 0.48 + i * 0.16, 0xFF8BEEDF, 40, true, t * 2 + i);
        }
        for (int i = 0; i < 3; i++) {
            List<Vec3> column = new ArrayList<>();
            for (int n = 0; n <= 14; n++) {
                double u = n / 14.0;
                double a = u * Math.PI * 2.0 + i * Math.PI * 2.0 / 3.0 + t * 5.0;
                column.add(point(fx, Math.cos(a) * (0.25 + u * 0.55), -0.7 + u * (2.1 + lift), Math.sin(a) * 0.25));
            }
            ribbon(out, pose, column, 0.05, 0xFFD8FFF7);
        }
    }

    private static void cyclone(ActiveEffect fx, float t, PoseStack.Pose pose, VertexConsumer out) {
        float rise = ease(clamp((t - 0.06F) / 0.58F));
        for (int strand = 0; strand < 5; strand++) {
            List<Vec3> spiral = new ArrayList<>();
            for (int i = 0; i <= 58; i++) {
                double u = i / 58.0;
                double y = -0.85 + u * 4.8 * rise;
                double radius = 0.22 + u * 1.25;
                double a = u * Math.PI * 6.0 + t * 6.0 + strand * Math.PI * 2.0 / 5.0;
                spiral.add(point(fx, Math.cos(a) * radius, y, 2.8 + Math.sin(a) * radius));
            }
            ribbon(out, pose, spiral, strand == 0 ? 0.10 : 0.06, 0xFF62D9C8);
        }
        for (int i = 0; i < 5; i++) {
            double u = i / 4.0;
            ring(out, pose, fx, 2.8 + u * 0.2, 0.3 + u * 1.2, 0xFFCEFFF7, 48, -t * 3.0 + i * 0.4);
        }
    }

    private static void ring(VertexConsumer out, PoseStack.Pose pose, ActiveEffect fx,
                             double depth, double radius, int color, int segments, double rotation) {
        List<Vec3> path = new ArrayList<>();
        for (int i = 0; i <= segments; i++) {
            double a = Math.PI * 2.0 * i / segments + rotation;
            path.add(point(fx, Math.cos(a) * radius, Math.sin(a) * radius, depth));
        }
        ribbon(out, pose, path, 0.035, color);
    }

    private static void ringAt(VertexConsumer out, PoseStack.Pose pose, ActiveEffect fx,
                               double height, double radius, int color, int segments,
                               boolean vertical, double rotation) {
        List<Vec3> path = new ArrayList<>();
        for (int i = 0; i <= segments; i++) {
            double a = Math.PI * 2.0 * i / segments + rotation;
            if (vertical) path.add(point(fx, Math.cos(a) * radius, height, Math.sin(a) * radius));
            else path.add(point(fx, Math.cos(a) * radius, height + Math.sin(a) * radius, 0.8));
        }
        ribbon(out, pose, path, 0.04, color);
    }

    private static void orb(VertexConsumer out, PoseStack.Pose pose, Vec3 center,
                            double radius, int color, int stacks, int slices) {
        for (int lat = 0; lat < stacks; lat++) {
            double p0 = -Math.PI / 2.0 + Math.PI * lat / stacks;
            double p1 = -Math.PI / 2.0 + Math.PI * (lat + 1) / stacks;
            for (int lon = 0; lon < slices; lon++) {
                double a0 = Math.PI * 2.0 * lon / slices;
                double a1 = Math.PI * 2.0 * (lon + 1) / slices;
                Vec3 a = center.add(Math.cos(p0) * Math.cos(a0) * radius, Math.sin(p0) * radius, Math.cos(p0) * Math.sin(a0) * radius);
                Vec3 b = center.add(Math.cos(p0) * Math.cos(a1) * radius, Math.sin(p0) * radius, Math.cos(p0) * Math.sin(a1) * radius);
                Vec3 c = center.add(Math.cos(p1) * Math.cos(a1) * radius, Math.sin(p1) * radius, Math.cos(p1) * Math.sin(a1) * radius);
                Vec3 d = center.add(Math.cos(p1) * Math.cos(a0) * radius, Math.sin(p1) * radius, Math.cos(p1) * Math.sin(a0) * radius);
                quad(out, pose, a, b, c, d, color);
            }
        }
    }

    /** Samples the top walkable surface at the given local effect coordinate. */
    private static double terrainOffset(ActiveEffect fx, double side, double forward) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return -1.0;
        double worldX = fx.origin().x + fx.right().x * side + fx.forward().x * forward;
        double worldZ = fx.origin().z + fx.right().z * side + fx.forward().z * forward;
        int blockX = (int) Math.floor(worldX);
        int blockZ = (int) Math.floor(worldZ);
        int surfaceY = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ);
        return surfaceY - fx.origin().y + 0.025;
    }

    private static Vec3 groundPoint(ActiveEffect fx, double side, double lift, double forward) {
        return point(fx, side, terrainOffset(fx, side, forward) + lift, forward);
    }

    private static void groundRing(ActiveEffect fx, PoseStack.Pose pose, VertexConsumer out,
                                   double centerForward, double radius, double lift,
                                   int color, int segments) {
        List<Vec3> path = new ArrayList<>();
        for (int i = 0; i <= segments; i++) {
            double angle = Math.PI * 2.0 * i / segments;
            double side = Math.cos(angle) * radius;
            double forward = centerForward + Math.sin(angle) * radius;
            path.add(groundPoint(fx, side, lift, forward));
        }
        ribbon(out, pose, path, 0.055, color);
    }

    /** A cuboid aligned to the caster's facing direction, not to world X/Z. */
    private static void orientedBox(VertexConsumer out, PoseStack.Pose pose, ActiveEffect fx,
                                    Vec3 center, double width, double height, double depth, int color) {
        double x = width * 0.5, y = height * 0.5, z = depth * 0.5;
        Vec3 side = fx.right().scale(x);
        Vec3 up = new Vec3(0.0, y, 0.0);
        Vec3 forward = fx.forward().scale(z);
        Vec3 a = center.subtract(side).subtract(up).subtract(forward);
        Vec3 b = center.add(side).subtract(up).subtract(forward);
        Vec3 c = center.add(side).add(up).subtract(forward);
        Vec3 d = center.subtract(side).add(up).subtract(forward);
        Vec3 e = center.subtract(side).subtract(up).add(forward);
        Vec3 f = center.add(side).subtract(up).add(forward);
        Vec3 g = center.add(side).add(up).add(forward);
        Vec3 h = center.subtract(side).add(up).add(forward);
        quad(out, pose, a, b, c, d, color);
        quad(out, pose, e, h, g, f, color);
        quad(out, pose, a, e, f, b, color);
        quad(out, pose, d, c, g, h, color);
        quad(out, pose, a, d, h, e, color);
        quad(out, pose, b, f, g, c, color);
    }

    private static void spike(VertexConsumer out, PoseStack.Pose pose, Vec3 base,
                              double width, double height, int color) {
        Vec3 a = base.add(-width, 0, -width);
        Vec3 b = base.add(width, 0, -width);
        Vec3 c = base.add(width, 0, width);
        Vec3 d = base.add(-width, 0, width);
        Vec3 tip = base.add(0, height, 0);
        quad(out, pose, a, b, tip, tip, color);
        quad(out, pose, b, c, tip, tip, color);
        quad(out, pose, c, d, tip, tip, color);
        quad(out, pose, d, a, tip, tip, color);
        quad(out, pose, a, d, c, b, 0xFF6F5C47);
    }

    private static void box(VertexConsumer out, PoseStack.Pose pose, Vec3 center,
                            double width, double height, double depth, int color) {
        double x = width * 0.5, y = height * 0.5, z = depth * 0.5;
        Vec3 a = center.add(-x, -y, -z), b = center.add(x, -y, -z);
        Vec3 c = center.add(x, y, -z), d = center.add(-x, y, -z);
        Vec3 e = center.add(-x, -y, z), f = center.add(x, -y, z);
        Vec3 g = center.add(x, y, z), h = center.add(-x, y, z);
        quad(out, pose, a, b, c, d, color);
        quad(out, pose, e, h, g, f, color);
        quad(out, pose, a, e, f, b, color);
        quad(out, pose, d, c, g, h, color);
        quad(out, pose, a, d, h, e, color);
        quad(out, pose, b, f, g, c, color);
    }

    private static void ribbon(VertexConsumer out, PoseStack.Pose pose, List<Vec3> path,
                               double width, int color) {
        if (path.size() < 2) return;
        for (int i = 0; i < path.size() - 1; i++) {
            Vec3 a = path.get(i);
            Vec3 b = path.get(i + 1);
            Vec3 tangent = b.subtract(a);
            Vec3 side = tangent.cross(new Vec3(0, 1, 0));
            if (side.lengthSqr() < 0.0001) side = tangent.cross(new Vec3(1, 0, 0));
            if (side.lengthSqr() < 0.0001) continue;
            side = side.normalize().scale(width);
            quad(out, pose, a.subtract(side), b.subtract(side), b.add(side), a.add(side), color);
        }
    }

    private static void quad(VertexConsumer out, PoseStack.Pose pose, Vec3 a, Vec3 b,
                             Vec3 c, Vec3 d, int color) {
        Vec3 normal = b.subtract(a).cross(c.subtract(a));
        if (normal.lengthSqr() < 0.000001) normal = new Vec3(0, 1, 0);
        else normal = normal.normalize();
        vertex(out, pose, a, color, 0.0F, 1.0F, normal);
        vertex(out, pose, b, color, 1.0F, 1.0F, normal);
        vertex(out, pose, c, color, 1.0F, 0.0F, normal);
        vertex(out, pose, d, color, 0.0F, 0.0F, normal);
        Vec3 reverse = normal.scale(-1.0);
        vertex(out, pose, d, color, 0.0F, 0.0F, reverse);
        vertex(out, pose, c, color, 1.0F, 0.0F, reverse);
        vertex(out, pose, b, color, 1.0F, 1.0F, reverse);
        vertex(out, pose, a, color, 0.0F, 1.0F, reverse);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, Vec3 p, int color,
                               float u, float v, Vec3 normal) {
        out.addVertex(pose, (float) p.x, (float) p.y, (float) p.z)
                .setColor(color)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0x00F000F0)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static float ease(float value) {
        float x = clamp(value);
        return x * x * (3.0F - 2.0F * x);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
