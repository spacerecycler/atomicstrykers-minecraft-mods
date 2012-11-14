package atomicstryker.kenshiro.common;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import net.minecraft.src.Block;
import net.minecraft.src.DamageSource;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityCreeper;
import net.minecraft.src.EntityLiving;
import net.minecraft.src.EntityPlayer;
import net.minecraft.src.EntitySkeleton;
import net.minecraft.src.MathHelper;
import net.minecraft.src.Packet250CustomPayload;
import net.minecraft.src.Packet53BlockChange;
import net.minecraft.src.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeSubscribe;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import atomicstryker.ForgePacketWrapper;
import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.common.network.PacketDispatcher;
import cpw.mods.fml.common.network.Player;

public class KenshiroServer
{
    private static KenshiroServer instance;
    private Set<Player> hasKenshiroSet;
    private Map<Player, Set<EntityLiving>> punchedEntitiesMap;
    
    public KenshiroServer()
    {
        instance = this;
        hasKenshiroSet = new HashSet<Player>();
        punchedEntitiesMap = new HashMap<Player, Set<EntityLiving>>();
        MinecraftForge.EVENT_BUS.register(this);
    }
    
    public static KenshiroServer instance()
    {
        return instance;
    }
    
    public boolean getHasClientKenshiroInstalled(Player player)
    {
    	return hasKenshiroSet.contains(player);
    }

	public void setClientHasKenshiroInstalled(Player player, boolean value)
	{
		if (!value)
		{
			hasKenshiroSet.remove(player);
		}
		else
		{
			hasKenshiroSet.add(player);
		}
	}

    public void onClientPunchedBlock(EntityPlayer player, int x, int y, int z)
    {
        int blockID = player.worldObj.getBlockId(x, y, z);
        Block block = Block.blocksList[blockID];
        PacketDispatcher.sendPacketToAllAround(x, y, z, 30D, player.worldObj.getWorldInfo().getDimension(), new Packet53BlockChange(x, y, z, player.worldObj));
        
        if (block != null)
        {
            int meta = player.worldObj.getBlockMetadata(x, y, z);
            if (block.removeBlockByPlayer(player.worldObj, player, x, y, z))
            {
                block.onBlockDestroyedByPlayer(player.worldObj, x, y, z, meta);
                block.harvestBlock(player.worldObj, player, x, y, z, meta);
            }
        }
    }

    public void onClientPunchedEntity(Player player, World world, int entityID)
    {
        Entity target = KenshiroMod.instance().getEntityByID(world, entityID);
        if (target != null
        && target instanceof EntityLiving)
        {
            EntityLiving targetEnt = (EntityLiving) target;
            
            KenshiroMod.instance().debuffEntityLiving(targetEnt);
            
            if (targetEnt.getHealth() > 7)
            {
                targetEnt.attackEntityFrom(DamageSource.causePlayerDamage((EntityPlayer) player), 7);
            }
            else
            {
                punchedEntitiesMap.get(player).add((EntityLiving) target);
            }
            
            Object[] toSend = {entityID};
            Packet250CustomPayload packetNew = ForgePacketWrapper.createPacket("AS_KSM", PacketType.ENTITYPUNCHED.ordinal(), toSend);
            PacketDispatcher.sendPacketToAllAround(target.posX, target.posY, target.posZ, 30D, world.getWorldInfo().getDimension(), packetNew);
        }
        
        if (target instanceof EntityCreeper)
        {
            KenshiroMod.instance().stopCreeperExplosion((EntityCreeper) target);
        }
        else if (target instanceof EntitySkeleton)
        {
            KenshiroMod.instance().stopSkeletonShooter((EntitySkeleton) target);
        }
    }

    public void onClientKickedEntity(EntityPlayer player, EntityLiving target)
    {
        player.addExhaustion(40F);
        target.attackEntityFrom(DamageSource.causePlayerDamage(player), 4);
        
        double var9 = player.posX - target.posX;
        double var7;
        for(var7 = player.posZ - target.posZ; var9 * var9 + var7 * var7 < 1.0E-4D; var7 = (Math.random() - Math.random()) * 0.01D)
        {
           var9 = (Math.random() - Math.random()) * 0.01D;
        }
        //((EntityLiving) mc.objectMouseOver.entityHit).knockBack(entPlayer, 10, var9, var7);
        
        float quad = MathHelper.sqrt_double(var9-var9 + var7*var7);
        target.addVelocity((var9 / (double)quad)*-1, 0.6, (var9 / (double)quad)*-1*-1);
        
        target.setFire(8);
        
        Object[] toSend = {player.entityId, target.entityId};
        Packet250CustomPayload packetNew = ForgePacketWrapper.createPacket("AS_KSM", PacketType.ENTITYKICKED.ordinal(), toSend);
        PacketDispatcher.sendPacketToAllAround(target.posX, target.posY, target.posZ, 30D, player.worldObj.getWorldInfo().getDimension(), packetNew);
    }

    public void onClientUnleashedKenshiroVolley(EntityPlayer playerEnt)
    {
        playerEnt.addExhaustion(40F);
        playerEnt.addExhaustion(40F);
        playerEnt.addExhaustion(40F);
        
        punchedEntitiesMap.put((Player) playerEnt, new HashSet());
    }

    public void onClientFinishedKenshiroVolley(EntityPlayer playerEnt)
    {
        Iterator<EntityLiving> iter = punchedEntitiesMap.get((Player)playerEnt).iterator();
        while (iter.hasNext())
        {
            EntityLiving target = iter.next();
            target.attackEntityFrom(DamageSource.causePlayerDamage(playerEnt), 21);
        }
        punchedEntitiesMap.remove(playerEnt);
    }
    
    @ForgeSubscribe
    public void onEntityLivingAttacked(LivingAttackEvent event)
    {
        if (event.source.getEntity() != null
        && !(event.source.getEntity() instanceof EntityPlayer))
        {
            for (Player p : punchedEntitiesMap.keySet())
            {
                if (punchedEntitiesMap.get(p).contains(event.source.getEntity()))
                {
                    event.setCanceled(true);
                }
            }
        }
    }
    
    private class ServerTickHandler implements ITickHandler
    {
        private final EnumSet tickTypes;
        public ServerTickHandler()
        {
            tickTypes = EnumSet.of(TickType.WORLD);
        }
        
        @Override
        public void tickStart(EnumSet<TickType> type, Object... tickData)
        {
        }
        
        @Override
        public void tickEnd(EnumSet<TickType> type, Object... tickData)
        {
            for (Player p : punchedEntitiesMap.keySet())
            {
                for (EntityLiving e : punchedEntitiesMap.get(p))
                {
                    if (e instanceof EntityCreeper)
                    {
                        KenshiroMod.instance().stopCreeperExplosion((EntityCreeper) e);
                    }
                }
            }
        }
        
        @Override
        public EnumSet<TickType> ticks()
        {
            return tickTypes;
        }
        
        @Override
        public String getLabel()
        {
            return "KenshiroMod";
        }
    }
}
