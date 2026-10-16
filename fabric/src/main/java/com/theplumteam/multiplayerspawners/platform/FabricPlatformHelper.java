package com.theplumteam.multiplayerspawners.platform;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.network.FabricNetworkHandler;
import com.theplumteam.multiplayerspawners.network.NetworkHandler;
import com.theplumteam.multiplayerspawners.platform.services.IPlatformHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.nio.file.Path;
import java.util.function.Supplier;

public class FabricPlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public <T extends Block> Supplier<T> registerBlock(String name, Supplier<T> block) {
        T registered = Registry.register(BuiltInRegistries.BLOCK, Constants.id(name), block.get());
        return () -> registered;
    }

    @Override
    public <T extends Item> Supplier<T> registerItem(String name, Supplier<T> item) {
        T registered = Registry.register(BuiltInRegistries.ITEM, Constants.id(name), item.get());
        return () -> registered;
    }

    @Override
    public NetworkHandler createNetworkHandler() {
        return new FabricNetworkHandler();
    }
}
