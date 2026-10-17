package com.theplumteam.multiplayerspawners.mixin;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.config.ModConfig;
import com.theplumteam.multiplayerspawners.network.NetworkHandler;
import com.theplumteam.multiplayerspawners.util.SpawnerDataAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Marks a spawner a player places as player-placed, so that it can behave like a vanilla one.
 *
 * <p>This runs when the item's placement returns rather than in {@link SpawnerBlock#setPlacedBy}:
 * mods such as Apothic Spawners override {@code setPlacedBy} in their spawner block without calling
 * the vanilla one, and they may load block entity data there, so the mark is set after it.
 */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {

    @Inject(method = "place", at = @At("RETURN"))
    private void multiplayerspawners$onPlaced(BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (level.isClientSide() || !cir.getReturnValue().consumesAction() || player == null
                || !(((BlockItem) (Object) this).getBlock() instanceof SpawnerBlock)
                || !(level.getBlockState(pos).getBlock() instanceof SpawnerBlock)
                || !(level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawnerBe)) {
            return;
        }
        SpawnerDataAccessor accessor = (SpawnerDataAccessor) spawnerBe.getSpawner();
        accessor.multiplayerspawners$setPlayerPlaced(true);
        spawnerBe.setChanged();
        Constants.debug("Marked spawner at {} as player-placed by {}.", pos, player.getName().getString());

        // Without this the client shows the mod's texture until the next chunk load or sync.
        if (ModConfig.SERVER.treatPlacedSpawnersAsVanilla.get()) {
            NetworkHandler.getInstance().sendSpawnerStateToTracking(level, pos,
                    accessor.multiplayerspawners$getDisabledPlayers(), true);
            Constants.debug("Synced vanilla visual state for new player-placed spawner at {}", pos);
        }
    }
}
