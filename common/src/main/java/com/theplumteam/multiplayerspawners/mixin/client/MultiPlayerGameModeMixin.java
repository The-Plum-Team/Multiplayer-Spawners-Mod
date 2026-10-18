package com.theplumteam.multiplayerspawners.mixin.client;

import com.theplumteam.multiplayerspawners.client.ClientSpawnerData;
import com.theplumteam.multiplayerspawners.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
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
 * The player still sees and hears the block break, as vanilla's prediction would show it; the server
 * shows it to the players around.
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
            this.minecraft.level.levelEvent(this.minecraft.player, Constants.BREAK_EFFECTS, pos,
                    Block.getId(this.minecraft.level.getBlockState(pos)));
            cir.setReturnValue(false);
        }
    }
}
