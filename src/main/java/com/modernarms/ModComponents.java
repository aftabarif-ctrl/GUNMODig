package com.modernarms;

import com.mojang.serialization.Codec;
import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModComponents {
	/** Rounds currently loaded in a weapon. */
	public static final ComponentType<Integer> MAG = Registry.register(Registries.DATA_COMPONENT_TYPE, ModernArms.id("mag"),
		ComponentType.<Integer>builder().codec(Codec.INT).packetCodec(PacketCodecs.VAR_INT).build());

	public static void init() {}
}
