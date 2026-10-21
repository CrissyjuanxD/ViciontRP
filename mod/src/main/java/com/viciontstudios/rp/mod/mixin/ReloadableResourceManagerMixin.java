package com.viciontstudios.rp.mod.mixin;

import com.viciontstudios.rp.mod.ViciontRPMod;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ReloadableResourceManager.class)
abstract class ReloadableResourceManagerMixin {
    @Shadow
    @Final
    private PackType type;

    @Inject(method = "createReload", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/packs/resources/CloseableResourceManager;close()V", shift = At.Shift.AFTER))
    private void viciontrp$packsClosed(CallbackInfoReturnable<?> cir) {
        if (type == PackType.CLIENT_RESOURCES) ViciontRPMod.packsClosed();
    }
}
