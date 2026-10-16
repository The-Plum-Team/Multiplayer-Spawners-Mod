package com.theplumteam.multiplayerspawners.network;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
//? if >=1.21 {
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
//?} else {
/*import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
*///?}

public class FabricNetworkHandler extends NetworkHandler {

    @Override
    public void init() {
        //? if >=1.21 {
        PayloadTypeRegistry.playS2C().register(SpawnerStatePacket.TYPE, SpawnerStatePacket.STREAM_CODEC);
        //?}
    }

    @Environment(EnvType.CLIENT)
    public static void initClient() {
        //? if >=1.21 {
        ClientPlayNetworking.registerGlobalReceiver(SpawnerStatePacket.TYPE,
                (packet, context) -> context.client().execute(() -> handleOnClient(packet)));
        //?} else {
        /*ClientPlayNetworking.registerGlobalReceiver(SpawnerStatePacket.ID, (client, handler, buf, sender) -> {
            SpawnerStatePacket packet = SpawnerStatePacket.read(buf);
            client.execute(() -> handleOnClient(packet));
        });
        *///?}
    }

    @Override
    protected void sendToPlayer(ServerPlayer player, SpawnerStatePacket packet) {
        //? if >=1.21 {
        ServerPlayNetworking.send(player, packet);
        //?} else {
        /*FriendlyByteBuf buf = PacketByteBufs.create();
        packet.write(buf);
        ServerPlayNetworking.send(player, SpawnerStatePacket.ID, buf);
        *///?}
    }

    @Override
    protected void sendToPlayersTracking(ServerLevel level, BlockPos pos, SpawnerStatePacket packet) {
        for (ServerPlayer player : PlayerLookup.tracking(level, pos)) {
            sendToPlayer(player, packet);
        }
    }
}
