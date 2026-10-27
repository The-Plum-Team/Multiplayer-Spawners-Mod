package com.theplumteam.multiplayerspawners.util;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.config.ModConfig;
import com.theplumteam.multiplayerspawners.mixin.BaseSpawnerAccessor;
import com.theplumteam.multiplayerspawners.network.NetworkHandler;
import com.theplumteam.multiplayerspawners.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Per-player ageing with Ageing Spawners (Mrbysco) and Spawner Control (Dplayend), which remove a
 * spawner once it has spawned a set number of mobs.
 *
 * <p>When one of them removes a spawner during its own tick, the spawner is instead disabled for
 * the players near it, who used it up, and stays for everyone else; its count starts again for the
 * next players (Ageing Spawners forgets it, Spawner Control's is set back to 0 here). Nothing here
 * refers to those mods' classes: the spawner being ticked is recorded around its tick, and a
 * removal of that spawner is recognised by Ageing Spawners' package on the call stack, or by
 * {@link BaseSpawner} itself calling it, which only Spawner Control's mixin does (other mods, such
 * as Apothic Spawners' unstable spawners, also remove a spawner in its tick and keep their own
 * behaviour).
 */
public final class SpawnerAgeing {

    private static final String AGEING_SPAWNERS_PACKAGE = "com.mrbysco.ageingspawners.";
    private static final String SPAWNER_CONTROL_ID = "spawnercontrol";
    /** The spawn count Spawner Control's mixin adds to {@link BaseSpawner}. */
    private static final String SPAWNER_CONTROL_COUNT = "spawnLimit";

    private static Boolean spawnerControlLoaded;
    private static Field spawnerControlCount;
    private static boolean spawnerControlCountMissing;

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
     * Called before {@code level.removeBlock(pos, ...)} (Ageing Spawners) and
     * {@code level.destroyBlock(pos, ...)} (Spawner Control).
     *
     * @return true when the removal was turned into disabling the spawner for the nearby players,
     *         so that the block must stay
     */
    public static boolean interceptRemoval(Level level, BlockPos pos) {
        if (level != tickingLevel || !pos.equals(tickingPos) || level.isClientSide()
                || !(level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawnerBe)
                || !(spawnerBe.getSpawner() instanceof SpawnerDataAccessor accessor)
                || (accessor.multiplayerspawners$isPlayerPlaced() && ModConfig.SERVER.treatPlacedSpawnersAsVanilla.get())) {
            return false;
        }
        boolean spawnerControl = calledBySpawnerControl();
        if (!spawnerControl && !calledByAgeingSpawners()) {
            return false;
        }
        // Without its count, Spawner Control would remove the spawner on a later tick anyway.
        Field count = spawnerControl ? spawnerControlCount() : null;
        if (spawnerControl && count == null) {
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
            // Nobody to age it for (it spawns without players, for example): the other mod decides.
            return false;
        }
        if (count != null && !resetCount(count, spawnerBe.getSpawner())) {
            return false;
        }

        for (ServerPlayer player : usedUp) {
            SpawnerStateManager.disableSpawnerForPlayer(level, pos, player.getName().getString());
            Notices.send(player, Component.translatable("message.multiplayerspawners.ran_out")
                    .withStyle(ChatFormatting.GOLD));
        }
        NetworkHandler.getInstance().sendSpawnerStateToTracking(level, pos, Set.copyOf(disabledPlayers), false);
        Constants.debug("{} aged out the spawner at {}: disabled for {} instead of removing it",
                spawnerControl ? "Spawner Control" : "Ageing Spawners", pos,
                usedUp.stream().map(player -> player.getName().getString()).toList());
        return true;
    }

    private static boolean calledByAgeingSpawners() {
        return StackWalker.getInstance().walk(frames -> frames.anyMatch(
                frame -> frame.getClassName().startsWith(AGEING_SPAWNERS_PACKAGE)));
    }

    /**
     * Whether the removal was called by {@link BaseSpawner} itself: vanilla's spawner logic never
     * removes its block, Spawner Control's mixin into its {@code serverTick} does. The frames of
     * {@link Level} and of the interfaces whose default methods lead to it are skipped.
     */
    private static boolean calledBySpawnerControl() {
        if (spawnerControlLoaded == null) {
            spawnerControlLoaded = Services.PLATFORM.isModLoaded(SPAWNER_CONTROL_ID);
        }
        if (!spawnerControlLoaded) {
            return false;
        }
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames -> frames
                .dropWhile(frame -> !isLevel(frame.getDeclaringClass()))
                .dropWhile(frame -> isLevel(frame.getDeclaringClass()))
                .findFirst()
                .map(frame -> frame.getDeclaringClass() == BaseSpawner.class)
                .orElse(false));
    }

    private static boolean isLevel(Class<?> type) {
        return type.isAssignableFrom(Level.class) || Level.class.isAssignableFrom(type);
    }

    /** Spawner Control's spawn count field on {@link BaseSpawner}, or null when it has none. */
    private static Field spawnerControlCount() {
        if (spawnerControlCount == null && !spawnerControlCountMissing) {
            spawnerControlCount = Arrays.stream(BaseSpawner.class.getDeclaredFields())
                    .filter(field -> field.getType() == int.class && !Modifier.isStatic(field.getModifiers())
                            && field.getName().endsWith(SPAWNER_CONTROL_COUNT))
                    .findFirst().orElse(null);
            if (spawnerControlCount == null) {
                spawnerControlCountMissing = true;
                Constants.LOG.warn("Spawner Control has no spawn count on BaseSpawner: its spawners are removed when they run out");
            } else {
                spawnerControlCount.setAccessible(true);
            }
        }
        return spawnerControlCount;
    }

    private static boolean resetCount(Field count, BaseSpawner spawner) {
        try {
            count.setInt(spawner, 0);
            return true;
        } catch (IllegalAccessException e) {
            Constants.LOG.warn("Could not reset Spawner Control's spawn count", e);
            return false;
        }
    }
}
