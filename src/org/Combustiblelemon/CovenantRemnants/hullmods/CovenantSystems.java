package org.Combustiblelemon.CovenantRemnants.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.util.Misc;
import org.magiclib.util.MagicIncompatibleHullmods;

import java.util.HashSet;
import java.util.Set;

public class CovenantSystems extends BaseHullMod {

	/* A Ship of the 14th Domain Battlegroup
	 * well-maintained survivor of the original battlegroup which founded the Hegemony
	 * Sterling example of the Domain Navy's traditional "decisive battle" doctrine
	 * focus on superior armour and firepower on ships of the line to destroy the enemy
	 * - slightly better flux handling
	 * - slightly better armour
	 * - slightly worse speed/maneuver
	 * - 
	 */

	private static final Set<String> BLOCKED_HULLMODS = new HashSet<>(2);
	
	private enum ENERGY_WEAPON_RANGE_MULT {
		CAPITAL_SHIP(1.8f),
		CRUISER(1.6f),
		DESTROYER(1.5f),
		FRIGATE(1.3f),
		FIGHTER(1.3f);

		private final float value;

		private ENERGY_WEAPON_RANGE_MULT(float value) {
			this.value = value;
		}

		public float getValue() {
			return value;
		}
	};


	static {
		BLOCKED_HULLMODS.add("dedicated_targeting_core");
		BLOCKED_HULLMODS.add("targetingunit");
	}

	@Override
	public boolean isApplicableToShip(ShipAPI ship) {
		return false;
	}

	@Override
	public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
		for (String tmp : BLOCKED_HULLMODS) {
			if (ship.getVariant().getHullMods().contains(tmp)) {
				MagicIncompatibleHullmods.removeHullmodWithWarning(ship.getVariant(),tmp,"covenant_remnants_covenantsystems");
			}
		}
	}

	public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
		float rangeMult = 1f;
		float ENERGY_WEAPON_FLUX_MULT = 0.80f;

		switch (hullSize) {
			case CAPITAL_SHIP:
				rangeMult = ENERGY_WEAPON_RANGE_MULT.CAPITAL_SHIP.value;
				break;

			case CRUISER:
				rangeMult = ENERGY_WEAPON_RANGE_MULT.CRUISER.value;
				break;

			case DESTROYER:
				rangeMult = ENERGY_WEAPON_RANGE_MULT.DESTROYER.value;
				break;

			case FRIGATE:
				rangeMult = ENERGY_WEAPON_RANGE_MULT.FRIGATE.value;
				break;

			case FIGHTER:
				rangeMult = ENERGY_WEAPON_RANGE_MULT.FIGHTER.value;
				break;
		}

		stats.getEnergyWeaponRangeBonus().modifyMult(id, rangeMult);
		stats.getEnergyWeaponFluxCostMod().modifyMult(id, ENERGY_WEAPON_FLUX_MULT);
	}
	
	public String getDescriptionParam(int index, HullSize hullSize) {
//		if (index == 0) return "" + (int) ((Float) mag.get(HullSize.FRIGATE) * 100f);
//		if (index == 1) return "" + (int) ((Float) mag.get(HullSize.DESTROYER) * 100f);
//		if (index == 2) return "" + (int) ((Float) mag.get(HullSize.CRUISER) * 100f);
//		if (index == 3) return "" + (int) ((Float) mag.get(HullSize.CAPITAL_SHIP) * 100f);
		//if (index == 0) return Misc.getRoundedValue((Float) mag.get(HullSize.FRIGATE) + 1f);
		//if (index == 1) return Misc.getRoundedValue((Float) mag.get(HullSize.DESTROYER) + 1f);
		//if (index == 2) return Misc.getRoundedValue((Float) mag.get(HullSize.CRUISER) + 1f);
		//if (index == 3) return Misc.getRoundedValue((Float) mag.get(HullSize.CAPITAL_SHIP) + 1f);
//		if (index == 0) return Misc.getRoundedValue(ARMOR_BONUS);
//		if (index == 1) return "" + (int) Math.round((1f - HANDLING_MULT) * 100f) + "%"; // + Strings.X;
//		if (index == 2) return "" + (int) Math.round((CAPACITY_MULT - 1f) * 100f) + "%";
		return null;
	}
}
