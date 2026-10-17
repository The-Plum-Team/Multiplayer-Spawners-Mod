// ServerPlayerGameModeMixin.java
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
        ItemStack spawnerItem = new ItemStack(Blocks.SPAWNER);
        ItemEntity itemEntity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, spawnerItem);
        itemEntity.setDefaultPickUpDelay();
        level.addFreshEntity(itemEntity);
        Constants.debug("Dropped empty spawner for player {} at {} using dungeon pickaxe", player.getName().getString(), pos);
    }

    @Unique
    private void multiplayerspawners$handleSpawnerInteraction(Level level, BlockPos pos, ServerPlayer player) {
        String playerName = player.getName().getString();
        SpawnerStateManager.disableSpawnerForPlayer(level, pos, playerName);
        Constants.debug("Spawner at {} has been permanently disabled by player {}", pos, playerName);

        multiplayerspawners$triggerImmediateClientFeedback(level, pos, player);
        multiplayerspawners$sendRespawnNotice(player);

        if (level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawnerBlockEntity) {
            SpawnerDataAccessor accessor = (SpawnerDataAccessor) spawnerBlockEntity.getSpawner();
            Set<String> disabledPlayers = accessor.multiplayerspawners$getDisabledPlayers();
            boolean treatedAsVanilla = accessor.multiplayerspawners$isPlayerPlaced()
                    && ModConfig.SERVER.treatPlacedSpawnersAsVanilla.get();

            NetworkHandler.getInstance().sendSpawnerStateToTracking(level, pos, disabledPlayers, treatedAsVanilla);
            level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
        }

        if (ModConfig.SERVER.enableGroupDisabling.get()) {
            int radius = ModConfig.SERVER.groupDisablingRadius.get();
            multiplayerspawners$handleGroupDisabling(level, pos, player, radius);
        }
    }

    /** Tells the player when the spawner they disabled becomes active again, if it ever does. */
    @Unique
    private static void multiplayerspawners$sendRespawnNotice(ServerPlayer player) {
        int minutes = ModConfig.SERVER.spawnerRespawnMinutes.get();
        if (minutes > 0) {
            player.sendSystemMessage(Component.literal("It will be active for you again in " + minutes
                    + (minutes == 1 ? " minute." : " minutes.")).withStyle(ChatFormatting.GRAY));
        }
    }

    @Unique
    private void multiplayerspawners$triggerImmediateClientFeedback(Level level, BlockPos pos, ServerPlayer player) {
        // FIX: Force a vanilla block update packet first.
        // This tells the client "The block is still here" (undoing the air prediction).
        // Only AFTER this do we send our custom packet.
        player.connection.send(new ClientboundBlockUpdatePacket(pos, level.getBlockState(pos)));

        if (level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawnerBlockEntity) {
            SpawnerDataAccessor accessor = (SpawnerDataAccessor) spawnerBlockEntity.getSpawner();
            Set<String> disabledPlayers = accessor.multiplayerspawners$getDisabledPlayers();
            boolean treatedAsVanilla = accessor.multiplayerspawners$isPlayerPlaced()
                    && ModConfig.SERVER.treatPlacedSpawnersAsVanilla.get();

            NetworkHandler.getInstance().sendSpawnerStateToPlayer(player, pos, disabledPlayers, false, treatedAsVanilla);
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
                    serverPlayer.sendSystemMessage(
                            Component.literal("A spawner in this area has been disabled by " + triggeringPlayer.getName().getString() + ".").withStyle(ChatFormatting.GOLD));
                }
            }
        }

        if (playersAffected > 0 && level.getBlockEntity(spawnerPos) instanceof SpawnerBlockEntity spawnerBlockEntity) {
            SpawnerDataAccessor accessor = (SpawnerDataAccessor) spawnerBlockEntity.getSpawner();
            Set<String> disabledPlayers = accessor.multiplayerspawners$getDisabledPlayers();
            boolean treatedAsVanilla = accessor.multiplayerspawners$isPlayerPlaced()
                    && ModConfig.SERVER.treatPlacedSpawnersAsVanilla.get();

            NetworkHandler.getInstance().sendSpawnerStateToTracking(level, spawnerPos, disabledPlayers, treatedAsVanilla);
            triggeringPlayer.sendSystemMessage(
                    Component.literal("Group disabling: Spawner disabled for " + playersAffected + " nearby player(s).").withStyle(ChatFormatting.GOLD));
        }
    }

    @Unique
    private void multiplayerspawners$forceVisualStateRefresh(Level level, BlockPos pos, ServerPlayer player) {
        if (level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawnerBlockEntity) {
            // FIX: Ensure vanilla packet is sent first here too
            player.connection.send(new ClientboundBlockUpdatePacket(pos, level.getBlockState(pos)));

            SpawnerDataAccessor accessor = (SpawnerDataAccessor) spawnerBlockEntity.getSpawner();
            Set<String> disabledPlayers = accessor.multiplayerspawners$getDisabledPlayers();
            boolean treatedAsVanilla = accessor.multiplayerspawners$isPlayerPlaced()
                    && ModConfig.SERVER.treatPlacedSpawnersAsVanilla.get();

            NetworkHandler.getInstance().sendSpawnerStateToPlayer(player, pos, disabledPlayers, true, treatedAsVanilla);
        }
    }

    @Unique
    private void multiplayerspawners$dropSpawnerRewards(Level level, BlockPos pos, ServerPlayer player) {
        BlockState blockState = level.getBlockState(pos);
        if (blockState.getBlock() instanceof SpawnerBlock) {
            blockState.getBlock().playerWillDestroy(level, pos, blockState, player);
            blockState.getBlock().dropResources(blockState, level, pos, level.getBlockEntity(pos), player, player.getMainHandItem());
        }
    }

    @Unique
    private static ClickEvent multiplayerspawners$runCommand(String command) {
        //? if >=1.21.5 {
        return new ClickEvent.RunCommand(command);
        //?} else {
        /*return new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);
        *///?}
    }

    @Unique
    private static HoverEvent multiplayerspawners$showText(Component text) {
        //? if >=1.21.5 {
        return new HoverEvent.ShowText(text);
        //?} else {
        /*return new HoverEvent(HoverEvent.Action.SHOW_TEXT, text);
        *///?}
    }

    @Unique
    private void multiplayerspawners$sendClickableBreakMessage(ServerPlayer player, BlockPos pos, boolean includeBreakLink) {
        MutableComponent explanationMessage = Component.literal("The spawner is disabled for you and nearby players. Other players will have it active so they can enjoy the dungeon.").withStyle(ChatFormatting.LIGHT_PURPLE);

        if (includeBreakLink) {
            explanationMessage.append(Component.literal(" "));
            MutableComponent clickablePart = Component.literal("[Click here to break it]")
                    .withStyle(style -> style
                            .withColor(ChatFormatting.RED)
                            .withBold(true)
                            .withClickEvent(multiplayerspawners$runCommand("/multiplayerspawners allow_break " + pos.getX() + " " + pos.getY() + " " + pos.getZ()))
                            .withHoverEvent(multiplayerspawners$showText(Component.literal("Click to break this spawner").withStyle(ChatFormatting.YELLOW)))
                    );
            explanationMessage.append(clickablePart);
        }
        player.sendSystemMessage(explanationMessage);
    }

    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void multiplayerspawners$onAttemptDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        Level world = this.player.level();
        BlockState blockState = world.getBlockState(pos);

        if (blockState.getBlock() instanceof SpawnerBlock) {
            boolean allowBreaking = ModConfig.SERVER.allowSpawnerBreakMessage.get();
            boolean isCreative = this.player.isCreative();
            int requiredAttempts = ModConfig.SERVER.requiredBreakAttempts.get();

            if (ModConfig.SERVER.treatPlacedSpawnersAsVanilla.get()) {
                if (world.getBlockEntity(pos) instanceof SpawnerBlockEntity spawnerBlockEntity) {
                    SpawnerDataAccessor accessor = (SpawnerDataAccessor) spawnerBlockEntity.getSpawner();
                    if (accessor.multiplayerspawners$isPlayerPlaced()) {
                        if (multiplayerspawners$isDungeonPickaxe(this.player.getMainHandItem())) {
                            multiplayerspawners$dropEmptySpawner(world, pos, this.player);
                        }
                        return;
                    }
                }
            }

            if (SpawnerStateManager.isSpawnerDisabledForPlayer(world, pos, this.player.getName().getString())) {
                boolean canBreak = allowBreaking || isCreative;
                multiplayerspawners$sendClickableBreakMessage(this.player, pos, canBreak);
                multiplayerspawners$forceVisualStateRefresh(world, pos, this.player);
                cir.setReturnValue(false);
                cir.cancel();
                return;
            }

            if (SpawnerStateManager.isSpawnerDisabledByAnyone(world, pos)) {
                boolean canBreak = allowBreaking || isCreative;
                multiplayerspawners$sendClickableBreakMessage(this.player, pos, canBreak);
                SpawnerStateManager.disableSpawnerForPlayer(world, pos, this.player.getName().getString());
                multiplayerspawners$triggerImmediateClientFeedback(world, pos, this.player);

                if (world.getBlockEntity(pos) instanceof SpawnerBlockEntity spawnerBlockEntity) {
                    SpawnerDataAccessor accessor = (SpawnerDataAccessor) spawnerBlockEntity.getSpawner();
                    Set<String> disabledPlayers = accessor.multiplayerspawners$getDisabledPlayers();
                    boolean treatedAsVanilla = accessor.multiplayerspawners$isPlayerPlaced()
                            && ModConfig.SERVER.treatPlacedSpawnersAsVanilla.get();
                    NetworkHandler.getInstance().sendSpawnerStateToTracking(world, pos, disabledPlayers, treatedAsVanilla);
                }

                multiplayerspawners$forceVisualStateRefresh(world, pos, this.player);
                player.sendSystemMessage(Component.literal("It will no longer spawn creatures while you are nearby.").withStyle(ChatFormatting.RED));
                multiplayerspawners$sendRespawnNotice(this.player);
                cir.setReturnValue(false);
                cir.cancel();
                return;
            }

            if (requiredAttempts == 1) {
                if (multiplayerspawners$isDungeonPickaxe(this.player.getMainHandItem())) {
                    multiplayerspawners$dropEmptySpawner(world, pos, this.player);
                }
                multiplayerspawners$dropSpawnerRewards(world, pos, this.player);
                multiplayerspawners$handleSpawnerInteraction(world, pos, this.player);
                boolean canBreak = allowBreaking || isCreative;
                multiplayerspawners$sendClickableBreakMessage(this.player, pos, canBreak);
                cir.setReturnValue(false);
                cir.cancel();
                return;
            }

            // Two-hit mode
            if (multiplayerspawners$isDungeonPickaxe(this.player.getMainHandItem())) {
                multiplayerspawners$dropEmptySpawner(world, pos, this.player);
            }
            multiplayerspawners$dropSpawnerRewards(world, pos, this.player);
            multiplayerspawners$handleSpawnerInteraction(world, pos, this.player);
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
}
