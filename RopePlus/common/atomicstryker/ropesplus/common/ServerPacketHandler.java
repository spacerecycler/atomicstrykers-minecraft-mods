package atomicstryker.ropesplus.common;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

import atomicstryker.ForgePacketWrapper;

import net.minecraft.src.EntityPlayer;
import net.minecraft.src.INetworkManager;
import net.minecraft.src.Packet250CustomPayload;
import cpw.mods.fml.common.network.IPacketHandler;
import cpw.mods.fml.common.network.Player;

public class ServerPacketHandler implements IPacketHandler
{

    @Override
    public void onPacketData(INetworkManager manager, Packet250CustomPayload packet, Player player)
    {
        DataInputStream data = new DataInputStream(new ByteArrayInputStream(packet.data));
        
        int packetID = ForgePacketWrapper.readPacketID(data);
        
        if (packetID == 1) // arrow slot selection from client
        {
            Class[] decodeAs = {Integer.class};
            Object[] packetReadout = ForgePacketWrapper.readPacketData(data, decodeAs);
            RopesPlusCore.setselectedSlot((EntityPlayer)player, (Integer) packetReadout[0]);
        }
    }

}
