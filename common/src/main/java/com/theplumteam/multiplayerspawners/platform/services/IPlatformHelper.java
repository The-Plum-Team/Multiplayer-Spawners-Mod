package com.theplumteam.multiplayerspawners.platform.services;

import com.theplumteam.multiplayerspawners.network.NetworkHandler;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.nio.file.Path;
import java.util.function.Supplier;

public interface IPlatformHelper {

    String getPlatformName();

    boolean isModLoaded(String modId);

    boolean isDevelopmentEnvironment();

    /**
     * The loader's global config directory (the instance's {@code config/}).
     */
    Path getConfigDir();

    <T extends Block> Supplier<T> registerBlock(String name, Supplier<T> block);

    <T extends Item> Supplier<T> registerItem(String name, Supplier<T> item);

    NetworkHandler createNetworkHandler();
}
