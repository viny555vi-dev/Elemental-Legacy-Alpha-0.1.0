package dev.elementallegacy.ability;

import dev.elementallegacy.data.ElementalWorldData;
import dev.elementallegacy.data.PlayerProgress;
import dev.elementallegacy.element.Element;
import dev.elementallegacy.network.AbilityAnimationPayload;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class ElementalAbilityService {
    private static final Map<UUID,Map<String,Integer>> COOLDOWNS=new HashMap<>();
    private static final Map<UUID,List<ScheduledCue>> ANIMATIONS=new HashMap<>();
    private static final Map<UUID,Integer> LAST_USE_TICK=new HashMap<>();
    private static final Map<UUID,List<TemporaryBlock>> TEMPORARY_BLOCKS=new HashMap<>();
    private record ScheduledCue(int tick,Runnable action){}
    private record TemporaryBlock(ServerLevel level,BlockPos pos,BlockState original,BlockState placed,long restoreGameTime){}
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
        AbilityAnimationPayload.broadcast(player, ability);
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
                case AIR_PUSH,AIR_SHIELD,AIR_JUMP,WATER_SHIELD,STONE_ARMOR,FOCUSED_EXPLOSION,EARTHQUAKE,CYCLONE -> true;
                default -> false;
            };
            if(!radial&&alignment<0.48)continue;
            if(radial&&distance>Math.min(a.range(),6.5))continue;
            float damage=a.damage();
            if(a==ElementalAbility.WATER_SHIELD||a==ElementalAbility.AIR_SHIELD||a==ElementalAbility.STONE_ARMOR)damage=8;
            if(!target.hurtServer(level,player.damageSources().playerAttack(player),damage))continue;
            hits++;
            Vec3 away = target.position().subtract(player.position());
            Vec3 horizontalAway = new Vec3(away.x, 0.0, away.z);
            if (horizontalAway.lengthSqr() < 0.0001) horizontalAway = new Vec3(look.x, 0.0, look.z);
            if (horizontalAway.lengthSqr() < 0.0001) horizontalAway = new Vec3(1.0, 0.0, 0.0);
            horizontalAway = horizontalAway.normalize();

            Vec3 push;
            switch (a) {
                case AIR_BLAST -> push = look.scale(1.95).add(0.0, 0.34, 0.0);
                case AIR_PUSH -> push = horizontalAway.scale(1.34).add(0.0, 0.62, 0.0);
                case AIR_SHIELD -> push = horizontalAway.scale(1.12).add(0.0, 0.48, 0.0);
                case AIR_JUMP -> push = horizontalAway.scale(0.78).add(0.0, 1.05, 0.0);
                case AIR_DASH -> push = look.scale(1.72).add(0.0, 0.68, 0.0);
                case CYCLONE -> {
                    Vec3 tangent = new Vec3(-horizontalAway.z, 0.0, horizontalAway.x);
                    double intensity = Math.max(0.35, 1.0 - Math.min(away.length(), 6.5) / 8.0);
                    push = tangent.scale(1.08 * intensity)
                            .add(horizontalAway.scale(-0.24))
                            .add(0.0, 0.62 + 0.38 * intensity, 0.0);
                }
                default -> {
                    push = look.scale(a.element() == Element.GALE ? 1.25 : 0.62);
                    if (radial) push = horizontalAway.scale(0.9).add(0.0, 0.35, 0.0);
                }
            }
            target.push(push.x, push.y, push.z);
            if (a.element() == Element.GALE) galeMobImpact(level, a, target, look, horizontalAway);
            if(a.element()==Element.EMBER)target.igniteForSeconds(a==ElementalAbility.FIRE_DRAGON?6:3);
            if(a==ElementalAbility.ICE_BIND||a==ElementalAbility.ICE_BLADES)target.setDeltaMovement(target.getDeltaMovement().multiply(0.25,0.35,0.25));
        }
        return hits;
    }

    /**
     * Technique-specific wind impacts on living targets. These are deliberately separate
     * from the caster's mesh so mobs receive a readable visual reaction of their own.
     */
    private static void galeMobImpact(ServerLevel level, ElementalAbility ability,
                                      LivingEntity target, Vec3 look, Vec3 away) {
        Vec3 center = target.position().add(0.0, target.getBbHeight() * 0.55, 0.0);
        Vec3 horizontal = new Vec3(away.x, 0.0, away.z);
        if (horizontal.lengthSqr() < 0.0001) horizontal = new Vec3(look.x, 0.0, look.z);
        if (horizontal.lengthSqr() < 0.0001) horizontal = new Vec3(1.0, 0.0, 0.0);
        horizontal = horizontal.normalize();

        switch (ability) {
            case AIR_BLAST -> {
                // Focused pressure strike: a bright frontal slash and a short-lived impact bloom.
                emit(level, ParticleTypes.SWEEP_ATTACK, center, 7, 0.22, 0.20, 0.22, 0.12);
                emit(level, ParticleTypes.CLOUD, center, 24, 0.34, 0.30, 0.34, 0.10);
                mobWindRing(level, center, 0.62, 22, 0.10, ParticleTypes.CLOUD);
            }
            case AIR_PUSH -> {
                // Radial repulsion: two horizontal pressure rings expand from the mob's body.
                mobWindRing(level, center.add(0.0, -0.34, 0.0), 0.72, 28, 0.08, ParticleTypes.CLOUD);
                mobWindRing(level, center.add(0.0, 0.34, 0.0), 0.92, 28, 0.07, ParticleTypes.SWEEP_ATTACK);
                emit(level, ParticleTypes.POOF, center, 14, 0.38, 0.25, 0.38, 0.06);
            }
            case AIR_SHIELD -> {
                // A defensive counter-current wraps the repelled target in intersecting arcs.
                mobWindRing(level, center, 0.78, 24, 0.10, ParticleTypes.SWEEP_ATTACK);
                for (int side : new int[]{-1, 1}) {
                    Vec3 arc = center.add(horizontal.scale(side * 0.34));
                    emit(level, ParticleTypes.CLOUD, arc, 12, 0.12, 0.36, 0.12, 0.08);
                }
                emit(level, ParticleTypes.POOF, center, 12, 0.32, 0.30, 0.32, 0.05);
            }
            case AIR_JUMP -> {
                // A vertical launch column climbs around the target as it is lifted.
                for (int step = 0; step < 6; step++) {
                    Vec3 point = center.add(0.0, -0.65 + step * 0.28, 0.0)
                            .add(horizontal.scale(0.18 + step * 0.045));
                    emit(level, ParticleTypes.CLOUD, point, 5, 0.13, 0.16, 0.13, 0.10);
                }
                emit(level, ParticleTypes.SWEEP_ATTACK, center, 5, 0.18, 0.38, 0.18, 0.08);
            }
            case AIR_DASH -> {
                // A high-speed cross-cut leaves several parallel wind slashes through the target.
                Vec3 tangent = new Vec3(-horizontal.z, 0.0, horizontal.x);
                for (int blade = -2; blade <= 2; blade++) {
                    Vec3 point = center.add(tangent.scale(blade * 0.24));
                    emit(level, ParticleTypes.SWEEP_ATTACK, point, 2, 0.06, 0.16, 0.06, 0.16);
                    emit(level, ParticleTypes.CLOUD, point, 4, 0.10, 0.12, 0.10, 0.08);
                }
            }
            case CYCLONE -> {
                // Spiral lift around the body matches the cyclone's tangential knockback.
                for (int step = 0; step < 18; step++) {
                    double angle = step * Math.PI * 0.58;
                    double radius = 0.28 + step * 0.035;
                    Vec3 point = center.add(Math.cos(angle) * radius,
                            -0.82 + step * 0.095,
                            Math.sin(angle) * radius);
                    emit(level, ParticleTypes.CLOUD, point, 4, 0.10, 0.13, 0.10, 0.065);
                    if (step % 3 == 0) emit(level, ParticleTypes.SWEEP_ATTACK, point, 1, 0.02, 0.04, 0.02, 0.06);
                }
                emit(level, ParticleTypes.POOF, center, 12, 0.55, 0.28, 0.55, 0.05);
            }
            default -> {
            }
        }
    }

    private static void mobWindRing(ServerLevel level, Vec3 center, double radius,
                                    int segments, double verticalJitter, ParticleOptions particle) {
        for (int i = 0; i < segments; i++) {
            double angle = Math.PI * 2.0 * i / segments;
            Vec3 point = center.add(Math.cos(angle) * radius,
                    Math.sin(angle * 2.0) * verticalJitter,
                    Math.sin(angle) * radius);
            emit(level, particle, point, 1, 0.015, 0.025, 0.015, 0.03);
        }
    }

    // Keeps visual effects server-authoritative so every nearby player sees the same action.
    private static void scheduleTechnique(ServerPlayer player,ServerLevel level,ElementalAbility a,Vec3 origin,Vec3 look){
        UUID id=player.getUUID();
        Vec3 right=look.cross(new Vec3(0,1,0)).normalize();
        if(right.lengthSqr()<0.001)right=new Vec3(1,0,0);
        Vec3 up=right.cross(look).normalize();
        Vec3 hands=origin.add(look.scale(0.38)).add(0,-0.38,0);
        final Vec3 rightBasis=right;
        final Vec3 upBasis=up;
        List<ScheduledCue> cues=new ArrayList<>();

        // Five distinct stages: gather, shape, travelling release, impact, and dissipate.
        cues.add(new ScheduledCue(0,()->{
            emitElement(level,a.element(),hands,32,0.34,0.36,0.34,0.045);
            emit(level,ParticleTypes.ENCHANT,hands,8,0.22,0.22,0.22,0.07);
        }));
        cues.add(new ScheduledCue(3,()->{
            ring(level,a.element(),hands,0.62,28);
            emitElement(level,a.element(),origin.add(0,-0.45,0),16,0.42,0.12,0.42,0.025);
            if (a == ElementalAbility.AIR_PUSH) airDebris(level, player.position(), 3.5, 34, 0.24);
            if (a == ElementalAbility.AIR_JUMP) airDebris(level, player.position(), 1.9, 28, 0.62);
            if (a == ElementalAbility.AIR_SHIELD) airDebris(level, player.position(), 1.55, 22, 0.28);
        }));
        // Terrain formations rise in nine timed stages instead of appearing as one instant block.
        for (int structureStep = 0; structureStep < 9; structureStep++) {
            final int stage = structureStep;
            cues.add(new ScheduledCue(6 + stage * 2, () -> {
                placeEnvironmentalStructure(player, level, a, origin, look, rightBasis, stage);
                if (stage == 0) {
                    emitElement(level, a.element(), origin.add(look.scale(0.75)),
                            18, 0.26, 0.28, 0.26, 0.035);
                }
            }));
        }
        for(int segment=1;segment<=9;segment++){
            final int step=segment;
            cues.add(new ScheduledCue(6+step*2,()->renderTechniqueSegment(
                    level,a,origin,look,rightBasis,upBasis,step)));
        }
        cues.add(new ScheduledCue(27,()->{
            Vec3 impactPoint=origin.add(look.scale(Math.min(a.range()*0.78,7.5)));
            impact(level,a,impactPoint);
            debris(level,impactPoint,a.element(),24);
            emitElement(level,a.element(),impactPoint,20,0.72,0.48,0.72,0.055);
        }));
        cues.add(new ScheduledCue(34,()->{
            Vec3 end=origin.add(look.scale(Math.min(a.range()*0.78,7.5)));
            emit(level,ParticleTypes.CLOUD,end,12,0.45,0.28,0.45,0.015);
            emitElement(level,a.element(),end,10,0.38,0.3,0.38,0.012);
        }));

        int startTick=player.tickCount;
        List<ScheduledCue> timed=new ArrayList<>();
        for(ScheduledCue cue:cues)timed.add(new ScheduledCue(startTick+cue.tick(),cue.action()));
        ANIMATIONS.put(id,timed);
    }

    /** Emit one moving slice of a technique per server tick pair, not the whole trail in one frame. */
    private static void renderTechniqueSegment(ServerLevel level,ElementalAbility ability,Vec3 origin,
                                               Vec3 forward,Vec3 right,Vec3 up,int segment){
        double t=segment/9.0D;
        Vec3 start=origin.add(forward.scale(0.45)).add(0,-0.28,0);
        switch(ability.id()){
            case "flame_projectile","water_projectile","air_blast","rock_throw" -> {
                Vec3 point=start.add(forward.scale(0.35+ t*6.2));
                emitElement(level,ability.element(),point,22,0.22,0.22,0.22,0.045);
                emit(level,ParticleTypes.CRIT,point,6,0.15,0.15,0.15,0.035);
                emitElement(level,ability.element(),point.subtract(forward.scale(0.35)),10,0.14,0.14,0.14,0.018);
            }
            case "flame_burst","fire_dragon","great_wave","seismic_wave","earthquake" -> {
                double distance=0.55+t*5.8;
                double width=0.22+t*(ability.id().equals("great_wave")||ability.id().equals("earthquake")?2.05:0.78);
                Vec3 center=start.add(forward.scale(distance)).add(0,-0.42,0);
                for(int lane=-4;lane<=4;lane++){
                    Vec3 point=center.add(right.scale(lane*width/4.0D))
                            .add(0,Math.sin(t*Math.PI*2.0D+lane*0.4D)*0.22,0);
                    emitElement(level,ability.element(),point,5,0.13,0.16,0.13,0.028);
                }
                emit(level,ParticleTypes.POOF,center,8,0.35,0.16,0.35,0.035);
            }
            case "fire_whip","water_whip","ice_blades" -> {
                double angle=-1.38+t*2.76;
                Vec3 point=start.add(right.scale(Math.sin(angle)*2.65))
                        .add(forward.scale(0.75+Math.cos(angle)*1.45))
                        .add(up.scale(Math.sin(t*Math.PI)*0.72));
                emitElement(level,ability.element(),point,18,0.13,0.13,0.13,0.035);
                emit(level,ParticleTypes.CRIT,point,5,0.08,0.08,0.08,0.025);
            }
            case "focused_explosion","ice_bind","rock_cage" -> {
                Vec3 center=origin.add(forward.scale(3.0));
                double angle=t*Math.PI*5.0D;
                double radius=0.5+0.35*Math.sin(t*Math.PI);
                Vec3 point=center.add(Math.cos(angle)*radius,-1.0+t*2.1,Math.sin(angle)*radius);
                emitElement(level,ability.element(),point,16,0.14,0.18,0.14,0.03);
                if(segment%2==0)ringAt(level,ability.element(),center.add(0,-0.8+t*1.7,0),0.75,28);
            }
            case "fire_propulsion","air_dash","air_jump" -> {
                Vec3 point=origin.subtract(forward.scale(0.25+t*2.35)).add(0,-0.55+t*0.24,0);
                emitElement(level,ability.element(),point,20,0.3,0.28,0.3,0.045);
                ring(level,ability.element(),point,0.55+t*0.22,28);
                if (ability == ElementalAbility.AIR_DASH && segment % 3 == 0)
                    airDebris(level, point, 1.35, 15, 0.22);
                if (ability == ElementalAbility.AIR_JUMP && segment == 1)
                    airDebris(level, origin, 1.5, 24, 0.6);
            }
            case "water_shield","air_shield","stone_armor" -> {
                double angle=t*Math.PI*2.0D;
                Vec3 point=origin.add(right.scale(Math.cos(angle)*1.15))
                        .add(0,-0.75+t*1.5,0).add(forward.scale(Math.sin(angle)*0.55));
                emitElement(level,ability.element(),point,16,0.13,0.16,0.13,0.022);
                ringAt(level,ability.element(),origin.add(0,-0.65+t*1.3,0),1.15,32);
            }
            case "stone_wall" -> {
                Vec3 wall=origin.add(forward.scale(2.8)).add(0,-0.65+t*2.65,0);
                for(int lane=-3;lane<=3;lane++){
                    Vec3 point=wall.add(right.scale(lane*0.42));
                    emit(level,ParticleTypes.POOF,point,7,0.14,0.16,0.14,0.025);
                    debris(level,point,Element.STONE,4);
                }
            }
            case "air_push" -> {
                double radius=0.35+t*3.0;
                ring(level,Element.GALE,origin.add(forward.scale(0.6)),radius,56);
                Vec3 center=origin.add(forward.scale(0.6));
                emit(level,ParticleTypes.CLOUD,center,18,radius*0.35,0.3,radius*0.35,0.055);
            }
            case "cyclone" -> {
                double angle=t*Math.PI*5.0D;
                double radius=1.85-t*1.25;
                Vec3 center=origin.add(forward.scale(3.0));
                Vec3 point=center.add(Math.cos(angle)*radius,-1.1+t*4.1,Math.sin(angle)*radius);
                emit(level,ParticleTypes.CLOUD,point,18,0.24,0.28,0.24,0.055);
                emitElement(level,Element.GALE,point,10,0.16,0.18,0.16,0.04);
                if (segment % 3 == 0) airDebris(level, center.add(0,-0.65,0), 2.25, 18, 0.72);
            }
            default -> {
                Vec3 point=start.add(forward.scale(t*5.0));
                emitElement(level,ability.element(),point,18,0.22,0.22,0.22,0.035);
            }
        }
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
                for(int radius=1;radius<=5;radius++)ring(l,Element.GALE,o,radius*0.48,48);
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
            case GALE -> {
                emit(l,ParticleTypes.CLOUD,p,42,0.8,0.65,0.8,0.12);
                emit(l,ParticleTypes.POOF,p,20,0.55,0.4,0.55,0.07);
                if (a == ElementalAbility.AIR_BLAST) airDebris(l,p,1.35,26,0.28);
            }
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
    private static void airDebris(ServerLevel level, Vec3 center, double radius, int count, double lift) {
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / count;
            double distance = radius * Math.sqrt((i + 0.5) / count);
            double x = center.x + Math.cos(angle) * distance;
            double z = center.z + Math.sin(angle) * distance;
            double y = center.y - 0.28 + ((i % 5) * 0.035);
            Vec3 particle = new Vec3(x, y, z);

            BlockPos ground = BlockPos.containing(x, y, z);
            if (level.getBlockState(ground).isAir()) ground = ground.below();
            if (level.getBlockState(ground).isAir()) ground = ground.below();
            BlockState groundState = level.getBlockState(ground);
            if (!groundState.isAir()) {
                emit(level, new BlockParticleOption(ParticleTypes.BLOCK, groundState),
                        particle, 2, 0.10, lift, 0.10, 0.075);
            }
            emit(level, ParticleTypes.CLOUD, particle, 2, 0.12, lift * 0.55, 0.12, 0.045);
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
    private static void placeEnvironmentalStructure(ServerPlayer player, ServerLevel level,
                                                    ElementalAbility ability, Vec3 origin,
                                                    Vec3 look, Vec3 right, int stage) {
        int baseY=player.blockPosition().getY();
        Vec3 center=player.position().add(look.scale(3.2));
        List<BlockPos> positions=new ArrayList<>();
        BlockState material=null;
        int lifetime=60;
        switch(ability){
            case STONE_WALL -> {
                material=Blocks.STONE.defaultBlockState();
                Vec3 wall=player.position().add(look.scale(3.0));
                for (int y = 0; y < 3; y++) {
                    for (int x = -3; x <= 3; x++) {
                        positions.add(BlockPos.containing(wall.add(right.scale(x * 0.9)).add(0, y, 0)));
                    }
                }
            }
            case ROCK_CAGE -> {
                material=Blocks.COBBLESTONE.defaultBlockState();
                for(int y=0;y<3;y++)for(int side=-1;side<=1;side++){
                    positions.add(BlockPos.containing(center.add(right.scale(side*0.9)).add(0,y-0.9,0)));
                    if(side!=0)positions.add(BlockPos.containing(center.add(look.scale(side*0.9)).add(0,y-0.9,0)));
                }
            }
            case ICE_BIND -> {
                material=Blocks.PACKED_ICE.defaultBlockState();
                for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=0;y<3;y++)
                    positions.add(BlockPos.containing(center.add(right.scale(x*0.85)).add(0,y-0.9,0).add(look.scale(z*0.85))));
            }
            case SEISMIC_WAVE,EARTHQUAKE -> {
                material=Blocks.COBBLESTONE.defaultBlockState();
                lifetime=40;
                for(int step=1;step<=6;step++){
                    Vec3 spike=player.position().add(look.scale(step*1.05));
                    positions.add(BlockPos.containing(spike.add(0,-0.2,0)));
                    if(ability==ElementalAbility.EARTHQUAKE){
                        positions.add(BlockPos.containing(spike.add(right.scale(0.65)).add(0,-0.2,0)));
                        positions.add(BlockPos.containing(spike.subtract(right.scale(0.65)).add(0,-0.2,0)));
                    }
                }
            }
            default -> { return; }
        }
        List<TemporaryBlock> placedBlocks = TEMPORARY_BLOCKS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
        int from = positions.size() * stage / 9;
        int to = positions.size() * (stage + 1) / 9;
        for (int index = from; index < to; index++) {
            BlockPos pos = positions.get(index);
            if (!level.isEmptyBlock(pos) || !level.getBlockState(pos.below()).isSolid()) continue;
            BlockState previous = level.getBlockState(pos);
            if (level.setBlock(pos, material, 3)) {
                placedBlocks.add(new TemporaryBlock(level, pos, previous, material,
                        level.getGameTime() + lifetime));
            }
        }
    }

    public static void tick(ServerPlayer p){
        UUID id=p.getUUID();
        List<ScheduledCue> cues=ANIMATIONS.get(id);
        if(cues!=null){
            if(!p.isAlive())ANIMATIONS.remove(id);
            else{
                Iterator<ScheduledCue> it=cues.iterator();
                while(it.hasNext()){ScheduledCue cue=it.next();if(p.tickCount>=cue.tick()){cue.action().run();it.remove();}}
                if(cues.isEmpty())ANIMATIONS.remove(id);
            }
        }
        Iterator<Map.Entry<UUID,List<TemporaryBlock>>> owners=TEMPORARY_BLOCKS.entrySet().iterator();
        while(owners.hasNext()){
            List<TemporaryBlock> blocks=owners.next().getValue();
            Iterator<TemporaryBlock> it=blocks.iterator();
            while(it.hasNext()){
                TemporaryBlock block=it.next();
                if(block.level().getGameTime()>=block.restoreGameTime()){
                    if(block.level().getBlockState(block.pos()).equals(block.placed()))
                        block.level().setBlock(block.pos(),block.original(),3);
                    it.remove();
                }
            }
            if(blocks.isEmpty())owners.remove();
        }
    }
}