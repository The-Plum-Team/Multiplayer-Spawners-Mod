package com.theplumteam.multiplayerspawners.platform;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.config.FabricConfigRegistry;
import com.theplumteam.multiplayerspawners.network.FabricNetworkHandler;
import com.theplumteam.multiplayerspawners.network.NetworkHandler;
import com.theplumteam.multiplayerspawners.platform.services.IPlatformHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

public class FabricPlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public String getEnvironmentName() {
        return FabricLoader.getInstance().getEnvironmentType().name();
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
    public <T> T get(Class<T> clazz) {
        return null;
    }

    @Override
    public <T extends Block> Supplier<T> registerBlock(String name, Supplier<T> block) {
        T registeredBlock = Registry.register(BuiltInRegistries.BLOCK, new ResourceLocation(Constants.MOD_ID, name), block.get());
        Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(Constants.MOD_ID, name),
                new BlockItem(registeredBlock, new Item.Properties()));
        return () -> registeredBlock;
    }

    @Override
    public <T extends Item> Supplier<T> registerItem(String name, Supplier<T> item) {
        T registeredItem = Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(Constants.MOD_ID, name), item.get());
        return () -> registeredItem;
    }

    @Override
    public void initializeConfig() {
        FabricConfigRegistry.register();
    }
    
    @Override
    public NetworkHandler createNetworkHandler() {
        return new FabricNetworkHandler();
    }
}
