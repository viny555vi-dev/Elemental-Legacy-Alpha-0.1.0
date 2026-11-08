package dev.elementallegacy.element;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
public enum Element {
 TIDE("tide","Água","Controle, adaptação e técnicas de água e gelo"),
 STONE("stone","Terra","Resistência, impacto e manipulação do terreno"),
 EMBER("ember","Fogo","Pressão ofensiva, calor e explosões concentradas"),
 GALE("gale","Ar","Mobilidade, precisão e controle de correntes de ar");
 private final String id,displayName,description;
 Element(String id,String displayName,String description){this.id=id;this.displayName=displayName;this.description=description;}
 public String id(){return id;} public String displayName(){return displayName;} public String description(){return description;}
 public static Optional<Element> byId(String value){
  if(value==null)return Optional.empty(); String n=value.toLowerCase(Locale.ROOT);
  return Arrays.stream(values()).filter(e->e.id.equals(n)||e.name().toLowerCase(Locale.ROOT).equals(n)||e.displayName.toLowerCase(Locale.ROOT).equals(n)).findFirst();
 }
}