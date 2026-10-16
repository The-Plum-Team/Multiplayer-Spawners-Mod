package com.theplumteam.multiplayerspawners.client;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.network.FabricNetworkHandler;
import com.theplumteam.multiplayerspawners.registration.ModBlocksItems;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
//? if >=1.21.6 && <26.1 {
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
//?} else if <1.21.6 {
/*import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.RenderType;
*///?}

public class MultiplayerSpawnersFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // The unlit torch textures have transparent pixels. From 26.1 the game derives the layer
        // from the texture itself.
        //? if >=1.21.6 && <26.1 {
        BlockRenderLayerMap.putBlocks(ChunkSectionLayer.CUTOUT, ModBlocksItems.UNLIT_TORCH.get(), ModBlocksItems.UNLIT_WALL_TORCH.get());
        //?} else if <1.21.6 {
        /*BlockRenderLayerMap.INSTANCE.putBlock(ModBlocksItems.UNLIT_TORCH.get(), RenderType.cutout());
        BlockRenderLayerMap.INSTANCE.putBlock(ModBlocksItems.UNLIT_WALL_TORCH.get(), RenderType.cutout());
        *///?}

        FabricNetworkHandler.initClient();

        // Leaving a world clears the client data. Joining must not: the first packets of the new
        // world can arrive before the join event.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientEventHandler.onClientDisconnect());

        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientEventHandler.onClientTick());
        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> ClientEventHandler.onChunkLoad(chunk));
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> ClientEventHandler.onChunkUnload(chunk));

        Constants.debug("MultiplayerSpawners Fabric client initialized");
    }
}
