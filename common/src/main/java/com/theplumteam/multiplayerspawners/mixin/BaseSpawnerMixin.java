package com.theplumteam.multiplayerspawners.mixin;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.util.SpawnerStateManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BaseSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(BaseSpawner.class)
public class BaseSpawnerMixin {

    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private void multiplayerspawners$onServerTick(ServerLevel level, BlockPos pos, CallbackInfo ci) {
        // Check if there are any players nearby who have disabled this spawner
        List<Player> nearbyPlayers = level.getEntitiesOfClass(Player.class,
                new net.minecraft.world.phys.AABB(pos).inflate(16.0)); // 16 block radius, same as spawner range

        if (nearbyPlayers.isEmpty()) {
            return; // No players to check against, let vanilla logic proceed.
        }

        for (Player player : nearbyPlayers) {
            if (player instanceof ServerPlayer) {
                // Use the utility class to check if spawner is disabled for this player
                if (SpawnerStateManager.isSpawnerDisabledForPlayer(level, pos, player.getName().getString())) {
                    Constants.LOG.debug("Spawner at {} tick cancelled because disabled player {} is nearby", pos, player.getName().getString());
                    ci.cancel(); // Cancel the spawning tick
                    return;
                }
            }
        }
    }
}