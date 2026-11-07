package dev.elementallegacy.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record PlayerProgress(String elementId, int level, int xp, float energy, String selectedAbility) {
    public static final int MAX_LEVEL = 50;
    public static final float MAX_ENERGY = 100.0F;
    public static final Codec<PlayerProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("element", "").forGetter(PlayerProgress::elementId),
            Codec.INT.optionalFieldOf("level", 1).forGetter(PlayerProgress::level),
            Codec.INT.optionalFieldOf("xp", 0).forGetter(PlayerProgress::xp),
            Codec.FLOAT.optionalFieldOf("energy", MAX_ENERGY).forGetter(PlayerProgress::energy),
            Codec.STRING.optionalFieldOf("selected_ability", "").forGetter(PlayerProgress::selectedAbility)
    ).apply(instance, PlayerProgress::new));

    public static PlayerProgress unchosen() {
        return new PlayerProgress("", 1, 0, MAX_ENERGY, "");
    }

    public boolean hasChosenElement() { return !elementId.isBlank(); }

    public PlayerProgress withEnergy(float value) {
        return new PlayerProgress(elementId, level, xp, clampEnergy(value), selectedAbility);
    }

    public PlayerProgress withSelectedAbility(String abilityId) {
        return new PlayerProgress(elementId, level, xp, energy, abilityId);
    }

    public PlayerProgress addXp(int amount) {
        if (!hasChosenElement() || amount <= 0 || level >= MAX_LEVEL) return this;
        int nextLevel = level;
        int nextXp = xp + amount;
        while (nextLevel < MAX_LEVEL) {
            int needed = xpToNextLevel(nextLevel);
            if (nextXp < needed) break;
            nextXp -= needed;
            nextLevel++;
        }
        if (nextLevel >= MAX_LEVEL) nextXp = 0;
        return new PlayerProgress(elementId, nextLevel, nextXp, energy, selectedAbility);
    }

    public static int xpToNextLevel(int currentLevel) {
        return 100 + (currentLevel - 1) * 35;
    }

    private static float clampEnergy(float value) {
        return Math.max(0.0F, Math.min(MAX_ENERGY, value));
    }
}
