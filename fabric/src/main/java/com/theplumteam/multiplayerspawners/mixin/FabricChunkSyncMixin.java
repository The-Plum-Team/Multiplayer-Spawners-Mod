package com.theplumteam.multiplayerspawners.mixin;

import com.theplumteam.multiplayerspawners.network.NetworkHandler;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class FabricChunkSyncMixin {

    @Shadow @Final protected Connection connection;

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V", at = @At("RETURN"))
    private void multiplayerspawners$onSendPacket(Packet<?> packet, PacketSendListener listener, CallbackInfo ci) {
        if (packet instanceof ClientboundLevelChunkWithLightPacket chunkPacket) {
            // Get the player from this connection handler
            Object self = this;
            if (self instanceof net.minecraft.server.network.ServerGamePacketListenerImpl gamePacketListener) {
                ServerPlayer player = gamePacketListener.player;
                if (player != null && player.level() instanceof ServerLevel serverLevel) {
                    int x = chunkPacket.getX();
                    int z = chunkPacket.getZ();

                    // Get the chunk without forcing a load (it must be loaded if we are sending it)
                    LevelChunk chunk = serverLevel.getChunkSource().getChunk(x, z, false);

                    if (chunk != null) {
                        // Send the spawner state "sidecar" packet
                        NetworkHandler.getInstance().sendChunkSpawnerStatesToPlayer(player, chunk);
                    }
                }
            }
        }
    }
}
