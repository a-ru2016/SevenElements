package io.github.xrickastley.sevenelements.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.github.xrickastley.sevenelements.component.ElementalInfusionComponent;
import io.github.xrickastley.sevenelements.factory.SevenElementsComponents;

import net.minecraft.item.ItemStack;

@Mixin(targets = "net.minecraft.screen.GrindstoneScreenHandler$4")
public class GrindstoneScreenHandler$4Mixin {
	@Inject(
		method = "getExperience(Lnet/minecraft/item/ItemStack;)I",
		at = @At("RETURN"),
		cancellable = true,
		require = 0
	)
	public void addElementsAsExperience(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
		int original = cir.getReturnValue();
		final ElementalInfusionComponent component = stack.get(SevenElementsComponents.ELEMENTAL_INFUSION_COMPONENT);

		if (component == null || !component.hasElementalInfusion()) return;

		cir.setReturnValue(original + (int) (60 * component.getGaugeUnits()));
	}
}
