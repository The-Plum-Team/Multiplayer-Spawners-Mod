package com.theplumteam.multiplayerspawners.network;

import com.theplumteam.multiplayerspawners.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
//? if >=1.21 {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

/**
 * Client-to-server: an operator changed one setting in the settings menu. The server checks the
 * player's permission and the value's range itself (ServerSettings); nothing here is trusted.
 */
public record SettingChangePacket(String key, int value)
        //? if >=1.21 {
        implements CustomPacketPayload
        //?}
{

    public static final ResourceLocation ID = Constants.id("setting_change");
    private static final int MAX_KEY_LENGTH = 64;

    //? if >=1.21 {
    public static final CustomPacketPayload.Type<SettingChangePacket> TYPE = new CustomPacketPayload.Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, SettingChangePacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> packet.write(buf), SettingChangePacket::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    public static SettingChangePacket read(FriendlyByteBuf buf) {
        return new SettingChangePacket(buf.readUtf(MAX_KEY_LENGTH), buf.readVarInt());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(key, MAX_KEY_LENGTH);
        buf.writeVarInt(value);
    }
}
