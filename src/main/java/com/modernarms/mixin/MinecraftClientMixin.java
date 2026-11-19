package com.modernarms.mixin;

import com.modernarms.WeaponType;
import com.modernarms.client.ClientFx;
import com.modernarms.client.ModernArmsClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	/** Left click with a gun = fire (never punch/mine). */
	@Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
	private void modernarms$attack(CallbackInfoReturnable<Boolean> cir) {
		if (ClientFx.heldGun() != null) { ModernArmsClient.onAttackPressed(); cir.setReturnValue(false); }
	}

	@Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
	private void modernarms$noMining(boolean breaking, CallbackInfo ci) {
		if (ClientFx.heldGun() != null) ci.cancel();
	}

	/** Right click with sniper/RPG is "aim", so don't open doors/chests etc. while holding them. */
	@Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
	private void modernarms$noUse(CallbackInfo ci) {
		WeaponType t = ClientFx.heldGun();
		if (t != null && t.canAim) ci.cancel();
	}
}
