package com.theplumteam.multiplayerspawners.mixin;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.block.SpawnerStateProperties;
import com.theplumteam.multiplayerspawners.config.ModConfig;
import com.theplumteam.multiplayerspawners.util.SpawnerStateManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {

    @Shadow
    protected ServerPlayer player;

    @Unique
    private void multiplayerspawners$handleSpawnerInteraction(Level level, BlockPos pos, ServerPlayer player) {
        String playerName = player.getName().getString();

        // This method is only called for spawners that are not yet "permanently disabled".
        // Disable the spawner for this player.
        SpawnerStateManager.disableSpawnerForPlayer(level, pos, playerName);
        Constants.LOG.info("Spawner at {} has been permanently disabled by player {}", pos, playerName);

        // Update the block state if it's not already in the disabled appearance.
        // This makes it so the first person to disable it changes the model for everyone.
        BlockState currentBlockState = level.getBlockState(pos);
        if (currentBlockState.hasProperty(SpawnerStateProperties.MULTIPLAYER_ACTIVATED) && currentBlockState.getValue(SpawnerStateProperties.MULTIPLAYER_ACTIVATED)) {
            BlockState newBlockState = currentBlockState.setValue(SpawnerStateProperties.MULTIPLAYER_ACTIVATED, false);
            level.setBlock(pos, newBlockState, 3); // Flag 3 updates the client
        }

        // Group disabling functionality
        if (ModConfig.SERVER.enableGroupDisabling.get()) {
            int radius = ModConfig.SERVER.groupDisablingRadius.get();
            multiplayerspawners$handleGroupDisabling(level, pos, player, radius);
        }

        // Send a message to the player
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cMultiplayer spawner permanently disabled! It can no longer be broken and will not spawn creatures while you are nearby."), true);
    }

    @Unique
    private void multiplayerspawners$handleGroupDisabling(Level level, BlockPos spawnerPos, ServerPlayer triggeringPlayer, int radius) {
        AABB searchArea = new AABB(spawnerPos).inflate(radius);
        List<Player> nearbyPlayers = level.getEntitiesOfClass(Player.class, searchArea);

        int playersAffected = 0;

        for (Player nearbyPlayer : nearbyPlayers) {
            if (nearbyPlayer instanceof ServerPlayer serverPlayer && !nearbyPlayer.equals(triggeringPlayer)) {
                String nearbyPlayerName = serverPlayer.getName().getString();

                // Check if spawner is already disabled for this nearby player to avoid redundant logic/messages
                if (!SpawnerStateManager.isSpawnerDisabledForPlayer(level, spawnerPos, nearbyPlayerName)) {
                    SpawnerStateManager.disableSpawnerForPlayer(level, spawnerPos, nearbyPlayerName);
                    playersAffected++;

                    // Notify the affected player
                    serverPlayer.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§6A spawner in this area has been disabled by " + triggeringPlayer.getName().getString() + "."),
                            false
                    );
                }
            }
        }

        if (playersAffected > 0) {
            triggeringPlayer.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§6Group disabling: Spawner disabled for " + playersAffected + " nearby player(s)."),
                    false
            );
            Constants.LOG.info("Group disabling: Spawner at {} disabled for {} other players.",
                    spawnerPos, playersAffected);
        }
    }

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void multiplayerspawners$onAttemptDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        Level world = this.player.serverLevel();
        BlockState blockState = world.getBlockState(pos);

        if (blockState.getBlock() instanceof SpawnerBlock) {
            // Check if this player has already disabled this spawner. If so, they just get the "unbreakable" message.
            if (SpawnerStateManager.isSpawnerDisabledForPlayer(world, pos, this.player.getName().getString())) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cThis spawner is permanently disabled and cannot be broken!"), true);
                cir.setReturnValue(false);
                cir.cancel();
                return;
            }

            // If the spawner has been disabled by ANY player, it becomes unbreakable for everyone.
            // This handles the case where Player B tries to break a spawner already disabled by Player A.
            if (SpawnerStateManager.isSpawnerDisabledByAnyone(world, pos)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cThis spawner is permanently disabled and cannot be broken!"), true);
                // We still want to disable it for THIS player too.
                SpawnerStateManager.disableSpawnerForPlayer(world, pos, this.player.getName().getString());
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cIt will no longer spawn creatures while you are nearby."), true);
                cir.setReturnValue(false);
                cir.cancel();
                return;
            }

            // If it's the first time anyone is interacting, handle the disabling process.
            Constants.LOG.info("Player {} is disabling a spawner for the first time at {}. Intercepting.", player.getName().getString(), pos);
            multiplayerspawners$handleSpawnerInteraction(world, pos, this.player);

            // Cancel the original block breaking event
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
}