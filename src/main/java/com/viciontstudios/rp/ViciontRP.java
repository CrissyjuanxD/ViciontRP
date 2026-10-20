package com.viciontstudios.rp;

import com.google.gson.Gson;
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
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ViciontRP extends JavaPlugin implements Listener {
    private static final String DEFAULT_API = "https://viciont-launcher.pepoavilesg26.workers.dev";
    private static final Set<String> BAD = Set.of("DECLINED", "FAILED_DOWNLOAD", "INVALID_URL", "FAILED_RELOAD");

    private final Map<UUID, Boolean> skip = new ConcurrentHashMap<>();
    private final Set<UUID> sent = ConcurrentHashMap.newKeySet();
    private final Set<UUID> greeted = ConcurrentHashMap.newKeySet();
    private volatile Api api;
    private volatile Api.Pack pack;
    private volatile String name = "este servidor";
    private volatile String problem;
    private volatile long checkedAt;
    private volatile UUID packId;
    private BukkitTask refresher;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        load();
    }

    @Override
    public void onDisable() {
        if (refresher != null) refresher.cancel();
    }

    private void load() {
        reloadConfig();
        if (refresher != null) refresher.cancel();
        refresher = null;
        api = new Api(getConfig().getString("api", DEFAULT_API), getConfig().getString("key", ""), "ViciontRP/" + getDescription().getVersion());
        packId = UUID.nameUUIDFromBytes(("viciontrp:" + api.instance).getBytes(StandardCharsets.UTF_8));
        problem = null;
        readCache();
        if (api.instance == null) {
            problem = "Falta la clave del plugin en config.yml";
            getLogger().warning("Falta la clave del plugin en config.yml. Créala en el launcher: Administrar instancias → tu instancia → Contenido → «Pack del servidor de Minecraft».");
            return;
        }
        long every = Math.max(15, getConfig().getLong("refresh-seconds", 60)) * 20L;
        refresher = getServer().getScheduler().runTaskTimerAsynchronously(this, this::refresh, 0L, every);
    }

    private void refresh() {
        try {
            apply(api.fetch(null, Duration.ofSeconds(10)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            fail(e);
        }
    }

    private void fail(IOException e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (!msg.equals(problem)) {
            getLogger().warning("No se pudo consultar el servidor de Viciont Studios: " + msg + (pack != null ? " (se usa el último pack conocido)" : ""));
        }
        problem = msg;
    }

    private synchronized void apply(Api.Info info) {
        if (problem != null) getLogger().info("Conectado con el servidor de Viciont Studios.");
        problem = null;
        checkedAt = System.currentTimeMillis();
        name = info.name;
        Api.Pack old = pack;
        pack = info.pack;
        String before = old == null ? null : old.sha1;
        String after = info.pack == null ? null : info.pack.sha1;
        if (Objects.equals(before, after)) return;
        writeCache();
        if (after == null) {
            getLogger().warning(name + " no tiene pack del servidor: elígelo en el launcher (Contenido) y publica.");
            return;
        }
        getLogger().info("Pack de " + name + ": " + after.substring(0, 12) + " (" + mb(info.pack.size) + ")");
        if (before != null && getConfig().getBoolean("update-online-players", true)) {
            getServer().getScheduler().runTask(this, () -> {
                for (Player p : getServer().getOnlinePlayers()) if (sent.contains(p.getUniqueId())) send(p);
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        skip.remove(e.getUniqueId());
        if (e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED || api.instance == null) return;
        try {
            Api.Info info = api.fetch(e.getName(), Duration.ofSeconds(3));
            apply(info);
            if (info.pack != null && info.launcher && info.current) skip.put(e.getUniqueId(), Boolean.TRUE);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (IOException ex) {
            fail(ex);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (Boolean.TRUE.equals(skip.remove(p.getUniqueId()))) {
            getLogger().info(p.getName() + " juega desde el launcher con el pack al día: no se le pide.");
            return;
        }
        if (pack == null || p.hasPermission("viciontrp.bypass")) return;
        long delay = Math.max(0, getConfig().getLong("join-delay-ticks", 20));
        getServer().getScheduler().runTaskLater(this, () -> {
            if (p.isOnline()) send(p);
        }, delay);
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent e) {
        Player p = e.getPlayer();
        if (!sent.contains(p.getUniqueId()) || !ours(e)) return;
        String status = e.getStatus().name();
        if (status.equals("SUCCESSFULLY_LOADED")) {
            String chat = msg("chat", p);
            if (!chat.isBlank() && greeted.add(p.getUniqueId())) p.sendMessage(chat);
            return;
        }
        if (BAD.contains(status) && getConfig().getBoolean("required", true)) {
            String reason = msg(status.equals("DECLINED") ? "declined" : "failed", p);
            getServer().getScheduler().runTask(this, () -> {
                if (p.isOnline()) p.kickPlayer(reason);
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        sent.remove(id);
        greeted.remove(id);
        skip.remove(id);
    }

    private boolean ours(PlayerResourcePackStatusEvent e) {
        try {
            return packId.equals(e.getID());
        } catch (NoSuchMethodError legacy) {
            return true;
        }
    }

    private void send(Player p) {
        Api.Pack pk = pack;
        if (pk == null) return;
        sent.add(p.getUniqueId());
        String prompt = msg("prompt", p);
        boolean required = getConfig().getBoolean("required", true);
        try {
            p.setResourcePack(packId, pk.url, pk.hash(), prompt, required);
        } catch (NoSuchMethodError legacy) {
            p.setResourcePack(pk.url, pk.hash(), prompt, required);
        }
    }

    private String msg(String key, Player p) {
        String text = getConfig().getString("messages." + key, "");
        if (text == null) return "";
        text = text.replace("{instance}", name).replace("{link}", getConfig().getString("launcher-link", "https://viciontstudios.pages.dev/#launcher"))
                .replace("{player}", p.getName());
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private static String mb(long bytes) {
        return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1048576.0);
    }

    private File cacheFile() {
        return new File(getDataFolder(), "cache.json");
    }

    private void readCache() {
        pack = null;
        try {
            File f = cacheFile();
            if (!f.isFile()) return;
            JsonObject o = JsonParser.parseString(Files.readString(f.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
            if (!Objects.equals(api.instance, o.has("instance") ? o.get("instance").getAsString() : null)) return;
            name = o.get("name").getAsString();
            pack = new Api.Pack(o.get("sha1").getAsString(), o.get("url").getAsString(), o.get("size").getAsLong());
        } catch (IOException | RuntimeException e) {
            pack = null;
        }
    }

    private void writeCache() {
        try {
            Files.createDirectories(getDataFolder().toPath());
            JsonObject o = new JsonObject();
            o.addProperty("instance", api.instance);
            o.addProperty("name", name);
            Api.Pack pk = pack;
            if (pk == null) {
                Files.deleteIfExists(cacheFile().toPath());
                return;
            }
            o.addProperty("sha1", pk.sha1);
            o.addProperty("url", pk.url);
            o.addProperty("size", pk.size);
            Files.writeString(cacheFile().toPath(), new Gson().toJson(o), StandardCharsets.UTF_8);
        } catch (IOException e) {
            getLogger().warning("No se pudo guardar cache.json: " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            load();
            s.sendMessage(ChatColor.GREEN + "ViciontRP recargado.");
            return true;
        }
        Api.Pack pk = pack;
        s.sendMessage(ChatColor.LIGHT_PURPLE + "ViciontRP " + ChatColor.GRAY + getDescription().getVersion());
        s.sendMessage(ChatColor.GRAY + "Instancia: " + ChatColor.WHITE + name + (api.instance != null ? ChatColor.DARK_GRAY + " (" + api.instance + ")" : ""));
        s.sendMessage(ChatColor.GRAY + "Pack: " + (pk == null ? ChatColor.RED + "ninguno" : ChatColor.WHITE + pk.sha1.substring(0, 12) + " · " + mb(pk.size)));
        if (problem != null) s.sendMessage(ChatColor.RED + "Problema: " + problem);
        else if (checkedAt > 0) s.sendMessage(ChatColor.GRAY + "Comprobado hace " + ((System.currentTimeMillis() - checkedAt) / 1000) + " s");
        s.sendMessage(ChatColor.GRAY + "Jugadores a los que se les mandó: " + ChatColor.WHITE + sent.size());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] args) {
        return args.length == 1 ? List.of("status", "reload") : List.of();
    }
}
