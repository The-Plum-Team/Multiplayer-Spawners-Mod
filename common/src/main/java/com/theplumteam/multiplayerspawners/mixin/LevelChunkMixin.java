package com.theplumteam.multiplayerspawners.mixin;
import com.theplumteam.multiplayerspawners.util.SpawnerLocationCache;
import com.theplumteam.multiplayerspawners.util.TorchTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {
@Shadow public abstract Level getLevel();

@Inject(method = "setBlockState", at = @At("RETURN"))
private void multiplayerspawners$onSetBlockState(BlockPos pos, BlockState state, boolean isMoving, CallbackInfoReturnable<BlockState> cir) {
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