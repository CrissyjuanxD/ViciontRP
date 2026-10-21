package com.viciontstudios.rp;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ViciontRP extends JavaPlugin implements Listener, PluginMessageListener {
    static final String CHANNEL = "viciontrp:main";
    private static final String DEFAULT_API = "https://viciont-launcher.pepoavilesg26.workers.dev";
    private static final Set<String> BAD = Set.of("DECLINED", "FAILED_DOWNLOAD", "INVALID_URL", "FAILED_RELOAD");
    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private static final long HELLO_WAIT_TICKS = 160;
    private static final long PROOF_TICKS = 300;
    private static final String PURPLE = "&#C8A2F8";
    private static final String PINK = "&#F8B4D9";
    private static final String BLUE = "&#A7D8F5";

    private static final class Login {
        final boolean launcher;
        final boolean current;
        final boolean unknown;

        Login(boolean launcher, boolean current, boolean unknown) {
            this.launcher = launcher;
            this.current = current;
            this.unknown = unknown;
        }
    }

    private static final class Client {
        final boolean launcher;
        volatile String assets;
        final String ticket;
        volatile Boolean verified;

        Client(boolean launcher, String assets, String ticket) {
            this.launcher = launcher;
            this.assets = assets;
            this.ticket = ticket;
        }
    }

    private final Map<UUID, Login> logins = new ConcurrentHashMap<>();
    private final Map<UUID, Client> clients = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, String>> given = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> waiting = new ConcurrentHashMap<>();
    private final Set<UUID> greeted = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> updating = new ConcurrentHashMap<>();
    private volatile Api api;
    private volatile Api.Info info;
    private volatile String shownKey;
    private volatile String problem;
    private volatile long checkedAt;
    private BukkitTask refresher;
    private BukkitTask countdown;
    private long countdownEnds;
    private boolean modern;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            Player.class.getMethod("addResourcePack", UUID.class, String.class, byte[].class, String.class, boolean.class);
            modern = true;
        } catch (NoSuchMethodException e) {
            modern = false;
        }
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        load();
    }

    @Override
    public void onDisable() {
        if (refresher != null) refresher.cancel();
        if (countdown != null) countdown.cancel();
    }

    private void load() {
        reloadConfig();
        if (refresher != null) refresher.cancel();
        refresher = null;
        api = new Api(getConfig().getString("api", DEFAULT_API), getConfig().getString("key", ""), "ViciontRP/" + getDescription().getVersion());
        problem = null;
        readCache();
        shownKey = info == null ? null : info.key();
        if (api.instance == null) {
            problem = "Falta la clave del plugin en config.yml";
            getLogger().warning("Falta la clave del plugin en config.yml. Créala en el launcher: Administrar instancias → tu instancia → Contenido → «Pack del servidor de Minecraft».");
            return;
        }
        getLogger().info("Modo " + (automatic() ? "automático" : "manual") + (launcherOnly() ? " · solo se puede entrar con el launcher" : ""));
        long every = Math.max(15, getConfig().getLong("refresh-seconds", 60)) * 20L;
        refresher = getServer().getScheduler().runTaskTimerAsynchronously(this, this::refresh, 0L, every);
    }

    private boolean automatic() {
        if (!getConfig().contains("update-mode", true)) return getConfig().getBoolean("update-online-players", true);
        return !"manual".equalsIgnoreCase(getConfig().getString("update-mode", "automatic"));
    }

    private boolean launcherOnly() {
        return "launcher-only".equalsIgnoreCase(getConfig().getString("access", "everyone"));
    }

    private boolean required() {
        return getConfig().getBoolean("required", true);
    }

    private void refresh() {
        try {
            apply(api.fetch(null, null, Duration.ofSeconds(10)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            fail(e);
        }
    }

    private void fail(IOException e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (!msg.equals(problem)) {
            getLogger().warning("No se pudo consultar el servidor de Viciont Studios: " + msg + (info != null ? " (se usan los últimos packs conocidos)" : ""));
        }
        problem = msg;
    }

    private synchronized void apply(Api.Info fresh) {
        if (problem != null) getLogger().info("Conectado con el servidor de Viciont Studios.");
        problem = null;
        checkedAt = System.currentTimeMillis();
        Api.Info old = info;
        info = fresh;
        if (old != null && Objects.equals(old.key(), fresh.key())) return;
        writeCache();
        if (fresh.packs.isEmpty()) getLogger().warning(fresh.name + " no tiene pack del servidor: elígelo en el launcher (Contenido) y publica.");
        else getLogger().info("Packs de " + fresh.name + ": " + describe(fresh));
        if (shownKey == null || old == null) {
            shownKey = fresh.key();
            return;
        }
        getServer().getScheduler().runTask(this, this::changed);
    }

    private static String describe(Api.Info i) {
        List<String> out = new ArrayList<>();
        for (Api.Pack p : i.packs) out.add(p.name() + " " + p.sha1.substring(0, 12) + " (" + mb(p.size) + ")");
        return String.join(", ", out);
    }

    private void changed() {
        if (!pending()) return;
        if (affected().isEmpty()) {
            shownKey = info.key();
            return;
        }
        if (automatic()) {
            startUpdate(false);
            return;
        }
        getLogger().info("Hay recursos nuevos de " + info.name + ": escribe /viciontrp actualizar para mandarlos a quien está jugando.");
        for (Player p : getServer().getOnlinePlayers()) if (p.hasPermission("viciontrp.admin")) p.sendMessage(msg("pending", p));
    }

    private boolean pending() {
        Api.Info now = info;
        return now != null && !now.key().equals(shownKey);
    }

    private List<Player> affected() {
        List<Player> out = new ArrayList<>();
        for (Player p : getServer().getOnlinePlayers()) if (!p.hasPermission("viciontrp.bypass")) out.add(p);
        return out;
    }

    private boolean startUpdate(boolean now) {
        if (countdown != null) return false;
        long secs = now ? 0 : Math.max(0, getConfig().getLong("warning-seconds", 30));
        if (secs == 0) {
            push();
            return true;
        }
        for (Player p : affected()) p.sendMessage(msg("warning", p).replace("{seconds}", String.valueOf(secs)));
        countdownEnds = System.currentTimeMillis() + secs * 1000;
        countdown = getServer().getScheduler().runTaskLater(this, () -> {
            countdown = null;
            push();
        }, secs * 20L);
        return true;
    }

    private void push() {
        Api.Info now = info;
        if (now == null) return;
        shownKey = now.key();
        int mod = 0;
        int packs = 0;
        for (Player p : affected()) {
            Client c = clients.get(p.getUniqueId());
            if (c != null && c.launcher) {
                if (now.assets == null || !now.assets.equals(c.assets)) {
                    update(p, now);
                    mod++;
                }
                continue;
            }
            if (sendPacks(p, now)) packs++;
        }
        getLogger().info("Recursos actualizados: " + mod + " desde el launcher, " + packs + " con el pack del servidor.");
    }

    private void update(Player p, Api.Info now) {
        updating.put(p.getUniqueId(), now.assets == null ? "" : now.assets);
        JsonObject m = new JsonObject();
        m.addProperty("t", "update");
        m.addProperty("assets", now.assets == null ? "" : now.assets);
        m.addProperty("version", now.version);
        send(p, m);
    }

    private UUID packId(Api.Pack pk) {
        return UUID.nameUUIDFromBytes(("viciontrp:" + api.instance + ":" + pk.path.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8));
    }

    private boolean sendPacks(Player p, Api.Info now) {
        Map<UUID, String> had = given.computeIfAbsent(p.getUniqueId(), k -> new ConcurrentHashMap<>());
        String prompt = msg("prompt", p);
        boolean required = required();
        boolean sent = false;
        if (modern) {
            Set<UUID> keep = new HashSet<>();
            for (Api.Pack pk : now.packs) keep.add(packId(pk));
            for (UUID u : new ArrayList<>(had.keySet())) {
                if (keep.contains(u)) continue;
                p.removeResourcePack(u);
                had.remove(u);
            }
            for (Api.Pack pk : now.packs) {
                UUID u = packId(pk);
                if (pk.sha1.equals(had.get(u))) continue;
                had.put(u, pk.sha1);
                p.addResourcePack(u, pk.url, pk.hash(), prompt, required);
                sent = true;
            }
            return sent;
        }
        if (now.packs.isEmpty()) return false;
        Api.Pack pk = now.packs.get(0);
        UUID u = packId(pk);
        if (pk.sha1.equals(had.get(u))) return false;
        had.clear();
        had.put(u, pk.sha1);
        try {
            p.setResourcePack(u, pk.url, pk.hash(), prompt, required);
        } catch (NoSuchMethodError legacy) {
            p.setResourcePack(pk.url, pk.hash(), prompt, required);
        }
        return true;
    }

    private void send(Player p, JsonObject m) {
        if (p.isOnline()) p.sendPluginMessage(this, CHANNEL, m.toString().getBytes(StandardCharsets.UTF_8));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        logins.remove(e.getUniqueId());
        if (e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED || api.instance == null) return;
        try {
            Api.Info i = api.fetch(e.getName(), null, Duration.ofSeconds(3));
            apply(i);
            logins.put(e.getUniqueId(), new Login(i.launcher, i.current, false));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (IOException ex) {
            fail(ex);
            logins.put(e.getUniqueId(), new Login(false, false, true));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        Login l = logins.getOrDefault(id, new Login(false, false, true));
        if (p.hasPermission("viciontrp.bypass")) return;
        if (launcherOnly() && !l.launcher) {
            getServer().getScheduler().runTaskLater(this, () -> {
                if (p.isOnline() && !proven(p)) deny(p);
            }, PROOF_TICKS);
        }
        long delay = Math.max(0, getConfig().getLong("join-delay-ticks", 20));
        getServer().getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline() || waiting.containsKey(id)) return;
            if (clients.containsKey(id) || !p.getListeningPluginChannels().contains(CHANNEL)) {
                decide(p);
                return;
            }
            waiting.put(id, getServer().getScheduler().runTaskLater(this, () -> {
                if (waiting.remove(id) != null && p.isOnline()) decide(p);
            }, HELLO_WAIT_TICKS));
        }, delay);
    }

    private boolean proven(Player p) {
        Login l = logins.get(p.getUniqueId());
        if (l != null && (l.launcher || l.unknown)) return true;
        Client c = clients.get(p.getUniqueId());
        return c != null && (Boolean.TRUE.equals(c.verified) || (c.ticket != null && c.verified == null && problem != null));
    }

    private void deny(Player p) {
        getLogger().info(p.getName() + " intentó entrar sin el launcher de Viciont Studios.");
        p.kickPlayer(msg("launcher-only", p));
    }

    private void decide(Player p) {
        Api.Info now = info;
        if (now == null) return;
        UUID id = p.getUniqueId();
        Login l = logins.getOrDefault(id, new Login(false, false, true));
        Client c = clients.get(id);
        if (c != null && c.launcher) {
            boolean behind = now.assets != null ? !now.assets.equals(c.assets) : !l.current;
            if (behind) update(p, now);
            else getLogger().info(p.getName() + " juega desde el launcher con los recursos al día.");
            return;
        }
        if (l.launcher && l.current) {
            getLogger().info(p.getName() + " juega desde el launcher con el pack al día: no se le pide.");
            return;
        }
        sendPacks(p, now);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player p, byte[] data) {
        if (!CHANNEL.equals(channel) || data.length > 32_000) return;
        JsonObject m;
        try {
            m = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException e) {
            return;
        }
        String type = text(m, "t");
        if ("hello".equals(type)) hello(p, m);
        else if ("status".equals(type)) status(p, m);
    }

    private void hello(Player p, JsonObject m) {
        UUID id = p.getUniqueId();
        Client c = new Client(bool(m, "launcher"), sha1(text(m, "assets")), text(m, "ticket"));
        clients.put(id, c);
        JsonObject ack = new JsonObject();
        ack.addProperty("t", "hello");
        ack.addProperty("v", 1);
        ack.addProperty("autoAccept", required());
        send(p, ack);
        if (c.ticket != null && api.instance != null) {
            String name = p.getName();
            getServer().getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    Api.Info i = api.fetch(name, c.ticket, Duration.ofSeconds(8));
                    c.verified = Boolean.TRUE.equals(i.ticket);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (IOException e) {
                    fail(e);
                }
                if (Boolean.FALSE.equals(c.verified) && launcherOnly()) {
                    getServer().getScheduler().runTask(this, () -> {
                        if (p.isOnline() && !p.hasPermission("viciontrp.bypass") && !proven(p)) deny(p);
                    });
                }
            });
        }
        BukkitTask w = waiting.remove(id);
        if (w != null) {
            w.cancel();
            decide(p);
        }
    }

    private void status(Player p, JsonObject m) {
        String s = text(m, "s");
        String target = updating.remove(p.getUniqueId());
        if (target == null) return;
        if ("done".equals(s) || "nothing".equals(s)) {
            Client c = clients.get(p.getUniqueId());
            if (c != null && !target.isEmpty()) c.assets = target;
            getLogger().info(p.getName() + ": recursos actualizados desde el launcher.");
            return;
        }
        String err = text(m, "error");
        getLogger().warning(p.getName() + ": el launcher no pudo actualizar los recursos" + (err != null ? " (" + err + ")" : "") + ". Se le manda el pack del servidor.");
        Api.Info now = info;
        if (now != null) sendPacks(p, now);
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent e) {
        Player p = e.getPlayer();
        Map<UUID, String> had = given.get(p.getUniqueId());
        if (had == null || had.isEmpty() || !ours(e, had)) return;
        String status = e.getStatus().name();
        if (status.equals("SUCCESSFULLY_LOADED")) {
            String chat = msg("chat", p);
            if (!ChatColor.stripColor(chat).isBlank() && greeted.add(p.getUniqueId())) p.sendMessage(chat);
            return;
        }
        if (BAD.contains(status) && required()) {
            String reason = msg(status.equals("DECLINED") ? "declined" : "failed", p);
            getServer().getScheduler().runTask(this, () -> {
                if (p.isOnline()) p.kickPlayer(reason);
            });
        }
    }

    private static boolean ours(PlayerResourcePackStatusEvent e, Map<UUID, String> had) {
        try {
            return had.containsKey(e.getID());
        } catch (NoSuchMethodError legacy) {
            return true;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        given.remove(id);
        greeted.remove(id);
        logins.remove(id);
        clients.remove(id);
        updating.remove(id);
        BukkitTask w = waiting.remove(id);
        if (w != null) w.cancel();
    }

    private String msg(String key, Player p) {
        String text = getConfig().getString("messages." + key, "");
        if (text == null) return "";
        Api.Info now = info;
        text = text.replace("{instance}", now == null ? "este servidor" : now.name)
                .replace("{link}", getConfig().getString("launcher-link", "https://viciontstudios.pages.dev/#launcher"))
                .replace("{player}", p == null ? "" : p.getName());
        return color(text);
    }

    static String color(String text) {
        Matcher m = HEX.matcher(text);
        StringBuilder b = new StringBuilder();
        while (m.find()) {
            StringBuilder r = new StringBuilder("§x");
            for (char ch : m.group(1).toLowerCase(Locale.ROOT).toCharArray()) r.append('§').append(ch);
            m.appendReplacement(b, Matcher.quoteReplacement(r.toString()));
        }
        m.appendTail(b);
        return ChatColor.translateAlternateColorCodes('&', b.toString());
    }

    private static String text(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    private static boolean bool(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
    }

    private static String sha1(String s) {
        return s != null && s.matches("^[a-f0-9]{40}$") ? s : null;
    }

    private static String mb(long bytes) {
        return String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0);
    }

    private File cacheFile() {
        return new File(getDataFolder(), "cache.json");
    }

    private void readCache() {
        info = null;
        try {
            File f = cacheFile();
            if (!f.isFile()) return;
            JsonObject o = JsonParser.parseString(Files.readString(f.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
            if (!Objects.equals(api.instance, text(o, "instance"))) return;
            List<Api.Pack> packs = new ArrayList<>();
            JsonElement list = o.get("packs");
            if (list != null && list.isJsonArray()) {
                for (JsonElement e : (JsonArray) list) {
                    JsonObject p = e.getAsJsonObject();
                    packs.add(new Api.Pack(text(p, "path"), text(p, "sha1"), text(p, "url"), p.get("size").getAsLong()));
                }
            } else if (text(o, "sha1") != null) {
                packs.add(new Api.Pack("resourcepacks/pack", text(o, "sha1"), text(o, "url"), o.get("size").getAsLong()));
            }
            info = new Api.Info(text(o, "name"), packs, sha1(text(o, "assets")), o.has("version") ? o.get("version").getAsInt() : 0, "", "", false, false, null);
        } catch (IOException | RuntimeException e) {
            info = null;
        }
    }

    private void writeCache() {
        try {
            Files.createDirectories(getDataFolder().toPath());
            Api.Info now = info;
            if (now == null) return;
            JsonObject o = new JsonObject();
            o.addProperty("instance", api.instance);
            o.addProperty("name", now.name);
            o.addProperty("assets", now.assets);
            o.addProperty("version", now.version);
            JsonArray list = new JsonArray();
            for (Api.Pack pk : now.packs) {
                JsonObject p = new JsonObject();
                p.addProperty("path", pk.path);
                p.addProperty("sha1", pk.sha1);
                p.addProperty("url", pk.url);
                p.addProperty("size", pk.size);
                list.add(p);
            }
            o.add("packs", list);
            Files.writeString(cacheFile().toPath(), new Gson().toJson(o), StandardCharsets.UTF_8);
        } catch (IOException e) {
            getLogger().warning("No se pudo guardar cache.json: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "status";
        if (sub.equals("reload")) {
            load();
            s.sendMessage(color(PURPLE + "ViciontRP recargado."));
            return true;
        }
        if (sub.equals("actualizar") || sub.equals("update")) {
            boolean now = args.length > 1 && (args[1].equalsIgnoreCase("ahora") || args[1].equalsIgnoreCase("now"));
            actualizar(s, now);
            return true;
        }
        status(s);
        return true;
    }

    private void actualizar(CommandSender s, boolean now) {
        if (api.instance == null) {
            s.sendMessage(color(PINK + "Falta la clave del plugin en config.yml."));
            return;
        }
        s.sendMessage(color(PURPLE + "Buscando recursos nuevos…"));
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            refresh();
            getServer().getScheduler().runTask(this, () -> {
                if (countdown != null) {
                    long left = Math.max(1, (countdownEnds - System.currentTimeMillis()) / 1000);
                    s.sendMessage(color(PURPLE + "Ya hay una actualización en marcha: faltan " + PINK + left + " s" + PURPLE + "."));
                    return;
                }
                if (!pending()) {
                    s.sendMessage(color(PURPLE + "Todos tienen ya los recursos de la última versión publicada."));
                    return;
                }
                if (affected().isEmpty()) {
                    shownKey = info.key();
                    s.sendMessage(color(PURPLE + "No hay nadie jugando: los que entren recibirán los recursos nuevos."));
                    return;
                }
                startUpdate(now);
                long secs = now ? 0 : Math.max(0, getConfig().getLong("warning-seconds", 30));
                s.sendMessage(color(PURPLE + (secs == 0 ? "Actualizando los recursos de todos." : "Los recursos se actualizarán en " + PINK + secs + " s" + PURPLE + ".")));
            });
        });
    }

    private void status(CommandSender s) {
        Api.Info now = info;
        s.sendMessage(color(PURPLE + "ViciontRP " + BLUE + getDescription().getVersion()));
        s.sendMessage(color(PURPLE + "Instancia: &f" + (now == null ? "?" : now.name) + (api.instance != null ? " &8(" + api.instance + ")" : "")));
        if (now == null || now.packs.isEmpty()) s.sendMessage(color(PURPLE + "Packs del servidor: " + PINK + "ninguno"));
        else for (Api.Pack pk : now.packs) s.sendMessage(color(PURPLE + "Pack: &f" + pk.name() + " &8" + pk.sha1.substring(0, 12) + " · " + mb(pk.size)));
        s.sendMessage(color(PURPLE + "Modo: &f" + (automatic() ? "automático" : "manual") + PURPLE + " · Acceso: &f" + (launcherOnly() ? "solo con el launcher" : "con o sin el launcher")));
        if (pending()) s.sendMessage(color(PINK + "Hay recursos nuevos sin mandar a quien está jugando: /viciontrp actualizar"));
        if (problem != null) s.sendMessage(color(PINK + "Problema: " + problem));
        else if (checkedAt > 0) s.sendMessage(color(PURPLE + "Comprobado hace " + ((System.currentTimeMillis() - checkedAt) / 1000) + " s"));
        long launcher = clients.values().stream().filter(x -> x.launcher).count();
        s.sendMessage(color(PURPLE + "Jugando: &f" + launcher + PURPLE + " con el launcher y el mod · &f" + given.size() + PURPLE + " con el pack del servidor"));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] args) {
        if (args.length == 1) return List.of("status", "actualizar", "reload");
        if (args.length == 2 && args[0].equalsIgnoreCase("actualizar")) return List.of("ahora");
        return List.of();
    }
}
