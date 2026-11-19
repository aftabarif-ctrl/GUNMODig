package com.modernarms.client;

import com.modernarms.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/** Sniper scope overlay, RPG sight overlay, ammo counter. */
public final class Hud {
	public static void render(DrawContext ctx, RenderTickCounter tick) {
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.player == null || mc.options.hudHidden) return;
		int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();

		float scope = ClientFx.scopeAmount();
		if (scope > 0.6f) drawScope(ctx, w, h, Math.min(1f, (scope - 0.6f) / 0.35f));
		float sight = ClientFx.rpgSightAmount();
		if (sight > 0.3f) drawRpgSight(ctx, w, h, Math.min(1f, (sight - 0.3f) / 0.7f));

		WeaponType t = ClientFx.heldGun();
		if (t != null) {
			var stack = mc.player.getMainHandStack();
			int mag = GunItem.getMag(stack);
			String s = ClientFx.isReloading() ? "RELOADING" : mag + " / " + t.mag;
			int color = ClientFx.isReloading() ? 0xFFFFD24A : mag == 0 ? 0xFFFF5555 : 0xFFFFFFFF;
			int tw = mc.textRenderer.getWidth(s);
			ctx.drawTextWithShadow(mc.textRenderer, Text.literal(s), w - tw - 14, h - 24, color);
			if (mag == 0 && !ClientFx.isReloading())
				ctx.drawTextWithShadow(mc.textRenderer, Text.literal("[R] RELOAD"), w - mc.textRenderer.getWidth("[R] RELOAD") - 14, h - 36, 0xFFFF5555);
		}
	}

	private static int argb(int a, int r, int g, int b) { return (a << 24) | (r << 16) | (g << 8) | b; }

	/** Fill everything outside a circle of radius r around (cx,cy). */
	private static void maskOutsideCircle(DrawContext ctx, int w, int h, int cx, int cy, int r, int color) {
		int top = cy - r, bottom = cy + r;
		if (top > 0) ctx.fill(0, 0, w, top, color);
		if (bottom < h) ctx.fill(0, bottom, w, h, color);
		for (int y = Math.max(0, top); y < Math.min(h, bottom); y++) {
			int dy = y - cy;
			int half = (int) Math.sqrt((double) r * r - (double) dy * dy);
			if (cx - half > 0) ctx.fill(0, y, cx - half, y + 1, color);
			if (cx + half < w) ctx.fill(cx + half, y, w, y + 1, color);
		}
	}

	private static void drawScope(DrawContext ctx, int w, int h, float a) {
		int cx = w / 2, cy = h / 2;
		int r = (int) (Math.min(w, h) * 0.46f);
		int black = argb((int) (255 * a), 0, 0, 0);
		maskOutsideCircle(ctx, w, h, cx, cy, r, black);
		// soft inner vignette ring
		maskOutsideCircle(ctx, w, h, cx, cy, r - 1, argb((int) (120 * a), 0, 0, 0));
		maskOutsideCircle(ctx, w, h, cx, cy, r - 3, argb((int) (60 * a), 0, 0, 0));

		int line = argb((int) (230 * a), 8, 8, 8);
		int gap = 14;
		// crosshair: thin lines that thicken toward the edges
		ctx.fill(cx - r, cy, cx - gap, cy + 1, line);
		ctx.fill(cx + gap, cy, cx + r, cy + 1, line);
		ctx.fill(cx, cy - r, cx + 1, cy - gap, line);
		ctx.fill(cx, cy + gap, cx + 1, cy + r, line);
		ctx.fill(cx - r, cy - 1, cx - r / 2, cy + 2, line);
		ctx.fill(cx + r / 2, cy - 1, cx + r, cy + 2, line);
		ctx.fill(cx - 1, cy - r, cx + 2, cy - r / 2, line);
		ctx.fill(cx - 1, cy + r / 2, cx + 2, cy + r, line);
		// mil-dots
		for (int i = 1; i <= 5; i++) {
			int d = gap + i * (r - gap) / 7;
			ctx.fill(cx - d - 1, cy - 1, cx - d + 1, cy + 2, line);
			ctx.fill(cx + d - 1, cy - 1, cx + d + 1, cy + 2, line);
			ctx.fill(cx - 1, cy - d - 1, cx + 2, cy - d + 1, line);
			ctx.fill(cx - 1, cy + d - 1, cx + 2, cy + d + 1, line);
		}
		ctx.fill(cx, cy, cx + 1, cy + 1, argb((int) (255 * a), 255, 40, 30)); // centre dot
	}

	private static void drawRpgSight(DrawContext ctx, int w, int h, float a) {
		int cx = w / 2, cy = h / 2;
		maskOutsideCircle(ctx, w, h, cx, cy, (int) (Math.min(w, h) * 0.62f), argb((int) (150 * a), 0, 0, 0));
		int c = argb((int) (230 * a), 235, 235, 235), sh = argb((int) (160 * a), 0, 0, 0);
		int s = 46, l = 12;
		// corner brackets
		int[][] corners = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
		for (int[] k : corners) {
			int x = cx + k[0] * s, y = cy + k[1] * s;
			int x2 = x - k[0] * l, y2 = y - k[1] * l;
			ctx.fill(Math.min(x, x2), y, Math.max(x, x2) + 1, y + 1, c);
			ctx.fill(x, Math.min(y, y2), x + 1, Math.max(y, y2) + 1, c);
		}
		// centre chevron + range ticks
		ctx.fill(cx, cy, cx + 1, cy + 1, argb((int) (255 * a), 255, 60, 40));
		for (int i = 1; i <= 4; i++) {
			int y = cy + i * 9;
			int half = i % 2 == 0 ? 7 : 4;
			ctx.fill(cx - half, y, cx + half + 1, y + 1, c);
			ctx.fill(cx - half, y + 1, cx + half + 1, y + 2, sh);
		}
		ctx.fill(cx - 10, cy, cx - 3, cy + 1, c);
		ctx.fill(cx + 4, cy, cx + 11, cy + 1, c);
		ctx.fill(cx, cy - 10, cx + 1, cy - 3, c);
	}
}
