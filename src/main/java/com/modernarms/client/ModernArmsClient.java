package com.modernarms.client;

import com.modernarms.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class ModernArmsClient implements ClientModInitializer {
	public static KeyBinding RELOAD;

	@Override
	public void onInitializeClient() {
		RELOAD = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.modernarms.reload", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.modernarms"));

		// The rocket is drawn purely with particles (flame core + smoke trail), so its entity renderer is empty.
		EntityRendererRegistry.register(ModEntities.ROCKET, RocketRenderer::new);

		ClientPlayNetworking.registerGlobalReceiver(Net.GunEventS2C.ID, (p, ctx) -> ClientFx.onGunEvent(ctx.client(), p));
		ClientPlayNetworking.registerGlobalReceiver(Net.TracerS2C.ID, (p, ctx) -> ClientFx.onTracer(ctx.client(), p));
		ClientPlayNetworking.registerGlobalReceiver(Net.ExplosionS2C.ID, (p, ctx) -> ClientFx.onExplosion(ctx.client(), p));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			ClientFx.tick(mc);
			while (RELOAD.wasPressed()) {
				if (mc.player != null && ClientFx.heldGun() != null && mc.currentScreen == null) ClientPlayNetworking.send(new Net.ReloadC2S());
			}
		});

		HudRenderCallback.EVENT.register(Hud::render);
	}

	/** Called by MinecraftClientMixin when the attack button is pressed while holding a gun. */
	public static void onAttackPressed() {
		if (ClientFx.heldGun() == null) return;
		ClientPlayNetworking.send(new Net.FireC2S(ClientFx.scopedForServer()));
	}
}
