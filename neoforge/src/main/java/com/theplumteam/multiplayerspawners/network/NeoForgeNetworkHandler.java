package com.theplumteam.multiplayerspawners.network;

import com.theplumteam.multiplayerspawners.Constants;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NeoForgeNetworkHandler extends NetworkHandler {

    private static final String PROTOCOL_VERSION = "5";

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Constants.MOD_ID).versioned(PROTOCOL_VERSION);
        registrar.playToClient(
                SpawnerStatePacket.TYPE,
                SpawnerStatePacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(() -> handleOnClient(packet)));
        registrar.playToClient(
                SettingsPacket.TYPE,
                SettingsPacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(() -> handleSettingsOnClient(packet)));
        registrar.playToServer(
                SettingChangePacket.TYPE,
                SettingChangePacket.STREAM_CODEC,
                (packet, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        handleSettingChangeOnServer(player, packet);
                    }
                }));
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
    protected void sendSettingsToPlayer(ServerPlayer player, SettingsPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    @Override
    public void sendSettingChange(SettingChangePacket packet) {
        //? if >=1.21.7 {
        net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(packet);
        //?} else {
        /*PacketDistributor.sendToServer(packet);
        *///?}
    }
}
