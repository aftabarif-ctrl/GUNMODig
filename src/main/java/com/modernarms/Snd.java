package com.modernarms;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;

/** Vanilla sound constants are a mix of SoundEvent and RegistryEntry<SoundEvent>; this unifies them. */
public final class Snd {
	@SuppressWarnings("unchecked")
	public static SoundEvent se(Object o) {
		if (o instanceof SoundEvent s) return s;
		return ((RegistryEntry<SoundEvent>) o).value();
	}
}
