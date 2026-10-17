package com.theplumteam.multiplayerspawners.registration;

import com.theplumteam.multiplayerspawners.block.UnlitTorchBlock;
import com.theplumteam.multiplayerspawners.block.UnlitWallTorchBlock;
import com.theplumteam.multiplayerspawners.platform.Services;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
//? if >=1.21.2 {
import com.theplumteam.multiplayerspawners.Constants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
//?}

import java.util.function.Supplier;

public class ModBlocksItems {

    public static final Supplier<Block> UNLIT_TORCH = Services.PLATFORM.registerBlock("unlit_torch",
            () -> new UnlitTorchBlock(torchProperties("unlit_torch")));
    public static final Supplier<Block> UNLIT_WALL_TORCH = Services.PLATFORM.registerBlock("unlit_wall_torch",
            () -> new UnlitWallTorchBlock(torchProperties("unlit_wall_torch")));
    public static final Supplier<Item> UNLIT_TORCH_ITEM = Services.PLATFORM.registerItem("unlit_torch",
            //? if >=1.21.2 {
            () -> new StandingAndWallBlockItem(UNLIT_TORCH.get(), UNLIT_WALL_TORCH.get(), Direction.DOWN, itemProperties("unlit_torch")));
            //?} else {
            /*() -> new StandingAndWallBlockItem(UNLIT_TORCH.get(), UNLIT_WALL_TORCH.get(), itemProperties("unlit_torch"), Direction.DOWN));
            *///?}

    /**
     * How Quark's monster box looks to a player it is used up for (the colours of the vanilla
     * spawner, as a disabled spawner looks). Only the client ever places it, in place of the box.
     */
    public static final Supplier<Block> USED_MONSTER_BOX = Services.PLATFORM.registerBlock("used_monster_box",
            () -> new Block(usedMonsterBoxProperties()));

    /**
     * Called by CommonClass.init() so that the static fields above, and with them the
     * registrations, run at the right time.
     */
    public static void init() {
    }

    private static BlockBehaviour.Properties torchProperties(String name) {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                //? if >=1.21.9 {
                .noCollision()
                //?} else {
                /*.noCollission()
                *///?}
                .instabreak()
                .lightLevel(state -> 0)
                .pushReaction(PushReaction.DESTROY);
        //? if >=1.21.2 {
        // From 1.21.2 a block knows its own registry key from construction.
        properties = properties.setId(ResourceKey.create(Registries.BLOCK, Constants.id(name)));
        //?}
        return properties;
    }

    /** As Quark's monster box, so that mining it looks the same on the client. */
    private static BlockBehaviour.Properties usedMonsterBoxProperties() {
        BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
                .strength(25F)
                .sound(SoundType.METAL)
                .noOcclusion();
        //? if >=1.21.2 {
        properties = properties.setId(ResourceKey.create(Registries.BLOCK, Constants.id("used_monster_box")));
        //?}
        return properties;
    }

    private static Item.Properties itemProperties(String name) {
        Item.Properties properties = new Item.Properties();
        //? if >=1.21.2 {
        properties = properties.setId(ResourceKey.create(Registries.ITEM, Constants.id(name))).useBlockDescriptionPrefix();
        //?}
        return properties;
    }
}
