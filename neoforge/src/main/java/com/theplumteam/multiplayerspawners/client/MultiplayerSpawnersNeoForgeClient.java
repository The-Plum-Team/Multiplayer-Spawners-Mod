package com.theplumteam.multiplayerspawners.client;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.registration.ModBlocksItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

@OnlyIn(Dist.CLIENT)
public class MultiplayerSpawnersNeoForgeClient {

    public static void init(IEventBus modEventBus) {
        Constants.LOG.info("Initializing MultiplayerSpawners NeoForge client");

        // Register client setup event
        modEventBus.addListener(MultiplayerSpawnersNeoForgeClient::onClientSetup);

        // Register client events on the NeoForge event bus
        NeoForge.EVENT_BUS.register(NeoForgeClientEvents.class);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        // Set the render layer for our transparent blocks
        // Note: In 1.21, this is typically done via model JSON or render type registration
        // ItemBlockRenderTypes is deprecated but still works
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ModBlocksItems.UNLIT_TORCH.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocksItems.UNLIT_WALL_TORCH.get(), RenderType.cutout());
        });

        Constants.LOG.info("MultiplayerSpawners NeoForge client setup complete");
    }

    /**
     * Force a block to re-render by marking it for update
     */
    public static void markBlockForRerender(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.levelRenderer != null) {
            // Mark the block for re-rendering
            mc.levelRenderer.setBlocksDirty(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
        }
    }
}
