package com.theplumteam.multiplayerspawners.client;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.network.FabricNetworkHandler;
import com.theplumteam.multiplayerspawners.registration.ModBlocksItems;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.renderer.RenderType;

public class MultiplayerSpawnersFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Set the render layer for our transparent blocks
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocksItems.UNLIT_TORCH.get(), RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocksItems.UNLIT_WALL_TORCH.get(), RenderType.cutout());

        // Initialize client-side network handler
        FabricNetworkHandler.initClient();

        // Register disconnect event to clear client data
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            Constants.LOG.info("CLIENT: Fabric disconnect event");
            ClientEventHandler.onClientDisconnect();
        });

        // Register join event to clear old data
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            Constants.LOG.info("CLIENT: Fabric join event");
            ClientEventHandler.onClientConnect();
        });

        // Register client tick events for dimension detection and periodic updates
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientEventHandler.onClientTick();
        });

        // Register chunk loading events
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            Constants.LOG.debug("CLIENT: Fabric chunk load event for {}, {}", chunk.getPos().x, chunk.getPos().z);
            ClientEventHandler.onChunkLoad(chunk);
        });

        // Register chunk unloading events
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            Constants.LOG.debug("CLIENT: Fabric chunk unload event for {}, {}", chunk.getPos().x, chunk.getPos().z);
            ClientEventHandler.onChunkUnload(chunk);
        });

        // Register player respawn event
        ClientPlayConnectionEvents.PLAYER_RESPAWN.register((oldPlayer, newPlayer, client) -> {
            Constants.LOG.info("CLIENT: Fabric player respawn event");
            ClientEventHandler.onPlayerRespawn();
        });

        Constants.LOG.info("MultiplayerSpawners Fabric enhanced client initialized");
    }
}