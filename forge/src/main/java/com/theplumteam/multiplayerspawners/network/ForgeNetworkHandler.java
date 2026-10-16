package com.theplumteam.multiplayerspawners.network;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

public class ForgeNetworkHandler extends NetworkHandler {

    private static final String PROTOCOL_VERSION = "4";
    private static SimpleChannel channel;

    @Override
    public void init() {
        channel = NetworkRegistry.newSimpleChannel(SpawnerStatePacket.ID,
                () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);
        channel.registerMessage(0, SpawnerStatePacket.class, SpawnerStatePacket::write, SpawnerStatePacket::read,
                ForgeNetworkHandler::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    private static void handle(SpawnerStatePacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> handleOnClient(packet));
        context.get().setPacketHandled(true);
    }

    @Override
    protected void sendToPlayer(ServerPlayer player, SpawnerStatePacket packet) {
        channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /**
     * Sends to the players tracking the chunk at `pos` without asking the level for that chunk:
     * this also runs from the chunk's own load event, and looking the loading chunk up from there
     * waits for itself forever.
     */
    @Override
    protected void sendToPlayersTracking(ServerLevel level, BlockPos pos, SpawnerStatePacket packet) {
        for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(new ChunkPos(pos), false)) {
            sendToPlayer(player, packet);
        }
    }
}
