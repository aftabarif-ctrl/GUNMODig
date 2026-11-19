package com.modernarms.client;

import com.modernarms.*;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/** All client-side state: animation timers, aim/zoom, camera shake, particles, delayed sounds. */
public final class ClientFx {
	public static long clientTicks;
	// animation timers (in clientTicks)
	private static long fireStart = -1000, reloadStart = -1000, boltStart = -1000;
	private static int fireDur = 8, reloadLen = 1, boltLen = 1;
	private static int animWeapon = -1;
	// aim
	private static float aimPrev, aimCur;
	// camera shake
	private static float trauma;
	// sway
	private static float swayPrev, swayCur, lagXPrev, lagXCur, lagYPrev, lagYCur, lastYaw, lastPitch;
	// matrix-stack bookkeeping for the held item mixin
	public static boolean pushed;
	public static MatrixStack pushedStack;

	private record Task(long at, Runnable run) {}
	private static final List<Task> TASKS = new ArrayList<>();

	private ClientFx() {}

	public static void schedule(int delay, Runnable r) { TASKS.add(new Task(clientTicks + Math.max(0, delay), r)); }

	// ------------------------------------------------------------- queries

	public static WeaponType heldGun() {
		ClientPlayerEntity p = MinecraftClient.getInstance().player;
		if (p != null && p.getMainHandStack().getItem() instanceof GunItem g) return g.type;
		return null;
	}

	public static float aim(float td) { return MathHelper.lerp(td, aimPrev, aimCur); }
	public static boolean hidesCrosshair() { WeaponType t = heldGun(); return t != null && t.canAim && aimCur > 0.4f; }
	public static float scopeAmount() { return heldGun() == WeaponType.SNIPER ? aimCur : 0f; }
	public static float rpgSightAmount() { return heldGun() == WeaponType.RPG ? aimCur : 0f; }
	public static boolean scopedForServer() { return heldGun() != null && aimCur > 0.8f; }

	public static float fovMultiplier() {
		WeaponType t = heldGun();
		if (t == null || !t.canAim || aimCur < 0.001f) return 1f;
		float e = aimCur * aimCur * (3 - 2 * aimCur); // smoothstep
		float zoom = t == WeaponType.SNIPER ? 0.17f : 0.6f;
		return MathHelper.lerp(e, 1f, zoom);
	}

	public static float trauma() { return trauma; }

	private static boolean animating(long start, int len) { long d = clientTicks - start; return d >= 0 && d < len; }
	public static boolean isReloading() { return animating(reloadStart, reloadLen); }
	public static boolean isBolting() { return animating(boltStart, boltLen); }

	// ------------------------------------------------------------- per tick

	public static void tick(MinecraftClient mc) {
		clientTicks++;
		for (int i = 0; i < TASKS.size(); ) { // run due tasks (index loop: tasks may add tasks)
			Task t = TASKS.get(i);
			if (t.at <= clientTicks) { TASKS.remove(i); t.run.run(); } else i++;
		}
		trauma = Math.max(0f, trauma - 0.035f);
		ClientPlayerEntity p = mc.player;
		if (p == null) { aimPrev = aimCur = 0; return; }

		aimPrev = aimCur;
		WeaponType t = heldGun();
		boolean want = t != null && t.canAim && mc.currentScreen == null && mc.options.useKey.isPressed() && !isReloading() && !isBolting();
		aimCur += ((want ? 1f : 0f) - aimCur) * 0.4f;
		if (Math.abs(aimCur - (want ? 1f : 0f)) < 0.01f) aimCur = want ? 1f : 0f;

		// walk sway amount + mouse-look weapon lag
		swayPrev = swayCur;
		float speed = (float) p.getVelocity().horizontalLength();
		float target = p.isOnGround() ? MathHelper.clamp(speed / 0.2f, 0f, 1.4f) : 0.2f;
		swayCur += (target - swayCur) * 0.25f;
		float yaw = p.getYaw(), pitch = p.getPitch();
		float dy = MathHelper.wrapDegrees(yaw - lastYaw), dp = pitch - lastPitch;
		lastYaw = yaw; lastPitch = pitch;
		lagXPrev = lagXCur; lagYPrev = lagYCur;
		lagXCur += (MathHelper.clamp(-dy * 0.0035f, -0.06f, 0.06f) - lagXCur) * 0.3f;
		lagYCur += (MathHelper.clamp(dp * 0.0035f, -0.06f, 0.06f) - lagYCur) * 0.3f;

		if (t == null) { reloadStart = boltStart = -1000; }
	}

	// ------------------------------------------------------------- held-item transform (called from mixin)

	private static float ease(float x, float a, float b) {
		float t = MathHelper.clamp((x - a) / (b - a), 0f, 1f);
		return t * t * (3 - 2 * t);
	}

	/** @return false if the item model should not be drawn at all (fully scoped sniper). */
	public static boolean applyGunTransform(WeaponType t, float td, MatrixStack m) {
		float aim = aim(td);
		if (t == WeaponType.SNIPER && aim > 0.85f) return false;
		float now = clientTicks + td;
		boolean mine = animWeapon == t.id;

		m.push();
		pushed = true;
		pushedStack = m;

		// --- weapon sway (walking bob + idle breathing + look lag); damped when aiming
		float damp = 1f - 0.85f * aim;
		float s = MathHelper.lerp(td, swayPrev, swayCur) * damp;
		float phase = now * 0.36f;
		float sx = (float) Math.sin(phase) * 0.014f * s;
		float sy = -(float) Math.abs(Math.cos(phase)) * 0.016f * s;
		float breathe = (float) Math.sin(now * 0.07f) * 0.0025f * damp;
		float lx = MathHelper.lerp(td, lagXPrev, lagXCur) * damp, ly = MathHelper.lerp(td, lagYPrev, lagYCur) * damp;
		m.translate(sx + lx, sy + ly + breathe, 0);
		m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(sx * 120f + lx * 40f));

		// --- aim: slide weapon to screen centre (RPG sight picture)
		if (t == WeaponType.RPG) m.translate(-0.50f * aim, 0.17f * aim, 0.10f * aim);

		// pivot for rotations (approx. where the hand holds the weapon)
		float px = 0.4f, py = -0.4f, pz = -0.6f;
		m.translate(px, py, pz);

		// --- recoil
		if (mine) {
			float ft = (now - fireStart) / fireDur;
			if (ft >= 0 && ft < 1) {
				float r = ft < 0.15f ? ft / 0.15f : (float) Math.pow(1 - (ft - 0.15f) / 0.85f, 2);
				float back = switch (t) { case PISTOL -> 0.10f; case SNIPER -> 0.22f; case RPG -> 0.30f; };
				float rot = switch (t) { case PISTOL -> 9f; case SNIPER -> 12f; case RPG -> 16f; };
				m.translate(0, 0.012f * r, back * r);
				m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rot * r)); // muzzle climbs
			}
		}

		// --- reload
		if (mine) {
			float rp = (now - reloadStart) / reloadLen;
			if (rp >= 0 && rp < 1) {
				float d = ease(rp, 0f, 0.18f) - ease(rp, 0.82f, 1f);
				float jig = (rp > 0.3f && rp < 0.62f) ? (float) Math.sin((rp - 0.3f) / 0.32f * Math.PI * 4) : 0f;
				float slam = (rp > 0.88f && rp < 0.98f) ? (float) Math.sin((rp - 0.88f) / 0.10f * Math.PI) : 0f;
				switch (t) {
					case PISTOL -> {
						m.translate(0, -0.22f * d + 0.012f * jig, 0.05f * d - 0.05f * slam);
						m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(24f * d));
						m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-28f * d));
					}
					case SNIPER -> {
						m.translate(0, -0.26f * d + 0.01f * jig, 0.07f * d - 0.05f * slam);
						m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(18f * d));
						m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-22f * d));
					}
					case RPG -> {
						m.translate(0, -0.34f * d + 0.012f * jig, 0.12f * d - 0.10f * slam);
						m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(48f * d));
						m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(14f * d));
					}
				}
			}
		}

		// --- sniper bolt-action cycle
		if (mine && t == WeaponType.SNIPER) {
			float bp = (now - boltStart) / boltLen;
			if (bp >= 0 && bp < 1) {
				float roll = ease(bp, 0f, 0.2f) - ease(bp, 0.78f, 1f);          // lift handle (whole rifle rolls)
				float pull = ease(bp, 0.2f, 0.45f) - ease(bp, 0.5f, 0.75f);      // pull back, push forward
				m.translate(-0.03f * roll, -0.03f * roll, 0.075f * pull);
				m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-16f * roll));
				m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(3f * pull));
			}
		}

		m.translate(-px, -py, -pz);
		return true;
	}

	// ------------------------------------------------------------- network events

	public static void onGunEvent(MinecraftClient mc, Net.GunEventS2C p) {
		if (mc.world == null || mc.player == null) return;
		Entity e = mc.world.getEntityById(p.entityId());
		if (e == null) return;
		WeaponType t = WeaponType.byId(p.weapon());
		boolean self = e == mc.player;
		switch (p.event()) {
			case Net.EV_FIRE -> {
				if (self) {
					animWeapon = t.id; fireStart = clientTicks; fireDur = t.recoilTicks;
					float kick = switch (t) { case PISTOL -> 1.1f; case SNIPER -> 4.5f; case RPG -> 6.5f; };
					mc.player.setPitch(MathHelper.clamp(mc.player.getPitch() - kick, -90f, 90f));
					mc.player.setYaw(mc.player.getYaw() + (mc.world.random.nextFloat() - 0.5f) * kick * 0.4f);
					float tr = switch (t) { case PISTOL -> 0.06f; case SNIPER -> 0.22f; case RPG -> 0.55f; };
					trauma = Math.max(trauma, tr);
				} else if (t == WeaponType.RPG) {
					double d = mc.player.getPos().distanceTo(e.getPos());
					if (d < 16) trauma = Math.max(trauma, (float) (0.35 * (1 - d / 16)));
				}
				muzzleFx(mc.world, e, t);
				if (t == WeaponType.PISTOL) ejectCasing(mc.world, e);
			}
			case Net.EV_RELOAD -> { if (self) { animWeapon = t.id; reloadStart = clientTicks; reloadLen = Math.max(1, p.duration()); } }
			case Net.EV_BOLT -> {
				if (self) { animWeapon = t.id; boltStart = clientTicks; boltLen = Math.max(1, p.duration()); }
				ClientWorld cw = mc.world;
				schedule((int) (p.duration() * 0.35f), () -> ejectCasing(cw, e)); // casing flies out as the bolt is pulled
			}
			default -> {}
		}
	}

	private static void muzzleFx(ClientWorld w, Entity e, WeaponType t) {
		Vec3d mz = MuzzleUtil.muzzle(e, t);
		Vec3d look = e.getRotationVec(1f);
		var r = w.random;
		int flames = switch (t) { case PISTOL -> 4; case SNIPER -> 8; case RPG -> 16; };
		double sp = t == WeaponType.RPG ? 0.35 : 0.18;
		for (int i = 0; i < flames; i++) {
			Vec3d v = look.multiply(sp * (0.5 + r.nextDouble())).add(r.nextGaussian() * 0.03, r.nextGaussian() * 0.03, r.nextGaussian() * 0.03);
			w.addParticle(ParticleTypes.FLAME, mz.x, mz.y, mz.z, v.x, v.y, v.z);
		}
		for (int i = 0; i < 3; i++) w.addParticle(ParticleTypes.ELECTRIC_SPARK, mz.x, mz.y, mz.z, look.x * 0.3 + r.nextGaussian() * 0.1, look.y * 0.3 + r.nextGaussian() * 0.1, look.z * 0.3 + r.nextGaussian() * 0.1);
		for (int i = 0; i < (t == WeaponType.PISTOL ? 2 : 5); i++) w.addParticle(ParticleTypes.SMOKE, mz.x, mz.y, mz.z, look.x * 0.06 + r.nextGaussian() * 0.02, 0.02 + r.nextDouble() * 0.02, look.z * 0.06 + r.nextGaussian() * 0.02);
		if (t == WeaponType.RPG) { // back-blast
			Vec3d back = e.getEyePos().add(look.multiply(-0.7)).add(0, -0.2, 0);
			for (int i = 0; i < 14; i++) w.addParticle(ParticleTypes.CLOUD, back.x, back.y, back.z, -look.x * 0.4 + r.nextGaussian() * 0.12, r.nextGaussian() * 0.08, -look.z * 0.4 + r.nextGaussian() * 0.12);
			for (int i = 0; i < 6; i++) w.addParticle(ParticleTypes.LARGE_SMOKE, back.x, back.y, back.z, -look.x * 0.2 + r.nextGaussian() * 0.08, 0.02, -look.z * 0.2 + r.nextGaussian() * 0.08);
		}
		// lingering smoke that keeps following the muzzle for a moment
		int linger = t == WeaponType.PISTOL ? 3 : t == WeaponType.SNIPER ? 6 : 10;
		for (int i = 1; i <= linger; i++) {
			final int k = i;
			schedule(i * 2, () -> {
				if (e.isRemoved()) return;
				Vec3d p = MuzzleUtil.muzzle(e, t);
				w.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.x, p.y, p.z, 0, 0.012 + 0.002 * k, 0);
			});
		}
	}

	private static void ejectCasing(ClientWorld w, Entity e) {
		if (w == null || e.isRemoved()) return;
		Vec3d right = MuzzleUtil.right(e);
		Vec3d pos = e.getEyePos().add(e.getRotationVec(1f).multiply(0.55)).add(right.multiply(0.22)).add(0, -0.16, 0);
		ParticleEffect brass = new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.GOLD_NUGGET));
		var r = w.random;
		for (int i = 0; i < 2; i++)
			w.addParticle(brass, pos.x, pos.y, pos.z, right.x * (0.12 + r.nextDouble() * 0.08), 0.12 + r.nextDouble() * 0.06, right.z * (0.12 + r.nextDouble() * 0.08));
		schedule(8 + (int) (r.nextFloat() * 3), () -> w.playSound(pos.x + right.x * 1.2, pos.y - 1.2, pos.z + right.z * 1.2,
			Snd.se(SoundEvents.BLOCK_CHAIN_HIT), SoundCategory.PLAYERS, 0.35f, 2.0f, false));
	}

	public static void onTracer(MinecraftClient mc, Net.TracerS2C p) {
		ClientWorld w = mc.world;
		if (w == null) return;
		Vec3d a = p.from(), b = p.to();
		double len = a.distanceTo(b);
		if (len < 0.5) return;
		Vec3d dir = b.subtract(a).multiply(1.0 / len);
		boolean sniper = p.kind() == Net.TRACER_SNIPER;
		double step = sniper ? Math.max(0.45, len / 140.0) : Math.max(0.9, len / 60.0);
		ParticleEffect main = sniper ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK;
		for (double d = 0.6; d < len; d += step) {
			Vec3d q = a.add(dir.multiply(d));
			w.addImportantParticle(main, q.x, q.y, q.z, 0, 0, 0);
			if (sniper && ((int) (d / step) % 3 == 0)) w.addImportantParticle(ParticleTypes.SMOKE, q.x, q.y, q.z, 0, 0.002, 0);
		}
	}

	public static void onExplosion(MinecraftClient mc, Net.ExplosionS2C p) {
		ClientWorld w = mc.world;
		if (w == null || mc.player == null) return;
		Vec3d c = new Vec3d(p.x(), p.y(), p.z());
		BlockState st = Block.getStateFromRawId(p.blockStateId());
		double dist = mc.player.getPos().distanceTo(c);
		var r = w.random;

		// screen shake: strong up close, fades out by ~56 blocks
		float tr = (float) MathHelper.clamp(1.0 - dist / 56.0, 0, 1);
		if (tr > 0) trauma = Math.max(trauma, 0.3f + 0.7f * tr);

		w.addImportantParticle(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 0, 0, 0);
		// fireball
		for (int i = 0; i < 70; i++) {
			Vec3d d = randDir(r);
			double s = 0.2 + r.nextDouble() * 0.5;
			w.addImportantParticle(ParticleTypes.FLAME, c.x + d.x * 0.6, c.y + d.y * 0.6, c.z + d.z * 0.6, d.x * s, d.y * s * 0.9 + 0.1, d.z * s);
		}
		for (int i = 0; i < 45; i++) {
			Vec3d d = randDir(r);
			double s = 0.15 + r.nextDouble() * 0.35;
			w.addImportantParticle(ParticleTypes.LARGE_SMOKE, c.x + d.x, c.y + d.y, c.z + d.z, d.x * s, d.y * s + 0.08, d.z * s);
		}
		for (int i = 0; i < 25; i++) w.addImportantParticle(ParticleTypes.LAVA, c.x, c.y + 0.5, c.z, r.nextGaussian() * 0.3, 0.3 + r.nextDouble() * 0.5, r.nextGaussian() * 0.3);
		// debris of the surface that was hit
		ParticleEffect debris = new BlockStateParticleEffect(ParticleTypes.BLOCK, st);
		for (int i = 0; i < 90; i++) {
			Vec3d d = randDir(r);
			double s = 0.25 + r.nextDouble() * 0.6;
			w.addImportantParticle(debris, c.x + d.x * 1.2, c.y + Math.abs(d.y) * 0.8, c.z + d.z * 1.2, d.x * s, Math.abs(d.y) * s + 0.3, d.z * s);
		}
		// ground shockwave rings (fast cloud + slower smoke)
		for (int ring = 0; ring < 3; ring++) {
			double speed = 1.0 - ring * 0.3;
			ParticleEffect pe = ring == 2 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CLOUD;
			final int rr = ring;
			schedule(ring * 2, () -> {
				int n = 56;
				for (int i = 0; i < n; i++) {
					double a = i * Math.PI * 2 / n;
					w.addImportantParticle(pe, c.x + Math.cos(a) * 0.6, c.y + 0.25 + rr * 0.15, c.z + Math.sin(a) * 0.6, Math.cos(a) * speed, 0.02, Math.sin(a) * speed);
				}
			});
		}
		// rising smoke column / mushroom
		for (int t = 0; t < 24; t++) {
			final int k = t;
			schedule(t, () -> {
				for (int i = 0; i < 4; i++)
					w.addImportantParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x + r.nextGaussian() * 0.8, c.y + 0.5 + k * 0.25, c.z + r.nextGaussian() * 0.8, r.nextGaussian() * 0.01, 0.1 + r.nextDouble() * 0.1, r.nextGaussian() * 0.01);
				if (k > 6 && k % 2 == 0) {
					double y = c.y + 2.5 + k * 0.3;
					for (int i = 0; i < 16; i++) {
						double a = i * Math.PI * 2 / 16;
						w.addImportantParticle(ParticleTypes.LARGE_SMOKE, c.x, y, c.z, Math.cos(a) * 0.18, 0.04, Math.sin(a) * 0.18);
					}
				}
			});
		}
		// echoing boom: delayed repeats get lower and quieter; distant players hear it later (speed-of-sound feel)
		int delay = (int) Math.min(60, dist / 6.0);
		float vol = (float) MathHelper.clamp(5.0 - dist / 20.0, 1.0, 5.0);
		boom(w, c, delay, vol, 0.55f, SoundEvents.ENTITY_GENERIC_EXPLODE);
		boom(w, c, delay + 7, vol * 0.7f, 0.42f, SoundEvents.ENTITY_GENERIC_EXPLODE);
		boom(w, c, delay + 16, vol * 0.5f, 0.32f, SoundEvents.ENTITY_GENERIC_EXPLODE);
		boom(w, c, delay + 4, vol * 0.8f, 0.6f, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER);
		boom(w, c, delay + 24, vol * 0.4f, 0.5f, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER);
	}

	private static void boom(ClientWorld w, Vec3d c, int delay, float vol, float pitch, Object sound) {
		schedule(delay, () -> w.playSound(c.x, c.y, c.z, Snd.se(sound), SoundCategory.BLOCKS, vol, pitch, false));
	}

	private static Vec3d randDir(net.minecraft.util.math.random.Random r) {
		Vec3d v = new Vec3d(r.nextGaussian(), r.nextGaussian(), r.nextGaussian());
		return v.lengthSquared() < 1e-6 ? new Vec3d(0, 1, 0) : v.normalize();
	}
}
