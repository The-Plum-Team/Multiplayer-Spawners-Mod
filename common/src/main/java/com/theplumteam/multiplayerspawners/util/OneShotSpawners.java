package com.theplumteam.multiplayerspawners.util;

import com.theplumteam.multiplayerspawners.Constants;
import com.theplumteam.multiplayerspawners.config.ModConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
//? if >=1.21.6 {
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
//?}

import java.util.ArrayList;
import java.util.List;

/**
 * Per-player single-use spawners: blocks of the tag multiplayerspawners:one_shot_spawners, which in
 * their own mod spawn their mobs once, when a player comes near, and then remove themselves (Quark's
 * monster box, Apotheosis' boss spawner).
 *
 * <p>Instead, the block stays: it is used up only for the players near it, and goes back to the
 * state it had before anyone used it, so it triggers again for the next players. While every
 * player near it has used it up, it does not tick. A player who mines it uses it up for themselves.
 *
 * <p>Nothing here refers to those mods' classes: the block entity's ticker is wrapped (its tag
 * decides), and a removal of that block during its own tick is caught in {@code Level.setBlock}.
 */
public final class OneShotSpawners {

    public static final TagKey<Block> TAG = TagKey.create(Registries.BLOCK, Constants.id("one_shot_spawners"));

    /** The single-use spawner whose tick is running (server thread only), or null. */
    private static BlockEntity ticking;

    private OneShotSpawners() {
    }

    public static boolean isOneShot(BlockState state) {
        return state.is(TAG);
    }

    /**
     * Before a block entity ticks. Returns false when the tick must be skipped: every player near
     * the single-use spawner has used it up already.
     */
    public static boolean beforeTick(BlockEntity blockEntity) {
        // Also forgets a tick that threw before afterTick ran.
        ticking = null;
        Level level = blockEntity.getLevel();
        if (level == null || level.isClientSide() || !isOneShot(blockEntity.getBlockState())
                || !(blockEntity instanceof OneShotData data)) {
            return true;
        }
        if (data.multiplayerspawners$getPristine() == null) {
            // Its first tick on this server: what it is before anyone has used it.
            data.multiplayerspawners$setPristine(save(blockEntity, level));
            blockEntity.setChanged();
        }
        List<Player> near = playersNear(level, blockEntity.getBlockPos());
        if (!near.isEmpty() && near.stream().allMatch(player -> data.multiplayerspawners$getUsedBy().contains(name(player)))) {
            return false;
        }
        ticking = blockEntity;
        return true;
    }

    public static void afterTick() {
        ticking = null;
    }

    /**
     * Called before {@code level.setBlock(pos, state, ...)}. Returns true when the change must not
     * happen: the single-use spawner ticking at {@code pos} is removing itself, so it is used up for
     * the players near it instead, and keeps its block.
     */
    public static boolean interceptRemoval(Level level, BlockPos pos, BlockState newState) {
        BlockEntity blockEntity = ticking;
        if (blockEntity == null || blockEntity.getLevel() != level || !blockEntity.getBlockPos().equals(pos)
                || newState.is(blockEntity.getBlockState().getBlock()) || !(blockEntity instanceof OneShotData data)) {
            return false;
        }
        List<ServerPlayer> usedUp = new ArrayList<>();
        for (Player player : playersNear(level, pos)) {
            if (player instanceof ServerPlayer serverPlayer && !data.multiplayerspawners$getUsedBy().contains(name(player))) {
                usedUp.add(serverPlayer);
            }
        }
        if (usedUp.isEmpty()) {
            // Nobody to use it up for: its own mod decides.
            return false;
        }
        Component blockName = blockEntity.getBlockState().getBlock().getName();
        for (ServerPlayer player : usedUp) {
            data.multiplayerspawners$getUsedBy().add(name(player));
            Notices.send(player, Component.empty().append(blockName)
                    .append(" has been used up for you. It stays for the other players.").withStyle(ChatFormatting.GOLD));
        }
        renew(level, pos, blockEntity, data);
        Constants.debug("Single-use spawner at {} used up for {} instead of removed", pos,
                usedUp.stream().map(OneShotSpawners::name).toList());
        return true;
    }

    /**
     * A survival player mining a single-use spawner: it is used up for them and stays for others.
     *
     * @return true when the block must not break
     */
    public static boolean onMined(Level level, BlockPos pos, ServerPlayer player) {
        if (player.isCreative() || !(level.getBlockEntity(pos) instanceof OneShotData data)
                || !isOneShot(level.getBlockState(pos))) {
            return false;
        }
        Component blockName = level.getBlockState(pos).getBlock().getName();
        if (data.multiplayerspawners$getUsedBy().add(name(player))) {
            ((BlockEntity) data).setChanged();
            Notices.send(player, Component.empty().append(blockName)
                    .append(" is now used up for you. It stays for the other players.").withStyle(ChatFormatting.GOLD));
        } else {
            Notices.send(player, Component.empty().append(blockName)
                    .append(" is already used up for you.").withStyle(ChatFormatting.GRAY));
        }
        return true;
    }

    /**
     * Replaces the block entity with a new one loaded from its pristine data, so that state its mod
     * keeps only in memory (Quark's monster box does not save its countdown) starts over too. The
     * players it is used up for carry over.
     */
    private static void renew(Level level, BlockPos pos, BlockEntity old, OneShotData data) {
        CompoundTag pristine = data.multiplayerspawners$getPristine();
        BlockEntity fresh = pristine == null ? null : old.getType().create(pos, old.getBlockState());
        if (fresh == null) {
            old.setChanged();
            return;
        }
        load(fresh, level, pristine);
        OneShotData freshData = (OneShotData) fresh;
        freshData.multiplayerspawners$getUsedBy().addAll(data.multiplayerspawners$getUsedBy());
        freshData.multiplayerspawners$setPristine(pristine);
        level.setBlockEntity(fresh);
        fresh.setChanged();
    }

    private static List<Player> playersNear(Level level, BlockPos pos) {
        double range = ModConfig.SERVER.oneShotSpawnerRange.get();
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        List<Player> near = new ArrayList<>();
        for (Player player : level.players()) {
            if (!player.isSpectator() && player.isAlive() && player.distanceToSqr(x, y, z) < range * range) {
                near.add(player);
            }
        }
        return near;
    }

    private static String name(Player player) {
        return player.getName().getString();
    }

    private static CompoundTag save(BlockEntity blockEntity, Level level) {
        //? if >=1.21 {
        return blockEntity.saveWithoutMetadata(level.registryAccess());
        //?} else {
        /*return blockEntity.saveWithoutMetadata();
        *///?}
    }

    private static void load(BlockEntity blockEntity, Level level, CompoundTag tag) {
        //? if >=1.21.6 {
        blockEntity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag.copy()));
        //?} else if >=1.21 {
        /*blockEntity.loadWithComponents(tag.copy(), level.registryAccess());
        *///?} else {
        /*blockEntity.load(tag.copy());
        *///?}
    }
}
