package dev.elementallegacy.element;
import dev.elementallegacy.ability.ElementalAbility;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Context-sensitive strength for Tide techniques. */
public final class WaterAffinity {
    private static final int SEARCH_RADIUS=6;
    private WaterAffinity(){}
    public static float forAbility(Entity entity,String abilityId){
        if(entity==null)return 1.0F;
        ElementalAbility ability=ElementalAbility.byId(abilityId).orElse(null);
        if(ability==null||ability.element()!=Element.TIDE)return 1.0F;
        return fromEnvironment(entity);
    }
    public static float fromEnvironment(Entity entity){
        if(entity.isSwimming())return 1.90F;
        if(entity.isUnderWater())return 1.72F;
        if(entity.isInWater())return 1.52F;
        Level level=entity.level();
        BlockPos origin=entity.blockPosition();
        double nearestDistanceSqr=Double.MAX_VALUE;
        for(int x=-SEARCH_RADIUS;x<=SEARCH_RADIUS;x++)for(int y=-2;y<=2;y++)for(int z=-SEARCH_RADIUS;z<=SEARCH_RADIUS;z++){
            BlockPos pos=origin.offset(x,y,z);
            if(!level.hasChunkAt(pos))continue;
            if(!level.getBlockState(pos).getFluidState().is(FluidTags.WATER))continue;
            double dx=pos.getX()+0.5D-entity.getX();
            double dy=pos.getY()+0.5D-(entity.getY()+entity.getBbHeight()*0.5D);
            double dz=pos.getZ()+0.5D-entity.getZ();
            nearestDistanceSqr=Math.min(nearestDistanceSqr,dx*dx+dy*dy+dz*dz);
        }
        if(nearestDistanceSqr==Double.MAX_VALUE)return 1.0F;
        double proximity=Math.max(0.0D,1.0D-Math.sqrt(nearestDistanceSqr)/7.5D);
        return (float)(1.0D+0.40D*proximity);
    }
}
