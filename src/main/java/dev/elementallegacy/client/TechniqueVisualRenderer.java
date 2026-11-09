package dev.elementallegacy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.elementallegacy.ElementalLegacy;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
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
    private static final Map<Integer, ActiveEffect> ACTIVE = new HashMap<>();
    private static final Set<String> SMOKE_MESHES_SUBMITTED = new HashSet<>();
    private static final String[] SMOKE_ABILITIES = {
            "flame_projectile", "flame_burst", "fire_whip", "focused_explosion", "fire_propulsion", "fire_dragon",
            "water_projectile", "water_whip", "ice_bind", "ice_blades", "water_shield", "great_wave",
            "rock_throw", "stone_wall", "rock_cage", "seismic_wave", "stone_armor", "earthquake",
            "air_blast", "air_push", "air_shield", "air_jump", "air_dash", "cyclone"
    };

    private record ActiveEffect(String abilityId, Vec3 origin, Vec3 forward, Vec3 right, long startedAt) {}
    private static boolean smokeStarted;
    private static int smokeNextTick;
    private static int smokeIndex;
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
        ACTIVE.put(entityId, new ActiveEffect(abilityId, origin, forward, right, mc.level.getGameTime()));
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
            float progress = (float) ((gameTime + partial - effect.startedAt()) / DURATION_TICKS);
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
                    RenderTypes.debugQuads(),
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
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            SMOKE_MESHES_SUBMITTED.clear();
            PlayerAbilityAnimator.resetSmokeEvidence();
            smokeIndex = 0;
            smokeFinishedLogged = false;
            submitLogged = false;
            smokeNextTick = mc.player.tickCount + 30;
        }
        if (smokeIndex >= SMOKE_ABILITIES.length) {
            if (!smokeFinishedLogged) {
                smokeFinishedLogged = true;
                System.out.println("[ELEMENTAL_VFX_SMOKE] COMPLETED: all 24 techniques rendered.");
            }
            return;
        }
        if (mc.player.tickCount < smokeNextTick) return;

        String ability = SMOKE_ABILITIES[smokeIndex++];
        start(mc.player.getId(), ability);
        PlayerAbilityAnimator.start(mc.player.getId(), ability);
        System.out.println("[ELEMENTAL_VFX_SMOKE] " + ability + " (" + smokeIndex + "/24)");
        smokeNextTick = mc.player.tickCount + DURATION_TICKS + 5;
    }

    private static void render(ActiveEffect fx, float t, PoseStack.Pose pose, VertexConsumer out) {
        String id = fx.abilityId();
        switch (id) {
            // EMBER — distinct, connected flame meshes rather than a trail of vanilla particles.
            case "flame_projectile" -> projectile(fx, t, pose, out, 0xFFFF4B1E, 0xFFFFC14B, 0.34);
            case "flame_burst" -> coneBurst(fx, t, pose, out, 0xFFFF5A20, 0xFFFFD16A);
            case "fire_whip" -> whip(fx, t, pose, out, 0xFFFF5A22, 0xFFFFD36B, 2.9);
            case "focused_explosion" -> explosion(fx, t, pose, out, 0xFFFF4B1E, 0xFFFFD06A);
            case "fire_propulsion" -> propulsion(fx, t, pose, out, 0xFFFF501F, 0xFFFFC94A);
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
            case "air_dash" -> propulsion(fx, t, pose, out, 0xFF52CDB8, 0xFFE2FFFF);
            case "cyclone" -> cyclone(fx, t, pose, out);
            default -> projectile(fx, t, pose, out, 0xFFFFFFFF, 0xFFB8E8FF, 0.3);
        }
    }

    private static Vec3 point(ActiveEffect fx, double side, double up, double forward) {
        return fx.right().scale(side).add(0.0, up, 0.0).add(fx.forward().scale(forward));
    }

    private static void projectile(ActiveEffect fx, float t, PoseStack.Pose pose, VertexConsumer out,
                                   int outer, int core, double radius) {
        float travel = ease(clamp((t - 0.12F) / 0.58F));
        double z = 0.6 + travel * 7.2;
        double bob = Math.sin(t * Math.PI * 8.0) * 0.12;
        ribbon(out, pose, List.of(
                point(fx, 0, 0, 0.25),
                point(fx, 0.05, 0.12, z - 0.9),
                point(fx, -0.06, -0.06, z - 0.35)
        ), radius * 0.52, outer);
        orb(out, pose, point(fx, 0, bob, z), radius, outer, 8, 14);
        orb(out, pose, point(fx, 0, bob, z), radius * 0.48, core, 7, 12);
        ring(out, pose, fx, z - 0.1, radius * 1.35, core, 28, t * 2.0);
    }

    private static void coneBurst(ActiveEffect fx, float t, PoseStack.Pose pose, VertexConsumer out,
                                  int outer, int inner) {
        float extend = ease(clamp((t - 0.12F) / 0.5F));
        for (int lane = -5; lane <= 5; lane++) {
            double side = lane * 0.33 * extend;
            double z = 1.0 + extend * 4.6;
            List<Vec3> path = List.of(
                    point(fx, lane * 0.05, 0.05, 0.25),
                    point(fx, side * 0.55, 0.18 + Math.abs(lane) * 0.035, z * 0.55),
                    point(fx, side, 0.10, z)
            );
            ribbon(out, pose, path, 0.12 + (5 - Math.abs(lane)) * 0.018, outer);
            ribbon(out, pose, path, 0.045, inner);
        }
        ring(out, pose, fx, 1.0 + extend * 4.6, 1.4 * extend + 0.1, inner, 42, t);
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

    private static void propulsion(ActiveEffect fx, float t, PoseStack.Pose pose, VertexConsumer out,
                                   int outer, int inner) {
        float active = ease(clamp((t - 0.08F) / 0.22F)) * (1.0F - ease(clamp((t - 0.72F) / 0.28F)));
        for (int strand = 0; strand < 4; strand++) {
            List<Vec3> path = new ArrayList<>();
            for (int i = 0; i <= 14; i++) {
                double u = i / 14.0;
                double angle = u * Math.PI * 3.2 + t * 9.0 + strand * Math.PI / 2.0;
                double r = 0.34 + 0.16 * Math.sin(u * Math.PI);
                path.add(point(fx, Math.cos(angle) * r, Math.sin(angle) * r, -u * 2.25 * active));
            }
            ribbon(out, pose, path, 0.085, outer);
        }
        ringAt(out, pose, fx, 0.2, 0.45 + active * 0.7, inner, 40, false, t);
    }

    private static void dragon(ActiveEffect fx, float t, PoseStack.Pose pose, VertexConsumer out) {
        float reach = ease(clamp((t - 0.12F) / 0.56F));
        for (int strand = 0; strand < 3; strand++) {
            List<Vec3> path = new ArrayList<>();
            int count = Math.max(2, (int) (34 * Math.max(0.08, reach)));
            for (int i = 0; i <= count; i++) {
                double u = i / 34.0;
                double wave = Math.sin(u * Math.PI * 5.2 - t * 6.0);
                double side = wave * (0.56 + strand * 0.09);
                double up = 0.82 + Math.sin(u * Math.PI * 2.1 - t * 4.0) * 0.36 + Math.sin(u * Math.PI) * 0.34;
                double z = 0.35 + u * 8.0 * reach;
                path.add(point(fx, side, up, z));
            }
            ribbon(out, pose, path, strand == 0 ? 0.25 : 0.12, strand == 2 ? 0xFFFFE79A : 0xFFFF6428);
        }
        if (reach > 0.12F) {
            Vec3 head = point(fx, Math.sin(t * 6.0) * 0.56, 0.82 + Math.sin(t * 4.0) * 0.36, 0.35 + reach * 8.0);
            orb(out, pose, head, 0.42, 0xFFFF8B31, 8, 14);
            orb(out, pose, head, 0.18, 0xFFFFE9A6, 7, 12);
            spike(out, pose, head.add(fx.forward().scale(0.32)), 0.2, 0.35, 0xFFFFD16A);
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

    private static void iceCage(ActiveEffect fx, float t, PoseStack.Pose pose, VertexConsumer out) {
        float rise = ease(clamp((t - 0.08F) / 0.48F));
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2.0 * i / 8.0;
            double side = Math.cos(angle) * 0.8;
            double forward = 3.0 + Math.sin(angle) * 0.8;
            spike(out, pose, point(fx, side, 1.35 * rise, forward), 0.23, 1.35 * rise, 0xFF7DDCF4);
        }
        ring(out, pose, fx, 3.0, 0.9, 0xFFE0FAFF, 36, t * 1.4);
        orb(out, pose, point(fx, 0, 1.4 * rise, 3.0), 0.3, 0xFFA9EDFF, 7, 12);
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
        float travel = ease(clamp((t - 0.12F) / 0.58F));
        for (int i = 0; i < 3; i++) {
            double z = 0.8 + travel * 6.0 + i * 0.5;
            Vec3 rock = point(fx, (i - 1) * 0.42, 0.25 + Math.sin(t * 12 + i) * 0.12, z);
            spike(out, pose, rock, 0.34, 0.62, 0xFF8E765B);
            orb(out, pose, rock.add(0, -0.1, 0), 0.13, 0xFFD9BD91, 5, 8);
        }
    }

    private static void stoneWall(ActiveEffect fx, float t, PoseStack.Pose pose, VertexConsumer out) {
        float build = ease(clamp((t - 0.08F) / 0.66F));
        for (int x = -3; x <= 3; x++) {
            double rowHeight = 0.42 + ((x + 3) % 2) * 0.12;
            double h = Math.max(0, build * 2.65 - Math.abs(x) * 0.07);
            box(out, pose, point(fx, x * 0.42, h * 0.5, 3.0), 0.39, Math.max(0.04, h), 0.3,
                    (x % 2 == 0) ? 0xFF9B805E : 0xFF705A43);
            if (build > 0.25F) {
                spike(out, pose, point(fx, x * 0.42, h, 3.0), 0.13, Math.max(0.1, rowHeight * 0.6), 0xFFC4A77B);
            }
        }
    }

    private static void rockCage(ActiveEffect fx, float t, PoseStack.Pose pose, VertexConsumer out) {
        float rise = ease(clamp((t - 0.05F) / 0.52F));
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2.0 * i / 8.0;
            Vec3 base = point(fx, Math.cos(a) * 0.95, 0, 3.2 + Math.sin(a) * 0.95);
            spike(out, pose, base.add(0, 1.25 * rise, 0), 0.31, 1.5 * rise, 0xFF887052);
            if (i % 2 == 0) box(out, pose, base.add(0, 1.35 * rise, 0), 0.28, 0.26, 0.28, 0xFFB89A70);
        }
        ring(out, pose, fx, 3.2, 1.05, 0xFFD5B88B, 42, t);
    }

    private static void seismic(ActiveEffect fx, float t, PoseStack.Pose pose, VertexConsumer out, boolean wide) {
        float wave = ease(clamp((t - 0.08F) / 0.62F));
        int count = wide ? 11 : 7;
        for (int i = 0; i < count; i++) {
            double side = (i - (count - 1) / 2.0) * (wide ? 0.48 : 0.42);
            double z = 1.2 + (i % 4) * 1.25 + wave * 1.7;
            double lift = Math.max(0.0, Math.min(1.0, wave * 1.45 - (i % 4) * 0.10));
            if (lift > 0.02) spike(out, pose, point(fx, side, 0.62 * lift, z), wide ? 0.32 : 0.25,
                    (wide ? 1.2 : 0.9) * lift, 0xFF9D8260);
        }
        for (int crack = 0; crack < (wide ? 7 : 4); crack++) {
            List<Vec3> line = new ArrayList<>();
            double sideBase = (crack - (wide ? 3 : 1.5)) * 0.42;
            for (int n = 0; n <= 9; n++) {
                double u = n / 9.0;
                double jitter = Math.sin(n * 2.3 + crack) * 0.14;
                line.add(point(fx, sideBase + jitter, -0.9, 0.6 + u * (4.8 + wave * 2.3)));
            }
            ribbon(out, pose, line, wide ? 0.055 : 0.04, 0xFF4A372A);
        }
        ring(out, pose, fx, 2.5 + wave * 4.0, (wide ? 3.4 : 2.0) * wave + 0.15, 0xFFD3B68D, 52, t * 2.0);
    }

    private static void stoneArmor(ActiveEffect fx, float t, PoseStack.Pose pose, VertexConsumer out) {
        float form = ease(clamp((t - 0.05F) / 0.48F));
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2.0 * i / 8.0;
            Vec3 center = point(fx, Math.cos(a) * 0.48, -0.05 + Math.sin(a) * 0.65, Math.sin(a) * 0.42);
            box(out, pose, center, 0.26 * form + 0.04, 0.38 * form + 0.04, 0.18,
                    i % 2 == 0 ? 0xFF8D7659 : 0xFFC0A47A);
        }
        ringAt(out, pose, fx, -0.25, 0.72, 0xFFDCC39A, 40, true, t);
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
        vertex(out, pose, a, color);
        vertex(out, pose, b, color);
        vertex(out, pose, c, color);
        vertex(out, pose, d, color);
        // Reverse winding as well: several effects are viewed from both sides during a cast.
        vertex(out, pose, d, color);
        vertex(out, pose, c, color);
        vertex(out, pose, b, color);
        vertex(out, pose, a, color);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, Vec3 p, int color) {
        out.addVertex(pose, (float) p.x, (float) p.y, (float) p.z).setColor(color);
    }

    private static float ease(float value) {
        float x = clamp(value);
        return x * x * (3.0F - 2.0F * x);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
