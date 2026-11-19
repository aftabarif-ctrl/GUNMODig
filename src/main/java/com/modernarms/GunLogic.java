package com.modernarms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** Server-authoritative gun logic: firing, hitscan, reloading, sounds. */
public final class GunLogic {
	private static final Map<UUID, Long> NEXT_FIRE = new HashMap<>();
	private static final Map<UUID, Reload> RELOADS = new HashMap<>();
	private static final List<Task> TASKS = new ArrayList<>();
	private static long ticks;

	private record Reload(WeaponType type, long end) {}
	private record Task(long at, Runnable run) {}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(GunLogic::tick);
		ServerPlayNetworking.registerGlobalReceiver(Net.FireC2S.ID, (p, ctx) -> tryFire(ctx.player(), p.scoped()));
		ServerPlayNetworking.registerGlobalReceiver(Net.ReloadC2S.ID, (p, ctx) -> startReload(ctx.player()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUuid();
			NEXT_FIRE.remove(id); RELOADS.remove(id);
		});
	}

	private static void after(int delay, Runnable r) { TASKS.add(new Task(ticks + delay, r)); }

	private static void tick(MinecraftServer server) {
		ticks++;
		if (!TASKS.isEmpty()) {
			List<Task> due = TASKS.stream().filter(t -> t.at <= ticks).toList();
			TASKS.removeAll(due);
			for (Task t : due) t.run.run();
		}
		for (Iterator<Map.Entry<UUID, Reload>> it = RELOADS.entrySet().iterator(); it.hasNext(); ) {
			var e = it.next();
			ServerPlayerEntity p = server.getPlayerManager().getPlayer(e.getKey());
			if (p == null || !p.isAlive()) { it.remove(); continue; }
			ItemStack held = p.getMainHandStack();
			Reload r = e.getValue();
			if (!(held.getItem() instanceof GunItem g) || g.type != r.type) { it.remove(); continue; } // swapped away: reload cancelled
			if (ticks >= r.end) { it.remove(); finishReload(p, held, r.type); }
		}
	}

	// ------------------------------------------------------------------ firing

	public static void tryFire(ServerPlayerEntity sp, boolean scoped) {
		ItemStack stack = sp.getMainHandStack();
		if (!(stack.getItem() instanceof GunItem gun) || sp.isSpectator() || !sp.isAlive()) return;
		WeaponType t = gun.type;
		UUID id = sp.getUuid();
		if (RELOADS.containsKey(id)) return;
		if (ticks < NEXT_FIRE.getOrDefault(id, 0L)) return;
		ServerWorld w = sp.getServerWorld();

		int mag = GunItem.getMag(stack);
		if (mag <= 0) {
			NEXT_FIRE.put(id, ticks + 8);
			w.playSound(null, sp.getX(), sp.getY(), sp.getZ(), Snd.se(SoundEvents.BLOCK_DISPENSER_FAIL), SoundCategory.PLAYERS, 0.6f, 1.9f); // dry-fire click
			sp.sendMessage(Text.translatable("msg.modernarms.empty"), true);
			return;
		}
		GunItem.setMag(stack, mag - 1);
		NEXT_FIRE.put(id, ticks + t.cooldown);
		announce(sp, Net.EV_FIRE, t, 0);

		switch (t) {
			case PISTOL -> { fireSounds(w, sp, t); hitscan(w, sp, t, scoped); }
			case SNIPER -> {
				fireSounds(w, sp, t); hitscan(w, sp, t, scoped);
				after(5, () -> { if (sp.isAlive()) announce(sp, Net.EV_BOLT, t, 13); });
				after(6, () -> w.playSound(null, sp.getX(), sp.getY(), sp.getZ(), Snd.se(SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN), SoundCategory.PLAYERS, 0.7f, 1.3f));
				after(10, () -> w.playSound(null, sp.getX(), sp.getY(), sp.getZ(), Snd.se(SoundEvents.ITEM_CROSSBOW_LOADING_MIDDLE), SoundCategory.PLAYERS, 0.8f, 0.8f));
				after(14, () -> w.playSound(null, sp.getX(), sp.getY(), sp.getZ(), Snd.se(SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE), SoundCategory.PLAYERS, 0.7f, 1.2f));
			}
			case RPG -> {
				fireSounds(w, sp, t);
				launchRocket(w, sp);
				after(12, () -> startReload(sp)); // auto-reload the single rocket
			}
		}
	}

	private static void fireSounds(ServerWorld w, ServerPlayerEntity sp, WeaponType t) {
		double x = sp.getX(), y = sp.getEyeY(), z = sp.getZ();
		switch (t) {
			case PISTOL -> {
				w.playSound(null, x, y, z, Snd.se(SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST), SoundCategory.PLAYERS, 0.9f, 1.9f);
				w.playSound(null, x, y, z, Snd.se(SoundEvents.ENTITY_ARROW_SHOOT), SoundCategory.PLAYERS, 0.5f, 1.7f);
			}
			case SNIPER -> {
				w.playSound(null, x, y, z, Snd.se(SoundEvents.ENTITY_GENERIC_EXPLODE), SoundCategory.PLAYERS, 0.9f, 1.9f);
				w.playSound(null, x, y, z, Snd.se(SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST), SoundCategory.PLAYERS, 2.5f, 0.6f);
				after(5, () -> w.playSound(null, x, y, z, Snd.se(SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST), SoundCategory.PLAYERS, 1.2f, 0.45f)); // distant echo
			}
			case RPG -> {
				w.playSound(null, x, y, z, Snd.se(SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH), SoundCategory.PLAYERS, 3.0f, 0.55f);
				w.playSound(null, x, y, z, Snd.se(SoundEvents.ENTITY_GENERIC_EXPLODE), SoundCategory.PLAYERS, 1.0f, 1.4f);
			}
		}
	}

	private static Vec3d aimDir(ServerPlayerEntity sp, WeaponType t, boolean scoped) {
		double deg = t.spread;
		if (t == WeaponType.SNIPER && !scoped) deg = 3.5;        // hip-firing the sniper is sloppy; scope for perfect accuracy
		if (deg > 0) {
			if (sp.isSprinting()) deg *= 2.0;
			if (!sp.isOnGround()) deg *= 1.6;
			if (sp.isSneaking()) deg *= 0.6;
		}
		if (deg <= 0) return sp.getRotationVec(1f);
		var r = sp.getRandom();
		float yaw = sp.getYaw() + (float) (r.nextGaussian() * deg * 0.5);
		float pitch = sp.getPitch() + (float) (r.nextGaussian() * deg * 0.5);
		return Vec3d.fromPolar(pitch, yaw);
	}

	private static void hitscan(ServerWorld w, ServerPlayerEntity sp, WeaponType t, boolean scoped) {
		Vec3d eye = sp.getEyePos();
		Vec3d dir = aimDir(sp, t, scoped);
		Vec3d end = eye.add(dir.multiply(t.range));
		BlockHitResult bhr = w.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, sp));
		Vec3d stop = bhr.getType() == HitResult.Type.MISS ? end : bhr.getPos();

		Box box = new Box(eye, stop).expand(1.0);
		EntityHitResult ehr = ProjectileUtil.raycast(sp, eye, stop, box, e -> !e.isSpectator() && e.canHit() && e != sp, eye.squaredDistanceTo(stop));

		Vec3d impact = stop;
		if (ehr != null) {
			impact = ehr.getPos();
			Entity target = ehr.getEntity();
			boolean head = target instanceof LivingEntity && impact.y >= target.getEyeY() - 0.22;
			float dmg = t.damage * (head ? 1.5f : 1.0f);
			if (target instanceof LivingEntity le) le.timeUntilRegen = 0; // allow rapid-fire damage
			target.damage(w.getDamageSources().playerAttack(sp), dmg);
			if (target instanceof LivingEntity le && t == WeaponType.SNIPER) le.takeKnockback(0.7, -dir.x, -dir.z);
			w.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, impact.x, impact.y, impact.z, head ? 6 : 3, 0.1, 0.1, 0.1, 0.1);
			w.playSound(null, sp.getX(), sp.getY(), sp.getZ(), Snd.se(SoundEvents.ENTITY_ARROW_HIT_PLAYER), SoundCategory.PLAYERS, 0.5f, head ? 1.9f : 1.4f);
		} else if (bhr.getType() == HitResult.Type.BLOCK) {
			BlockState state = w.getBlockState(bhr.getBlockPos());
			Vec3d p = impact.add(Vec3d.of(bhr.getSide().getVector()).multiply(0.05));
			int n = t == WeaponType.SNIPER ? 16 : 8;
			w.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), p.x, p.y, p.z, n, 0.08, 0.08, 0.08, 0.18);
			w.spawnParticles(ParticleTypes.SMOKE, p.x, p.y, p.z, 2, 0.03, 0.03, 0.03, 0.01);
			w.playSound(null, p.x, p.y, p.z, state.getSoundGroup().getHitSound(), SoundCategory.BLOCKS, 0.7f, 1.7f);
		}

		var tracer = new Net.TracerS2C(MuzzleUtil.muzzle(sp, t), impact, t == WeaponType.SNIPER ? Net.TRACER_SNIPER : Net.TRACER_PISTOL);
		for (ServerPlayerEntity p : PlayerLookup.around(w, sp.getPos(), 128)) ServerPlayNetworking.send(p, tracer);
	}

	private static void launchRocket(ServerWorld w, ServerPlayerEntity sp) {
		RocketEntity r = new RocketEntity(ModEntities.ROCKET, w);
		r.setOwner(sp);
		Vec3d dir = sp.getRotationVec(1f);
		Vec3d pos = MuzzleUtil.muzzle(sp, WeaponType.RPG);
		r.setPosition(pos);
		r.setVelocity(dir.multiply(0.8));
		r.setYaw(sp.getYaw());
		r.setPitch(sp.getPitch());
		w.spawnEntity(r);
		// back-blast shoves nobody, but the smoke is client-side in ClientFx
	}

	private static void announce(ServerPlayerEntity sp, byte event, WeaponType t, int duration) {
		var payload = new Net.GunEventS2C(sp.getId(), event, (byte) t.id, duration);
		for (ServerPlayerEntity p : PlayerLookup.around(sp.getServerWorld(), sp.getPos(), 96)) ServerPlayNetworking.send(p, payload);
	}

	// ------------------------------------------------------------------ reloading

	public static void startReload(ServerPlayerEntity sp) {
		ItemStack stack = sp.getMainHandStack();
		if (!(stack.getItem() instanceof GunItem gun) || !sp.isAlive()) return;
		WeaponType t = gun.type;
		UUID id = sp.getUuid();
		if (RELOADS.containsKey(id) || GunItem.getMag(stack) >= t.mag) return;
		if (!sp.isCreative() && countAmmo(sp) <= 0) {
			sp.sendMessage(Text.translatable("msg.modernarms.noammo"), true);
			return;
		}
		RELOADS.put(id, new Reload(t, ticks + t.reloadTicks));
		NEXT_FIRE.put(id, ticks + t.reloadTicks);
		announce(sp, Net.EV_RELOAD, t, t.reloadTicks);
		ServerWorld w = sp.getServerWorld();
		int len = t.reloadTicks;
		switch (t) {
			case PISTOL -> {
				snd(w, sp, 4, SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, 0.6f, 1.9f);        // mag out
				snd(w, sp, (int) (len * 0.65), SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, 0.7f, 1.7f); // mag in
				snd(w, sp, len - 5, SoundEvents.ITEM_CROSSBOW_LOADING_END, 0.7f, 1.5f);   // slide rack
			}
			case SNIPER -> {
				snd(w, sp, 5, SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, 0.7f, 1.2f);
				snd(w, sp, 18, SoundEvents.ITEM_CROSSBOW_LOADING_START, 0.8f, 0.9f);
				snd(w, sp, 34, SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, 0.8f, 1.3f);
				snd(w, sp, len - 5, SoundEvents.ITEM_CROSSBOW_LOADING_END, 0.8f, 0.9f);
			}
			case RPG -> {
				snd(w, sp, 6, SoundEvents.BLOCK_CHAIN_PLACE, 1.0f, 0.8f);
				snd(w, sp, 28, SoundEvents.ITEM_CROSSBOW_LOADING_MIDDLE, 1.0f, 0.6f);
				snd(w, sp, len - 8, SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, 1.0f, 0.7f);
				snd(w, sp, len - 3, SoundEvents.ITEM_CROSSBOW_LOADING_END, 1.0f, 0.6f);
			}
		}
	}

	private static void snd(ServerWorld w, ServerPlayerEntity sp, int delay, Object sound, float vol, float pitch) {
		after(Math.max(1, delay), () -> w.playSound(null, sp.getX(), sp.getY(), sp.getZ(), Snd.se(sound), SoundCategory.PLAYERS, vol, pitch));
	}

	private static int countAmmo(ServerPlayerEntity sp) {
		int n = 0;
		var inv = sp.getInventory();
		for (int i = 0; i < inv.size(); i++) if (inv.getStack(i).isOf(ModItems.AMMO)) n += inv.getStack(i).getCount();
		return n;
	}

	private static boolean consumeAmmo(ServerPlayerEntity sp) {
		var inv = sp.getInventory();
		for (int i = 0; i < inv.size(); i++) {
			ItemStack s = inv.getStack(i);
			if (s.isOf(ModItems.AMMO)) { s.decrement(1); return true; }
		}
		return false;
	}

	private static void finishReload(ServerPlayerEntity sp, ItemStack held, WeaponType t) {
		if (!sp.isCreative() && !consumeAmmo(sp)) { sp.sendMessage(Text.translatable("msg.modernarms.noammo"), true); return; }
		GunItem.setMag(held, t.mag); // one Universal Ammo refills a full magazine / loads a rocket
		sp.sendMessage(Text.translatable("msg.modernarms.ready"), true);
	}
}
