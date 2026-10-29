package com.theplumteam.multiplayerspawners.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

public class ForgeNetworkHandler extends NetworkHandler {

    private static final String PROTOCOL_VERSION = "5";
    private static SimpleChannel channel;

    @Override
    public void init() {
        channel = NetworkRegistry.newSimpleChannel(SpawnerStatePacket.ID,
                () -> PROTOCOL_VERSION, PROTOCOL_VERSION::equals, PROTOCOL_VERSION::equals);
        channel.registerMessage(0, SpawnerStatePacket.class, SpawnerStatePacket::write, SpawnerStatePacket::read,
                ForgeNetworkHandler::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(1, SettingsPacket.class, SettingsPacket::write, SettingsPacket::read,
                ForgeNetworkHandler::handleSettings, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel.registerMessage(2, SettingChangePacket.class, SettingChangePacket::write, SettingChangePacket::read,
                ForgeNetworkHandler::handleSettingChange, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    private static void handle(SpawnerStatePacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> handleOnClient(packet));
        context.get().setPacketHandled(true);
    }

    private static void handleSettings(SettingsPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> handleSettingsOnClient(packet));
        context.get().setPacketHandled(true);
    }

    private static void handleSettingChange(SettingChangePacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            context.get().enqueueWork(() -> handleSettingChangeOnServer(player, packet));
        }
        context.get().setPacketHandled(true);
    }

    @Override
    protected void sendToPlayer(ServerPlayer player, SpawnerStatePacket packet) {
        channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    @Override
    protected void sendSettingsToPlayer(ServerPlayer player, SettingsPacket packet) {
        channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    @Override
    public void sendSettingChange(SettingChangePacket packet) {
        channel.sendToServer(packet);
    }
}
