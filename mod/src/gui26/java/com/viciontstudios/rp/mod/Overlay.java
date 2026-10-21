package com.viciontstudios.rp.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class Overlay implements Hud.Painter {
    private final GuiGraphicsExtractor g;
    private final Font font;

    private Overlay(GuiGraphicsExtractor g) {
        this.g = g;
        this.font = Minecraft.getInstance().font;
    }

    public static void draw(GuiGraphicsExtractor g) {
        if (Hud.visible()) Hud.draw(new Overlay(g));
    }

    @Override
    public int width() {
        return g.guiWidth();
    }

    @Override
    public int height() {
        return g.guiHeight();
    }

    @Override
    public int textWidth(String text) {
        return font.width(text);
    }

    @Override
    public void text(String text, int x, int y, int color) {
        g.text(font, text, x, y, color, true);
    }

    @Override
    public void fill(int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y1, color);
    }
}
