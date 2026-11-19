package com.modernarms.mixin;

import com.modernarms.client.ClientFx;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
	@Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
	private void modernarms$hideCrosshair(net.minecraft.client.gui.DrawContext ctx, net.minecraft.client.render.RenderTickCounter tc, CallbackInfo ci) {
		if (ClientFx.hidesCrosshair()) ci.cancel();
	}
}
