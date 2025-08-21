package com.theplumteam.multiplayerspawners.event;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.network.NetworkHandler;
import com.theplumteam.multiplayerspawners.util.SpawnerDataAccessor;
import com.theplumteam.multiplayerspawners.util.SpawnerStateManager;
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
                                                    
                                                    // Break the spawner directly
                                                    world.destroyBlock(pos, true, player);
                                                    
                                                    player.displayClientMessage(Component.literal("Spawner successfully broken and removed!").withStyle(ChatFormatting.GREEN), false);
                                                    Constants.LOG.info("Player {} broke disabled spawner at {} via command", player.getName().getString(), pos);
                                                    
                                                    return 1;
                                                })
                                        )
                                )
                        )
                )
        );
    }
}