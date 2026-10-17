package com.theplumteam.multiplayerspawners.mixin;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.config.ModConfig;
import com.theplumteam.multiplayerspawners.network.NetworkHandler;
import com.theplumteam.multiplayerspawners.util.SpawnerAgeing;
import com.theplumteam.multiplayerspawners.util.SpawnerDataAccessor;
import com.theplumteam.multiplayerspawners.util.SpawnerStateManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * Stops a spawner's server tick while every player in its activation range has disabled it.
 *
 * <p>This hooks the block entity's ticker rather than {@link BaseSpawner#serverTick}: mods such as
 * Apothic Spawners replace the spawner logic with a subclass whose {@code serverTick} never calls
 * the vanilla one, but every spawner block entity still ticks through here.
 */
@Mixin(SpawnerBlockEntity.class)
public abstract class SpawnerTickMixin {

    @Inject(method = "serverTick", at = @At("HEAD"), cancellable = true)
    private static void multiplayerspawners$onServerTick(Level level, BlockPos pos, BlockState state, SpawnerBlockEntity blockEntity, CallbackInfo ci) {
        BaseSpawner spawner = blockEntity.getSpawner();
        if (!(spawner instanceof SpawnerDataAccessor accessor)) {
            return;
        }
        // Until the tick returns, a removal of this spawner may be Ageing Spawners ageing it out.
        SpawnerAgeing.startTick(level, pos);

        // Once a second per spawner (spread over the ticks), make it active again for the players
        // whose respawn time has passed, and tell the clients that see it.
        if (Math.floorMod(level.getGameTime() + pos.asLong(), 20L) == 0
                && SpawnerStateManager.expireDisabledPlayers(level, pos, accessor)) {
            Constants.debug("Spawner at {} is active again for some players; still disabled for {}", pos,
                    accessor.multiplayerspawners$getDisabledPlayers());
            boolean treatedAsVanilla = accessor.multiplayerspawners$isPlayerPlaced()
                    && ModConfig.SERVER.treatPlacedSpawnersAsVanilla.get();
            NetworkHandler.getInstance().sendSpawnerStateToTracking(level, pos,
                    Set.copyOf(accessor.multiplayerspawners$getDisabledPlayers()), treatedAsVanilla);
        }

        // A player-placed spawner treated as vanilla runs untouched.
        if (accessor.multiplayerspawners$isPlayerPlaced() && ModConfig.SERVER.treatPlacedSpawnersAsVanilla.get()) {
            return;
        }

        Set<String> disabledPlayers = accessor.multiplayerspawners$getDisabledPlayers();
        if (disabledPlayers.isEmpty()) {
            return;
        }

        // The players the spawner itself would count as near (the same test as vanilla's
        // isNearPlayer, with this spawner's own range). With none, the spawner's own logic decides.
        double range = ((BaseSpawnerAccessor) spawner).multiplayerspawners$getRequiredPlayerRange();
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        boolean anyNear = false;
        for (Player player : level.players()) {
            if (player.isSpectator() || !player.isAlive() || player.distanceToSqr(x, y, z) >= range * range) {
                continue;
            }
            if (!disabledPlayers.contains(player.getName().getString())) {
                // Someone near still has it active: let the spawner work.
                return;
            }
            anyNear = true;
        }

        if (anyNear) {
            Constants.debug("Spawner at {} tick cancelled because all nearby players have it disabled.", pos);
            SpawnerAgeing.endTick();
            ci.cancel();
        }
    }

    @Inject(method = "serverTick", at = @At("RETURN"))
    private static void multiplayerspawners$afterServerTick(Level level, BlockPos pos, BlockState state, SpawnerBlockEntity blockEntity, CallbackInfo ci) {
        SpawnerAgeing.endTick();
    }
}
