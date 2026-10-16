package com.theplumteam.multiplayerspawners.client;

import com.theplumteam.multiplayerspawners.registration.ModBlocksItems;
//? if <26.1 {
import net.minecraft.client.renderer.ItemBlockRenderTypes;
//?}
//? if >=1.21.6 && <26.1 {
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
//?} else if <1.21.6 {
/*import net.minecraft.client.renderer.RenderType;
*///?}
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

public class MultiplayerSpawnersNeoForgeClient {

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(MultiplayerSpawnersNeoForgeClient::onClientSetup);
        NeoForge.EVENT_BUS.register(NeoForgeClientEvents.class);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        // The unlit torch textures have transparent pixels. From 26.1 the game derives the layer
        // from the texture itself.
        //? if >=1.21.6 && <26.1 {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ModBlocksItems.UNLIT_TORCH.get(), ChunkSectionLayer.CUTOUT);
            ItemBlockRenderTypes.setRenderLayer(ModBlocksItems.UNLIT_WALL_TORCH.get(), ChunkSectionLayer.CUTOUT);
        });
        //?} else if <1.21.6 {
        /*event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ModBlocksItems.UNLIT_TORCH.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocksItems.UNLIT_WALL_TORCH.get(), RenderType.cutout());
        });
        *///?}
    }
}
