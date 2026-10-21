package com.viciontstudios.rp.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class Overlay implements Bar.Painter {
    private final GuiGraphics g;
    private final Font font;

    private Overlay(GuiGraphics g) {
        this.g = g;
        this.font = Minecraft.getInstance().font;
    }

    public static void draw(GuiGraphics g) {
        if (Bar.visible()) Bar.draw(new Overlay(g));
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
        g.drawString(font, text, x, y, color, true);
    }

    @Override
    public void fill(int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y1, color);
    }
}
