package dev.elementallegacy.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** World-scoped player progression. Stored in the Overworld data folder. */
public final class ElementalWorldData extends SavedData {
    private static final Codec<Map<String, PlayerProgress>> PLAYERS_CODEC =
            Codec.unboundedMap(Codec.STRING, PlayerProgress.CODEC);

    public static final SavedDataType<ElementalWorldData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("elemental_legacy", "player_progress"),
            ElementalWorldData::new,
            RecordCodecBuilder.create(instance -> instance.group(
                    PLAYERS_CODEC.optionalFieldOf("players", Map.of()).forGetter(data -> data.players)
            ).apply(instance, ElementalWorldData::new)),
            null
    );

    private final Map<String, PlayerProgress> players;

    public ElementalWorldData() {
        this(new HashMap<>());
    }

    private ElementalWorldData(Map<String, PlayerProgress> players) {
        this.players = new HashMap<>(players);
    }

    public static ElementalWorldData get(MinecraftServer server) {
        var overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            throw new IllegalStateException("Overworld is not available while loading elemental player data.");
        }
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    public PlayerProgress get(UUID playerId) {
        return players.getOrDefault(playerId.toString(), PlayerProgress.unchosen());
    }

    public void put(UUID playerId, PlayerProgress progress) {
        players.put(playerId.toString(), progress);
        setDirty();
    }

    public boolean choose(UUID playerId, String elementId) {
        PlayerProgress current = get(playerId);
        if (current.hasChosenElement()) return false;
        put(playerId, new PlayerProgress(elementId, 1, 0, PlayerProgress.MAX_ENERGY, "", "", ""));
        return true;
    }

    public void addXp(UUID playerId, int amount) {
        PlayerProgress current = get(playerId);
        PlayerProgress updated = current.addXp(amount);
        if (!updated.equals(current)) put(playerId, updated);
    }

    public boolean spendEnergy(UUID playerId, float amount) {
        PlayerProgress current = get(playerId);
        if (!current.hasChosenElement() || amount < 0.0F || current.energy() < amount) return false;
        put(playerId, current.withEnergy(current.energy() - amount));
        return true;
    }

    public void regenerateEnergy(UUID playerId, float amount) {
        PlayerProgress current = get(playerId);
        if (!current.hasChosenElement() || current.energy() >= PlayerProgress.MAX_ENERGY) return;
        put(playerId, current.withEnergy(current.energy() + amount));
    }
}
