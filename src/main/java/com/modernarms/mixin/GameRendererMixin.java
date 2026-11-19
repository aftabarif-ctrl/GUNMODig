package com.modernarms.mixin;

import com.modernarms.client.ClientFx;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	/** Scope / sight zoom. getFov returns a double in 1.21.1; the raw type handles both float and double. */
	@SuppressWarnings({"rawtypes", "unchecked"})
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void modernarms$zoom(CallbackInfoReturnable cir) {
		float m = ClientFx.fovMultiplier();
		if (m >= 0.999f) return;
		Object v = cir.getReturnValue();
		if (v instanceof Float f) cir.setReturnValue(f * m);
		else if (v instanceof Double d) cir.setReturnValue(d * m);
	}
}
