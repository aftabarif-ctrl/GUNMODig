package com.modernarms.client;

import com.modernarms.RocketEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

/** The rocket is drawn purely with particles, so this renderer draws nothing. */
public class RocketRenderer extends EntityRenderer<RocketEntity> {
	private static final Identifier TEX = Identifier.of("minecraft", "textures/misc/white.png");

	public RocketRenderer(EntityRendererFactory.Context ctx) { super(ctx); }

	@Override public Identifier getTexture(RocketEntity entity) { return TEX; }

	@Override
	public void render(RocketEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vcp, int light) {}
}
