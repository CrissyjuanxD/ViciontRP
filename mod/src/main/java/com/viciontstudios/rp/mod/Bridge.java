package com.viciontstudios.rp.mod;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

final class Bridge {
    private static final Bridge INSTANCE = create();

    private final HttpClient client;
    private final String base;
    private final String token;

    private Bridge(String base, String token) {
        this.base = base;
        this.token = token;
        this.client = base == null ? null : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    private static Bridge create() {
        String base = System.getenv("VICIONT_BRIDGE");
        String token = System.getenv("VICIONT_BRIDGE_TOKEN");
        boolean local = base != null && base.matches("^http://127\\.0\\.0\\.1:\\d{2,5}$");
        boolean valid = token != null && token.matches("^[a-f0-9]{32,128}$");
        return local && valid ? new Bridge(base, token) : new Bridge(null, null);
    }

    static Bridge get() {
        return INSTANCE;
    }

    boolean available() {
        return client != null;
    }

    JsonObject call(String method, String path, JsonObject body, Duration timeout) throws IOException, InterruptedException {
        if (client == null) throw new IOException("Sin launcher");
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(base + path)).timeout(timeout)
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json");
        req.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        HttpResponse<String> res = client.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonObject o;
        try {
            o = JsonParser.parseString(res.body()).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IOException("Respuesta no válida del launcher (HTTP " + res.statusCode() + ")");
        }
        if (res.statusCode() != 200 || !Json.bool(o, "ok")) throw new IOException(Json.text(o, "error", "Error " + res.statusCode()));
        return o;
    }
}
