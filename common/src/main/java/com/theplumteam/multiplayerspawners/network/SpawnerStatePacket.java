package com.theplumteam.multiplayerspawners.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Batched packet sent from server to client to sync spawner disabled states.
 * Now supports multiple spawner updates in a single network call.
 */
public class SpawnerStatePacket {

    private final List<SpawnerUpdate> updates;
    private final boolean isFullSync;

    // Inner class to represent a single spawner's state
    public static class SpawnerUpdate {
        public final BlockPos pos;
        public final Set<String> disabledPlayers;
        public final boolean treatedAsVanilla;

        public SpawnerUpdate(BlockPos pos, Set<String> disabledPlayers, boolean treatedAsVanilla) {
            this.pos = pos;
            this.disabledPlayers = disabledPlayers;
            this.treatedAsVanilla = treatedAsVanilla;
        }
    }

    // Constructor for a single update (convenience)
    public SpawnerStatePacket(BlockPos pos, Set<String> disabledPlayers, boolean isFullSync, boolean treatedAsVanilla) {
        this.updates = new ArrayList<>();
        this.updates.add(new SpawnerUpdate(pos, new HashSet<>(disabledPlayers), treatedAsVanilla));
        this.isFullSync = isFullSync;
    }

    // Constructor for batched updates
    public SpawnerStatePacket(List<SpawnerUpdate> updates, boolean isFullSync) {
        this.updates = updates;
        this.isFullSync = isFullSync;
    }

    public SpawnerStatePacket(FriendlyByteBuf buf) {
        this.isFullSync = buf.readBoolean();
        int updateCount = buf.readVarInt();
        this.updates = new ArrayList<>(updateCount);

        for (int i = 0; i < updateCount; i++) {
            BlockPos pos = buf.readBlockPos();
            boolean treatedAsVanilla = buf.readBoolean();
            int playerCount = buf.readVarInt();
            Set<String> disabledPlayers = new HashSet<>(playerCount);
            for (int p = 0; p < playerCount; p++) {
                disabledPlayers.add(buf.readUtf());
            }
            this.updates.add(new SpawnerUpdate(pos, disabledPlayers, treatedAsVanilla));
        }
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(isFullSync);
        buf.writeVarInt(updates.size());

        for (SpawnerUpdate update : updates) {
            buf.writeBlockPos(update.pos);
            buf.writeBoolean(update.treatedAsVanilla);
            buf.writeVarInt(update.disabledPlayers.size());
            for (String playerName : update.disabledPlayers) {
                buf.writeUtf(playerName);
            }
        }
    }

    public List<SpawnerUpdate> getUpdates() {
        return updates;
    }

    public boolean isFullSync() {
        return isFullSync;
    }
}
