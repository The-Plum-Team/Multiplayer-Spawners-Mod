package com.theplumteam.multiplayerspawners.mixin.client;

import com.theplumteam.multiplayerspawners.client.ClientSpawnerData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Does not predict the break of a block the server keeps when this player mines it: a spawner the
 * mod disables instead of breaking, or a single-use spawner it uses up. The client would otherwise
 * remove the block at once and get it back from the server a moment later, so it flickered (and
 * lost its block entity's data until the next update). The break request still goes to the server.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void multiplayerspawners$keepBlockTheServerKeeps(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (this.minecraft.level != null && this.minecraft.player != null
                && ClientSpawnerData.serverKeepsWhenMined(this.minecraft.level, pos, this.minecraft.player)) {
            cir.setReturnValue(false);
        }
    }
}
