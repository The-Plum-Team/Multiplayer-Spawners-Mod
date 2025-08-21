package com.theplumteam.multiplayerspawners.event;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.network.NetworkHandler;
import com.theplumteam.multiplayerspawners.util.SpawnerDataAccessor;
import com.theplumteam.multiplayerspawners.util.SpawnerStateManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;

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
                                                        player.displayClientMessage(Component.literal("§cThat spawner is not disabled for you!"), true);
                                                        return 0;
                                                    }
                                                    
                                                    // Enable the spawner for this player
                                                    SpawnerStateManager.enableSpawnerForPlayer(world, pos, player.getName().getString());
                                                    
                                                    // Send network update
                                                    if (world.getBlockEntity(pos) instanceof SpawnerBlockEntity spawnerBlockEntity) {
                                                        SpawnerDataAccessor accessor = (SpawnerDataAccessor) spawnerBlockEntity.getSpawner();
                                                        Set<String> disabledPlayers = accessor.multiplayerspawners$getDisabledPlayers();
                                                        NetworkHandler.getInstance().sendSpawnerStateToPlayer(player, pos, disabledPlayers, false);
                                                        NetworkHandler.getInstance().sendSpawnerStateToTracking(world, pos, disabledPlayers);
                                                    }
                                                    
                                                    player.displayClientMessage(Component.literal("§aYou can now break the spawner at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "!"), true);
                                                    Constants.LOG.info("Player {} gained permission to break spawner at {}", player.getName().getString(), pos);
                                                    
                                                    return 1;
                                                })
                                        )
                                )
                        )
                )
        );
    }
}