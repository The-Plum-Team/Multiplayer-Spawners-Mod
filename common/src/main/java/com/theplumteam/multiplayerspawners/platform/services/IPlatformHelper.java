package com.theplumteam.multiplayerspawners.platform.services;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public interface IPlatformHelper {

    String getPlatformName();

    String getEnvironmentName();

    boolean isModLoaded(String modId);

    boolean isDevelopmentEnvironment();

    <T> T get  (Class<T> clazz);
}