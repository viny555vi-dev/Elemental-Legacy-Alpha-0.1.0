package dev.elementallegacy.ability;

import dev.elementallegacy.data.ElementalWorldData;
import dev.elementallegacy.data.PlayerProgress;
import dev.elementallegacy.element.Element;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ElementalAbilityService {
    private static final Map<UUID, Map<String, Integer>> COOLDOWNS = new HashMap<>();
    private static final Map<UUID, List<ScheduledCue>> ANIMATIONS = new HashMap<>();

    private record ScheduledCue(int tick, Runnable action) {}


    private ElementalAbilityService() {}

    public static String use(ServerPlayer player, ElementalAbility ability) {
        ElementalWorldData data = ElementalWorldData.get(player.level().getServer());
        PlayerProgress progress = data.get(player.getUUID());
        if (!progress.hasChosenElement()) return "Você precisa escolher um elemento primeiro: /elemental choose <tide|stone|ember|gale>.";
        Element element = Element.byId(progress.elementId()).orElse(null);
        if (element == null || ability.element() != element) return "Essa técnica não pertence ao seu elemento.";
        if (progress.energy() < ability.energyCost()) return "Energia insuficiente. Necessário: " + ability.energyCost() + ". Atual: " + Math.round(progress.energy()) + ".";
        int now = player.tickCount;
        int readyAt = COOLDOWNS.getOrDefault(player.getUUID(), Map.of()).getOrDefault(ability.id(), 0);
        if (now < readyAt) return "Técnica em recarga: " + String.format(java.util.Locale.ROOT, "%.1f", (readyAt - now) / 20.0) + "s.";

        data.spendEnergy(player.getUUID(), ability.energyCost());
        COOLDOWNS.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>())
                .put(ability.id(), now + ability.cooldownTicks());

        ServerLevel level = (ServerLevel) player.level();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 origin = player.getEyePosition();
        AABB searchBox = player.getBoundingBox().inflate(ability.range());
        int hits = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, searchBox,
                entity -> entity != player && entity.isAlive() && !entity.isSpectator())) {
            Vec3 offset = target.getBoundingBox().getCenter().subtract(origin);
            double distance = offset.length();
            if (distance > ability.range() || distance < 0.01D) continue;
            double alignment = look.dot(offset.normalize());
            if (alignment < 0.35D) continue;

            boolean damaged = target.hurtServer(level, player.damageSources().playerAttack(player), ability.damage());
            if (!damaged) continue;
            hits++;
            Vec3 push = look.scale(ability.element() == Element.GALE ? 1.35D : 0.65D);
            if (ability == ElementalAbility.CYCLONE) push = push.add(0.0D, 0.75D, 0.0D);
            target.push(push.x, push.y, push.z);
            if (ability.element() == Element.EMBER) target.igniteForSeconds(3.0F);
        }

        // Each technique has its own silhouette and particle choreography rather than
        // sharing one generic burst. Particles are server-emitted so nearby players see it.
        scheduleTechnique(player, level, ability, origin, look);
        if (hits > 0) {
            data.addXp(player.getUUID(), ability.xpReward() * hits);
            return ability.displayName() + " executada! Alvos atingidos: " + hits + ". +" + (ability.xpReward() * hits) + " XP.";
        }
        return ability.displayName() + " executada, mas nenhum inimigo foi atingido. Energia consumida; nenhum XP concedido.";
    }

    /**
     * Runs a short, server-authoritative visual timeline. The wind-up, active effect,
     * impact accent and recovery are emitted on separate ticks so the technique reads
     * as an animation instead of a single-frame particle dump.
     */
    private static void scheduleTechnique(ServerPlayer player, ServerLevel level,
                                           ElementalAbility ability, Vec3 origin, Vec3 look) {
        UUID id = player.getUUID();
        List<ScheduledCue> cues = new ArrayList<>();
        Vec3 right = look.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize();
        if (right.lengthSqr() < 0.001D) right = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 up = right.cross(look).normalize();
        Vec3 hands = origin.add(look.scale(0.35D)).add(0.0D, -0.35D, 0.0D);

        // Each element has its own anticipation language and silhouette.
        switch (ability) {
            case FLAME_BURST -> {
                cues.add(new ScheduledCue(0, () -> {
                    emit(level, ParticleTypes.FLAME, hands, 12, 0.18D, 0.18D, 0.18D, 0.025D);
                    emit(level, ParticleTypes.SMOKE, hands, 5, 0.12D, 0.12D, 0.12D, 0.01D);
                }));
                cues.add(new ScheduledCue(3, () -> {
                    for (int ring = 0; ring < 2; ring++) {
                        for (int i = 0; i < 12; i++) {
                            double a = i * Math.PI / 6.0D + ring * Math.PI;
                            Vec3 p = hands.add(Math.cos(a) * (0.22D + ring * 0.12D),
                                    Math.sin(a) * 0.18D, Math.sin(a) * (0.22D + ring * 0.12D));
                            emit(level, ParticleTypes.FLAME, p, 2, 0.025D, 0.025D, 0.025D, 0.01D);
                        }
                    }
                }));
                cues.add(new ScheduledCue(7, () -> renderTechnique(level, ability, origin, look)));
                cues.add(new ScheduledCue(13, () -> {
                    Vec3 impact = origin.add(look.scale(4.4D));
                    emit(level, ParticleTypes.FLAME, impact, 20, 0.5D, 0.35D, 0.5D, 0.08D);
                    emit(level, ParticleTypes.LARGE_SMOKE, impact, 8, 0.35D, 0.25D, 0.35D, 0.025D);
                }));
                cues.add(new ScheduledCue(20, () -> emit(level, ParticleTypes.SMOKE,
                        origin.add(look.scale(4.4D)), 7, 0.35D, 0.18D, 0.35D, 0.008D)));
            }
            case FIRE_WHIP -> {
                cues.add(new ScheduledCue(0, () -> emit(level, ParticleTypes.FLAME, hands, 10, 0.2D, 0.15D, 0.2D, 0.02D)));
                cues.add(new ScheduledCue(4, () -> {
                    for (int i = 0; i < 9; i++) {
                        Vec3 p = hands.add(right.scale((i - 4) * 0.12D)).add(up.scale(i * 0.07D));
                        emit(level, ParticleTypes.FLAME, p, 3, 0.035D, 0.035D, 0.035D, 0.015D);
                    }
                }));
                cues.add(new ScheduledCue(7, () -> renderTechnique(level, ability, origin, look)));
                cues.add(new ScheduledCue(13, () -> emit(level, ParticleTypes.FLAME,
                        origin.add(look.scale(2.2D)), 16, 0.7D, 0.2D, 0.7D, 0.07D)));
                cues.add(new ScheduledCue(18, () -> emit(level, ParticleTypes.SMOKE,
                        origin.add(look.scale(2.2D)), 8, 0.45D, 0.15D, 0.45D, 0.01D)));
            }
            case WATER_BURST -> {
                cues.add(new ScheduledCue(0, () -> emit(level, ParticleTypes.BUBBLE, hands, 12, 0.2D, 0.2D, 0.2D, 0.02D)));
                cues.add(new ScheduledCue(4, () -> {
                    for (int i = 0; i < 16; i++) {
                        double a = i * Math.PI / 8.0D;
                        Vec3 p = hands.add(Math.cos(a) * 0.38D, Math.sin(a) * 0.22D, Math.sin(a) * 0.38D);
                        emit(level, ParticleTypes.SPLASH, p, 2, 0.025D, 0.025D, 0.025D, 0.01D);
                    }
                }));
                cues.add(new ScheduledCue(7, () -> renderTechnique(level, ability, origin, look)));
                cues.add(new ScheduledCue(14, () -> emit(level, ParticleTypes.SPLASH,
                        origin.add(look.scale(4.5D)), 22, 0.6D, 0.35D, 0.6D, 0.08D)));
                cues.add(new ScheduledCue(19, () -> emit(level, ParticleTypes.BUBBLE,
                        origin.add(look.scale(4.5D)), 10, 0.35D, 0.2D, 0.35D, 0.015D)));
            }
            case ICE_BIND -> {
                cues.add(new ScheduledCue(0, () -> emit(level, ParticleTypes.SNOWFLAKE, hands, 10, 0.18D, 0.18D, 0.18D, 0.01D)));
                cues.add(new ScheduledCue(5, () -> {
                    Vec3 center = origin.add(look.scale(2.8D));
                    for (int i = 0; i < 12; i++) {
                        double a = i * Math.PI / 6.0D;
                        Vec3 p = center.add(Math.cos(a) * 0.55D, -0.45D + i * 0.07D, Math.sin(a) * 0.55D);
                        emit(level, ParticleTypes.SNOWFLAKE, p, 3, 0.025D, 0.05D, 0.025D, 0.01D);
                    }
                }));
                cues.add(new ScheduledCue(8, () -> renderTechnique(level, ability, origin, look)));
                cues.add(new ScheduledCue(15, () -> emit(level, ParticleTypes.END_ROD,
                        origin.add(look.scale(2.8D)), 12, 0.35D, 0.55D, 0.35D, 0.015D)));
                cues.add(new ScheduledCue(21, () -> emit(level, ParticleTypes.CLOUD,
                        origin.add(look.scale(2.8D)), 8, 0.3D, 0.2D, 0.3D, 0.01D)));
            }
            case ROCK_STRIKE -> {
                cues.add(new ScheduledCue(0, () -> emit(level, ParticleTypes.POOF,
                        origin.add(look.scale(0.5D)).add(0.0D, -0.8D, 0.0D), 12, 0.25D, 0.12D, 0.25D, 0.025D)));
                cues.add(new ScheduledCue(4, () -> {
                    Vec3 ground = origin.add(look.scale(1.0D)).add(0.0D, -0.85D, 0.0D);
                    emit(level, ParticleTypes.CRIT, ground, 10, 0.3D, 0.12D, 0.3D, 0.08D);
                    emit(level, ParticleTypes.POOF, ground, 8, 0.25D, 0.12D, 0.25D, 0.025D);
                }));
                cues.add(new ScheduledCue(7, () -> renderTechnique(level, ability, origin, look)));
                cues.add(new ScheduledCue(13, () -> emit(level, ParticleTypes.CRIT,
                        origin.add(look.scale(3.8D)), 18, 0.45D, 0.35D, 0.45D, 0.15D)));
                cues.add(new ScheduledCue(19, () -> emit(level, ParticleTypes.POOF,
                        origin.add(look.scale(3.8D)), 9, 0.3D, 0.15D, 0.3D, 0.025D)));
            }
            case SEISMIC_WAVE -> {
                cues.add(new ScheduledCue(0, () -> emit(level, ParticleTypes.POOF,
                        origin.add(0.0D, -0.8D, 0.0D), 10, 0.3D, 0.08D, 0.3D, 0.015D)));
                cues.add(new ScheduledCue(5, () -> {
                    Vec3 ground = origin.add(look.scale(1.2D)).add(0.0D, -0.85D, 0.0D);
                    for (int i = 0; i < 16; i++) {
                        double a = i * Math.PI / 8.0D;
                        emit(level, ParticleTypes.CRIT, ground.add(Math.cos(a) * 0.65D, 0.0D,
                                Math.sin(a) * 0.65D), 2, 0.04D, 0.05D, 0.04D, 0.05D);
                    }
                }));
                cues.add(new ScheduledCue(8, () -> renderTechnique(level, ability, origin, look)));
                cues.add(new ScheduledCue(15, () -> emit(level, ParticleTypes.POOF,
                        origin.add(look.scale(4.0D)).add(0.0D, -0.75D, 0.0D), 20, 0.8D, 0.12D, 0.8D, 0.08D)));
                cues.add(new ScheduledCue(22, () -> emit(level, ParticleTypes.POOF,
                        origin.add(look.scale(4.0D)).add(0.0D, -0.75D, 0.0D), 8, 0.5D, 0.08D, 0.5D, 0.015D)));
            }
            case AIR_BLAST -> {
                cues.add(new ScheduledCue(0, () -> emit(level, ParticleTypes.CLOUD, hands, 10, 0.18D, 0.12D, 0.18D, 0.015D)));
                cues.add(new ScheduledCue(4, () -> {
                    for (int i = -2; i <= 2; i++) {
                        Vec3 p = hands.add(right.scale(i * 0.13D));
                        emit(level, ParticleTypes.CLOUD, p, 4, 0.035D, 0.035D, 0.035D, 0.02D);
                    }
                }));
                cues.add(new ScheduledCue(7, () -> renderTechnique(level, ability, origin, look)));
                cues.add(new ScheduledCue(13, () -> emit(level, ParticleTypes.POOF,
                        origin.add(look.scale(4.2D)), 16, 0.55D, 0.35D, 0.55D, 0.1D)));
                cues.add(new ScheduledCue(19, () -> emit(level, ParticleTypes.CLOUD,
                        origin.add(look.scale(4.2D)), 10, 0.45D, 0.2D, 0.45D, 0.015D)));
            }
            case CYCLONE -> {
                cues.add(new ScheduledCue(0, () -> emit(level, ParticleTypes.CLOUD, hands, 12, 0.2D, 0.2D, 0.2D, 0.025D)));
                cues.add(new ScheduledCue(5, () -> {
                    Vec3 center = origin.add(look.scale(2.7D));
                    for (int i = 0; i < 18; i++) {
                        double a = i * Math.PI / 9.0D;
                        Vec3 p = center.add(Math.cos(a) * 0.65D, -0.5D + i * 0.055D, Math.sin(a) * 0.65D);
                        emit(level, ParticleTypes.CLOUD, p, 3, 0.035D, 0.05D, 0.035D, 0.025D);
                    }
                }));
                cues.add(new ScheduledCue(8, () -> renderTechnique(level, ability, origin, look)));
                cues.add(new ScheduledCue(16, () -> emit(level, ParticleTypes.CLOUD,
                        origin.add(look.scale(2.7D)).add(0.0D, 1.0D, 0.0D), 18, 0.5D, 0.8D, 0.5D, 0.07D)));
                cues.add(new ScheduledCue(24, () -> emit(level, ParticleTypes.POOF,
                        origin.add(look.scale(2.7D)), 12, 0.55D, 0.35D, 0.55D, 0.025D)));
            }
        }

        int startTick = player.tickCount;
        List<ScheduledCue> timed = new ArrayList<>();
        for (ScheduledCue cue : cues) timed.add(new ScheduledCue(startTick + cue.tick(), cue.action()));
        ANIMATIONS.put(id, timed);
    }

    /** Advance the caster's queued visual cues once per server player tick. */
    public static void tick(ServerPlayer player) {
        List<ScheduledCue> cues = ANIMATIONS.get(player.getUUID());
        if (cues == null) return;
        if (!player.isAlive()) {
            ANIMATIONS.remove(player.getUUID());
            return;
        }
        Iterator<ScheduledCue> iterator = cues.iterator();
        while (iterator.hasNext()) {
            ScheduledCue cue = iterator.next();
            if (player.tickCount >= cue.tick()) {
                cue.action().run();
                iterator.remove();
            }
        }
        if (cues.isEmpty()) ANIMATIONS.remove(player.getUUID());
    }

    private static void renderTechnique(ServerLevel level, ElementalAbility ability, Vec3 origin, Vec3 look) {
        Vec3 right = look.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize();
        if (right.lengthSqr() < 0.001D) right = new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 up = right.cross(look).normalize();
        Vec3 forward = look;

        switch (ability) {
            case WATER_BURST -> {
                // A widening, cresting wave with a foamy leading edge.
                for (int step = 1; step <= 9; step++) {
                    double distance = step * 0.48D;
                    double width = 0.18D + step * 0.105D;
                    Vec3 center = origin.add(forward.scale(distance));
                    for (int side = -3; side <= 3; side++) {
                        Vec3 point = center.add(right.scale(side * width / 3.0D))
                                .add(0.12D * Math.sin(step * 0.7D + side), 0.0D, 0.0D);
                        emit(level, ParticleTypes.SPLASH, point, 2, 0.12D, 0.16D, 0.12D, 0.08D);
                    }
                    emit(level, ParticleTypes.BUBBLE, center, 5, width, 0.18D, width, 0.025D);
                }
                emit(level, ParticleTypes.SPLASH, origin.add(forward.scale(4.5D)), 30, 0.8D, 0.55D, 0.8D, 0.16D);
            }
            case ICE_BIND -> {
                // A freezing seal: two rising spiral bands and a snowburst at the center.
                Vec3 center = origin.add(forward.scale(2.8D));
                for (int i = 0; i < 28; i++) {
                    double angle = i * Math.PI / 7.0D;
                    double y = (i / 28.0D) * 2.4D;
                    double radius = 0.48D + 0.12D * Math.sin(angle * 2.0D);
                    Vec3 point = center.add(Math.cos(angle) * radius, y - 0.7D, Math.sin(angle) * radius);
                    emit(level, ParticleTypes.SNOWFLAKE, point, 2, 0.04D, 0.08D, 0.04D, 0.015D);
                    if (i % 2 == 0) emit(level, ParticleTypes.END_ROD, point, 1, 0.02D, 0.05D, 0.02D, 0.01D);
                }
                emit(level, ParticleTypes.SNOWFLAKE, center, 36, 0.7D, 0.9D, 0.7D, 0.035D);
                emit(level, ParticleTypes.CLOUD, center, 18, 0.55D, 0.35D, 0.55D, 0.025D);
            }
            case ROCK_STRIKE -> {
                // A heavy forward punch: a compact stone trail and a sharp impact plume.
                for (int step = 1; step <= 6; step++) {
                    Vec3 point = origin.add(forward.scale(step * 0.58D)).add(0.0D, -0.18D, 0.0D);
                    emit(level, ParticleTypes.CRIT, point, 5, 0.22D, 0.18D, 0.22D, 0.12D);
                    if (step % 2 == 0) emit(level, ParticleTypes.POOF, point, 3, 0.16D, 0.12D, 0.16D, 0.035D);
                }
                Vec3 impact = origin.add(forward.scale(3.8D));
                emit(level, ParticleTypes.CRIT, impact, 28, 0.55D, 0.5D, 0.55D, 0.2D);
                emit(level, ParticleTypes.POOF, impact, 16, 0.4D, 0.3D, 0.4D, 0.09D);
            }
            case SEISMIC_WAVE -> {
                // Successive ground-hugging shockwave rings travel away from the bender.
                for (int ring = 1; ring <= 5; ring++) {
                    Vec3 center = origin.add(forward.scale(ring * 0.78D)).add(0.0D, -0.9D, 0.0D);
                    double radius = 0.35D + ring * 0.12D;
                    for (int i = 0; i < 16; i++) {
                        double angle = i * Math.PI / 8.0D;
                        Vec3 point = center.add(right.scale(Math.cos(angle) * radius))
                                .add(forward.scale(Math.sin(angle) * radius));
                        emit(level, ParticleTypes.CRIT, point, 2, 0.07D, 0.12D, 0.07D, 0.08D);
                    }
                }
                Vec3 impact = origin.add(forward.scale(4.0D)).add(0.0D, -0.75D, 0.0D);
                emit(level, ParticleTypes.POOF, impact, 24, 1.0D, 0.18D, 1.0D, 0.12D);
            }
            case FLAME_BURST -> {
                // A fast fire projectile with a bright core, smoke tail and explosive finish.
                for (int step = 1; step <= 9; step++) {
                    Vec3 point = origin.add(forward.scale(step * 0.48D));
                    emit(level, ParticleTypes.FLAME, point, 5, 0.16D, 0.16D, 0.16D, 0.025D);
                    emit(level, ParticleTypes.SMOKE, point.subtract(forward.scale(0.18D)), 2, 0.09D, 0.1D, 0.09D, 0.015D);
                }
                Vec3 impact = origin.add(forward.scale(4.4D));
                emit(level, ParticleTypes.FLAME, impact, 34, 0.65D, 0.55D, 0.65D, 0.11D);
                emit(level, ParticleTypes.LAVA, impact, 8, 0.35D, 0.35D, 0.35D, 0.08D);
                emit(level, ParticleTypes.LARGE_SMOKE, impact, 10, 0.45D, 0.4D, 0.45D, 0.035D);
            }
            case FIRE_WHIP -> {
                // A sweeping crescent of fire, wider than the straight fire blast.
                Vec3 center = origin.add(forward.scale(2.2D));
                for (int i = 0; i <= 24; i++) {
                    double angle = -Math.PI * 0.78D + i * (Math.PI * 1.56D / 24.0D);
                    Vec3 point = center.add(right.scale(Math.sin(angle) * 2.0D))
                            .add(up.scale(Math.cos(angle) * 1.25D));
                    emit(level, ParticleTypes.FLAME, point, 3, 0.07D, 0.07D, 0.07D, 0.025D);
                    if (i % 3 == 0) emit(level, ParticleTypes.SMOKE, point, 2, 0.08D, 0.08D, 0.08D, 0.01D);
                }
                emit(level, ParticleTypes.FLAME, center, 18, 0.65D, 0.45D, 0.65D, 0.07D);
            }
            case AIR_BLAST -> {
                // Compressed air lanes converge into a pressure burst.
                for (int lane = -2; lane <= 2; lane++) {
                    for (int step = 1; step <= 8; step++) {
                        Vec3 point = origin.add(forward.scale(step * 0.5D))
                                .add(right.scale(lane * (0.22D - step * 0.018D)));
                        emit(level, ParticleTypes.CLOUD, point, 2, 0.08D, 0.08D, 0.08D, 0.035D);
                    }
                }
                Vec3 impact = origin.add(forward.scale(4.2D));
                emit(level, ParticleTypes.SWEEP_ATTACK, impact, 2, 0.35D, 0.25D, 0.35D, 0.0D);
                emit(level, ParticleTypes.POOF, impact, 22, 0.65D, 0.45D, 0.65D, 0.12D);
            }
            case CYCLONE -> {
                // A rising corkscrew: layered air rings spiral upward into a small tornado.
                Vec3 center = origin.add(forward.scale(2.7D));
                for (int i = 0; i < 48; i++) {
                    double angle = i * Math.PI / 6.0D;
                    double height = (i / 48.0D) * 3.2D;
                    double radius = 0.82D * (1.0D - height / 4.2D);
                    Vec3 point = center.add(Math.cos(angle) * radius, height - 0.8D,
                            Math.sin(angle) * radius);
                    emit(level, ParticleTypes.CLOUD, point, 3, 0.09D, 0.12D, 0.09D, 0.035D);
                    if (i % 4 == 0) emit(level, ParticleTypes.POOF, point, 2, 0.12D, 0.1D, 0.12D, 0.025D);
                }
                emit(level, ParticleTypes.CLOUD, center.add(0.0D, 1.1D, 0.0D), 24, 0.55D, 1.1D, 0.55D, 0.08D);
            }
        }
    }

    private static void emit(ServerLevel level, ParticleOptions particle, Vec3 position,
                             int count, double spreadX, double spreadY, double spreadZ, double speed) {
        level.sendParticles(particle, position.x, position.y, position.z,
                count, spreadX, spreadY, spreadZ, speed);
    }

    public static void clearPlayer(UUID playerId) {
        COOLDOWNS.remove(playerId);
        ANIMATIONS.remove(playerId);
    }
}
