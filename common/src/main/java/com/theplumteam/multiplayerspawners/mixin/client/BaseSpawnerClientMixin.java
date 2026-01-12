package com.theplumteam.multiplayerspawners.mixin.client;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.client.ClientSpawnerData;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BaseSpawner.class)
public class BaseSpawnerClientMixin {

    /**
     * Cancels client-side particle effects and animations when the spawner is disabled for the current player.
     * Player-placed spawners are NOT affected - they always show normal effects.
     */
    @Inject(method = "clientTick", at = @At("HEAD"), cancellable = true)
    private void multiplayerspawners$onClientTick(Level level, BlockPos pos, CallbackInfo ci) {
        // Only run on client side
        if (!level.isClientSide()) {
            return;
        }

        // Player-placed spawners should always show particles (vanilla behavior)
        if (ClientSpawnerData.isPlayerPlacedSpawner(pos)) {
            return; // Let vanilla behavior proceed
        }

        // Check if this spawner is disabled for the current player
        if (ClientSpawnerData.isSpawnerDisabledForCurrentPlayer(pos)) {
            // Cancel the client tick to prevent particles and animations
            Constants.LOG.debug("CLIENT: Cancelling spawner particles and animations at {} - spawner is disabled for current player", pos);
            ci.cancel();
        }
        // If not disabled, let the normal client tick proceed (particles will show)
    }

    /**
     * Prevents the mob inside the spawner from being rendered when the spawner is disabled for the current player.
     * The renderer calls this method to get the entity to display. By returning null, we render nothing.
     * Player-placed spawners are NOT affected - they always show the mob.
     */
    @Inject(method = "getOrCreateDisplayEntity", at = @At("HEAD"), cancellable = true)
    private void multiplayerspawners$hideDisplayEntity(Level level, RandomSource random, BlockPos pos, CallbackInfoReturnable<Entity> cir) {
        // This method is only used on the client for rendering, but checking is good practice.
        if (level.isClientSide()) {
            // Player-placed spawners should always show the mob (vanilla behavior)
            if (ClientSpawnerData.isPlayerPlacedSpawner(pos)) {
                return; // Let vanilla behavior proceed
            }

            if (ClientSpawnerData.isSpawnerDisabledForCurrentPlayer(pos)) {
                // Cancel the original method and return null, so the renderer has no entity to draw.
                cir.setReturnValue(null);
            }
        }
    }
}