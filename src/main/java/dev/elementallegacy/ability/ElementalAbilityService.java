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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ElementalAbilityService {
    private static final Map<UUID, Map<String, Integer>> COOLDOWNS = new HashMap<>();

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

        ServerLevel level = player.serverLevel();
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

        ParticleOptions particle = particleFor(ability.element());
        Vec3 particlePos = origin.add(look.scale(Math.min(ability.range(), 3.0D)));
        level.sendParticles(particle, particlePos.x, particlePos.y, particlePos.z, 24, 0.7D, 0.5D, 0.7D, 0.08D);
        if (hits > 0) {
            data.addXp(player.getUUID(), ability.xpReward() * hits);
            return ability.displayName() + " executada! Alvos atingidos: " + hits + ". +" + (ability.xpReward() * hits) + " XP.";
        }
        return ability.displayName() + " executada, mas nenhum inimigo foi atingido. Energia consumida; nenhum XP concedido.";
    }

    private static ParticleOptions particleFor(Element element) {
        return switch (element) {
            case TIDE -> ParticleTypes.SPLASH;
            case STONE -> ParticleTypes.CRIT;
            case EMBER -> ParticleTypes.FLAME;
            case GALE -> ParticleTypes.CLOUD;
        };
    }

    public static void clearPlayer(UUID playerId) {
        COOLDOWNS.remove(playerId);
    }
}
