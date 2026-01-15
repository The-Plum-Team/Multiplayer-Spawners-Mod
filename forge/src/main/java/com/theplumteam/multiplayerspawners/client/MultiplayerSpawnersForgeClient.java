package com.theplumteam.multiplayerspawners.client;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.registration.ModBlocksItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@OnlyIn(Dist.CLIENT)
public class MultiplayerSpawnersForgeClient {

    public static void init() {
        Constants.debug("Initializing MultiplayerSpawners Forge client");

        // Register client setup event
        FMLJavaModLoadingContext.get().getModEventBus().addListener(MultiplayerSpawnersForgeClient::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        // Set the render layer for our transparent blocks
        ItemBlockRenderTypes.setRenderLayer(ModBlocksItems.UNLIT_TORCH.get(), RenderType.cutout());
        ItemBlockRenderTypes.setRenderLayer(ModBlocksItems.UNLIT_WALL_TORCH.get(), RenderType.cutout());

        Constants.debug("MultiplayerSpawners Forge client setup complete");
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