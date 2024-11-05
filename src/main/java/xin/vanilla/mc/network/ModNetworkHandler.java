package xin.vanilla.mc.network;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import xin.vanilla.mc.SakuraSignIn;

public class ModNetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    private static int ID = 0;
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SakuraSignIn.MODID, "main_network"),
            () -> PROTOCOL_VERSION,
            clientVersion -> true,      // 客户端版本始终有效
            serverVersion -> true       // 服务端版本始终有效
    );

    public static int nextID() {
        return ID++;
    }

    public static void registerPackets() {
        INSTANCE.registerMessage(nextID(), PlayerDataSyncPacket.class, PlayerDataSyncPacket::toBytes, PlayerDataSyncPacket::new, PlayerDataSyncPacket::handle);
        INSTANCE.registerMessage(nextID(), ServerConfigSyncPacket.class, ServerConfigSyncPacket::toBytes, ServerConfigSyncPacket::new, ServerConfigSyncPacket::handle);
        INSTANCE.registerMessage(nextID(), ClientConfigSyncPacket.class, ClientConfigSyncPacket::toBytes, ClientConfigSyncPacket::new, ClientConfigSyncPacket::handle);
        INSTANCE.registerMessage(nextID(), SignInDataSyncPacket.class, SignInDataSyncPacket::toBytes, SignInDataSyncPacket::new, SignInDataSyncPacket::handle);
        INSTANCE.registerMessage(nextID(), ItemStackPacket.class, ItemStackPacket::toBytes, ItemStackPacket::new, ItemStackPacket::handle);
        INSTANCE.registerMessage(nextID(), SignInPacket.class, SignInPacket::toBytes, SignInPacket::new, SignInPacket::handle);
    }
}
