package com.theplumteam.multiplayerspawners.mixin;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.config.ModConfig;
import com.theplumteam.multiplayerspawners.util.SpawnerDataAccessor;
import com.theplumteam.multiplayerspawners.util.SpawnerStateManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BaseSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(BaseSpawner.class)
public abstract class BaseSpawnerMixin {

    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private void multiplayerspawners$onServerTick(ServerLevel level, BlockPos pos, CallbackInfo ci) {

        // Check if this spawner is player-placed and config allows vanilla behavior
        if (ModConfig.SERVER.treatPlacedSpawnersAsVanilla.get()) {
            // Cast this BaseSpawner to our accessor
            if ((Object) this instanceof SpawnerDataAccessor accessor) {
                if (accessor.multiplayerspawners$isPlayerPlaced()) {
                    // It's player placed and treated as vanilla.
                    // Simply return, allowing the vanilla 'serverTick' method to proceed without interruption.
                    return;
                }
            }
        }

        List<Player> nearbyPlayers = level.getEntitiesOfClass(Player.class,
                new net.minecraft.world.phys.AABB(pos).inflate(16.0)); // 16 block radius, same as spawner range

        if (nearbyPlayers.isEmpty()) {
            // No players nearby, vanilla logic will handle deactivation
            return;
        }

        // Check if there is at least one player for whom the spawner should be active
        boolean hasActivePlayer = false;
        for (Player player : nearbyPlayers) {
            if (!SpawnerStateManager.isSpawnerDisabledForPlayer(level, pos, player.getName().getString())) {
                hasActivePlayer = true;
                break;
            }
        }

        // If there are no active players nearby, cancel the spawner tick
        if (!hasActivePlayer) {
            Constants.debug("Spawner at {} tick cancelled because all nearby players have it disabled.", pos);
            ci.cancel();
        }
        // If hasActivePlayer is true, we do NOT cancel, letting vanilla logic proceed

        // NOTE: Visual state is now handled client-side through the network sync
        // Each player will see their own texture based on ClientSpawnerData
    }
}