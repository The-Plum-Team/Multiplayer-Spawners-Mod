package com.theplumteam.multiplayerspawners.platform;

import com.theplumteam.multiplayerspawners.platform.services.IPlatformHelper;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;

public class ForgePlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "Forge";
    }

    @Override
    public String getEnvironmentName() {
        return FMLLoader.getNaming();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }

    @Override
    public <T> T get(Class<T> clazz) {
        // This is a placeholder. You might need to implement actual service loading here.
        return null;
    }
}