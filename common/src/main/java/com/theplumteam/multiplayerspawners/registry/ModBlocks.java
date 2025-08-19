package com.theplumteam.multiplayerspawners.registry;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.block.MultiplayerSpawnerBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class ModBlocks {

    // Register the custom spawner block
    public static final Block MULTIPLAYER_SPAWNER = registerBlock("multiplayer_spawner",
            new MultiplayerSpawnerBlock(BlockBehaviour.Properties.copy(Blocks.SPAWNER)
                    .strength(5.0f, 6.0f)
                    .requiresCorrectToolForDrops()));

    private static Block registerBlock(String name, Block block) {
        registerBlockItem(name, block);
        return Registry.register(BuiltInRegistries.BLOCK, new ResourceLocation(Constants.MOD_ID, name), block);
    }

    private static Item registerBlockItem(String name, Block block) {
        return Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(Constants.MOD_ID, name),
                new BlockItem(block, new Item.Properties()));
    }

    public static void initialize() {
        Constants.LOG.info("Registering blocks for " + Constants.MOD_ID);
    }
}