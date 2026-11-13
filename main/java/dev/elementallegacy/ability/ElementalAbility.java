package dev.elementallegacy.ability;

import dev.elementallegacy.element.Element;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public enum ElementalAbility {
    FLAME_PROJECTILE("flame_projectile", "Projétil Flamejante", Element.EMBER, 10, 3, 12, 18, 8, "Dispara uma esfera de fogo com rastro e explosão."),
    FLAME_BURST("flame_burst", "Rajada Ígnea", Element.EMBER, 14, 5, 18, 8, 11, "Avanço de fogo em cone curto."),
    FIRE_WHIP("fire_whip", "Chicote de Fogo", Element.EMBER, 16, 7, 22, 9, 14, "Chicote flamejante em arco lateral."),
    FOCUSED_EXPLOSION("focused_explosion", "Explosão Concentrada", Element.EMBER, 24, 12, 30, 7, 18, "Carga concentrada que detona em área."),
    FIRE_PROPULSION("fire_propulsion", "Propulsão Ígnea", Element.EMBER, 20, 10, 20, 5, 16, "Impulso explosivo para deslocamento e dano próximo."),
    FIRE_DRAGON("fire_dragon", "Dragão de Fogo", Element.EMBER, 40, 25, 48, 16, 30, "Grande fluxo serpenteante de fogo."),
    WATER_PROJECTILE("water_projectile", "Projétil de Água", Element.TIDE, 10, 3, 12, 18, 8, "Disparo pressurizado de água."),
    WATER_WHIP("water_whip", "Chicote de Água", Element.TIDE, 16, 6, 20, 10, 13, "Arco fluido que varre a frente."),
    ICE_BIND("ice_bind", "Prisão Glacial", Element.TIDE, 20, 10, 24, 8, 16, "Coluna de gelo que prende e desacelera."),
    ICE_BLADES("ice_blades", "Lâminas de Gelo", Element.TIDE, 22, 12, 28, 13, 18, "Leque de lâminas congeladas."),
    WATER_SHIELD("water_shield", "Escudo de Água", Element.TIDE, 18, 12, 8, 5, 14, "Barreira circular que repele ameaças próximas."),
    GREAT_WAVE("great_wave", "Onda Gigantesca", Element.TIDE, 40, 25, 46, 15, 30, "Onda larga que avança e derruba inimigos."),
    ROCK_THROW("rock_throw", "Rochas Arremessadas", Element.STONE, 12, 4, 15, 16, 9, "Projéteis de pedra em sequência."),
    STONE_WALL("stone_wall", "Muralha de Pedra", Element.STONE, 22, 12, 12, 6, 16, "Ergue uma linha defensiva de rocha."),
    ROCK_CAGE("rock_cage", "Prisão de Rocha", Element.STONE, 24, 14, 26, 8, 18, "Prende o alvo em uma explosão de rocha."),
    SEISMIC_WAVE("seismic_wave", "Tremor Sísmico", Element.STONE, 22, 12, 28, 10, 18, "Onda de choque que percorre o solo."),
    STONE_ARMOR("stone_armor", "Armadura de Pedra", Element.STONE, 20, 16, 8, 4, 15, "Reforça o corpo e repele inimigos próximos."),
    EARTHQUAKE("earthquake", "Terremoto", Element.STONE, 42, 25, 50, 14, 30, "Abalo amplo com múltiplos impactos."),
    AIR_BLAST("air_blast", "Rajada de Ar", Element.GALE, 10, 3, 12, 18, 8, "Disparo comprimido de ar."),
    AIR_PUSH("air_push", "Empurrão de Ar", Element.GALE, 12, 4, 14, 8, 10, "Pulso circular que afasta inimigos."),
    AIR_SHIELD("air_shield", "Escudo de Ar", Element.GALE, 18, 10, 8, 5, 14, "Redemoinho defensivo ao redor do usuário."),
    AIR_JUMP("air_jump", "Salto de Ar", Element.GALE, 16, 8, 12, 6, 13, "Salto impulsionado por uma coluna de vento."),
    AIR_DASH("air_dash", "Corrida Aérea", Element.GALE, 22, 12, 18, 10, 17, "Avanço veloz envolto em correntes de ar."),
    CYCLONE("cyclone", "Tornado", Element.GALE, 40, 25, 46, 14, 30, "Tornado ascendente que lança inimigos.");

    private final String id, displayName, description;
    private final Element element;
    private final int energyCost, xpReward;
    private final float cooldownSeconds, damage;
    private final double range;

    ElementalAbility(String id, String displayName, Element element, int energyCost, float cooldownSeconds,
                     float damage, double range, int xpReward, String description) {
        this.id=id; this.displayName=displayName; this.element=element; this.energyCost=energyCost;
        this.cooldownSeconds=cooldownSeconds; this.damage=damage; this.range=range;
        this.xpReward=xpReward; this.description=description;
    }
    public String id(){return id;}
    public String displayName(){return displayName;}
    public Element element(){return element;}
    public int energyCost(){return energyCost;}
    public int cooldownTicks(){return Math.round(cooldownSeconds*20.0F);}
    public float damage(){return damage;}
    public double range(){return range;}
    public int xpReward(){return xpReward;}
    public String description(){return description;}
    public static Optional<ElementalAbility> byId(String id){
        if(id==null)return Optional.empty();
        return Arrays.stream(values()).filter(a->a.id.equalsIgnoreCase(id)).findFirst();
    }
    public static List<ElementalAbility> forElement(Element element){
        return Arrays.stream(values()).filter(a->a.element==element).toList();
    }
}