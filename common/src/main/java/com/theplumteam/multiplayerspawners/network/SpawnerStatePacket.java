package com.theplumteam.multiplayerspawners.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import java.util.HashSet;
import java.util.Set;

/**
 * Packet sent from server to client to sync spawner disabled states
 */
public class SpawnerStatePacket {

    private final BlockPos pos;
    private final Set<String> disabledPlayers;
    private final boolean isFullSync; // true for initial sync, false for updates

    public SpawnerStatePacket(BlockPos pos, Set<String> disabledPlayers, boolean isFullSync) {
        this.pos = pos;
        this.disabledPlayers = new HashSet<>(disabledPlayers);
        this.isFullSync = isFullSync;
    }

    public SpawnerStatePacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.isFullSync = buf.readBoolean();
        int playerCount = buf.readVarInt();
        this.disabledPlayers = new HashSet<>();
        for (int i = 0; i < playerCount; i++) {
            this.disabledPlayers.add(buf.readUtf());
        }
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeBoolean(isFullSync);
        buf.writeVarInt(disabledPlayers.size());
        for (String playerName : disabledPlayers) {
            buf.writeUtf(playerName);
        }
    }

    public BlockPos getPos() {
        return pos;
    }

    public Set<String> getDisabledPlayers() {
        return disabledPlayers;
    }

    public boolean isFullSync() {
        return isFullSync;
    }
}