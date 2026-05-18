package io.github.xrickastley.sevenelements.registry;

import io.github.xrickastley.sevenelements.element.reaction.ElementalReaction;

import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.minecraft.registry.Registry;

public final class SevenElementsRegistries {
	public static Registry<ElementalReaction> ELEMENTAL_REACTION = null;

	public static void load() {
		if (ELEMENTAL_REACTION == null) {
			ELEMENTAL_REACTION = FabricRegistryBuilder.createSimple(SevenElementsRegistryKeys.ELEMENTAL_REACTION)
				.attribute(RegistryAttribute.SYNCED)
				.buildAndRegister();
		}
	}
}

