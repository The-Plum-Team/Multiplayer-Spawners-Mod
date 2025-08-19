package com.theplumteam.multiplayerspawners.platform.services;

import com.theplumteam.multiplayerspawners.util.SpawnerPersistentData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

public interface IPlatformHelper {

    String getPlatformName();

    String getEnvironmentName();

    boolean isModLoaded(String modId);

    boolean isDevelopmentEnvironment();

    <T> T get(Class<T> clazz);

    <T extends Block> Supplier<T> registerBlock(String name, Supplier<T> block);

    <T extends Item> Supplier<T> registerItem(String name, Supplier<T> item);

    SpawnerPersistentData getOrCreateSpawnerData(MinecraftServer server);

    // NEW: Config initialization method
    void initializeConfig();
}