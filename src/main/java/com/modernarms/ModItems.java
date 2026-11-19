package com.modernarms;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModItems {
	public static final Item AMMO = reg("universal_ammo", new Item(new Item.Settings().maxCount(64)));
	public static final Item PISTOL = reg("pistol",
		new GunItem(new Item.Settings().maxCount(1).component(ModComponents.MAG, WeaponType.PISTOL.mag), WeaponType.PISTOL));
	public static final Item SNIPER = reg("sniper_rifle",
		new GunItem(new Item.Settings().maxCount(1).component(ModComponents.MAG, WeaponType.SNIPER.mag), WeaponType.SNIPER));
	public static final Item RPG = reg("rpg",
		new GunItem(new Item.Settings().maxCount(1).component(ModComponents.MAG, WeaponType.RPG.mag), WeaponType.RPG));

	private static Item reg(String name, Item item) {
		return Registry.register(Registries.ITEM, ModernArms.id(name), item);
	}

	public static void init() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(e -> {
			e.add(PISTOL); e.add(SNIPER); e.add(RPG); e.add(AMMO);
		});
	}
}
