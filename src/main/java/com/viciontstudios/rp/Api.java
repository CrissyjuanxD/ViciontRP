package com.viciontstudios.rp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Api {
    static final Pattern KEY = Pattern.compile("^vrp1\\.([a-z0-9][a-z0-9-]{1,47})\\.[A-Za-z0-9_-]{20,64}$");
    private static final Pattern SHA1 = Pattern.compile("^[a-f0-9]{40}$");

    static final class Pack {
        final String sha1;
        final String url;
        final long size;

        Pack(String sha1, String url, long size) {
            this.sha1 = sha1;
            this.url = url;
            this.size = size;
        }

        byte[] hash() {
            byte[] out = new byte[20];
            for (int i = 0; i < 20; i++) out[i] = (byte) Integer.parseInt(sha1.substring(i * 2, i * 2 + 2), 16);
            return out;
        }
    }

    static final class Info {
        final String name;
        final Pack pack;
        final boolean launcher;
        final boolean current;

        Info(String name, Pack pack, boolean launcher, boolean current) {
            this.name = name;
            this.pack = pack;
            this.launcher = launcher;
            this.current = current;
        }
    }

    static final class Failure extends IOException {
        final int status;

        Failure(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private final String base;
    private final String key;
    final String instance;
    private final String agent;

    Api(String base, String key, String agent) {
        this.base = base.replaceAll("/+$", "");
        this.key = key == null ? "" : key.trim();
        Matcher m = KEY.matcher(this.key);
        this.instance = m.matches() ? m.group(1) : null;
        this.agent = agent;
    }

    Info fetch(String nick, Duration timeout) throws IOException, InterruptedException {
        if (instance == null) throw new Failure(0, "Falta la clave del plugin en config.yml (o no es válida).");
        String url = base + "/v1/rp/" + instance + (nick == null ? "" : "?nick=" + URLEncoder.encode(nick, StandardCharsets.UTF_8));
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
                .header("Authorization", "Bearer " + key).header("User-Agent", agent).header("Accept", "application/json").GET().build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonObject o;
        try {
            o = JsonParser.parseString(res.body()).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new Failure(res.statusCode(), "Respuesta no válida del servidor de Viciont Studios (HTTP " + res.statusCode() + ")");
        }
        if (res.statusCode() != 200) throw new Failure(res.statusCode(), text(o, "message", "Error " + res.statusCode()));
        JsonObject inst = obj(o, "instance");
        String name = inst == null ? instance : text(inst, "name", instance);
        Pack pack = null;
        JsonObject p = obj(o, "pack");
        if (p != null) {
            String sha1 = text(p, "sha1", "").toLowerCase();
            String link = text(p, "url", "");
            if (SHA1.matcher(sha1).matches() && (link.startsWith("https://") || link.startsWith("http://"))) pack = new Pack(sha1, link, p.has("size") ? p.get("size").getAsLong() : 0);
        }
        JsonObject pl = obj(o, "player");
        boolean launcher = pl != null && pl.has("launcher") && pl.get("launcher").getAsBoolean();
        boolean current = pl != null && pl.has("current") && pl.get("current").getAsBoolean();
        return new Info(name, pack, launcher, current);
    }

    private static JsonObject obj(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static String text(JsonObject o, String k, String def) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
    }
}
