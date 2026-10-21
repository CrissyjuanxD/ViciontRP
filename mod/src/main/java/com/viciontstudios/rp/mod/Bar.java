package com.viciontstudios.rp.mod;

public final class Bar {
    public interface Painter {
        int width();

        int height();

        int textWidth(String text);

        void text(String text, int x, int y, int color);

        void fill(int x0, int y0, int x1, int y1, int color);
    }

    private static final String TEXT = "Actualizando Assets";
    private static final int MARGIN = 8;
    private static final int HOTBAR = 121;
    private static final int STATUS = 95;
    private static float shown;

    private Bar() {
    }

    public static boolean visible() {
        if (Assets.phase != Assets.Phase.IDLE) return true;
        shown = 0;
        return false;
    }

    public static void draw(Painter p) {
        float target = Assets.progress();
        shown += (target - shown) * 0.25f;
        long files = Assets.files;
        String status = Math.round(target * 100) + "%" + count(Math.min(Assets.filesDone, files), files);
        String widest = "100%" + count(files, files);
        int center = p.width() / 2;
        int right = p.width() - MARGIN;
        int w = p.textWidth(TEXT + " " + widest);
        boolean split = right - w < center + HOTBAR;
        if (split) w = Math.max(p.textWidth(TEXT), p.textWidth(widest));
        int x = right - w;
        int y = p.height() - (x >= center + HOTBAR ? 12 : x >= center + STATUS ? 30 : 66);
        if (split) {
            p.text(TEXT, x, y - 21, 0xFFFFFFFF);
            p.text(status, x, y - 11, 0xFFFFFFFF);
        } else {
            p.text(TEXT + " " + status, x, y - 11, 0xFFFFFFFF);
        }
        p.fill(x - 1, y - 1, x + w + 1, y + 4, 0xA0000000);
        p.fill(x, y, x + Math.round(w * shown), y + 3, 0xFFFFFFFF);
    }

    private static String count(long done, long files) {
        return files > 0 ? " | (" + done + "/" + files + ")" : "";
    }
}
