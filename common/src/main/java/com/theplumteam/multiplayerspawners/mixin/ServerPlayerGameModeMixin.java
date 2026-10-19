// ServerPlayerGameModeMixin.java
package com.theplumteam.multiplayerspawners.mixin;

import com.theplumteam.multiplayerspawners.util.SpawnerBreaking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A player mining a block: {@link SpawnerBreaking#hit} decides what a spawner does. */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {

    @Shadow
    protected ServerPlayer player;

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void multiplayerspawners$onAttemptDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (SpawnerBreaking.hit(this.player, this.player.level(), pos, true)) {
            // The server keeps the block (the client did not predict its break).
            cir.setReturnValue(false);
            return;
        }
        // The block break event this fires is the player's own: SpawnerBreaking.onModBreak lets it be.
        SpawnerBreaking.enterGameModeBreak(this.player, pos);
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void multiplayerspawners$afterDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        SpawnerBreaking.leaveGameModeBreak();
    }
}
