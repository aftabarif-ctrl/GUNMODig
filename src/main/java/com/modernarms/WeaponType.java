package com.modernarms;

/** All gameplay tuning lives here. */
public enum WeaponType {
	//        id  mag cooldown reload damage range spread(deg) recoilTicks canAim
	PISTOL(0, 12, 4, 36, 6.0f, 80, 0.6f, 6, false),
	SNIPER(1, 5, 18, 50, 38.0f, 320, 0.0f, 14, true),
	RPG(2, 1, 30, 64, 0f, 0, 0f, 18, true);

	public final int id, mag, cooldown, reloadTicks, range, recoilTicks;
	public final float damage, spread;
	public final boolean canAim;

	WeaponType(int id, int mag, int cooldown, int reloadTicks, float damage, int range, float spread, int recoilTicks, boolean canAim) {
		this.id = id; this.mag = mag; this.cooldown = cooldown; this.reloadTicks = reloadTicks;
		this.damage = damage; this.range = range; this.spread = spread; this.recoilTicks = recoilTicks; this.canAim = canAim;
	}

	public static WeaponType byId(int id) {
		for (WeaponType t : values()) if (t.id == id) return t;
		return PISTOL;
	}
}
