package com.theplumteam.multiplayerspawners.mixin;

import com.theplumteam.multiplayerspawners.util.SpawnerBreaking;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Explosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
//? if >=1.21.2 {
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?} else {
/*import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

import java.util.List;

/**
 * Hands {@link SpawnerBreaking#onExplosion} the blocks an explosion is about to destroy, once it
 * has collected them and before it destroys any or tells the clients (from 1.21.2 the server's
 * explosion is ServerExplosion).
 */
//? if >=1.21.2 {
@Mixin(ServerExplosion.class)
//?} else {
/*@Mixin(Explosion.class)
*///?}
public abstract class ExplosionMixin {

    //? if >=1.21.2 {
    @Shadow
    @Final
    private ServerLevel level;

    @Inject(method = "calculateExplodedPositions", at = @At("RETURN"))
    private void multiplayerspawners$onBlocksCollected(CallbackInfoReturnable<List<BlockPos>> cir) {
        SpawnerBreaking.onExplosion(this.level, (Explosion) (Object) this, cir.getReturnValue());
    }
    //?} else {
    /*@Shadow
    @Final
    private Level level;

    @Inject(method = "explode", at = @At("RETURN"))
    private void multiplayerspawners$onBlocksCollected(CallbackInfo ci) {
        Explosion explosion = (Explosion) (Object) this;
        SpawnerBreaking.onExplosion(this.level, explosion, explosion.getToBlow());
    }
    *///?}
}
