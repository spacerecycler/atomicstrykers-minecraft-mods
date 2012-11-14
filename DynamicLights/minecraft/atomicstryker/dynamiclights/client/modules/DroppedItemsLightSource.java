package atomicstryker.dynamiclights.client.modules;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.src.Entity;
import net.minecraft.src.EntityItem;
import net.minecraft.src.ItemStack;
import net.minecraftforge.common.Configuration;
import net.minecraftforge.common.Property;
import atomicstryker.dynamiclights.client.DynamicLights;
import atomicstryker.dynamiclights.client.IDynamicLightSource;
import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.ITickHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.Init;
import cpw.mods.fml.common.Mod.PreInit;
import cpw.mods.fml.common.Side;
import cpw.mods.fml.common.TickType;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.TickRegistry;

/**
 * 
 * @author AtomicStryker
 *
 * Offers Dynamic Light functionality to EntityItem instances.
 * Dropped Torches and such can give off Light through this Module.
 *
 */
@Mod(modid = "DynamicLights_dropItems", name = "Dynamic Lights on ItemEntities", version = "1.0.1", dependencies = "after:DynamicLights")
public class DroppedItemsLightSource
{
    private Minecraft mcinstance;
    private long nextUpdate;
    private long updateInterval;
    private ArrayList<EntityItemAdapter> trackedItems;
    private Thread thread;
    private boolean threadRunning;
    
    private HashMap<Integer, Integer> itemsMap;
    
    @PreInit
    public void preInit(FMLPreInitializationEvent evt)
    {
        itemsMap = new HashMap<Integer, Integer>();
        Configuration config = new Configuration(evt.getSuggestedConfigurationFile());
        config.load();
        
        Property itemsList = config.get(config.CATEGORY_GENERAL, "LightItems", "50:15,89:12,348:10,91:15,327:15,76:10,331:10,314:14");
        itemsList.comment = "Item IDs that shine light when dropped in the World. Syntax: ItemID:LightValue, seperated by commas";
        
        Property updateI = config.get(config.CATEGORY_GENERAL, "update Interval", 1000);
        updateI.comment = "Update Interval time for all Item entities in milliseconds. The lower the better and costlier.";
        updateInterval = updateI.getInt();
        
        String[] tokens = itemsList.value.split(",");
        for (String pair : tokens)
        {
            String[] values = pair.split(":");
            int id = Integer.valueOf(values[0]);
            int value = Integer.valueOf(values[1]);
            itemsMap.put(id, value);
        }
        
        config.save();
    }
    
    @Init
    public void load(FMLInitializationEvent evt)
    {
        mcinstance = FMLClientHandler.instance().getClient();
        nextUpdate = System.currentTimeMillis();
        trackedItems = new ArrayList<EntityItemAdapter>();
        threadRunning = false;
        TickRegistry.registerTickHandler(new TickHandler(), Side.CLIENT);
    }
    
    private class TickHandler implements ITickHandler
    {
        private final EnumSet ticks;
        public TickHandler()
        {
            ticks = EnumSet.of(TickType.CLIENT);
        }

        @Override
        public void tickStart(EnumSet<TickType> type, Object... tickData)
        {
        }

        @Override
        public void tickEnd(EnumSet<TickType> type, Object... tickData)
        {
            if (mcinstance.theWorld != null && System.currentTimeMillis() > nextUpdate && !DynamicLights.globalLightsOff())
            {
                nextUpdate = System.currentTimeMillis() + updateInterval;
                
                if (!threadRunning)
                {
                    thread = new EntityListChecker(mcinstance.theWorld.loadedEntityList);
                    thread.setPriority(Thread.MIN_PRIORITY);
                    thread.start();
                    threadRunning = true;
                }
            }
        }

        @Override
        public EnumSet<TickType> ticks()
        {
            return ticks;
        }

        @Override
        public String getLabel()
        {
            return "DynamicLights_dropItems";
        }
    }
    
    private int getLightFromItemStack(ItemStack stack)
    {
        if (stack != null)
        {
            Integer i = itemsMap.get(stack.itemID);
            if (i != null)
            {
                return i;
            }
        }
        return 0;
    }
    
    private class EntityListChecker extends Thread
    {
        private final Object[] list;
        
        public EntityListChecker(List<Entity> input)
        {
            list = input.toArray();
        }
        
        @Override
        public void run()
        {
            ArrayList<EntityItemAdapter> newList = new ArrayList<EntityItemAdapter>();
            
            Entity ent;
            for (Object o : list)
            {
                ent = (Entity) o;
                // Loop all loaded Entities, find alive and valid ItemEntities
                if (ent instanceof EntityItem && ent.isEntityAlive())
                {
                    // now find them in the already tracked item adapters
                    boolean found = false;
                    Iterator<EntityItemAdapter> iter = trackedItems.iterator();
                    EntityItemAdapter adapter = null;
                    while (iter.hasNext())
                    {
                        adapter = iter.next();
                        if (adapter.getAttachmentEntity().equals(ent)) // already tracked!
                        {
                            adapter.onTick(); // execute a tick
                            newList.add(adapter); // put them in the new list
                            found = true;
                            iter.remove(); // remove them from the old
                            break;
                        }
                    }
                    
                    if (!found) // wasnt already tracked
                    {
                        // make new, tick, put in new list
                        adapter = new EntityItemAdapter((EntityItem) ent);
                        adapter.onTick();
                        newList.add(adapter);
                    }
                }
            }
            // any remaining adapters were not in the loaded entities. The main Dynamic Lights mod will kill them.
            trackedItems = newList;
            threadRunning = false;
        }
        
    }
    
    private class EntityItemAdapter implements IDynamicLightSource
    {
        
        private EntityItem entity;
        private int lightLevel;
        private boolean enabled;
        
        public EntityItemAdapter(EntityItem eI)
        {
            lightLevel = 0;
            enabled = false;
            entity = eI;
        }
        
        /**
         * Since they are IDynamicLightSource instances, they will already receive updates! Why do we need
         * to do this? Because seperate Thread!
         */
        public void onTick()
        {
            if (entity.isBurning())
            {
                lightLevel = 15;
            }
            else
            {
                lightLevel = getLightFromItemStack(entity.item);
            }
            
            if (!enabled && lightLevel > 8)
            {
                enableLight();
            }
            else if (enabled && lightLevel < 9)
            {
                disableLight();
            }
        }
        
        private void enableLight()
        {
            DynamicLights.addLightSource(this);
            enabled = true;
        }
        
        private void disableLight()
        {
            DynamicLights.removeLightSource(this);
            enabled = false;
        }
     
        @Override
        public Entity getAttachmentEntity()
        {
            return entity;
        }

        @Override
        public int getLightLevel()
        {
            return lightLevel;
        }
    }

}
