package com.theplumteam.multiplayerspawners.mixin;

import com.theplumteam.multiplayerspawners.block.SpawnerStateProperties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Block.class)
public abstract class BlockMixin {

    // Debug
    static {
        System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        System.out.println("!!!!!!!!!! BLOCKMIXIN IS LOADED BY JAVA !!!!!!!!!!");
        System.out.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
    }

    @Shadow public abstract BlockState defaultBlockState();
    @Shadow protected abstract void registerDefaultState(BlockState state);

    /**
     * Injects into the end of the createBlockStateDefinition method in the Block class.
     * We then check if the block instance being constructed is a SpawnerBlock.
     * If it is, we add our new property to its state definition.
     */
    @Inject(method = "createBlockStateDefinition", at = @At("RETURN"))
    private void multiplayerspawners$addSpawnerProperties(StateDefinition.Builder<Block, BlockState> builder, CallbackInfo ci) {
        Block thisBlock = (Block) (Object) this;
        if (thisBlock instanceof SpawnerBlock) {
            builder.add(SpawnerStateProperties.MULTIPLAYER_ACTIVATED);
        }
    }

    /**
     * Injects into the end of the Block constructor.
     * After the original constructor has run, we check if the block is a SpawnerBlock.
     * If it is, we register a new default state that includes our property set to 'true'.
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void multiplayerspawners$setDefaultSpawnerState(BlockBehaviour.Properties properties, CallbackInfo ci) {
        Block thisBlock = (Block) (Object) this;
        if (thisBlock instanceof SpawnerBlock) {
            this.registerDefaultState(this.defaultBlockState().setValue(SpawnerStateProperties.MULTIPLAYER_ACTIVATED, true));
        }
    }
}