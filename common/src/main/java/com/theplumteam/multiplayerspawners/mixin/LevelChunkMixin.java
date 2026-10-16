package com.theplumteam.multiplayerspawners.mixin;

import com.theplumteam.multiplayerspawners.client.ClientSpawnerData;
import com.theplumteam.multiplayerspawners.block.SpawnerStateProperties;
import com.theplumteam.multiplayerspawners.util.SpawnerLocationCache;
import com.theplumteam.multiplayerspawners.util.TorchTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {

    @Shadow
    public abstract Level getLevel();

    //? if >=1.21.5 {
    @Shadow
    public abstract BlockState setBlockState(BlockPos pos, BlockState state, int flags);
    //?} else {
    /*@Shadow
    public abstract BlockState setBlockState(BlockPos pos, BlockState state, boolean flags);
    *///?}

    /**
     * ENFORCER: This injection acts as a firewall.
     * It intercepts incoming block state changes (from server packets or local logic).
     * If the server tries to set a spawner to "Active" (Custom Texture), but our client data
     * says it should be "Inactive" (Vanilla/Disabled), we FORCE the state to match our data.
     */
    @Inject(method = "setBlockState", at = @At("HEAD"), cancellable = true)
    //? if >=1.21.5 {
    private void multiplayerspawners$enforceVisualState(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> cir) {
    //?} else {
    /*private void multiplayerspawners$enforceVisualState(BlockPos pos, BlockState state, boolean flags, CallbackInfoReturnable<BlockState> cir) {
    *///?}
        // Only run on client side
        if (this.getLevel().isClientSide()) {
            // Check if we are dealing with a spawner block that has our property
            if (state.getBlock() instanceof SpawnerBlock && state.hasProperty(SpawnerStateProperties.MULTIPLAYER_ACTIVATED)) {

                boolean incomingActivated = state.getValue(SpawnerStateProperties.MULTIPLAYER_ACTIVATED);

                // Determine what the state SHOULD be based on client data
                boolean isVanillaTreated = ClientSpawnerData.isPlayerPlacedSpawner(pos);
                boolean isDisabled = ClientSpawnerData.isSpawnerDisabledForCurrentPlayer(pos);

                // LOGIC RECAP from ClientSpawnerData:
                // 1. If treated as Vanilla -> We use Vanilla texture (Activated = FALSE)
                // 2. If Disabled -> We use Vanilla texture (Activated = FALSE)
                // 3. If Active AND Not Vanilla -> We use Custom texture (Activated = TRUE)

                boolean shouldBeActivated = !isVanillaTreated && !isDisabled;

                // If the incoming state disagrees with our required state, we intercept and fix it.
                if (incomingActivated != shouldBeActivated) {

                    // Stop the original operation
                    cir.cancel();

                    // Apply the CORRECT state immediately
                    BlockState fixedState = state.setValue(SpawnerStateProperties.MULTIPLAYER_ACTIVATED, shouldBeActivated);

                    // Call the method again with the fixed state.
                    // Since fixedState matches the condition now, this recursive call will skip this 'if' block and proceed normally.
                    cir.setReturnValue(this.setBlockState(pos, fixedState, flags));
                }
            }
        }
    }

    @Inject(method = "setBlockState", at = @At("RETURN"))
    //? if >=1.21.5 {
    private void multiplayerspawners$onSetBlockState(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> cir) {
    //?} else {
    /*private void multiplayerspawners$onSetBlockState(BlockPos pos, BlockState state, boolean flags, CallbackInfoReturnable<BlockState> cir) {
    *///?}
        Level level = this.getLevel();
        if (level.isClientSide() || cir.getReturnValue() == null) {
            return;
        }

        BlockState oldState = cir.getReturnValue();

        // Torch tracking
        boolean wasTorch = oldState.is(Blocks.TORCH) || oldState.is(Blocks.WALL_TORCH);
        boolean isTorch = state.is(Blocks.TORCH) || state.is(Blocks.WALL_TORCH);

        if (!wasTorch && isTorch) {
            TorchTracker.addTorch(level, pos.immutable());
        } else if (wasTorch && !isTorch) {
            TorchTracker.removeTorch(level, pos.immutable());
        }

        // Spawner cache tracking
        boolean wasSpawner = oldState.is(Blocks.SPAWNER);
        boolean isSpawner = state.is(Blocks.SPAWNER);

        if (!wasSpawner && isSpawner) {
            SpawnerLocationCache.addSpawner(level.dimension(), pos.immutable());
        } else if (wasSpawner && !isSpawner) {
            SpawnerLocationCache.removeSpawner(level.dimension(), pos.immutable());
        }
    }
}
