package com.theplumteam.multiplayerspawners.mixin;

import com.theplumteam.multiplayerspawners.util.OneShotSpawners;
import com.theplumteam.multiplayerspawners.util.SpawnerAgeing;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets {@link SpawnerAgeing} keep a spawner that Ageing Spawners ({@code removeBlock}) or Spawner
 * Control ({@code destroyBlock}, before it drops anything) is about to remove during its tick, and
 * {@link OneShotSpawners} keep a single-use spawner that removes itself during its tick (every
 * removal, {@code removeBlock}, {@code destroyBlock} or {@code setBlock} with another block, ends in
 * the four-argument {@code setBlock}).
 */
@Mixin(Level.class)
public abstract class LevelMixin {

    @Inject(method = "removeBlock", at = @At("HEAD"), cancellable = true)
    private void multiplayerspawners$onRemoveBlock(BlockPos pos, boolean isMoving, CallbackInfoReturnable<Boolean> cir) {
        if (SpawnerAgeing.interceptRemoval((Level) (Object) this, pos)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z", at = @At("HEAD"), cancellable = true)
    private void multiplayerspawners$onDestroyBlock(BlockPos pos, boolean dropBlock, Entity entity, int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
        if (SpawnerAgeing.interceptRemoval((Level) (Object) this, pos)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"), cancellable = true)
    private void multiplayerspawners$onSetBlock(BlockPos pos, BlockState state, int flags, int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
        if (OneShotSpawners.interceptRemoval((Level) (Object) this, pos, state)) {
            cir.setReturnValue(false);
        }
    }
}
