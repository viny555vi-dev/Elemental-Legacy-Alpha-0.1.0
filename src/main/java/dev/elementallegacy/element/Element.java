package dev.elementallegacy.element;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum Element {
    TIDE("tide", "Maré", "Controle, adaptação e técnicas de água e gelo"),
    STONE("stone", "Rocha", "Resistência, impacto e manipulação do terreno"),
    EMBER("ember", "Brasa", "Pressão ofensiva, calor e explosões concentradas"),
    GALE("gale", "Vendaval", "Mobilidade, precisão e controle de correntes de ar");

    private final String id;
    private final String displayName;
    private final String description;

    Element(String id, String displayName, String description) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public String description() { return description; }

    public static Optional<Element> byId(String value) {
        if (value == null) return Optional.empty();
        String normalized = value.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(element -> element.id.equals(normalized)
                || element.name().toLowerCase(Locale.ROOT).equals(normalized)
                || element.displayName.toLowerCase(Locale.ROOT).equals(normalized)).findFirst();
    }
}
