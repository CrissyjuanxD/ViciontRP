package com.viciontstudios.rp.mod;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

final class Net {
    static final int PROTOCOL = 1;

    record Message(String json) implements CustomPacketPayload {
        static final Type<Message> TYPE = new Type<>(Ids.id("viciontrp", "main"));
        static final StreamCodec<FriendlyByteBuf, Message> CODEC = CustomPacketPayload.codec(Message::write, Message::read);

        private static Message read(FriendlyByteBuf buf) {
            byte[] b = new byte[Math.min(buf.readableBytes(), 32_000)];
            buf.readBytes(b);
            buf.skipBytes(buf.readableBytes());
            return new Message(new String(b, StandardCharsets.UTF_8));
        }

        private void write(FriendlyByteBuf buf) {
            buf.writeBytes(json.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static volatile boolean joined;
    private static ServerData accepted;
    private static ServerData.ServerPackStatus before;

    private Net() {
    }

    static void init() {
        Channels.register(Message.TYPE, Message.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(Message.TYPE, (payload, context) -> receive(payload.json()));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
            reset();
            joined = true;
            hello();
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    private static void reset() {
        joined = false;
        Assets.cancel();
        if (accepted != null && before != null) accepted.setResourcePackStatus(before);
        accepted = null;
        before = null;
    }

    private static void hello() {
        Assets.WORKER.execute(() -> {
            JsonObject m = new JsonObject();
            m.addProperty("t", "hello");
            m.addProperty("v", PROTOCOL);
            m.addProperty("mod", ViciontRPMod.version());
            Bridge bridge = Bridge.get();
            boolean launcher = false;
            if (bridge.available()) {
                try {
                    JsonObject h = bridge.call("GET", "/hello", null, Duration.ofSeconds(5));
                    launcher = true;
                    m.addProperty("instance", Json.text(h, "instance", null));
                    m.addProperty("assets", Json.text(h, "assets", null));
                    m.addProperty("launcherVersion", Json.text(h, "launcher", null));
                } catch (Exception e) {
                    Assets.LOG.warn("No se pudo hablar con el launcher: {}", e.getMessage());
                }
            }
            m.addProperty("launcher", launcher);
            Minecraft.getInstance().execute(() -> {
                send(m);
                Assets.LOG.info("Saludo enviado al servidor");
            });
        });
    }

    private static void ticket() {
        if (!Bridge.get().available()) return;
        Assets.WORKER.execute(() -> {
            try {
                JsonObject t = Bridge.get().call("POST", "/ticket", new JsonObject(), Duration.ofSeconds(15));
                JsonObject m = new JsonObject();
                m.addProperty("t", "ticket");
                m.addProperty("ticket", Json.text(t, "ticket", ""));
                Minecraft.getInstance().execute(() -> send(m));
            } catch (Exception e) {
                Assets.LOG.warn("El launcher no pudo dar el pase de la partida: {}", e.getMessage());
            }
        });
    }

    static void status(String status, String error) {
        JsonObject m = new JsonObject();
        m.addProperty("t", "status");
        m.addProperty("s", status);
        if (error != null) m.addProperty("error", error.length() > 300 ? error.substring(0, 300) : error);
        send(m);
    }

    private static void send(JsonObject m) {
        try {
            if (joined && Minecraft.getInstance().getConnection() != null) ClientPlayNetworking.send(new Message(m.toString()));
        } catch (IllegalStateException e) {
            Assets.LOG.warn("No se pudo avisar al servidor: {}", e.getMessage());
        }
    }

    private static void receive(String json) {
        JsonObject m;
        try {
            m = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            return;
        }
        String type = Json.text(m, "t", "");
        if (type.equals("hello") && Json.bool(m, "autoAccept")) accept();
        if (type.equals("hello") && Json.bool(m, "ticket")) ticket();
        if (type.equals("update")) Assets.update(Json.text(m, "assets", ""));
    }

    private static void accept() {
        ServerData server = Minecraft.getInstance().getCurrentServer();
        if (server == null || accepted == server) return;
        accepted = server;
        before = server.getResourcePackStatus();
        server.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
    }
}
