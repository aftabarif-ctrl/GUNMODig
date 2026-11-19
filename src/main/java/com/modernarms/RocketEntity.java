package com.modernarms;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** A rocket that visibly accelerates, leaves a smoke trail, and detonates on contact. */
public class RocketEntity extends ProjectileEntity {
	public static final float POWER = 5.5f;           // TNT = 4, charged creeper = 6
	private static final double START_SPEED = 0.8, MAX_SPEED = 2.4, ACCEL = 0.09;
	private static final int MAX_LIFE = 160;           // 8 seconds
	private static final int ARM_TICKS = 4;            // can't hit its owner right after leaving the tube
	private static final double KNOCKBACK_RADIUS = 9.0;
	private int life;

	public RocketEntity(EntityType<? extends RocketEntity> type, World world) { super(type, world); }

	@Override protected void initDataTracker(DataTracker.Builder builder) {}

	@Override
	protected boolean canHit(Entity e) {
		if (life < ARM_TICKS && getOwner() == e) return false;
		return super.canHit(e);
	}

	@Override
	public void tick() {
		super.tick();
		life++;
		Vec3d dir = getVelocity();
		if (dir.lengthSquared() < 1e-6) { discard(); return; }
		double spd = Math.min(MAX_SPEED, START_SPEED + life * ACCEL);
		Vec3d vel = dir.normalize().multiply(spd);
		setVelocity(vel);

		World w = getWorld();
		if (!w.isClient) {
			HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
			if (hit.getType() != HitResult.Type.MISS) {
				onCollision(hit);
				if (isRemoved()) return;
			}
			if (life > MAX_LIFE) { detonate(getPos()); return; }
			if (life % 4 == 0) w.playSound(null, getX(), getY(), getZ(), Snd.se(SoundEvents.BLOCK_FIRE_AMBIENT), SoundCategory.NEUTRAL, 1.2f, 1.6f);
		}
		setPosition(getX() + vel.x, getY() + vel.y, getZ() + vel.z);
		setYaw((float) (MathHelper.atan2(vel.x, vel.z) * 57.29577951308232));
		setPitch((float) (MathHelper.atan2(vel.y, vel.horizontalLength()) * 57.29577951308232));
		if (w.isClient) trail(w, vel);
	}

	private void trail(World w, Vec3d vel) {
		// Bright exhaust core + long smoke trail, sampled along the last step so it looks continuous at high speed.
		for (int i = 0; i < 3; i++) {
			double f = i / 3.0;
			double x = getX() - vel.x * f, y = getY() - vel.y * f, z = getZ() - vel.z * f;
			w.addParticle(ParticleTypes.FLAME, x, y, z, random.nextGaussian() * 0.02, random.nextGaussian() * 0.02, random.nextGaussian() * 0.02);
			w.addParticle(ParticleTypes.SMOKE, x, y, z, random.nextGaussian() * 0.01, 0.01, random.nextGaussian() * 0.01);
		}
		w.addParticle(ParticleTypes.END_ROD, getX(), getY(), getZ(), 0, 0, 0);
		if (life % 2 == 0) w.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX() - vel.x, getY() - vel.y, getZ() - vel.z, 0, 0.012, 0);
	}

	@Override protected void onEntityHit(EntityHitResult r) { super.onEntityHit(r); detonate(r.getPos()); }
	@Override protected void onBlockHit(BlockHitResult r) { super.onBlockHit(r); detonate(r.getPos()); }

	private void detonate(Vec3d pos) {
		if (!(getWorld() instanceof ServerWorld sw) || isRemoved()) return;

		// Sample a block near the impact so clients can spawn matching debris.
		BlockPos bp = BlockPos.ofFloored(pos);
		BlockState sample = null;
		for (BlockPos p : new BlockPos[]{bp, bp.down(), bp.down(2), bp.up()}) {
			BlockState s = sw.getBlockState(p);
			if (!s.isAir()) { sample = s; break; }
		}
		if (sample == null) sample = net.minecraft.block.Blocks.STONE.getDefaultState();
		int stateId = Block.getRawIdFromState(sample);

		// Real Minecraft explosion: destroys blocks (TNT rules), damages/knocks back entities, sets fires.
		sw.createExplosion(this, pos.x, pos.y, pos.z, POWER, true, World.ExplosionSourceType.TNT);

		// Extra shove on top of vanilla knockback so the blast feels huge.
		var box = new net.minecraft.util.math.Box(pos, pos).expand(KNOCKBACK_RADIUS);
		for (Entity e : sw.getOtherEntities(this, box, en -> en instanceof LivingEntity)) {
			Vec3d off = e.getPos().add(0, e.getHeight() * 0.5, 0).subtract(pos);
			double d = off.length();
			if (d > KNOCKBACK_RADIUS) continue;
			Vec3d n = d < 1e-3 ? new Vec3d(0, 1, 0) : off.multiply(1.0 / d);
			double k = (1.0 - d / KNOCKBACK_RADIUS) * 1.5;
			e.addVelocity(n.x * k, n.y * k * 0.6 + 0.35 * (1.0 - d / KNOCKBACK_RADIUS), n.z * k);
			if (e instanceof ServerPlayerEntity sp) sp.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(sp));
		}

		var payload = new Net.ExplosionS2C(pos.x, pos.y, pos.z, stateId, POWER);
		for (ServerPlayerEntity p : PlayerLookup.around(sw, pos, 160)) ServerPlayNetworking.send(p, payload);
		discard();
	}
}
