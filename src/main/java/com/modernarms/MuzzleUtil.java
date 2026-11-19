package com.modernarms;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

public final class MuzzleUtil {
	public static Vec3d right(Entity e) {
		Vec3d look = e.getRotationVec(1f);
		Vec3d r = look.crossProduct(new Vec3d(0, 1, 0));
		return r.lengthSquared() < 1e-6 ? new Vec3d(1, 0, 0) : r.normalize();
	}

	/** Approximate barrel tip, to the shooter's right and slightly below the eyes. */
	public static Vec3d muzzle(Entity e, WeaponType t) {
		double len = switch (t) { case PISTOL -> 0.9; case SNIPER -> 1.6; case RPG -> 1.7; };
		double side = t == WeaponType.PISTOL ? 0.22 : 0.18;
		double down = t == WeaponType.PISTOL ? 0.14 : 0.12;
		return e.getEyePos().add(e.getRotationVec(1f).multiply(len)).add(right(e).multiply(side)).add(0, -down, 0);
	}
}
