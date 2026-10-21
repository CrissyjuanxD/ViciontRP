package com.viciontstudios.rp.mod;

import net.minecraft.resources.ResourceLocation;

final class Ids {
    private Ids() {
    }

    static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
