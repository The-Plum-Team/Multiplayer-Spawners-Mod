package com.theplumteam.multiplayerspawners.event;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.config.ModConfig;
import com.theplumteam.multiplayerspawners.network.NetworkHandler;
import com.theplumteam.multiplayerspawners.util.SpawnerDataAccessor;
import com.theplumteam.multiplayerspawners.util.SpawnerStateManager;
import com.theplumteam.multiplayerspawners.util.TorchTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

//? if >=1.21.11 {
import net.minecraft.server.permissions.Permissions;
//?}

import java.util.Set;

public class CommandHandler {

    /**
     * Refreshes all spawner states for all players on the server.
     * Used when config changes that affect spawner visual appearance.
     */
    private static void refreshAllSpawners(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            NetworkHandler.getInstance().sendAllSpawnerStatesToPlayer(player);
        }
        Constants.debug("Refreshed spawner states for all {} players", server.getPlayerList().getPlayers().size());
    }

    /**
     * Helper to save config changes to disk immediately.
     * This ensures settings persist after server restart/world reload.
     */
    private static void saveServerConfig() {
        ModConfig.saveServer();
        Constants.debug("MultiplayerSpawners configuration saved to disk.");
    }

    /**
     * Operators of level 2 (game masters), as vanilla's own world-changing commands require.
     */
    private static boolean isGamemaster(CommandSourceStack source) {
        //? if >=1.21.11 {
        return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        //?} else {
        /*return source.hasPermission(2);
        *///?}
    }

    public static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("multiplayerspawners")
                .then(Commands.literal("toggle_placed_vanilla")
                        .requires(CommandHandler::isGamemaster)
                        .executes(context -> {
                            boolean current = ModConfig.SERVER.treatPlacedSpawnersAsVanilla.get();
                            boolean newValue = !current;
                            ModConfig.SERVER.treatPlacedSpawnersAsVanilla.set(newValue);

                            // EXPLICITLY SAVE TO DISK
                            saveServerConfig();

                            String status = newValue ? "vanilla" : "multiplayer";
                            ChatFormatting color = newValue ? ChatFormatting.GREEN : ChatFormatting.GOLD;

                            context.getSource().sendSuccess(() -> Component.translatable("commands.multiplayerspawners.toggle_placed_vanilla." + status)
                                    .withStyle(color), true);

                            if (newValue) {
                                context.getSource().sendSuccess(() -> Component.translatable("commands.multiplayerspawners.toggle_placed_vanilla.vanilla.detail")
                                        .withStyle(ChatFormatting.GRAY), false);
                            } else {
                                context.getSource().sendSuccess(() -> Component.translatable("commands.multiplayerspawners.toggle_placed_vanilla.multiplayer.detail")
                                        .withStyle(ChatFormatting.GRAY), false);
                            }

                            // Force refresh all spawner states to update visuals immediately
                            refreshAllSpawners(context.getSource().getServer());

                            return 1;
                        })
                )
                .then(Commands.literal("set_break_attempts")
                        .requires(CommandHandler::isGamemaster)
                        .then(Commands.argument("value", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 10))
                                .executes(context -> {
                                    int newValue = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "value");
                                    int oldValue = ModConfig.SERVER.requiredBreakAttempts.get();

                                    ModConfig.SERVER.requiredBreakAttempts.set(newValue);

                                    // EXPLICITLY SAVE TO DISK
                                    saveServerConfig();

                                    String type = (newValue == 1) ? "one_hit" : "two_step";
                                    ChatFormatting color = (newValue == 1) ? ChatFormatting.GOLD : ChatFormatting.GREEN;

                                    context.getSource().sendSuccess(() -> Component.translatable("commands.multiplayerspawners.set_break_attempts." + type, newValue)
                                            .withStyle(color), true);

                                    Constants.debug("Player {} changed requiredBreakAttempts from {} to {}",
                                        context.getSource().getTextName(), oldValue, newValue);

                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("toggle_break_message")
                        .requires(CommandHandler::isGamemaster)
                        .executes(context -> {
                            boolean current = ModConfig.SERVER.allowSpawnerBreakMessage.get();
                            boolean newValue = !current;
                            ModConfig.SERVER.allowSpawnerBreakMessage.set(newValue);

                            // EXPLICITLY SAVE TO DISK
                            saveServerConfig();

                            String status = newValue ? "enabled" : "disabled";
                            ChatFormatting color = newValue ? ChatFormatting.GREEN : ChatFormatting.RED;

                            context.getSource().sendSuccess(() -> Component.translatable("commands.multiplayerspawners.toggle_break_message." + status)
                                    .withStyle(color), true);

                            if (!newValue) {
                                context.getSource().sendSuccess(() -> Component.translatable("commands.multiplayerspawners.toggle_break_message.disabled.detail")
                                        .withStyle(ChatFormatting.GRAY), false);
                            }

                            return 1;
                        })
                )
                .then(Commands.literal("toggle_notices")
                        .requires(CommandHandler::isGamemaster)
                        .executes(context -> {
                            boolean newValue = !ModConfig.SERVER.showNotices.get();
                            ModConfig.SERVER.showNotices.set(newValue);

                            // EXPLICITLY SAVE TO DISK
                            saveServerConfig();

                            context.getSource().sendSuccess(() -> Component.translatable("commands.multiplayerspawners.toggle_notices." + (newValue ? "on" : "off"))
                                    .withStyle(newValue ? ChatFormatting.GREEN : ChatFormatting.GOLD), true);
                            if (!newValue) {
                                context.getSource().sendSuccess(() -> Component.translatable("commands.multiplayerspawners.toggle_notices.off.detail")
                                        .withStyle(ChatFormatting.GRAY), false);
                            }
                            return 1;
                        })
                )
                .then(Commands.literal("toggle_break_tools")
                        .requires(CommandHandler::isGamemaster)
                        .executes(context -> {
                            boolean newValue = !ModConfig.SERVER.breakWithSpawnerTools.get();
                            ModConfig.SERVER.breakWithSpawnerTools.set(newValue);

                            // EXPLICITLY SAVE TO DISK
                            saveServerConfig();

                            context.getSource().sendSuccess(() -> Component.translatable("commands.multiplayerspawners.toggle_break_tools." + (newValue ? "enabled" : "disabled"))
                                    .withStyle(newValue ? ChatFormatting.GREEN : ChatFormatting.GOLD), true);
                            if (newValue) {
                                context.getSource().sendSuccess(() -> Component.translatable("commands.multiplayerspawners.toggle_break_tools.enabled.detail")
                                        .withStyle(ChatFormatting.GRAY), false);
                            }
                            return 1;
                        })
                )
                .then(Commands.literal("toggle_spawn_eggs")
                        .requires(CommandHandler::isGamemaster)
                        .executes(context -> {
                            boolean newValue = !ModConfig.SERVER.spawnEggsChangeSpawners.get();
                            ModConfig.SERVER.spawnEggsChangeSpawners.set(newValue);

                            // EXPLICITLY SAVE TO DISK
                            saveServerConfig();

                            context.getSource().sendSuccess(() -> Component.translatable("commands.multiplayerspawners.toggle_spawn_eggs." + (newValue ? "allowed" : "creative_only"))
                                    .withStyle(newValue ? ChatFormatting.GREEN : ChatFormatting.GOLD), true);
                            if (!newValue) {
                                context.getSource().sendSuccess(() -> Component.translatable("commands.multiplayerspawners.toggle_spawn_eggs.creative_only.detail")
                                        .withStyle(ChatFormatting.GRAY), false);
                            }
                            return 1;
                        })
                )
                .then(Commands.literal("allow_break")
                        .then(Commands.argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                .then(Commands.argument("y", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                        .then(Commands.argument("z", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                                .executes(context -> {
                                                    CommandSourceStack source = context.getSource();
                                                    if (!(source.getEntity() instanceof ServerPlayer player)) {
                                                        source.sendFailure(Component.translatable("commands.multiplayerspawners.allow_break.players_only"));
                                                        return 0;
                                                    }

                                                    // Security Check: Prevent manual command entry if feature is disabled
                                                    boolean isBreakAllowed = ModConfig.SERVER.allowSpawnerBreakMessage.get();
                                                    if (!isBreakAllowed && !player.isCreative()) {
                                                        player.sendSystemMessage(Component.translatable("commands.multiplayerspawners.allow_break.prohibited")
                                                                .withStyle(ChatFormatting.RED));
                                                        return 0;
                                                    }

                                                    int x = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "x");
                                                    int y = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "y");
                                                    int z = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "z");

                                                    BlockPos pos = new BlockPos(x, y, z);
                                                    Level world = player.level();

                                                    // Check if the spawner exists and is disabled for this player
                                                    if (!SpawnerStateManager.isSpawnerDisabledForPlayer(world, pos, player.getName().getString())) {
                                                        player.sendSystemMessage(Component.translatable("commands.multiplayerspawners.allow_break.not_disabled").withStyle(ChatFormatting.RED));
                                                        return 0;
                                                    }

                                                    // Check if it's actually a spawner block
                                                    BlockState blockState = world.getBlockState(pos);
                                                    if (!(blockState.getBlock() instanceof SpawnerBlock)) {
                                                        player.sendSystemMessage(Component.translatable("commands.multiplayerspawners.allow_break.no_spawner").withStyle(ChatFormatting.RED));
                                                        return 0;
                                                    }

                                                    // Break the spawner directly without dropping items/experience
                                                    world.destroyBlock(pos, false, player);

                                                    player.sendSystemMessage(Component.translatable("commands.multiplayerspawners.allow_break.success").withStyle(ChatFormatting.GREEN));
                                                    Constants.debug("Player {} broke disabled spawner at {} via command", player.getName().getString(), pos);

                                                    return 1;
                                                })
                                        )
                                )
                        )
                )
                .then(Commands.literal("respawn")
                        .requires(CommandHandler::isGamemaster)
                        .then(Commands.argument("minutes", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 43200))
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    int newMinutes = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "minutes");
                                    int oldMinutes = ModConfig.SERVER.spawnerRespawnMinutes.get();

                                    ModConfig.SERVER.spawnerRespawnMinutes.set(newMinutes);

                                    // EXPLICITLY SAVE TO DISK
                                    saveServerConfig();

                                    if (newMinutes == 0) {
                                        source.sendSuccess(() -> Component.translatable("commands.multiplayerspawners.respawn.disabled")
                                                .withStyle(ChatFormatting.GOLD), true);
                                    } else {
                                        source.sendSuccess(() -> Component.translatable("commands.multiplayerspawners.respawn.set", newMinutes, oldMinutes)
                                                .withStyle(ChatFormatting.GREEN), true);
                                    }
                                    Constants.debug("Player {} changed spawnerRespawnMinutes from {} to {}",
                                            source.getTextName(), oldMinutes, newMinutes);

                                    return 1;
                                })
                        )
                        .executes(context -> {
                            CommandSourceStack source = context.getSource();
                            int currentMinutes = ModConfig.SERVER.spawnerRespawnMinutes.get();

                            if (currentMinutes == 0) {
                                source.sendSuccess(() -> Component.translatable("commands.multiplayerspawners.respawn.query.disabled")
                                        .withStyle(ChatFormatting.YELLOW), false);
                            } else {
                                source.sendSuccess(() -> Component.translatable("commands.multiplayerspawners.respawn.query", currentMinutes)
                                        .withStyle(ChatFormatting.AQUA), false);
                            }

                            return 1;
                        })
                )
                .then(Commands.literal("torch")
                        .requires(CommandHandler::isGamemaster)
                        .then(Commands.literal("delay")
                                .then(Commands.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 300))
                                        .executes(context -> {
                                            CommandSourceStack source = context.getSource();
                                            int newDelay = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "seconds");

                                            // Get old value for comparison
                                            int oldDelay = TorchTracker.getCurrentDelaySeconds();

                                            // Update the config value
                                            ModConfig.SERVER.torchExtinguishDelaySeconds.set(newDelay);

                                            // EXPLICITLY SAVE TO DISK
                                            saveServerConfig();

                                            if (newDelay == 0) {
                                                source.sendSuccess(() -> Component.translatable("commands.multiplayerspawners.torch.delay.disabled")
                                                        .withStyle(ChatFormatting.GOLD), true);
                                                Constants.debug("Player {} disabled torch extinguishing (was {} seconds)",
                                                        source.getTextName(), oldDelay);
                                            } else {
                                                source.sendSuccess(() -> Component.translatable("commands.multiplayerspawners.torch.delay.set", newDelay, oldDelay)
                                                        .withStyle(ChatFormatting.GREEN), true);
                                                Constants.debug("Player {} changed torch extinguish delay from {} to {} seconds",
                                                        source.getTextName(), oldDelay, newDelay);
                                            }

                                            return 1;
                                        })
                                )
                                .executes(context -> {
                                    // Show current delay when no argument is provided
                                    CommandSourceStack source = context.getSource();
                                    int currentDelay = TorchTracker.getCurrentDelaySeconds();

                                    if (currentDelay == 0) {
                                        source.sendSuccess(() -> Component.translatable("commands.multiplayerspawners.torch.delay.query.disabled")
                                                .withStyle(ChatFormatting.YELLOW), false);
                                    } else {
                                        source.sendSuccess(() -> Component.translatable("commands.multiplayerspawners.torch.delay.query", currentDelay)
                                                .withStyle(ChatFormatting.AQUA), false);
                                    }

                                    return 1;
                                })
                        )
                        .then(Commands.literal("info")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    int currentDelay = TorchTracker.getCurrentDelaySeconds();

                                    source.sendSuccess(() -> Component.translatable("commands.multiplayerspawners.torch.info.title")
                                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

                                    if (currentDelay == 0) {
                                        source.sendSuccess(() -> Component.translatable("commands.multiplayerspawners.torch.info.disabled")
                                                .withStyle(ChatFormatting.RED), false);
                                    } else {
                                        source.sendSuccess(() -> Component.translatable("commands.multiplayerspawners.torch.info.enabled")
                                                .withStyle(ChatFormatting.GREEN), false);
                                        source.sendSuccess(() -> Component.translatable("commands.multiplayerspawners.torch.info.delay", currentDelay)
                                                .withStyle(ChatFormatting.AQUA), false);
                                    }

                                    source.sendSuccess(() -> Component.translatable("commands.multiplayerspawners.torch.info.range", TorchTracker.SPAWNER_DISTANCE)
                                            .withStyle(ChatFormatting.GRAY), false);
                                    source.sendSuccess(() -> Component.translatable("commands.multiplayerspawners.torch.info.effect")
                                            .withStyle(ChatFormatting.GRAY), false);

                                    return 1;
                                })
                        )
                )
        );
    }
}