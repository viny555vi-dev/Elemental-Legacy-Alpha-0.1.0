package dev.elementallegacy.network;

import dev.elementallegacy.ability.ElementalAbility;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public record AbilityAnimationPayload(int entityId, String abilityId) implements CustomPacketPayload {
    public static final Type<AbilityAnimationPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("elemental_legacy", "ability_animation"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AbilityAnimationPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> {
                        buffer.writeVarInt(payload.entityId());
                        buffer.writeUtf(payload.abilityId());
                    },
                    buffer -> new AbilityAnimationPayload(buffer.readVarInt(), buffer.readUtf(64))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(TYPE, STREAM_CODEC);
    }

    public static void broadcast(ServerPlayer caster, ElementalAbility ability) {
        AbilityAnimationPayload payload = new AbilityAnimationPayload(caster.getId(), ability.id());
        double visibilityRange = 64.0D * 64.0D;
        for (ServerPlayer viewer : caster.serverLevel().players()) {
            if (viewer.distanceToSqr(caster) <= visibilityRange) {
                viewer.connection.send(payload.toVanillaClientbound());
            }
        }
    }
}