package com.viciontstudios.rp;

import com.google.gson.JsonArray;
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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class Api {
    static final Pattern KEY = Pattern.compile("^vrp1\\.([a-z0-9][a-z0-9-]{1,47})\\.[A-Za-z0-9_-]{20,64}$");
    private static final Pattern SHA1 = Pattern.compile("^[a-f0-9]{40}$");

    static final class Pack {
        final String path;
        final String sha1;
        final String url;
        final long size;

        Pack(String path, String sha1, String url, long size) {
            this.path = path;
            this.sha1 = sha1;
            this.url = url;
            this.size = size;
        }

        String name() {
            return path.substring(path.lastIndexOf('/') + 1);
        }

        byte[] hash() {
            byte[] out = new byte[20];
            for (int i = 0; i < 20; i++) out[i] = (byte) Integer.parseInt(sha1.substring(i * 2, i * 2 + 2), 16);
            return out;
        }
    }

    static final class Info {
        final String name;
        final List<Pack> packs;
        final String assets;
        final int version;
        final String mc;
        final String loader;
        final boolean launcher;
        final boolean current;
        final Boolean ticket;

        Info(String name, List<Pack> packs, String assets, int version, String mc, String loader, boolean launcher, boolean current, Boolean ticket) {
            this.name = name;
            this.packs = List.copyOf(packs);
            this.assets = assets;
            this.version = version;
            this.mc = mc;
            this.loader = loader;
            this.launcher = launcher;
            this.current = current;
            this.ticket = ticket;
        }

        String key() {
            StringBuilder b = new StringBuilder(assets == null ? "-" : assets);
            for (Pack p : packs) b.append('|').append(p.path).append('=').append(p.sha1);
            return b.toString();
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

    Info fetch(String nick, String ticket, Duration timeout) throws IOException, InterruptedException {
        if (instance == null) throw new Failure(0, "Falta la clave del plugin en config.yml (o no es válida).");
        String url = base + "/v1/rp/" + instance + (nick == null ? "" : "?nick=" + URLEncoder.encode(nick, StandardCharsets.UTF_8));
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
                .header("Authorization", "Bearer " + key).header("User-Agent", agent).header("Accept", "application/json");
        if (ticket != null && ticket.length() < 3000) req.header("X-Viciont-Ticket", ticket);
        HttpResponse<String> res = client.send(req.GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonObject o;
        try {
            o = JsonParser.parseString(res.body()).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new Failure(res.statusCode(), "Respuesta no válida del servidor de Viciont Studios (HTTP " + res.statusCode() + ")");
        }
        if (res.statusCode() != 200) throw new Failure(res.statusCode(), text(o, "message", "Error " + res.statusCode()));
        JsonObject inst = obj(o, "instance");
        String name = inst == null ? instance : text(inst, "name", instance);
        List<Pack> packs = new ArrayList<>();
        JsonElement list = o.get("packs");
        if (list != null && list.isJsonArray()) {
            for (JsonElement e : (JsonArray) list) if (e.isJsonObject()) addPack(packs, e.getAsJsonObject());
        } else if (obj(o, "pack") != null) {
            addPack(packs, obj(o, "pack"));
        }
        String assets = inst == null ? null : text(inst, "assets", null);
        if (assets != null && !SHA1.matcher(assets).matches()) assets = null;
        int version = inst != null && inst.has("version") && inst.get("version").isJsonPrimitive() ? inst.get("version").getAsInt() : 0;
        JsonObject pl = obj(o, "player");
        Boolean tk = pl != null && pl.has("ticket") ? pl.get("ticket").getAsBoolean() : null;
        return new Info(name, packs, assets, version, inst == null ? "" : text(inst, "mc", ""), inst == null ? "" : text(inst, "loader", ""),
                flag(pl, "launcher"), flag(pl, "current"), tk);
    }

    private static void addPack(List<Pack> packs, JsonObject p) {
        String sha1 = text(p, "sha1", "").toLowerCase();
        String link = text(p, "url", "");
        String path = text(p, "path", "resourcepacks/" + text(p, "name", "pack"));
        if (!SHA1.matcher(sha1).matches() || !(link.startsWith("https://") || link.startsWith("http://"))) return;
        if (packs.stream().anyMatch(x -> x.path.equalsIgnoreCase(path))) return;
        packs.add(new Pack(path, sha1, link, p.has("size") ? p.get("size").getAsLong() : 0));
    }

    private static boolean flag(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonPrimitive() && o.get(k).getAsBoolean();
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
