package com.modernarms.mixin;

import com.modernarms.client.ClientFx;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow protected abstract void setRotation(float yaw, float pitch);
	@Shadow public abstract float getYaw();
	@Shadow public abstract float getPitch();

	/** Trauma-based screen shake (smooth multi-frequency noise, quadratic falloff). */
	@Inject(method = "update", at = @At("RETURN"))
	private void modernarms$shake(BlockView area, Entity focused, boolean thirdPerson, boolean inverse, float tickDelta, CallbackInfo ci) {
		float tr = ClientFx.trauma();
		if (tr <= 0.001f) return;
		float amp = tr * tr;
		float time = (System.nanoTime() / 1_000_000L) / 50f;
		float dy = (float) (Math.sin(time * 2.7) + 0.5 * Math.sin(time * 6.1)) * 3.2f * amp;
		float dp = (float) (Math.sin(time * 3.3 + 1.7) + 0.5 * Math.sin(time * 7.7 + 0.4)) * 2.6f * amp;
		setRotation(getYaw() + dy, getPitch() + dp);
	}
}
