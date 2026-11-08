package dev.elementallegacy.ability;

import dev.elementallegacy.element.Element;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public enum ElementalAbility {
    WATER_BURST("water_burst", "Impacto das Marés", Element.TIDE, 8, 3.0F, 5.0F, 5.0D, 12, "Uma descarga de água que atinge inimigos à frente."),
    ICE_BIND("ice_bind", "Prisão Glacial", Element.TIDE, 14, 2.0F, 2.0F, 4.0D, 18, "Uma onda fria que causa dano e afasta o alvo."),
    ROCK_STRIKE("rock_strike", "Punho de Rocha", Element.STONE, 10, 3.5F, 7.0F, 4.5D, 14, "Um impacto concentrado de pedra."),
    SEISMIC_WAVE("seismic_wave", "Onda Sísmica", Element.STONE, 18, 2.5F, 4.0F, 6.0D, 22, "Um tremor em área que empurra os inimigos."),
    FLAME_BURST("flame_burst", "Rajada Ígnea", Element.EMBER, 12, 3.0F, 6.0F, 5.0D, 16, "Uma explosão de fogo que incendeia os alvos."),
    FIRE_WHIP("fire_whip", "Chicote de Brasa", Element.EMBER, 18, 2.0F, 8.0F, 6.0D, 22, "Um golpe flamejante de alcance maior."),
    AIR_BLAST("air_blast", "Lança de Ar", Element.GALE, 8, 2.5F, 4.0F, 8.0D, 12, "Uma rajada que lança o alvo para longe."),
    CYCLONE("cyclone", "Mini Tornado", Element.GALE, 18, 2.5F, 3.0F, 7.0D, 22, "Uma corrente ascendente que arremessa inimigos.");

    private final String id;
    private final String displayName;
    private final Element element;
    private final int energyCost;
    private final float cooldownSeconds;
    private final float damage;
    private final double range;
    private final int xpReward;
    private final String description;

    ElementalAbility(String id, String displayName, Element element, int energyCost, float cooldownSeconds,
                     float damage, double range, int xpReward, String description) {
        this.id = id;
        this.displayName = displayName;
        this.element = element;
        this.energyCost = energyCost;
        this.cooldownSeconds = cooldownSeconds;
        this.damage = damage;
        this.range = range;
        this.xpReward = xpReward;
        this.description = description;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public Element element() { return element; }
    public int energyCost() { return energyCost; }
    public int cooldownTicks() { return Math.round(cooldownSeconds * 20.0F); }
    public float damage() { return damage; }
    public double range() { return range; }
    public int xpReward() { return xpReward; }
    public String description() { return description; }

    public static Optional<ElementalAbility> byId(String id) {
        if (id == null) return Optional.empty();
        return Arrays.stream(values()).filter(a -> a.id.equalsIgnoreCase(id)).findFirst();
    }

    public static List<ElementalAbility> forElement(Element element) {
        return Arrays.stream(values()).filter(a -> a.element == element).toList();
    }
}
