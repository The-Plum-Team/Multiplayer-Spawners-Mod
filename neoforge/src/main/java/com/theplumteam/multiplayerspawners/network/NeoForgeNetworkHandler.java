package com.theplumteam.multiplayerspawners.network;

import com.theplumteam.multiplayerspawners.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public class NeoForgeNetworkHandler extends NetworkHandler {

    private static final String PROTOCOL_VERSION = "4";

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar(Constants.MOD_ID).versioned(PROTOCOL_VERSION).playToClient(
                SpawnerStatePacket.TYPE,
                SpawnerStatePacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(() -> handleOnClient(packet)));
    }

    @Override
    public void init() {
        // Registration happens in registerPayloads, on the mod event bus.
    }

    @Override
    protected void sendToPlayer(ServerPlayer player, SpawnerStatePacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    @Override
    protected void sendToPlayersTracking(ServerLevel level, BlockPos pos, SpawnerStatePacket packet) {
        PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4), packet);
    }
}
