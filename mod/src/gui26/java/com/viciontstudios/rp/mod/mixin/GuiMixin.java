package com.viciontstudios.rp.mod.mixin;

import com.viciontstudios.rp.mod.Overlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
abstract class GuiMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void viciontrp$draw(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        Overlay.draw(graphics);
    }
}
