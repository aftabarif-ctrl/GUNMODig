package com.modernarms;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModEntities {
	public static final EntityType<RocketEntity> ROCKET = Registry.register(Registries.ENTITY_TYPE, ModernArms.id("rocket"),
		EntityType.Builder.<RocketEntity>create(RocketEntity::new, SpawnGroup.MISC)
			.dimensions(0.3f, 0.3f).maxTrackingRange(16).trackingTickInterval(1).makeFireImmune().build("rocket"));

	public static void init() {}
}
