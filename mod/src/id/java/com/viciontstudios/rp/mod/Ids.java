package com.viciontstudios.rp.mod;

import net.minecraft.resources.Identifier;

final class Ids {
    private Ids() {
    }

    static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }
}
