package com.viciontstudios.rp.mod;

public final class Hud {
    public interface Painter {
        int width();

        int height();

        int textWidth(String text);

        void text(String text, int x, int y, int color);

        void fill(int x0, int y0, int x1, int y1, int color);
    }

    private static final String TEXT = "Descargando Assets";
    private static final int WIDTH = 120;
    private static float shown;

    private Hud() {
    }

    public static boolean visible() {
        if (Assets.phase != Assets.Phase.IDLE) return true;
        shown = 0;
        return false;
    }

    public static void draw(Painter p) {
        float target = Assets.progress();
        shown += (target - shown) * 0.25f;
        int x = p.width() - WIDTH - 8;
        int y = p.height() - 12;
        String percent = Math.round(target * 100) + "%";
        p.text(TEXT, x, y - 11, 0xFFFFFFFF);
        p.text(percent, x + WIDTH - p.textWidth(percent), y - 11, 0xFFFFFFFF);
        p.fill(x - 1, y - 1, x + WIDTH + 1, y + 4, 0xA0000000);
        p.fill(x, y, x + Math.round(WIDTH * shown), y + 3, 0xFFFFFFFF);
    }
}
