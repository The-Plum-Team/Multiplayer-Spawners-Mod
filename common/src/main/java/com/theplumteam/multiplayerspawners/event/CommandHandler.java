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

import java.util.Set;

public class CommandHandler {

    public static void register(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("multiplayerspawners")
                // NEW COMMAND
                .then(Commands.literal("set_break_attempts")
                        .requires(source -> source.hasPermission(2)) // OP level 2
                        .then(Commands.argument("value", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 10))
                                .executes(context -> {
                                    int newValue = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "value");
                                    int oldValue = ModConfig.SERVER.requiredBreakAttempts.get();

                                    ModConfig.SERVER.requiredBreakAttempts.set(newValue);

                                    String type = (newValue == 1) ? "ONE-HIT (Break message on first hit)" : "TWO-STEP (Break message on second hit)";
                                    ChatFormatting color = (newValue == 1) ? ChatFormatting.GOLD : ChatFormatting.GREEN;

                                    context.getSource().sendSuccess(() -> Component.literal("Spawner break attempts set to: " + newValue + " [" + type + "]")
                                            .withStyle(color), true);

                                    Constants.LOG.info("Player {} changed requiredBreakAttempts from {} to {}",
                                        context.getSource().getTextName(), oldValue, newValue);

                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("toggle_break_message")
                        .requires(source -> source.hasPermission(2)) // OP level 2
                        .executes(context -> {
                            boolean current = ModConfig.SERVER.allowSpawnerBreakMessage.get();
                            boolean newValue = !current;
                            ModConfig.SERVER.allowSpawnerBreakMessage.set(newValue);

                            String status = newValue ? "ENABLED" : "DISABLED";
                            ChatFormatting color = newValue ? ChatFormatting.GREEN : ChatFormatting.RED;

                            context.getSource().sendSuccess(() -> Component.literal("Spawner break message is now " + status)
                                    .withStyle(color), true);

                            if (!newValue) {
                                context.getSource().sendSuccess(() -> Component.literal("Survival players will no longer be able to break disabled spawners.")
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
                                                        source.sendFailure(Component.literal("Only players can use this command!"));
                                                        return 0;
                                                    }

                                                    // Security Check: Prevent manual command entry if feature is disabled
                                                    boolean isBreakAllowed = ModConfig.SERVER.allowSpawnerBreakMessage.get();
                                                    if (!isBreakAllowed && !player.isCreative()) {
                                                        player.displayClientMessage(Component.literal("Breaking disabled spawners is currently prohibited on this server.")
                                                                .withStyle(ChatFormatting.RED), false);
                                                        return 0;
                                                    }

                                                    int x = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "x");
                                                    int y = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "y");
                                                    int z = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "z");

                                                    BlockPos pos = new BlockPos(x, y, z);
                                                    Level world = player.serverLevel();

                                                    // Check if the spawner exists and is disabled for this player
                                                    if (!SpawnerStateManager.isSpawnerDisabledForPlayer(world, pos, player.getName().getString())) {
                                                        player.displayClientMessage(Component.literal("That spawner is not disabled for you!").withStyle(ChatFormatting.RED), false);
                                                        return 0;
                                                    }

                                                    // Check if it's actually a spawner block
                                                    BlockState blockState = world.getBlockState(pos);
                                                    if (!(blockState.getBlock() instanceof SpawnerBlock)) {
                                                        player.displayClientMessage(Component.literal("No spawner found at that location!").withStyle(ChatFormatting.RED), false);
                                                        return 0;
                                                    }

                                                    // Break the spawner directly without dropping items/experience
                                                    world.destroyBlock(pos, false, player);

                                                    player.displayClientMessage(Component.literal("Spawner successfully broken and removed!").withStyle(ChatFormatting.GREEN), false);
                                                    Constants.LOG.info("Player {} broke disabled spawner at {} via command", player.getName().getString(), pos);

                                                    return 1;
                                                })
                                        )
                                )
                        )
                )
                .then(Commands.literal("torch")
                        .requires(source -> source.hasPermission(2)) // Require operator level
                        .then(Commands.literal("delay")
                                .then(Commands.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 300))
                                        .executes(context -> {
                                            CommandSourceStack source = context.getSource();
                                            int newDelay = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "seconds");

                                            // Get old value for comparison
                                            int oldDelay = TorchTracker.getCurrentDelaySeconds();

                                            // Update the config value
                                            ModConfig.SERVER.torchExtinguishDelaySeconds.set(newDelay);

                                            if (newDelay == 0) {
                                                source.sendSuccess(() -> Component.literal("Torch extinguishing has been DISABLED. Torches will no longer be extinguished by spawners.")
                                                        .withStyle(ChatFormatting.GOLD), true);
                                                Constants.LOG.info("Player {} disabled torch extinguishing (was {} seconds)",
                                                        source.getTextName(), oldDelay);
                                            } else {
                                                source.sendSuccess(() -> Component.literal("Torch extinguish delay set to " + newDelay + " seconds (was " + oldDelay + " seconds)")
                                                        .withStyle(ChatFormatting.GREEN), true);
                                                Constants.LOG.info("Player {} changed torch extinguish delay from {} to {} seconds",
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
                                        source.sendSuccess(() -> Component.literal("Torch extinguishing is currently DISABLED.")
                                                .withStyle(ChatFormatting.YELLOW), false);
                                    } else {
                                        source.sendSuccess(() -> Component.literal("Current torch extinguish delay: " + currentDelay + " seconds")
                                                .withStyle(ChatFormatting.AQUA), false);
                                    }

                                    return 1;
                                })
                        )
                        .then(Commands.literal("info")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    int currentDelay = TorchTracker.getCurrentDelaySeconds();

                                    source.sendSuccess(() -> Component.literal("=== Torch Extinguishing System ===")
                                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

                                    if (currentDelay == 0) {
                                        source.sendSuccess(() -> Component.literal("Status: DISABLED")
                                                .withStyle(ChatFormatting.RED), false);
                                    } else {
                                        source.sendSuccess(() -> Component.literal("Status: ENABLED")
                                                .withStyle(ChatFormatting.GREEN), false);
                                        source.sendSuccess(() -> Component.literal("Delay: " + currentDelay + " seconds")
                                                .withStyle(ChatFormatting.AQUA), false);
                                    }

                                    source.sendSuccess(() -> Component.literal("Range: 7 blocks from spawners")
                                            .withStyle(ChatFormatting.GRAY), false);
                                    source.sendSuccess(() -> Component.literal("Effect: Torches placed near spawners will be extinguished")
                                            .withStyle(ChatFormatting.GRAY), false);

                                    return 1;
                                })
                        )
                )
        );
    }
}