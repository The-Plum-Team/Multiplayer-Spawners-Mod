package com.theplumteam.multiplayerspawners.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;

public class UnlitTorchBlock extends TorchBlock {

    public UnlitTorchBlock(Properties properties) {
        //? if >=1.21 {
        super(ParticleTypes.FLAME, properties);
        //?} else {
        /*super(properties, ParticleTypes.FLAME);
        *///?}
    }

    @Override
    public void animateTick(BlockState blockState, Level level, BlockPos blockPos, RandomSource randomSource) {
        // An unlit torch gives off no flame particles.
    }
}
