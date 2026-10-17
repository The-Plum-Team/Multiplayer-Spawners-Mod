package com.theplumteam.multiplayerspawners.mixin.client;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.client.ClientSpawnerData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.Set;

/**
 * Hides a spawner's particles and spinning while it is disabled for the current player.
 *
 * <p>Like {@code SpawnerTickMixin}, this hooks the block entity's ticker, which every spawner
 * implementation goes through, rather than {@code BaseSpawner#clientTick}, which mods such as
 * Apothic Spawners replace.
 */
@Mixin(SpawnerBlockEntity.class)
public abstract class SpawnerClientTickMixin {

    /**
     * Tracks spawners that were previously disabled, to only log on state change.
     */
    @Unique
    private static final Set<BlockPos> multiplayerspawners$previouslyDisabled = new HashSet<>();

    @Inject(method = "clientTick", at = @At("HEAD"), cancellable = true)
    private static void multiplayerspawners$onClientTick(Level level, BlockPos pos, BlockState state, SpawnerBlockEntity blockEntity, CallbackInfo ci) {
        // Player-placed spawners should always show particles (vanilla behavior)
        if (ClientSpawnerData.isPlayerPlacedSpawner(pos)) {
            return;
        }

        boolean isDisabled = ClientSpawnerData.isSpawnerDisabledForCurrentPlayer(pos);
        boolean wasDisabled = multiplayerspawners$previouslyDisabled.contains(pos);

        if (isDisabled) {
            if (!wasDisabled) {
                multiplayerspawners$previouslyDisabled.add(pos);
                Constants.debug("CLIENT MIXIN: Spawner at {} is now disabled for current player - hiding particles", pos);
            }
            ci.cancel();
        } else if (wasDisabled) {
            multiplayerspawners$previouslyDisabled.remove(pos);
            Constants.debug("CLIENT MIXIN: Spawner at {} is now enabled for current player - showing particles", pos);
        }
    }
}
