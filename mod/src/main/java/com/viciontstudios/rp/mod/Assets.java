package com.viciontstudios.rp.mod;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class Assets {
    enum Phase { IDLE, CHECKING, DOWNLOADING, APPLYING }

    static final Logger LOG = LoggerFactory.getLogger("ViciontRP");
    static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ViciontRP");
        t.setDaemon(true);
        return t;
    });
    private static final long MAX_WAIT_MS = 15 * 60_000L;

    static volatile Phase phase = Phase.IDLE;
    static volatile long done;
    static volatile long total;
    static volatile long files;
    static volatile long filesDone;
    private static volatile boolean swap;
    private static volatile String swapError;
    private static volatile int run;

    private Assets() {
    }

    static float progress() {
        if (phase == Phase.APPLYING) return 1f;
        return total > 0 ? Math.min(1f, (float) done / total) : 0f;
    }

    static void update(String target) {
        if (phase != Phase.IDLE) return;
        Bridge bridge = Bridge.get();
        if (!bridge.available()) {
            Net.status("unsupported", "Sin el launcher de Viciont Studios");
            return;
        }
        int id = ++run;
        phase = Phase.CHECKING;
        done = 0;
        total = 0;
        files = 0;
        filesDone = 0;
        LOG.info("Actualizando los assets de la instancia ({})", target);
        WORKER.execute(() -> prepare(id, target));
    }

    static void cancel() {
        if (phase != Phase.CHECKING && phase != Phase.DOWNLOADING) return;
        run++;
        phase = Phase.IDLE;
        WORKER.execute(() -> {
            try {
                Bridge.get().call("POST", "/assets/cancel", new JsonObject(), Duration.ofSeconds(5));
            } catch (Exception ignored) {
            }
        });
    }

    private static void prepare(int id, String target) {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("assets", target);
            Bridge.get().call("POST", "/assets/prepare", body, Duration.ofSeconds(20));
            long end = System.currentTimeMillis() + MAX_WAIT_MS;
            while (run == id) {
                JsonObject s = Bridge.get().call("GET", "/assets/status", null, Duration.ofSeconds(10));
                String state = Json.text(s, "state", "");
                done = Json.number(s, "done");
                total = Json.number(s, "total");
                files = Json.number(s, "files");
                filesDone = Json.number(s, "filesDone");
                if (state.equals("downloading")) phase = Phase.DOWNLOADING;
                if (state.equals("ready")) {
                    boolean replace = Json.bool(s, "replace");
                    List<String> enable = Json.list(s, "enable");
                    Minecraft.getInstance().execute(() -> {
                        if (run == id) reload(id, replace, enable);
                    });
                    return;
                }
                if (state.equals("nothing")) {
                    finish(id, "nothing", null);
                    return;
                }
                if (state.equals("failed")) {
                    finish(id, "failed", Json.text(s, "error", "No se pudieron descargar los assets"));
                    return;
                }
                if (System.currentTimeMillis() > end) throw new IllegalStateException("La descarga tardó demasiado");
                Thread.sleep(250);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOG.warn("No se pudieron actualizar los assets: {}", e.getMessage());
            finish(id, "failed", e.getMessage());
        }
    }

    private static void reload(int id, boolean replace, List<String> enable) {
        Minecraft mc = Minecraft.getInstance();
        phase = Phase.APPLYING;
        swap = replace;
        swapError = null;
        PackRepository repo = mc.getResourcePackRepository();
        if (!enable.isEmpty()) {
            repo.reload();
            List<String> ids = new ArrayList<>(repo.getSelectedIds());
            for (String name : enable) {
                String pid = "file/" + name;
                if (repo.isAvailable(pid) && !ids.contains(pid)) ids.add(pid);
            }
            repo.setSelected(ids);
            mc.options.resourcePacks.clear();
            for (Pack p : repo.getSelectedPacks()) if (!p.isFixedPosition()) mc.options.resourcePacks.add(p.getId());
            mc.options.save();
        }
        mc.reloadResourcePacks().whenComplete((v, e) -> mc.execute(() -> {
            if (run != id) return;
            String err = swapError != null ? swapError : e != null ? String.valueOf(e.getMessage()) : null;
            swap = false;
            finish(id, err == null ? "done" : "failed", err);
        }));
    }

    static void onPacksClosed() {
        if (!swap) return;
        swap = false;
        try {
            Bridge.get().call("POST", "/assets/apply", new JsonObject(), Duration.ofSeconds(30));
            LOG.info("Assets nuevos colocados");
        } catch (Exception e) {
            swapError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            LOG.warn("El launcher no pudo colocar los assets nuevos: {}", swapError);
        }
    }

    private static void finish(int id, String status, String error) {
        Minecraft.getInstance().execute(() -> {
            if (run != id) return;
            phase = Phase.IDLE;
            if (status.equals("done")) LOG.info("Assets actualizados");
            Net.status(status, error);
        });
    }
}
