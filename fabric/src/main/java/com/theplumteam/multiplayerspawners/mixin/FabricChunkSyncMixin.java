package com.theplumteam.multiplayerspawners.mixin;

import com.theplumteam.multiplayerspawners.network.NetworkHandler;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21 {
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
//?}

/**
 * Sends a chunk's spawner states right after the chunk itself, so that the client never renders
 * a spawner of a fresh chunk with stale state. Fabric has no event for a chunk being sent.
 */
//? if >=1.21 {
@Mixin(ServerCommonPacketListenerImpl.class)
//?} else {
/*@Mixin(ServerGamePacketListenerImpl.class)
*///?}
public abstract class FabricChunkSyncMixin {

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V", at = @At("RETURN"))
    private void multiplayerspawners$onSendPacket(Packet<?> packet, PacketSendListener listener, CallbackInfo ci) {
        if (packet instanceof ClientboundLevelChunkWithLightPacket chunkPacket
                && (Object) this instanceof ServerGamePacketListenerImpl gamePacketListener) {
            ServerPlayer player = gamePacketListener.player;
            if (player != null && player.level() instanceof ServerLevel serverLevel) {
                // The chunk is loaded, since it is being sent; never load one here.
                //? if >=26.3 {
                LevelChunk chunk = serverLevel.getChunkSource().getChunk(chunkPacket.x(), chunkPacket.z(), false);
                //?} else {
                /*LevelChunk chunk = serverLevel.getChunkSource().getChunk(chunkPacket.getX(), chunkPacket.getZ(), false);
                *///?}
                if (chunk != null) {
                    NetworkHandler.getInstance().sendChunkSpawnerStatesToPlayer(player, chunk);
                }
            }
        }
    }
}
