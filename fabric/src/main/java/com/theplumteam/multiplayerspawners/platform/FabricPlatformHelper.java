package com.theplumteam.multiplayerspawners.platform;

import com.theplumteam.multiplayerspawners.platform.services.IPlatformHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;

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
        // This is a placeholder. You might need to implement actual service loading here.
        return null;
    }
}