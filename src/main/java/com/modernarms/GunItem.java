package com.modernarms;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class GunItem extends Item {
	public final WeaponType type;

	public GunItem(Settings settings, WeaponType type) {
		super(settings);
		this.type = type;
	}

	public static int getMag(ItemStack s) { return s.getOrDefault(ModComponents.MAG, 0); }
	public static void setMag(ItemStack s, int v) { s.set(ModComponents.MAG, Math.max(0, v)); }

	/** Pistol fires on right click (hold to keep firing). Sniper/RPG right-click is "aim" (client side), they fire on left click. */
	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);
		if (type != WeaponType.PISTOL || hand != Hand.MAIN_HAND) return TypedActionResult.pass(stack);
		if (!world.isClient && player instanceof ServerPlayerEntity sp) GunLogic.tryFire(sp, false);
		return TypedActionResult.consume(stack); // CONSUME = no vanilla arm swing, we animate ourselves
	}

	/** Changing the loaded-ammo component must not replay the "equip" animation. */
	@Override
	public boolean allowComponentsUpdateAnimation(PlayerEntity player, Hand hand, ItemStack oldStack, ItemStack newStack) {
		return false;
	}
}
