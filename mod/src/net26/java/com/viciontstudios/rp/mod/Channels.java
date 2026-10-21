package com.viciontstudios.rp.mod;

import net.fabricmc.fabric.api.client.networking.v1.ServerboundPlayChannelEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.function.Consumer;

final class Channels {
    private Channels() {
    }

    static <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type, StreamCodec<FriendlyByteBuf, T> codec) {
        PayloadTypeRegistry.clientboundPlay().register(type, codec);
        PayloadTypeRegistry.serverboundPlay().register(type, codec);
    }

    static void onServerChannels(Consumer<List<?>> listener) {
        ServerboundPlayChannelEvents.REGISTER.register((handler, sender, client, channels) -> listener.accept(channels));
    }
}
