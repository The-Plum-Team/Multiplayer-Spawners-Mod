package com.theplumteam.multiplayerspawners.network;

import com.theplumteam.multiplayerspawners.Constants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
//? if >=1.21 {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server-to-client: the server settings an operator can change, by their key in the server file
 * (a toggle is 0 or 1). With {@code open} the client opens the settings menu; without it the menu,
 * if it is open, shows the values the server now holds, as after one of its changes.
 */
public record SettingsPacket(Map<String, Integer> values, boolean open)
        //? if >=1.21 {
        implements CustomPacketPayload
        //?}
{

    public static final ResourceLocation ID = Constants.id("settings");
    /** More than the menu has, and few enough that a hostile packet cannot make the client allocate much. */
    private static final int MAX_VALUES = 64;
    private static final int MAX_KEY_LENGTH = 64;

    //? if >=1.21 {
    public static final CustomPacketPayload.Type<SettingsPacket> TYPE = new CustomPacketPayload.Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, SettingsPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> packet.write(buf), SettingsPacket::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    public static SettingsPacket read(FriendlyByteBuf buf) {
        boolean open = buf.readBoolean();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_VALUES) {
            throw new IllegalArgumentException("Too many settings: " + count);
        }
        Map<String, Integer> values = new LinkedHashMap<>(count);
        for (int i = 0; i < count; i++) {
            values.put(buf.readUtf(MAX_KEY_LENGTH), buf.readVarInt());
        }
        return new SettingsPacket(values, open);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(open);
        buf.writeVarInt(values.size());
        values.forEach((key, value) -> {
            buf.writeUtf(key, MAX_KEY_LENGTH);
            buf.writeVarInt(value);
        });
    }
}
