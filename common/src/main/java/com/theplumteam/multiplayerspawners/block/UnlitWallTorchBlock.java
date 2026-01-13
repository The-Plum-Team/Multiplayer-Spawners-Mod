package com.theplumteam.multiplayerspawners.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;

public class UnlitWallTorchBlock extends WallTorchBlock {

    public UnlitWallTorchBlock() {
        super(ParticleTypes.FLAME, BlockBehaviour.Properties.of().noCollission().instabreak().lightLevel((state) -> 0).pushReaction(PushReaction.DESTROY));
    }

    @Override
    public void animateTick(BlockState blockState, Level level, BlockPos blockPos, RandomSource randomSource) {
        // Do nothing to prevent flame particles from spawning
    }
}