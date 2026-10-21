package com.viciontstudios.rp.mod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

final class Json {
    private Json() {
    }

    static String text(JsonObject o, String k, String def) {
        JsonElement e = o == null ? null : o.get(k);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
    }

    static boolean bool(JsonObject o, String k) {
        JsonElement e = o == null ? null : o.get(k);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() && e.getAsBoolean();
    }

    static long number(JsonObject o, String k) {
        JsonElement e = o == null ? null : o.get(k);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsLong() : 0;
    }

    static List<String> list(JsonObject o, String k) {
        List<String> out = new ArrayList<>();
        JsonElement e = o == null ? null : o.get(k);
        if (e == null || !e.isJsonArray()) return out;
        for (JsonElement x : (JsonArray) e) if (x.isJsonPrimitive()) out.add(x.getAsString());
        return out;
    }
}
