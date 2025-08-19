package com.theplumteam.multiplayerspawners;

import net.minecraftforge.fml.common.Mod;
import org.spongepowered.asm.mixin.Mixins;

@Mod(Constants.MOD_ID)
public class MultiplayerSpawnersForge {

    public MultiplayerSpawnersForge() {

        // This method is invoked by the Forge mod loader when it is ready
        // to load your mod. You can access Forge and Common code in this
        // project.

        // Use Forge to bootstrap the Common mod.
        Constants.LOG.info("Hello Forge world! MultiplayerSpawnersForge has been loaded!");
        CommonClass.init();

    }
}