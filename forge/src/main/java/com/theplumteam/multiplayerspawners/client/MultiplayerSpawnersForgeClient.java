package com.theplumteam.multiplayerspawners.client;

import com.theplumteam.multiplayerspawners.registration.ModBlocksItems;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class MultiplayerSpawnersForgeClient {

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(MultiplayerSpawnersForgeClient::onClientSetup);
        MinecraftForge.EVENT_BUS.register(ForgeClientEvents.class);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        // The unlit torch textures have transparent pixels.
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ModBlocksItems.UNLIT_TORCH.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocksItems.UNLIT_WALL_TORCH.get(), RenderType.cutout());
        });
    }
}
