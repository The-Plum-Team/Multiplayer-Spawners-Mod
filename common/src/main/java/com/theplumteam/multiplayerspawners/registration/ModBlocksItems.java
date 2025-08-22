package com.theplumteam.multiplayerspawners.registration;

import com.theplumteam.multiplayerspawners.block.UnlitTorchBlock;
import com.theplumteam.multiplayerspawners.block.UnlitWallTorchBlock;
import com.theplumteam.multiplayerspawners.platform.Services;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

public class ModBlocksItems {

    // Move the registration fields from Constants to here
    public static final Supplier<Block> UNLIT_TORCH = Services.PLATFORM.registerBlock("unlit_torch", UnlitTorchBlock::new);
    public static final Supplier<Block> UNLIT_WALL_TORCH = Services.PLATFORM.registerBlock("unlit_wall_torch", UnlitWallTorchBlock::new);
    public static final Supplier<Item> UNLIT_TORCH_ITEM = Services.PLATFORM.registerItem("unlit_torch", () -> new StandingAndWallBlockItem(UNLIT_TORCH.get(), UNLIT_WALL_TORCH.get(), new Item.Properties(), Direction.DOWN));

    /**
     * This method is called by CommonClass.init() to ensure this class is loaded and the static final fields
     * are initialized, which triggers the registration process at the correct time.
     */
    public static void init() {
        // The static initializers do all the work. This method just ensures the class is loaded.
    }
}