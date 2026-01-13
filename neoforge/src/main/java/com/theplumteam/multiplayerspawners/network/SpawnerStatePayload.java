package com.theplumteam.multiplayerspawners.network;

import com.theplumteam.multiplayerspawners.Constants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * NeoForge 1.21.1 payload for spawner state synchronization
 */
public record SpawnerStatePayload(List<SpawnerStatePacket.SpawnerUpdate> updates, boolean isFullSync) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SpawnerStatePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "spawner_state"));

    public static final StreamCodec<FriendlyByteBuf, SpawnerStatePayload> STREAM_CODEC = StreamCodec.of(
            SpawnerStatePayload::write,
            SpawnerStatePayload::read
    );

    public static SpawnerStatePayload read(FriendlyByteBuf buf) {
        boolean isFullSync = buf.readBoolean();
        int updateCount = buf.readVarInt();
        List<SpawnerStatePacket.SpawnerUpdate> updates = new ArrayList<>(updateCount);

        for (int i = 0; i < updateCount; i++) {
            BlockPos pos = buf.readBlockPos();
            boolean treatedAsVanilla = buf.readBoolean();
            int playerCount = buf.readVarInt();
            Set<String> disabledPlayers = new HashSet<>(playerCount);
            for (int p = 0; p < playerCount; p++) {
                disabledPlayers.add(buf.readUtf());
            }
            updates.add(new SpawnerStatePacket.SpawnerUpdate(pos, disabledPlayers, treatedAsVanilla));
        }

        return new SpawnerStatePayload(updates, isFullSync);
    }

    public static void write(FriendlyByteBuf buf, SpawnerStatePayload payload) {
        buf.writeBoolean(payload.isFullSync());
        buf.writeVarInt(payload.updates().size());

        for (SpawnerStatePacket.SpawnerUpdate update : payload.updates()) {
            buf.writeBlockPos(update.pos);
            buf.writeBoolean(update.treatedAsVanilla);
            buf.writeVarInt(update.disabledPlayers.size());
            for (String playerName : update.disabledPlayers) {
                buf.writeUtf(playerName);
            }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
