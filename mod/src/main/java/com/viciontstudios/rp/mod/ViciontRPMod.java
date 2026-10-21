package com.viciontstudios.rp.mod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class ViciontRPMod implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Net.init();
        Assets.LOG.info("ViciontRP {} listo{}", version(), Bridge.get().available() ? " (con el launcher de Viciont Studios)" : "");
    }

    public static void packsClosed() {
        Assets.onPacksClosed();
    }

    static String version() {
        return FabricLoader.getInstance().getModContainer("viciontrp").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
    }
}
