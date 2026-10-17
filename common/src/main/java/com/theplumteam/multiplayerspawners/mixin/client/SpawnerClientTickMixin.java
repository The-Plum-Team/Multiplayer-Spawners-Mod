package com.theplumteam.multiplayerspawners.mixin.client;

import com.theplumteam.multiplayerspawners.client.ClientSpawnerData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides a spawner's particles and spinning while it is disabled for the current player.
 *
 * <p>Like {@code SpawnerTickMixin}, this hooks the block entity's ticker, which every spawner
 * implementation goes through, rather than {@code BaseSpawner#clientTick}, which mods such as
 * Apothic Spawners replace.
 */
@Mixin(SpawnerBlockEntity.class)
public abstract class SpawnerClientTickMixin {

    @Inject(method = "clientTick", at = @At("HEAD"), cancellable = true)
    private static void multiplayerspawners$onClientTick(Level level, BlockPos pos, BlockState state, SpawnerBlockEntity blockEntity, CallbackInfo ci) {
        // Player-placed spawners treated as vanilla keep their particles.
        if (!ClientSpawnerData.isPlayerPlacedSpawner(pos) && ClientSpawnerData.isSpawnerDisabledForCurrentPlayer(pos)) {
            ci.cancel();
        }
    }
}
