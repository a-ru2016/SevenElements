package io.github.xrickastley.sevenelements.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;

import java.util.HashSet;
import java.util.Set;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import io.github.xrickastley.sevenelements.SevenElements;
import io.github.xrickastley.sevenelements.component.ElementComponent;
import io.github.xrickastley.sevenelements.effect.SevenElementsStatusEffects;
import io.github.xrickastley.sevenelements.element.Element;
import io.github.xrickastley.sevenelements.element.ElementalApplication;
import io.github.xrickastley.sevenelements.util.Array;
import io.github.xrickastley.sevenelements.util.CircleRenderer;
import io.github.xrickastley.sevenelements.util.Functions;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

@Mixin(InGameHud.class)
public class InGameHudMixin {
	@Shadow
	@Final
	private MinecraftClient client;

	@Shadow
	@Final
	private static Identifier POWDER_SNOW_OUTLINE;

	@Shadow
	private void renderOverlay(DrawContext context, Identifier texture, float opacity) {
		throw new AssertionError();
	}

	@Shadow
	private int getHeartRows(int heartCount) {
		throw new AssertionError();
	}

	@Inject(
		method = "renderStatusBars",
		at = @At("TAIL")
	)
	private void renderAppliedElements(DrawContext context, CallbackInfo ci) {
		PlayerEntity player = this.client.getCameraEntity() instanceof PlayerEntity ? (PlayerEntity) this.client.getCameraEntity() : null;
		if (player == null) return;

		this.client.getProfiler().swap("seven-elements:elements");

		int width = this.client.getWindow().getScaledWidth();
		int height = this.client.getWindow().getScaledHeight();

		int x = width / 2 - 91;
		int y = height - 39;

		int maxHealth = net.minecraft.util.math.MathHelper.ceil(player.getMaxHealth());
		int absorption = net.minecraft.util.math.MathHelper.ceil(player.getAbsorptionAmount());
		int lines = this.getHeartRows(maxHealth + absorption);

		y -= lines * 10;

		if (player.getArmor() > 0) {
			y -= 10;
		}

		final ElementComponent component = ElementComponent.KEY.get(player);

		final double scaleFactor = MinecraftClient.getInstance().getWindow().getScaleFactor();
		final Set<Identifier> existing = new HashSet<>();
		final Array<Identifier> appliedElements = component
			.getAppliedElements()
			.map(Functions.compose(ElementalApplication::getElement, Element::getTexture))
			.filter(existing::add);

		if (component.getCrystallizeShield() != null && component.getCrystallizeShield().getRight() > 0)
			appliedElements.add(SevenElements.identifier("textures/status_effect/defense.png"));

		for (int i = 0; i < appliedElements.length(); i++) {
			final Identifier texture = appliedElements.get(i);
			final int x1 = x + (i * 10);
			final CircleRenderer circleRenderer = new CircleRenderer((x1 + 4.5) * scaleFactor, (y + 4.5) * scaleFactor, 0);

			circleRenderer
				.add(4.5 * scaleFactor, 1, 0x7F646464)
				.draw(context.getMatrices().peek().getPositionMatrix());

			context.drawTexture(texture, x1, y, 9, 9, 0, 0, 9, 9, 9, 9);
		}
	}


	@Inject(
		method = "renderMiscOverlays",
		at = @At("TAIL")
	)
	private void renderFrozenOverlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
		if (client.player != null && client.player.hasStatusEffect(SevenElementsStatusEffects.FROZEN))
			this.renderOverlay(context, POWDER_SNOW_OUTLINE, 1.0F);
	}
}
