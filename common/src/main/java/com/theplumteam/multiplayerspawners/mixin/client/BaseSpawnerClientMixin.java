package com.theplumteam.multiplayerspawners.mixin.client;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.client.ClientSpawnerData;
import net.minecraft.core.BlockPos;
//? if <1.21 {
/*import net.minecraft.util.RandomSource;
*///?}
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;

@Mixin(BaseSpawner.class)
public class BaseSpawnerClientMixin {

    /**
     * Tracks spawners that were previously disabled, to only log on state change.
     */
    @Unique
    private static final Set<BlockPos> multiplayerspawners$previouslyDisabled = new HashSet<>();

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
        boolean isDisabled = ClientSpawnerData.isSpawnerDisabledForCurrentPlayer(pos);
        boolean wasDisabled = multiplayerspawners$previouslyDisabled.contains(pos);

        if (isDisabled) {
            // Log only on state change to prevent spam
            if (!wasDisabled) {
                multiplayerspawners$previouslyDisabled.add(pos);
                Constants.debug("CLIENT MIXIN: Spawner at {} is now disabled for current player - hiding particles", pos);
            }
            // Cancel the client tick to prevent particles and animations
            ci.cancel();
        } else if (wasDisabled) {
            // Spawner was re-enabled, log the state change
            multiplayerspawners$previouslyDisabled.remove(pos);
            Constants.debug("CLIENT MIXIN: Spawner at {} is now enabled for current player - showing particles", pos);
        }
        // If not disabled, let the normal client tick proceed (particles will show)
    }

    /**
     * Prevents the mob inside the spawner from being rendered when the spawner is disabled for the current player.
     * The renderer calls this method to get the entity to display. By returning null, we render nothing.
     * Player-placed spawners are NOT affected - they always show the mob.
     */
    @Inject(method = "getOrCreateDisplayEntity", at = @At("HEAD"), cancellable = true)
    //? if >=1.21 {
    private void multiplayerspawners$hideDisplayEntity(Level level, BlockPos pos, CallbackInfoReturnable<Entity> cir) {
    //?} else {
    /*private void multiplayerspawners$hideDisplayEntity(Level level, RandomSource random, BlockPos pos, CallbackInfoReturnable<Entity> cir) {
    *///?}
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