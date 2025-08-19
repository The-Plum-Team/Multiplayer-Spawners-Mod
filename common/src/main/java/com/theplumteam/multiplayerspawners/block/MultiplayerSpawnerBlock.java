package com.theplumteam.multiplayerspawners.block;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

public class MultiplayerSpawnerBlock extends Block {
    // Define the boolean property for the block state
    public static final BooleanProperty ACTIVATED = BooleanProperty.create("activated");

    public MultiplayerSpawnerBlock(Properties properties) {
        super(properties);
        // Set the default state to not activated
        this.registerDefaultState(this.stateDefinition.any().setValue(ACTIVATED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        // Add our property to the block state definition
        builder.add(ACTIVATED);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            // Get the current value of the "activated" property
            boolean currentState = state.getValue(ACTIVATED);

            // Flip the state
            BlockState newState = state.setValue(ACTIVATED, !currentState);

            // Update the block state in the world
            level.setBlock(pos, newState, 3);

            // Play a sound to indicate the change
            if (!currentState) {
                // Activating - play spawner activate sound
                //level.playSound(null, pos, SoundEvents.SPAWNER_SPAWN, SoundSource.BLOCKS, 1.0F, 1.0F);
            } else {
                // Deactivating - play break sound
                //level.playSound(null, pos, SoundEvents.SPAWNER_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
            }

            return InteractionResult.SUCCESS;
        }

        return InteractionResult.SUCCESS;
    }

    // Override this method to prevent the block from being broken by left-clicking
    // Instead, it should act like right-clicking when left-clicked
    @Override
    public void attack(BlockState state, Level level, BlockPos pos, Player player) {
        // Call the use method instead of breaking
        this.use(state, level, pos, player, InteractionHand.MAIN_HAND, null);
    }
}