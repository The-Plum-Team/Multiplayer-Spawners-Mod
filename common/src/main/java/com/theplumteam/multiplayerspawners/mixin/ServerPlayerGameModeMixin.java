package com.theplumteam.multiplayerspawners.mixin;

import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.config.ModConfig;
import com.theplumteam.multiplayerspawners.network.NetworkHandler;
import com.theplumteam.multiplayerspawners.util.SpawnerDataAccessor;
import com.theplumteam.multiplayerspawners.util.SpawnerStateManager;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
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
    private static final String DUNGEON_PICKAXE_ID = "composite_material:dungeon_pickaxe";

    @Unique
    private boolean multiplayerspawners$isDungeonPickaxe(ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return false;
        }

        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(itemStack.getItem());
        return DUNGEON_PICKAXE_ID.equals(itemId.toString());
    }

    @Unique
    private void multiplayerspawners$dropEmptySpawner(Level level, BlockPos pos, ServerPlayer player) {
        // Create a spawner item stack
        ItemStack spawnerItem = new ItemStack(Blocks.SPAWNER);

        // Create the item entity and drop it at the spawner location
        ItemEntity itemEntity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, spawnerItem);
        itemEntity.setDefaultPickUpDelay();
        level.addFreshEntity(itemEntity);

        Constants.LOG.info("Dropped empty spawner for player {} at {} using dungeon pickaxe",
                player.getName().getString(), pos);
    }

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

            // Force a block entity update to sync NBT data
            level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
        }

        // Group disabling functionality
        if (ModConfig.SERVER.enableGroupDisabling.get()) {
            int radius = ModConfig.SERVER.groupDisablingRadius.get();
            multiplayerspawners$handleGroupDisabling(level, pos, player, radius);
        }

        // No message when disabling spawner - feedback is provided through drops
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
                            Component.literal("A spawner in this area has been disabled by " + triggeringPlayer.getName().getString() + ".").withStyle(ChatFormatting.GOLD),
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
                    Component.literal("Group disabling: Spawner disabled for " + playersAffected + " nearby player(s).").withStyle(ChatFormatting.GOLD),
                    false
            );
            Constants.LOG.info("Group disabling: Spawner at {} disabled for {} other players.",
                    spawnerPos, playersAffected);
        }
    }

    @Unique
    private void multiplayerspawners$forceVisualStateRefresh(Level level, BlockPos pos, ServerPlayer player) {
        if (level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawnerBlockEntity) {
            SpawnerDataAccessor accessor = (SpawnerDataAccessor) spawnerBlockEntity.getSpawner();
            Set<String> disabledPlayers = accessor.multiplayerspawners$getDisabledPlayers();

            // Send immediate visual state refresh packet with high priority
            Constants.debug("Sending forced visual refresh for cancelled break at {} to player {}", pos, player.getName().getString());
            NetworkHandler.getInstance().sendSpawnerStateToPlayer(player, pos, disabledPlayers, true);

            // Explicitly reset the client's breaking progress
            player.connection.send(new ClientboundBlockUpdatePacket(pos, level.getBlockState(pos)));
        }
    }

    @Unique
    private void multiplayerspawners$dropSpawnerRewards(Level level, BlockPos pos, ServerPlayer player) {
        BlockState blockState = level.getBlockState(pos);
        if (blockState.getBlock() instanceof SpawnerBlock) {
            // Drop experience like a normal spawner break
            blockState.getBlock().playerWillDestroy(level, pos, blockState, player);

            // Drop any additional items that other mods might add to spawner drops
            // This calls the block's dropResources method which handles mod compatibility
            blockState.getBlock().dropResources(blockState, level, pos, level.getBlockEntity(pos), player, player.getMainHandItem());
        }
    }

    @Unique
    private void multiplayerspawners$sendClickableBreakMessage(ServerPlayer player, BlockPos pos, boolean includeBreakLink) {
        // Create the explanatory text
        MutableComponent explanationMessage = Component.literal("The spawner is disabled for you and nearby players. Other players will have it active so they can enjoy the dungeon.").withStyle(ChatFormatting.LIGHT_PURPLE);

        if (includeBreakLink) {
            // Add space before clickable part
            explanationMessage.append(Component.literal(" "));

            // Create the clickable part
            MutableComponent clickablePart = Component.literal("[Click here to break it]")
                    .withStyle(style -> style
                            .withColor(ChatFormatting.RED)
                            .withBold(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                                    "/multiplayerspawners allow_break " + pos.getX() + " " + pos.getY() + " " + pos.getZ()))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("Click to break this spawner").withStyle(ChatFormatting.YELLOW)))
                    );

            explanationMessage.append(clickablePart);
        }

        player.displayClientMessage(explanationMessage, false);
    }

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void multiplayerspawners$onAttemptDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        Level world = this.player.serverLevel();
        BlockState blockState = world.getBlockState(pos);

        if (blockState.getBlock() instanceof SpawnerBlock) {
            // Check configuration for break message
            boolean allowBreaking = ModConfig.SERVER.allowSpawnerBreakMessage.get();
            boolean isCreative = this.player.isCreative();
            int requiredAttempts = ModConfig.SERVER.requiredBreakAttempts.get();

            if (SpawnerStateManager.isSpawnerDisabledForPlayer(world, pos, this.player.getName().getString())) {
                // Always show explanatory message, but only include break link if allowed
                boolean canBreak = allowBreaking || isCreative;
                multiplayerspawners$sendClickableBreakMessage(this.player, pos, canBreak);

                // Force immediate visual state refresh to prevent texture reset
                multiplayerspawners$forceVisualStateRefresh(world, pos, this.player);

                cir.setReturnValue(false);
                cir.cancel();
                return;
            }

            if (SpawnerStateManager.isSpawnerDisabledByAnyone(world, pos)) {
                // Always show explanatory message, but only include break link if allowed
                boolean canBreak = allowBreaking || isCreative;
                multiplayerspawners$sendClickableBreakMessage(this.player, pos, canBreak);
                SpawnerStateManager.disableSpawnerForPlayer(world, pos, this.player.getName().getString());

                // Send network update for this new player disabling (immediate feedback for this player)
                multiplayerspawners$triggerImmediateClientFeedback(world, pos, this.player);

                // Send to other tracking players
                if (world.getBlockEntity(pos) instanceof SpawnerBlockEntity spawnerBlockEntity) {
                    SpawnerDataAccessor accessor = (SpawnerDataAccessor) spawnerBlockEntity.getSpawner();
                    Set<String> disabledPlayers = accessor.multiplayerspawners$getDisabledPlayers();
                    NetworkHandler.getInstance().sendSpawnerStateToTracking(world, pos, disabledPlayers);
                }

                // Force immediate visual state refresh to prevent texture reset
                multiplayerspawners$forceVisualStateRefresh(world, pos, this.player);

                player.displayClientMessage(Component.literal("It will no longer spawn creatures while you are nearby.").withStyle(ChatFormatting.RED), false);
                cir.setReturnValue(false);
                cir.cancel();
                return;
            }

            // --- ONE HIT BREAK MODE (requiredAttempts == 1) ---
            // First hit: disable spawner, drop rewards, AND show break message immediately
            if (requiredAttempts == 1) {
                Constants.LOG.info("Player {} is disabling spawner at {} with ONE-HIT mode. Showing break message immediately.", player.getName().getString(), pos);

                // Check if player is holding the dungeon pickaxe
                if (multiplayerspawners$isDungeonPickaxe(this.player.getMainHandItem())) {
                    multiplayerspawners$dropEmptySpawner(world, pos, this.player);
                    Constants.LOG.info("Player {} used dungeon pickaxe to disable spawner at {} - dropped empty spawner",
                            player.getName().getString(), pos);
                }

                // Drop experience and items as if the spawner was broken normally
                multiplayerspawners$dropSpawnerRewards(world, pos, this.player);

                // Disable the spawner for this player
                multiplayerspawners$handleSpawnerInteraction(world, pos, this.player);

                // Show the break message immediately (one-hit mode)
                boolean canBreak = allowBreaking || isCreative;
                multiplayerspawners$sendClickableBreakMessage(this.player, pos, canBreak);

                cir.setReturnValue(false);
                cir.cancel();
                return;
            }

            // --- STANDARD TWO-HIT MODE (requiredAttempts >= 2) ---
            // First hit: just disable the spawner and drop rewards (no break message yet)
            Constants.LOG.info("Player {} is disabling a spawner for the first time at {}. Intercepting and dropping experience.", player.getName().getString(), pos);

            // Check if player is holding the dungeon pickaxe and the spawner is active
            ItemStack heldItem = this.player.getMainHandItem();
            boolean hasDungeonPickaxe = multiplayerspawners$isDungeonPickaxe(heldItem);

            if (hasDungeonPickaxe) {
                // Drop an empty spawner block
                multiplayerspawners$dropEmptySpawner(world, pos, this.player);
                Constants.LOG.info("Player {} used dungeon pickaxe to disable spawner at {} - dropped empty spawner",
                        player.getName().getString(), pos);
            }

            // Drop experience and items as if the spawner was broken normally
            multiplayerspawners$dropSpawnerRewards(world, pos, this.player);

            // Then handle the spawner interaction (disable it)
            multiplayerspawners$handleSpawnerInteraction(world, pos, this.player);

            cir.setReturnValue(false);
            cir.cancel();
        }
    }
}