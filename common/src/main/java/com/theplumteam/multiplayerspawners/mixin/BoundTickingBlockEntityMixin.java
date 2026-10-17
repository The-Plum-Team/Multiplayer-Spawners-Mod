package com.theplumteam.multiplayerspawners.mixin;

import com.theplumteam.multiplayerspawners.util.OneShotSpawners;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wraps every block entity's ticker for {@link OneShotSpawners}: a single-use spawner does not tick
 * while every player near it has used it up, and its tick is recorded so that a removal of the
 * block during it can be caught.
 */
@Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
public abstract class BoundTickingBlockEntityMixin {

    @Shadow
    @Final
    private BlockEntity blockEntity;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void multiplayerspawners$beforeTick(CallbackInfo ci) {
        if (!OneShotSpawners.beforeTick(this.blockEntity)) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void multiplayerspawners$afterTick(CallbackInfo ci) {
        OneShotSpawners.afterTick();
    }
}
