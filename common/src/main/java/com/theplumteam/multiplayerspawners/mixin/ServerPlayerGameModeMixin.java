package com.theplumteam.multiplayerspawners.mixin;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.config.ModConfig;
import com.theplumteam.multiplayerspawners.network.NetworkHandler;
import com.theplumteam.multiplayerspawners.util.SpawnerDataAccessor;
import com.theplumteam.multiplayerspawners.util.SpawnerStateManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Set;

@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {

    @Shadow
    protected ServerPlayer player;

    @Unique
    private void multiplayerspawners$handleSpawnerInteraction(Level level, BlockPos pos, ServerPlayer player) {
        String playerName = player.getName().getString();

        // Disable the spawner for this player by writing to the Block Entity NBT.
        SpawnerStateManager.disableSpawnerForPlayer(level, pos, playerName);
        Constants.LOG.info("Spawner at {} has been permanently disabled by player {}", pos, playerName);

        // REMOVED: Server-side visual block state update
        // The client will handle visual updates based on network packets
        // This prevents the texture from changing for players who shouldn't see it disabled

        // Send immediate network update to the disabling player first
        multiplayerspawners$triggerImmediateClientFeedback(level, pos, player);

        // Send network update to other tracking players
        if (level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawnerBlockEntity) {
            SpawnerDataAccessor accessor = (SpawnerDataAccessor) spawnerBlockEntity.getSpawner();
            Set<String> disabledPlayers = accessor.multiplayerspawners$getDisabledPlayers();

            // Send to all players tracking this chunk (but the disabling player already got immediate feedback)
            NetworkHandler.getInstance().sendSpawnerStateToTracking(level, pos, disabledPlayers);
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
    private void multiplayerspawners$triggerImmediateClientFeedback(Level level, BlockPos pos, ServerPlayer player) {
        // For the disabling player specifically, send immediate feedback packet
        if (level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawnerBlockEntity) {
            SpawnerDataAccessor accessor = (SpawnerDataAccessor) spawnerBlockEntity.getSpawner();
            Set<String> disabledPlayers = accessor.multiplayerspawners$getDisabledPlayers();

            // Send immediate feedback to the player who disabled it
            Constants.LOG.info("SERVER: Sending immediate feedback packet to player {} for spawner at {}",
                    player.getName().getString(), pos);
            NetworkHandler.getInstance().sendSpawnerStateToPlayer(player, pos, disabledPlayers, false);
        }
    }

    @Unique
    private void multiplayerspawners$handleGroupDisabling(Level level, BlockPos spawnerPos, ServerPlayer triggeringPlayer, int radius) {
        AABB searchArea = new AABB(spawnerPos).inflate(radius);
        List<Player> nearbyPlayers = level.getEntitiesOfClass(Player.class, searchArea);

        int playersAffected = 0;

        for (Player nearbyPlayer : nearbyPlayers) {
            if (nearbyPlayer instanceof ServerPlayer serverPlayer && !nearbyPlayer.equals(triggeringPlayer)) {
                String nearbyPlayerName = serverPlayer.getName().getString();

                if (!SpawnerStateManager.isSpawnerDisabledForPlayer(level, spawnerPos, nearbyPlayerName)) {
                    SpawnerStateManager.disableSpawnerForPlayer(level, spawnerPos, nearbyPlayerName);
                    playersAffected++;
                    serverPlayer.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§6A spawner in this area has been disabled by " + triggeringPlayer.getName().getString() + "."),
                            false
                    );
                }
            }
        }

        // Send network update after group disabling (only if other players were affected)
        if (playersAffected > 0 && level.getBlockEntity(spawnerPos) instanceof SpawnerBlockEntity spawnerBlockEntity) {
            SpawnerDataAccessor accessor = (SpawnerDataAccessor) spawnerBlockEntity.getSpawner();
            Set<String> disabledPlayers = accessor.multiplayerspawners$getDisabledPlayers();

            // Send updated state to all tracking players (includes the triggering player and newly affected players)
            NetworkHandler.getInstance().sendSpawnerStateToTracking(level, spawnerPos, disabledPlayers);

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
            if (SpawnerStateManager.isSpawnerDisabledForPlayer(world, pos, this.player.getName().getString())) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cThis spawner is permanently disabled and cannot be broken!"), true);
                cir.setReturnValue(false);
                cir.cancel();
                return;
            }

            if (SpawnerStateManager.isSpawnerDisabledByAnyone(world, pos)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cThis spawner is permanently disabled and cannot be broken!"), true);
                SpawnerStateManager.disableSpawnerForPlayer(world, pos, this.player.getName().getString());

                // Send network update for this new player disabling (immediate feedback for this player)
                multiplayerspawners$triggerImmediateClientFeedback(world, pos, this.player);

                // Send to other tracking players
                if (world.getBlockEntity(pos) instanceof SpawnerBlockEntity spawnerBlockEntity) {
                    SpawnerDataAccessor accessor = (SpawnerDataAccessor) spawnerBlockEntity.getSpawner();
                    Set<String> disabledPlayers = accessor.multiplayerspawners$getDisabledPlayers();
                    NetworkHandler.getInstance().sendSpawnerStateToTracking(world, pos, disabledPlayers);
                }

                player.displayClientMessage(net.minecraft.network.chat.Component.literal("§cIt will no longer spawn creatures while you are nearby."), true);
                cir.setReturnValue(false);
                cir.cancel();
                return;
            }

            Constants.LOG.info("Player {} is disabling a spawner for the first time at {}. Intercepting.", player.getName().getString(), pos);
            multiplayerspawners$handleSpawnerInteraction(world, pos, this.player);

            cir.setReturnValue(false);
            cir.cancel();
        }
    }
}