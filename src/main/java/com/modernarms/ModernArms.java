package com.modernarms;

import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;

public class ModernArms implements ModInitializer {
	public static final String MOD_ID = "modernarms";

	public static Identifier id(String path) { return Identifier.of(MOD_ID, path); }

	@Override
	public void onInitialize() {
		ModComponents.init();
		ModItems.init();
		ModEntities.init();
		Net.init();
		GunLogic.init();
	}
}
