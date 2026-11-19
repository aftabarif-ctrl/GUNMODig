package com.modernarms.mixin;

import com.modernarms.GunItem;
import com.modernarms.client.ClientFx;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
	/** Wrap the first-person render of our guns with sway / recoil / reload / bolt / aim transforms. */
	@Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
	private void modernarms$head(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress,
								 ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vcp, int light, CallbackInfo ci) {
		if (!(item.getItem() instanceof GunItem g) || hand != Hand.MAIN_HAND) return;
		if (!ClientFx.applyGunTransform(g.type, tickDelta, matrices)) ci.cancel(); // scoped sniper: hide the model
	}

	@Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
	private void modernarms$tail(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand, float swingProgress,
								 ItemStack item, float equipProgress, MatrixStack matrices, VertexConsumerProvider vcp, int light, CallbackInfo ci) {
		if (ClientFx.pushed) { ClientFx.pushedStack.pop(); ClientFx.pushed = false; }
	}
}
