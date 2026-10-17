package com.theplumteam.multiplayerspawners.mixin;

import com.theplumteam.multiplayerspawners.util.SpawnerAgeing;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets {@link SpawnerAgeing} keep a spawner that Ageing Spawners is about to remove during its tick.
 */
@Mixin(Level.class)
public abstract class LevelMixin {

    @Inject(method = "removeBlock", at = @At("HEAD"), cancellable = true)
    private void multiplayerspawners$onRemoveBlock(BlockPos pos, boolean isMoving, CallbackInfoReturnable<Boolean> cir) {
        if (SpawnerAgeing.interceptRemoval((Level) (Object) this, pos)) {
            cir.setReturnValue(true);
        }
    }
}
