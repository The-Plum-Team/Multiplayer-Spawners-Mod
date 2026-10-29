package com.theplumteam.multiplayerspawners.network;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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
        PayloadTypeRegistry.playS2C().register(SettingsPacket.TYPE, SettingsPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SettingChangePacket.TYPE, SettingChangePacket.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SettingChangePacket.TYPE,
                (packet, context) -> context.server().execute(() -> handleSettingChangeOnServer(context.player(), packet)));
        //?} else {
        /*ServerPlayNetworking.registerGlobalReceiver(SettingChangePacket.ID, (server, player, handler, buf, sender) -> {
            SettingChangePacket packet = SettingChangePacket.read(buf);
            server.execute(() -> handleSettingChangeOnServer(player, packet));
        });
        *///?}
    }

    @Environment(EnvType.CLIENT)
    public static void initClient() {
        //? if >=1.21 {
        ClientPlayNetworking.registerGlobalReceiver(SpawnerStatePacket.TYPE,
                (packet, context) -> context.client().execute(() -> handleOnClient(packet)));
        ClientPlayNetworking.registerGlobalReceiver(SettingsPacket.TYPE,
                (packet, context) -> context.client().execute(() -> handleSettingsOnClient(packet)));
        //?} else {
        /*ClientPlayNetworking.registerGlobalReceiver(SpawnerStatePacket.ID, (client, handler, buf, sender) -> {
            SpawnerStatePacket packet = SpawnerStatePacket.read(buf);
            client.execute(() -> handleOnClient(packet));
        });
        ClientPlayNetworking.registerGlobalReceiver(SettingsPacket.ID, (client, handler, buf, sender) -> {
            SettingsPacket packet = SettingsPacket.read(buf);
            client.execute(() -> handleSettingsOnClient(packet));
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
    protected void sendSettingsToPlayer(ServerPlayer player, SettingsPacket packet) {
        //? if >=1.21 {
        ServerPlayNetworking.send(player, packet);
        //?} else {
        /*FriendlyByteBuf buf = PacketByteBufs.create();
        packet.write(buf);
        ServerPlayNetworking.send(player, SettingsPacket.ID, buf);
        *///?}
    }

    @Override
    public boolean canOpenSettings(ServerPlayer player) {
        //? if >=1.21 {
        return ServerPlayNetworking.canSend(player, SettingsPacket.TYPE);
        //?} else {
        /*return ServerPlayNetworking.canSend(player, SettingsPacket.ID);
        *///?}
    }

    @Override
    public void sendSettingChange(SettingChangePacket packet) {
        //? if >=1.21 {
        ClientPlayNetworking.send(packet);
        //?} else {
        /*FriendlyByteBuf buf = PacketByteBufs.create();
        packet.write(buf);
        ClientPlayNetworking.send(SettingChangePacket.ID, buf);
        *///?}
    }
}
