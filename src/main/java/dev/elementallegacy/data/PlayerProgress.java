package dev.elementallegacy.data;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
public record PlayerProgress(String elementId,int level,int xp,float energy,String selectedAbility) {
 public static final int MAX_LEVEL=50; public static final float MAX_ENERGY=100.0F;
 public static final Codec<PlayerProgress> CODEC=RecordCodecBuilder.create(i->i.group(
  Codec.STRING.optionalFieldOf("element","").forGetter(PlayerProgress::elementId),
  Codec.INT.optionalFieldOf("level",1).forGetter(PlayerProgress::level),
  Codec.INT.optionalFieldOf("xp",0).forGetter(PlayerProgress::xp),
  Codec.FLOAT.optionalFieldOf("energy",MAX_ENERGY).forGetter(PlayerProgress::energy),
  Codec.STRING.optionalFieldOf("selected_ability","").forGetter(PlayerProgress::selectedAbility)
 ).apply(i,PlayerProgress::new));
 public static PlayerProgress unchosen(){return new PlayerProgress("",1,0,MAX_ENERGY,"");}
 public boolean hasChosenElement(){return !elementId.isBlank();}
 public PlayerProgress withEnergy(float v){return new PlayerProgress(elementId,level,xp,clampEnergy(v),selectedAbility);}
 public PlayerProgress withSelectedAbility(String id){return new PlayerProgress(elementId,level,xp,energy,id);}
 public PlayerProgress addXp(int amount){
  if(!hasChosenElement()||amount<=0||level>=MAX_LEVEL)return this;
  int l=level,x=xp+amount;
  while(l<MAX_LEVEL){int needed=xpToNextLevel(l);if(x<needed)break;x-=needed;l++;}
  if(l>=MAX_LEVEL)x=0;
  return new PlayerProgress(elementId,l,x,energy,selectedAbility);
 }
 public static int xpToNextLevel(int l){return 100+(l-1)*35;}
 private static float clampEnergy(float v){return Math.max(0.0F,Math.min(MAX_ENERGY,v));}
}