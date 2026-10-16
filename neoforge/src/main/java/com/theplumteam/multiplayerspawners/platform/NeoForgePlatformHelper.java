package com.theplumteam.multiplayerspawners.platform;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.network.NeoForgeNetworkHandler;
import com.theplumteam.multiplayerspawners.network.NetworkHandler;
import com.theplumteam.multiplayerspawners.platform.services.IPlatformHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.nio.file.Path;
import java.util.function.Supplier;

public class NeoForgePlatformHelper implements IPlatformHelper {

    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, Constants.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, Constants.MOD_ID);

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }

    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        //? if >=1.21.9 {
        /*return !FMLLoader.getCurrent().isProduction();
        *///?} else {
        return !FMLLoader.isProduction();
        //?}
    }

    @Override
    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public <T extends Block> Supplier<T> registerBlock(String name, Supplier<T> block) {
        return BLOCKS.register(name, block);
    }

    @Override
    public <T extends Item> Supplier<T> registerItem(String name, Supplier<T> item) {
        return ITEMS.register(name, item);
    }

    @Override
    public NetworkHandler createNetworkHandler() {
        return new NeoForgeNetworkHandler();
    }
}
