package io.github.xrickastley.sevenelements.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Position;
import net.minecraft.world.World;

@Mixin(ArrowItem.class)
public class ArrowItemMixin {
	@Inject(
		method = "createArrow",
		at = @At("RETURN")
	)
	private void onCreateArrow(World world, ItemStack stack, LivingEntity shooter, ItemStack weaponStack, CallbackInfoReturnable<PersistentProjectileEntity> cir) {
		PersistentProjectileEntity arrow = cir.getReturnValue();
		if (arrow != null) {
			if (weaponStack != null && !weaponStack.isEmpty()) {
				arrow.sevenelements$setOriginStack(weaponStack);
			} else {
				arrow.sevenelements$setOriginStack(stack);
			}
		}
	}

	@Inject(
		method = "createEntity",
		at = @At("RETURN")
	)
	private void onCreateEntity(World world, Position pos, ItemStack stack, Direction direction, CallbackInfoReturnable<ProjectileEntity> cir) {
		ProjectileEntity projectile = cir.getReturnValue();
		if (projectile != null) {
			projectile.sevenelements$setOriginStack(stack);
		}
	}
}
