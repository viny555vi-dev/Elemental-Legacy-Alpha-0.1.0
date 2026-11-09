package dev.elementallegacy.ability;

import dev.elementallegacy.data.ElementalWorldData;
import dev.elementallegacy.data.PlayerProgress;
import dev.elementallegacy.element.Element;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class ElementalAbilityService {
    private static final Map<UUID,Map<String,Integer>> COOLDOWNS=new HashMap<>();
    private static final Map<UUID,List<ScheduledCue>> ANIMATIONS=new HashMap<>();
    private static final Map<UUID,Integer> LAST_USE_TICK=new HashMap<>();
    private record ScheduledCue(int tick,Runnable action){}
    private ElementalAbilityService(){}

    public static String use(ServerPlayer player, ElementalAbility ability){
        ElementalWorldData data=ElementalWorldData.get(player.level().getServer());
        PlayerProgress progress=data.get(player.getUUID());
        if(!progress.hasChosenElement())return "Escolha um elemento primeiro: /elemental choose <tide|stone|ember|gale>.";
        Element element=Element.byId(progress.elementId()).orElse(null);
        if(element==null||ability.element()!=element)return "Essa técnica não pertence ao seu elemento.";
        if(progress.energy()<ability.energyCost())return "Energia insuficiente. Necessário: "+ability.energyCost()+". Atual: "+Math.round(progress.energy())+".";
        int now=player.tickCount;
        int ready=COOLDOWNS.getOrDefault(player.getUUID(),Map.of()).getOrDefault(ability.id(),0);
        if(now<ready)return "Técnica em recarga: "+String.format(Locale.ROOT,"%.1f",(ready-now)/20.0)+"s.";

        data.spendEnergy(player.getUUID(),ability.energyCost());
        LAST_USE_TICK.put(player.getUUID(),now);
        COOLDOWNS.computeIfAbsent(player.getUUID(),k->new HashMap<>()).put(ability.id(),now+ability.cooldownTicks());
        ServerLevel level=(ServerLevel)player.level();
        Vec3 look=player.getLookAngle().normalize();
        Vec3 origin=player.getEyePosition();
        // A real vanilla arm swing is synchronized to observers; the impulse adds a visible body action.
        player.swing(InteractionHand.MAIN_HAND,new SwingAnimation(SwingAnimationType.WHACK,9),true);
        applyBodyMotion(player,ability,look);
        int hits=applyCombat(level,player,ability,origin,look);
        scheduleTechnique(player,level,ability,origin,look);
        if(hits>0){
            data.addXp(player.getUUID(),ability.xpReward()*hits);
            return ability.displayName()+" executada! Alvos atingidos: "+hits+". +"+(ability.xpReward()*hits)+" XP.";
        }
        return ability.displayName()+" executada. Nenhum inimigo atingido; energia consumida e sem XP.";
    }

    private static void applyBodyMotion(ServerPlayer p,ElementalAbility a,Vec3 look){
        Vec3 impulse=Vec3.ZERO;
        switch(a){
            case FIRE_PROPULSION,AIR_DASH -> impulse=look.scale(1.25).add(0,0.18,0);
            case AIR_JUMP -> impulse=new Vec3(0,0.95,0).add(look.scale(0.25));
            case FIRE_DRAGON,GREAT_WAVE,EARTHQUAKE,CYCLONE,FOCUSED_EXPLOSION,SEISMIC_WAVE -> impulse=look.scale(0.12);
            default -> {}
        }
        if(impulse.lengthSqr()>0)p.setDeltaMovement(p.getDeltaMovement().add(impulse));
        p.hurtMarked=true;
    }

    private static int applyCombat(ServerLevel level,ServerPlayer player,ElementalAbility a,Vec3 origin,Vec3 look){
        AABB box=player.getBoundingBox().inflate(a.range(),Math.max(3,a.range()*0.55),a.range());
        int hits=0;
        for(LivingEntity target:level.getEntitiesOfClass(LivingEntity.class,box,e->e!=player&&e.isAlive()&&!e.isSpectator())){
            Vec3 offset=target.getBoundingBox().getCenter().subtract(origin);
            double distance=offset.length();
            if(distance>a.range()||distance<0.01)continue;
            double alignment=look.dot(offset.normalize());
            boolean radial=switch(a){
                case AIR_PUSH,AIR_SHIELD,WATER_SHIELD,STONE_ARMOR,FOCUSED_EXPLOSION,EARTHQUAKE,CYCLONE -> true;
                default -> false;
            };
            if(!radial&&alignment<0.48)continue;
            if(radial&&distance>Math.min(a.range(),6.5))continue;
            float damage=a.damage();
            if(a==ElementalAbility.WATER_SHIELD||a==ElementalAbility.AIR_SHIELD||a==ElementalAbility.STONE_ARMOR)damage=8;
            if(!target.hurtServer(level,player.damageSources().playerAttack(player),damage))continue;
            hits++;
            Vec3 push=look.scale(a.element()==Element.GALE?1.25:0.62);
            if(a==ElementalAbility.CYCLONE||a==ElementalAbility.AIR_JUMP)push=push.add(0,0.72,0);
            if(radial)push=target.position().subtract(player.position()).normalize().scale(0.9).add(0,0.35,0);
            target.push(push.x,push.y,push.z);
            if(a.element()==Element.EMBER)target.igniteForSeconds(a==ElementalAbility.FIRE_DRAGON?6:3);
            if(a==ElementalAbility.ICE_BIND||a==ElementalAbility.ICE_BLADES)target.setDeltaMovement(target.getDeltaMovement().multiply(0.25,0.35,0.25));
        }
        return hits;
    }

    // Keeps visual effects server-authoritative so every nearby player sees the same action.
    private static void scheduleTechnique(ServerPlayer player,ServerLevel level,ElementalAbility a,Vec3 origin,Vec3 look){
        UUID id=player.getUUID();
        Vec3 right=look.cross(new Vec3(0,1,0)).normalize();
        if(right.lengthSqr()<0.001)right=new Vec3(1,0,0);
        Vec3 up=right.cross(look).normalize();
        Vec3 hands=origin.add(look.scale(0.38)).add(0,-0.38,0);
        Vec3 r=right,u=up;
        List<ScheduledCue> cues=new ArrayList<>();
        // Wind-up: visible energy gathering at the hands/body, followed by the technique's signature.
        cues.add(new ScheduledCue(0,()->emitElement(level,a.element(),hands,26,0.28,0.3,0.28,0.035)));
        cues.add(new ScheduledCue(3,()->{
            ring(level,a.element(),hands,0.52,22);
            emit(level,ParticleTypes.ENCHANT,hands,10,0.24,0.24,0.24,0.08);
        }));
        cues.add(new ScheduledCue(6,()->renderSignature(level,a,origin,look,r,u)));
        cues.add(new ScheduledCue(11,()->{
            Vec3 impact=origin.add(look.scale(Math.min(a.range()*0.72,7)));
            impact(level,a,impact);
            debris(level,impact,a.element(),16);
        }));
        cues.add(new ScheduledCue(17,()->{
            Vec3 end=origin.add(look.scale(Math.min(a.range()*0.72,7)));
            emitElement(level,a.element(),end,15,0.55,0.38,0.55,0.018);
            emit(level,ParticleTypes.CLOUD,end,7,0.3,0.2,0.3,0.012);
        }));
        int start=player.tickCount;
        List<ScheduledCue> timed=new ArrayList<>();
        for(ScheduledCue c:cues)timed.add(new ScheduledCue(start+c.tick(),c.action()));
        ANIMATIONS.put(id,timed);
    }

    private static void renderSignature(ServerLevel l,ElementalAbility a,Vec3 o,Vec3 f,Vec3 r,Vec3 u){
        Vec3 start=o.add(f.scale(0.45)).add(0,-0.35,0);
        switch(a){
            case FLAME_PROJECTILE,WATER_PROJECTILE,AIR_BLAST,ROCK_THROW -> {
                for(int s=1;s<=13;s++){
                    Vec3 p=start.add(f.scale(s*0.42));
                    emitElement(l,a.element(),p,9,0.18,0.18,0.18,0.035);
                    emit(l,ParticleTypes.CRIT,p,2,0.1,0.1,0.1,0.02);
                }
            }
            case FLAME_BURST,FIRE_DRAGON,GREAT_WAVE,SEISMIC_WAVE,EARTHQUAKE -> {
                int lanes=a==ElementalAbility.FIRE_DRAGON?5:3;
                for(int s=1;s<=12;s++){
                    double width=(a==ElementalAbility.GREAT_WAVE||a==ElementalAbility.EARTHQUAKE?0.18:0.10)*s;
                    for(int lane=-lanes;lane<=lanes;lane++){
                        Vec3 p=start.add(f.scale(s*0.48)).add(r.scale(lane*width/lanes)).add(0,Math.sin(s*0.55+lane)*0.22,0);
                        emitElement(l,a.element(),p,4,0.10,0.12,0.10,0.025);
                    }
                }
                ring(l,a.element(),o.add(f.scale(4)),1.25,40);
            }
            case FIRE_WHIP,WATER_WHIP,ICE_BLADES -> {
                for(int s=0;s<22;s++){
                    double t=s/21.0, angle=-1.2+t*2.4;
                    Vec3 p=start.add(r.scale(Math.sin(angle)*2.4)).add(f.scale(Math.cos(angle)*1.2)).add(u.scale(Math.sin(t*Math.PI)*0.65));
                    emitElement(l,a.element(),p,5,0.09,0.09,0.09,0.025);
                }
            }
            case FOCUSED_EXPLOSION,ICE_BIND,ROCK_CAGE -> {
                Vec3 c=o.add(f.scale(3.1));
                for(int y=0;y<7;y++){
                    double yy=y*0.38-1.1;
                    ringAt(l,a.element(),c.add(0,yy,0),0.68,28);
                }
                emitElement(l,a.element(),c,45,0.8,1.0,0.8,0.045);
            }
            case FIRE_PROPULSION,AIR_DASH,AIR_JUMP -> {
                for(int s=0;s<12;s++){
                    Vec3 p=o.subtract(f.scale(s*0.32)).add(0,-0.55+s*0.06,0);
                    emitElement(l,a.element(),p,8,0.25,0.25,0.25,0.04);
                }
                ring(l,a.element(),o,1.1,36);
            }
            case WATER_SHIELD,AIR_SHIELD,STONE_ARMOR -> {
                for(int y=0;y<5;y++)ringAt(l,a.element(),o.add(0,-1+y*0.48,0),1.15,36);
                emitElement(l,a.element(),o,35,1.0,1.1,1.0,0.025);
            }
            case STONE_WALL -> {
                for(int x=-3;x<=3;x++)for(int y=0;y<5;y++){
                    Vec3 p=o.add(f.scale(2.8)).add(r.scale(x*0.42)).add(0,y*0.42-0.6,0);
                    emit(l,ParticleTypes.POOF,p,5,0.12,0.12,0.12,0.02);
                    debris(l,p,Element.STONE,4);
                }
            }
            case AIR_PUSH -> {
                for(int radius=1;radius<=5;radius++)ring(l,Element.GALE,radius*0.48,48);
            }
            case CYCLONE -> {
                Vec3 c=o.add(f.scale(3.2));
                for(int y=0;y<18;y++){
                    double angle=y*0.72,rad=0.35+y*0.055;
                    Vec3 p=c.add(Math.cos(angle)*rad,y*0.18-1.2,Math.sin(angle)*rad);
                    emit(l,ParticleTypes.CLOUD,p,5,0.16,0.22,0.16,0.035);
                    emitElement(l,Element.GALE,p,3,0.1,0.12,0.1,0.02);
                }
            }
        }
    }

    private static void impact(ServerLevel l,ElementalAbility a,Vec3 p){
        switch(a.element()){
            case EMBER -> {emit(l,ParticleTypes.FLAME,p,42,0.72,0.62,0.72,0.12);emit(l,ParticleTypes.LAVA,p,8,0.35,0.3,0.35,0.04);emit(l,ParticleTypes.LARGE_SMOKE,p,15,0.45,0.3,0.45,0.035);}
            case TIDE -> {emit(l,ParticleTypes.SPLASH,p,48,0.85,0.65,0.85,0.16);emit(l,ParticleTypes.BUBBLE,p,22,0.5,0.4,0.5,0.06);}
            case STONE -> {emit(l,ParticleTypes.POOF,p,38,0.8,0.25,0.8,0.11);emit(l,ParticleTypes.CRIT,p,24,0.7,0.45,0.7,0.14);}
            case GALE -> {emit(l,ParticleTypes.CLOUD,p,42,0.8,0.65,0.8,0.12);emit(l,ParticleTypes.POOF,p,20,0.55,0.4,0.55,0.07);}
        }
    }

    private static void ring(ServerLevel l,Element e,Vec3 c,double radius,int count){
        ringAt(l,e,c,radius,count);
    }
    private static void ringAt(ServerLevel l,Element e,Vec3 c,double radius,int count){
        for(int i=0;i<count;i++){
            double a=2*Math.PI*i/count;
            Vec3 p=c.add(Math.cos(a)*radius,0,Math.sin(a)*radius);
            emitElement(l,e,p,2,0.035,0.035,0.035,0.008);
        }
    }
    private static void debris(ServerLevel l,Vec3 p,Element e,int count){
        BlockPos pos=BlockPos.containing(p.x,p.y-0.2,p.z);
        BlockState state=l.getBlockState(pos);
        if(state.isAir())state=l.getBlockState(pos.below());
        if(state.isAir())return;
        ParticleOptions dust=new BlockParticleOption(ParticleTypes.BLOCK,state);
        emit(l,dust,p,count,0.55,0.32,0.55,0.12);
    }
    private static void emitElement(ServerLevel l,Element e,Vec3 p,int n,double dx,double dy,double dz,double speed){
        switch(e){
            case EMBER -> {emit(l,ParticleTypes.FLAME,p,n,dx,dy,dz,speed);emit(l,ParticleTypes.SMOKE,p,Math.max(1,n/4),dx*0.7,dy*0.7,dz*0.7,speed*0.3);}
            case TIDE -> {emit(l,ParticleTypes.SPLASH,p,n,dx,dy,dz,speed);emit(l,ParticleTypes.BUBBLE,p,Math.max(1,n/3),dx*0.7,dy*0.7,dz*0.7,speed*0.3);}
            case STONE -> {emit(l,ParticleTypes.POOF,p,n,dx,dy,dz,speed);emit(l,ParticleTypes.CRIT,p,Math.max(1,n/3),dx*0.7,dy*0.7,dz*0.7,speed);}
            case GALE -> {emit(l,ParticleTypes.CLOUD,p,n,dx,dy,dz,speed);emit(l,ParticleTypes.SWEEP_ATTACK,p,Math.max(1,n/4),dx*0.7,dy*0.7,dz*0.7,speed*0.2);}
        }
    }
    private static void emit(ServerLevel l,ParticleOptions type,Vec3 p,int n,double dx,double dy,double dz,double speed){
        l.sendParticles(type,p.x,p.y,p.z,n,dx,dy,dz,speed);
    }

    public static boolean isRegenerationPaused(ServerPlayer p){
        Integer last=LAST_USE_TICK.get(p.getUUID());
        return last!=null&&p.tickCount-last<30;
    }
    public static void tick(ServerPlayer p){
        List<ScheduledCue> cues=ANIMATIONS.get(p.getUUID());
        if(cues==null)return;
        if(!p.isAlive()){ANIMATIONS.remove(p.getUUID());return;}
        Iterator<ScheduledCue> it=cues.iterator();
        while(it.hasNext()){ScheduledCue c=it.next();if(p.tickCount>=c.tick()){c.action().run();it.remove();}}
        if(cues.isEmpty())ANIMATIONS.remove(p.getUUID());
    }
}