package com.theplumteam.multiplayerspawners.util;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.config.ModConfig;
import com.theplumteam.multiplayerspawners.mixin.BaseSpawnerAccessor;
import com.theplumteam.multiplayerspawners.network.NetworkHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Per-player ageing with Ageing Spawners (Mrbysco), which removes a spawner once it has spawned a
 * set number of mobs.
 *
 * <p>When that mod removes a spawner during its own tick, the spawner is instead disabled for the
 * players near it, who used it up, and stays for everyone else; Ageing Spawners then forgets the
 * spawner's count, so it starts again for the next players. Nothing here refers to that mod's
 * classes: the spawner being ticked is recorded around its tick, and a removal of that spawner is
 * recognised by Ageing Spawners' package on the call stack (other mods, such as Apothic Spawners'
 * unstable spawners, also remove a spawner in its tick and keep their own behaviour).
 */
public final class SpawnerAgeing {

    private static final String AGEING_SPAWNERS_PACKAGE = "com.mrbysco.ageingspawners.";

    /** The spawner whose server tick is running (server thread only), or null. */
    private static Level tickingLevel;
    private static BlockPos tickingPos;

    private SpawnerAgeing() {
    }

    public static void startTick(Level level, BlockPos pos) {
        tickingLevel = level;
        tickingPos = pos;
    }

    public static void endTick() {
        tickingLevel = null;
        tickingPos = null;
    }

    /**
     * Called before {@code level.removeBlock(pos, ...)}.
     *
     * @return true when the removal was turned into disabling the spawner for the nearby players,
     *         so that the block must stay
     */
    public static boolean interceptRemoval(Level level, BlockPos pos) {
        if (level != tickingLevel || !pos.equals(tickingPos) || level.isClientSide()
                || !(level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawnerBe)
                || !(spawnerBe.getSpawner() instanceof SpawnerDataAccessor accessor)
                || (accessor.multiplayerspawners$isPlayerPlaced() && ModConfig.SERVER.treatPlacedSpawnersAsVanilla.get())
                || !calledByAgeingSpawners()) {
            return false;
        }

        // The players the spawner was spawning for: near it, and not having disabled it yet.
        Set<String> disabledPlayers = accessor.multiplayerspawners$getDisabledPlayers();
        double range = ((BaseSpawnerAccessor) spawnerBe.getSpawner()).multiplayerspawners$getRequiredPlayerRange();
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        List<ServerPlayer> usedUp = new ArrayList<>();
        for (Player player : level.players()) {
            if (player instanceof ServerPlayer serverPlayer && !player.isSpectator() && player.isAlive()
                    && player.distanceToSqr(x, y, z) < range * range
                    && !disabledPlayers.contains(player.getName().getString())) {
                usedUp.add(serverPlayer);
            }
        }
        if (usedUp.isEmpty()) {
            // Nobody to age it for (it spawns without players, for example): Ageing Spawners decides.
            return false;
        }

        for (ServerPlayer player : usedUp) {
            SpawnerStateManager.disableSpawnerForPlayer(level, pos, player.getName().getString());
            Notices.send(player, Component.literal("This spawner has run out for you. It stays for the other players.")
                    .withStyle(ChatFormatting.GOLD));
        }
        NetworkHandler.getInstance().sendSpawnerStateToTracking(level, pos, Set.copyOf(disabledPlayers), false);
        Constants.debug("Ageing Spawners aged out the spawner at {}: disabled for {} instead of removing it", pos,
                usedUp.stream().map(player -> player.getName().getString()).toList());
        return true;
    }

    private static boolean calledByAgeingSpawners() {
        return StackWalker.getInstance().walk(frames -> frames.anyMatch(
                frame -> frame.getClassName().startsWith(AGEING_SPAWNERS_PACKAGE)));
    }
}
