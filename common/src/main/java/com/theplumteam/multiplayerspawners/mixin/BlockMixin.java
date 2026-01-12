package com.theplumteam.multiplayerspawners.mixin;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.block.SpawnerStateProperties;
import com.theplumteam.multiplayerspawners.config.ModConfig;
import com.theplumteam.multiplayerspawners.network.NetworkHandler;
import com.theplumteam.multiplayerspawners.util.SpawnerDataAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
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
            Constants.LOG.info("BLOCK_MIXIN: Added MULTIPLAYER_ACTIVATED property to spawner block");
        }
    }

    /**
     * Injects into the end of the Block constructor.
     * After the original constructor has run, we check if the block is a SpawnerBlock.
     * If it is, we register a new default state that includes our property, but only if the property exists.
     * Default to true (active) - client will override based on player data.
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    private void multiplayerspawners$setDefaultSpawnerState(BlockBehaviour.Properties properties, CallbackInfo ci) {
        Block thisBlock = (Block) (Object) this;
        if (thisBlock instanceof SpawnerBlock) {
            try {
                BlockState currentDefault = this.defaultBlockState();

                // Only set the property if it exists in the current state
                if (currentDefault.hasProperty(SpawnerStateProperties.MULTIPLAYER_ACTIVATED)) {
                    // Default to active - the client-side renderer will handle the actual display
                    this.registerDefaultState(currentDefault.setValue(SpawnerStateProperties.MULTIPLAYER_ACTIVATED, true));
                    Constants.LOG.info("BLOCK_MIXIN: Set default state for spawner block to MULTIPLAYER_ACTIVATED=true");
                } else {
                    Constants.LOG.warn("BLOCK_MIXIN: Spawner block {} does not have MULTIPLAYER_ACTIVATED property, skipping default state setup", thisBlock.getClass().getSimpleName());
                }
            } catch (Exception e) {
                Constants.LOG.error("BLOCK_MIXIN: Failed to set default state for spawner block {}: {}", thisBlock.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    /**
     * Track when a spawner is placed by a player and sync visual state immediately.
     * This marks the spawner as "player-placed" so it can optionally behave like vanilla.
     */
    @Inject(method = "setPlacedBy", at = @At("RETURN"))
    private void multiplayerspawners$onPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack, CallbackInfo ci) {
        if ((Object) this instanceof SpawnerBlock) {
            if (placer instanceof Player) {
                if (level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawnerBe) {
                    // 1. Mark as player placed
                    SpawnerDataAccessor accessor = (SpawnerDataAccessor) spawnerBe.getSpawner();
                    accessor.multiplayerspawners$setPlayerPlaced(true);
                    spawnerBe.setChanged();

                    Constants.LOG.debug("Marked spawner at {} as player-placed by {}.", pos, placer.getName().getString());

                    // 2. CHECK CONFIG AND SYNC VISUALS IMMEDIATELY
                    // If the config says player-placed spawners should be vanilla, we MUST tell the client immediately.
                    // Otherwise, the client defaults to "True" (Multiplayer texture) until the next chunk load or sync.
                    if (ModConfig.SERVER.treatPlacedSpawnersAsVanilla.get() && !level.isClientSide()) {
                        NetworkHandler.getInstance().sendSpawnerStateToTracking(
                                level,
                                pos,
                                accessor.multiplayerspawners$getDisabledPlayers(),
                                true // treatedAsVanilla = TRUE
                        );
                        Constants.LOG.debug("Synced vanilla visual state for new player-placed spawner at {}", pos);
                    }
                }
            }
        }
    }
}