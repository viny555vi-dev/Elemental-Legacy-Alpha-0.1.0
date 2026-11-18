package dev.elementallegacy.ability;

import dev.elementallegacy.data.ElementalWorldData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative defensive state for Stone Armor.
 *
 * The visual assembly and recovery span 230 ticks. The actual protective window
 * starts after the 50-tick assembly and lasts at most 160 ticks. Damage absorption
 * is capped, so the armor cannot provide unlimited mitigation.
 */
public final class StoneArmorService {
    private static final int ASSEMBLY_TICKS = 50;
    private static final int ACTIVE_TICKS = 160;
    private static final int RECOVERY_TICKS = 20;
    private static final float DAMAGE_REDUCTION = 0.35F;
    private static final float KNOCKBACK_REDUCTION = 0.50F;
    private static final float MAX_ABSORBED_DAMAGE = 24.0F;
    private static final float ENERGY_PER_SECOND = 3.0F;
    private static final int ENERGY_INTERVAL_TICKS = 20;
    private static final Identifier MOVEMENT_MODIFIER_ID =
            Identifier.fromNamespaceAndPath("elemental_legacy", "stone_armor_weight");

    private static final Map<UUID, ArmorState> ACTIVE = new HashMap<>();

    private static final class ArmorState {
        private final int activeFromTick;
        private final int activeUntilTick;
        private final int finishTick;
        private int nextEnergyTick;
        private float remainingAbsorption = MAX_ABSORBED_DAMAGE;
        private boolean movementPenaltyApplied;
        private boolean broken;

        private ArmorState(int startTick) {
            this.activeFromTick = startTick + ASSEMBLY_TICKS;
            this.activeUntilTick = activeFromTick + ACTIVE_TICKS;
            this.finishTick = activeUntilTick + RECOVERY_TICKS;
            this.nextEnergyTick = activeFromTick + ENERGY_INTERVAL_TICKS;
        }
    }

    private StoneArmorService() {}

    public static void activate(ServerPlayer player) {
        finish(player);
        ACTIVE.put(player.getUUID(), new ArmorState(player.tickCount));
        if (Boolean.getBoolean("elemental.vfx.smoke")) {
            System.out.println("[ELEMENTAL_STONE_ARMOR] ACTIVATED player=" + player.getUUID()
                    + " assembleTicks=" + ASSEMBLY_TICKS
                    + " activeTicks=" + ACTIVE_TICKS
                    + " absorbCap=" + MAX_ABSORBED_DAMAGE);
        }
    }

    /** Called from the server player tick; manages active duration, energy and movement penalty. */
    public static void tick(ServerPlayer player) {
        ArmorState state = ACTIVE.get(player.getUUID());
        if (state == null) return;

        int now = player.tickCount;
        if (!player.isAlive() || now >= state.finishTick) {
            finish(player);
            return;
        }

        if (state.broken || now < state.activeFromTick || now >= state.activeUntilTick) {
            removeMovementPenalty(player, state);
            return;
        }

        applyMovementPenalty(player, state);
        if (now >= state.nextEnergyTick) {
            state.nextEnergyTick = now + ENERGY_INTERVAL_TICKS;
            if (!ElementalWorldData.get(player.level().getServer())
                    .spendEnergy(player.getUUID(), ENERGY_PER_SECOND)) {
                breakArmor(player, state, "energy_depleted");
            }
        }
    }

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ArmorState state = ACTIVE.get(player.getUUID());
        if (!isProtecting(player, state)) return;

        float incoming = event.getAmount();
        if (incoming <= 0.0F) return;

        float prevented = Math.min(incoming * DAMAGE_REDUCTION, state.remainingAbsorption);
        if (prevented <= 0.0F) return;

        event.setAmount(Math.max(0.0F, incoming - prevented));
        state.remainingAbsorption -= prevented;
        if (Boolean.getBoolean("elemental.vfx.smoke")) {
            System.out.printf(java.util.Locale.ROOT,
                    "[ELEMENTAL_STONE_ARMOR] DAMAGE_REDUCED incoming=%.3f prevented=%.3f remaining=%.3f%n",
                    incoming, prevented, state.remainingAbsorption);
        }
        if (state.remainingAbsorption <= 0.0001F) {
            breakArmor(player, state, "absorption_depleted");
        }
    }

    public static void onKnockBack(LivingKnockBackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ArmorState state = ACTIVE.get(player.getUUID());
        if (!isProtecting(player, state)) return;
        event.setStrength(event.getStrength() * KNOCKBACK_REDUCTION);
    }

    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ArmorState state = ACTIVE.get(player.getUUID());
        if (!isProtecting(player, state)) return;

        // The armor's mass reduces vertical take-off without disabling the jump.
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, motion.y * 0.85D, motion.z);
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) finish(player);
    }

    private static boolean isProtecting(ServerPlayer player, ArmorState state) {
        return state != null && !state.broken
                && player.tickCount >= state.activeFromTick
                && player.tickCount < state.activeUntilTick
                && state.remainingAbsorption > 0.0001F;
    }

    private static void applyMovementPenalty(ServerPlayer player, ArmorState state) {
        if (state.movementPenaltyApplied) return;
        var attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null) return;
        attribute.addTransientModifier(new AttributeModifier(
                MOVEMENT_MODIFIER_ID, -0.10D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        state.movementPenaltyApplied = true;
    }

    private static void removeMovementPenalty(ServerPlayer player, ArmorState state) {
        if (!state.movementPenaltyApplied) return;
        var attribute = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute != null) attribute.removeModifier(MOVEMENT_MODIFIER_ID);
        state.movementPenaltyApplied = false;
    }

    private static void breakArmor(ServerPlayer player, ArmorState state, String reason) {
        if (state.broken) return;
        state.broken = true;
        removeMovementPenalty(player, state);
        if (Boolean.getBoolean("elemental.vfx.smoke")) {
            System.out.println("[ELEMENTAL_STONE_ARMOR] BROKEN reason=" + reason
                    + " player=" + player.getUUID());
        }
    }

    private static void finish(ServerPlayer player) {
        ArmorState previous = ACTIVE.remove(player.getUUID());
        if (previous != null) {
            removeMovementPenalty(player, previous);
            if (Boolean.getBoolean("elemental.vfx.smoke")) {
                System.out.println("[ELEMENTAL_STONE_ARMOR] FINISHED player=" + player.getUUID());
            }
        }
    }
}
