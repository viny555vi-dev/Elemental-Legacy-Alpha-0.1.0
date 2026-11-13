package dev.elementallegacy.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record PlayerProgress(
        String elementId,
        int level,
        int xp,
        float energy,
        String selectedAbility,
        String selectedZAbility,
        String selectedXAbility
) {
    public static final int MAX_LEVEL = 50;
    public static final float MAX_ENERGY = 100.0F;

    public static final Codec<PlayerProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("element", "").forGetter(PlayerProgress::elementId),
            Codec.INT.optionalFieldOf("level", 1).forGetter(PlayerProgress::level),
            Codec.INT.optionalFieldOf("xp", 0).forGetter(PlayerProgress::xp),
            Codec.FLOAT.optionalFieldOf("energy", MAX_ENERGY).forGetter(PlayerProgress::energy),
            Codec.STRING.optionalFieldOf("selected_ability", "").forGetter(PlayerProgress::selectedAbility),
            Codec.STRING.optionalFieldOf("selected_z_ability", "").forGetter(PlayerProgress::selectedZAbility),
            Codec.STRING.optionalFieldOf("selected_x_ability", "").forGetter(PlayerProgress::selectedXAbility)
    ).apply(instance, PlayerProgress::new));

    public static PlayerProgress unchosen() {
        return new PlayerProgress("", 1, 0, MAX_ENERGY, "", "", "");
    }

    public boolean hasChosenElement() {
        return !elementId.isBlank();
    }

    public PlayerProgress withEnergy(float value) {
        return new PlayerProgress(elementId, level, xp, clampEnergy(value),
                selectedAbility, selectedZAbility, selectedXAbility);
    }

    public PlayerProgress withSelectedAbility(String id) {
        return new PlayerProgress(elementId, level, xp, energy, id,
                selectedZAbility, selectedXAbility);
    }

    public PlayerProgress withButtonAbility(String slot, String id) {
        if ("z".equalsIgnoreCase(slot)) {
            return new PlayerProgress(elementId, level, xp, energy, selectedAbility, id, selectedXAbility);
        }
        if ("x".equalsIgnoreCase(slot)) {
            return new PlayerProgress(elementId, level, xp, energy, selectedAbility, selectedZAbility, id);
        }
        throw new IllegalArgumentException("Unknown elemental shortcut: " + slot);
    }

    public PlayerProgress addXp(int amount) {
        if (!hasChosenElement() || amount <= 0 || level >= MAX_LEVEL) return this;
        int nextLevel = level;
        int remainingXp = xp + amount;
        while (nextLevel < MAX_LEVEL) {
            int needed = xpToNextLevel(nextLevel);
            if (remainingXp < needed) break;
            remainingXp -= needed;
            nextLevel++;
        }
        if (nextLevel >= MAX_LEVEL) remainingXp = 0;
        return new PlayerProgress(elementId, nextLevel, remainingXp, energy,
                selectedAbility, selectedZAbility, selectedXAbility);
    }

    public static int xpToNextLevel(int level) {
        return 100 + (level - 1) * 35;
    }

    private static float clampEnergy(float value) {
        return Math.max(0.0F, Math.min(MAX_ENERGY, value));
    }
}