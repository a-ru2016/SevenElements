package io.github.xrickastley.sevenelements.element.reaction;

import org.jetbrains.annotations.Nullable;

import io.github.xrickastley.sevenelements.element.Element;
import io.github.xrickastley.sevenelements.element.ElementalApplication;
import io.github.xrickastley.sevenelements.element.ElementalApplications;
import io.github.xrickastley.sevenelements.element.ElementalDamageSource;
import io.github.xrickastley.sevenelements.element.InternalCooldownContext;
import io.github.xrickastley.sevenelements.registry.SevenElementsDamageTypes;

import net.minecraft.entity.LivingEntity;

public abstract sealed class AbstractSwirlElementalReaction
	extends ElementalReaction
	permits PyroSwirlElementalReaction, HydroSwirlElementalReaction, ElectroSwirlElementalReaction, CryoSwirlElementalReaction, FrozenSwirlElementalReaction
{
	private final Element swirlElement;
	private final boolean elementalAbsorptionOnly;

	/**
	 * Creates a Swirl reaction with the specified settings. <br> <br>
	 *
	 * The specified <b>aura element</b> will serve as the "swirlable" element. <br> <br>
	 *
	 * For example, if the Aura Element is {@link Element#PYRO}, then the Pyro element is swirled
	 * and spread to nearby targets (r=3m). <br> <br>
	 *
	 * For the Gauge Units applied by the Swirl reaction, as well as its duration, you may refer
	 * here: <a href=https://genshin-impact.fandom.com/wiki/Elemental_Gauge_Theory/Advanced_Mechanics#Swirl_Elemental_Application">
	 * Swirl Elemental Application</a>
	 *
	 * @param settings The {@code Settings} for this {@code ElementalReaction}.
	 */
	AbstractSwirlElementalReaction(Settings settings) {
		this(settings, settings.getAuraElement());
	}

	/**
	 * Creates a Swirl reaction with the specified settings. <br> <br>
	 *
	 * The "swirlable" element is the spread element upon triggering the swirl reaction. <br> <br>
	 *
	 * For example, if the swirlable Element is {@link Element#PYRO}, then the Pyro element is
	 * swirled and spread to nearby targets (r=3m). <br> <br>
	 *
	 * For the Gauge Units applied by the Swirl reaction, as well as its duration, you may refer
	 * here: <a href=https://genshin-impact.fandom.com/wiki/Elemental_Gauge_Theory/Advanced_Mechanics#Swirl_Elemental_Application">
	 * Swirl Elemental Application</a>
	 *
	 * @param settings The {@code Settings} for this {@code ElementalReaction}.
	 * @param elementalAbsorptionOnly Whether Swirl will <i>only</i> deal its Elemental Absorption
	 * damage to the Swirl target instead, i.e. the entity the Swirl reaction was triggered on.
	 */
	AbstractSwirlElementalReaction(Settings settings, boolean elementalAbsorptionOnly) {
		super(settings);

		this.swirlElement = settings.getAuraElement();
		this.elementalAbsorptionOnly = elementalAbsorptionOnly;
	}

	/**
	 * Creates a Swirl reaction with the specified settings. <br> <br>
	 *
	 * The "swirlable" element is the spread element upon triggering the swirl reaction. <br> <br>
	 *
	 * For example, if the swirlable Element is {@link Element#PYRO}, then the Pyro element is
	 * swirled and spread to nearby targets (r=3m). <br> <br>
	 *
	 * For the Gauge Units applied by the Swirl reaction, as well as its duration, you may refer
	 * here: <a href=https://genshin-impact.fandom.com/wiki/Elemental_Gauge_Theory/Advanced_Mechanics#Swirl_Elemental_Application">
	 * Swirl Elemental Application</a>
	 *
	 * @param settings The {@code Settings} for this {@code ElementalReaction}.
	 * @param swirlElement The element to Swirl.
	 */
	AbstractSwirlElementalReaction(Settings settings, Element swirlElement) {
		super(settings);

		this.swirlElement = swirlElement;
		this.elementalAbsorptionOnly = false;
	}

	private static final java.util.Map<Long, java.util.List<LivingEntity>> SWIRLED_MAP = new java.util.concurrent.ConcurrentHashMap<>();

	@Override
	protected void onReaction(LivingEntity entity, ElementalApplication auraElement, ElementalApplication triggeringElement, double reducedGauge, @Nullable LivingEntity origin) {
		final double gaugeOriginAura = auraElement.getCurrentGauge() + reducedGauge;
		final double gaugeAnemo = triggeringElement.getCurrentGauge() + reducedGauge;

		final double gaugeReaction = gaugeOriginAura >= (0.5 * gaugeAnemo)
			? gaugeAnemo
			: gaugeOriginAura;

		final double gaugeSwirlAttack = ((gaugeReaction - 0.04) * 1.25) + 1;

		// 元素熟知（武器攻撃力）補正の計算
		float masteryMultiplier = 1.0f;
		if (origin != null) {
			double totalAttack = origin.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE);
			double baseAttack = origin.getAttributeBaseValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_ATTACK_DAMAGE);
			double weaponAttack = Math.max(0, totalAttack - baseAttack);
			masteryMultiplier = 1.0f + (float) (weaponAttack * 0.15);
		}

		for (final LivingEntity target : ElementalReaction.getEntitiesInAoE(entity, 6, t -> t != origin)) {
			final float damage = (!elementalAbsorptionOnly || target == entity
				? ElementalReaction.getReactionDamage(entity, 0.6)
				: 0f) * masteryMultiplier;

			/*
			 * There isn't much documentation on the DMG of Elemental Absorption, but from tests,
			 * there *are* instances in which Swirl DMG = Elemental Absorption DMG. As such, it is
			 * used instead.
			 */

			final ElementalDamageSource source = new ElementalDamageSource(
				entity
					.getDamageSources()
					.create(SevenElementsDamageTypes.SWIRL, origin),
				ElementalApplications.gaugeUnits(target, swirlElement, target == entity ? 0f : gaugeSwirlAttack, true),
				InternalCooldownContext.ofNone(origin)
			).shouldApplyDMGBonus(false);

			target.damage(source, damage);
		}

		// --- 吸引処理 ---
		// 1. 周囲のエンティティを拡散したエンティティ(entity)へ吸い寄せる (吸引範囲を 10.0m に拡大、吸引強度を 0.1 に減少)
		for (final LivingEntity target : ElementalReaction.getEntitiesInAoE(entity, 10.0, t -> t != origin && t != entity)) {
			net.minecraft.util.math.Vec3d dir = entity.getPos().subtract(target.getPos());
			double distance = dir.length();
			if (distance > 0.1) {
				net.minecraft.util.math.Vec3d pullVec = dir.normalize().multiply(0.1 * (1.0 - (distance / 10.0)));
				target.setVelocity(target.getVelocity().add(pullVec));
				target.velocityModified = true;
			}
		}

		// 2. 同一チック内で拡散が複数発生した場合、それら拡散が起きたエンティティ同士を引き寄せる (吸引強度を 0.2 に減少)
		long time = entity.getWorld().getTime();
		java.util.List<LivingEntity> swirledThisTick = SWIRLED_MAP.computeIfAbsent(time, k -> new java.util.ArrayList<>());
		for (LivingEntity other : swirledThisTick) {
			if (other != entity && other.isAlive()) {
				net.minecraft.util.math.Vec3d dirToOther = other.getPos().subtract(entity.getPos());
				double dist = dirToOther.length();
				if (dist > 0.1) {
					net.minecraft.util.math.Vec3d pullVec = dirToOther.normalize().multiply(0.2);
					entity.setVelocity(entity.getVelocity().add(pullVec));
					other.setVelocity(other.getVelocity().add(pullVec.multiply(-1.0)));
					entity.velocityModified = true;
					other.velocityModified = true;
				}
			}
		}
		swirledThisTick.add(entity);
		SWIRLED_MAP.keySet().removeIf(t -> t < time - 20); // クリーンアップ
	}
}
