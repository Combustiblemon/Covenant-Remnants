package org.combustiblelemon.covenantremnants.systems

import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import java.awt.Color

/**
 * PD Overdrive — a stat-mod ship system for the SDV-class point-defense screen.
 *
 * It does not add weapons; it sharpens the ones already installed. While active it
 * dramatically improves weapon tracking (turn rate) and autofire accuracy, and grants a
 * modest energy-weapon range bonus, turning the ship's fixed battery of small mounts into
 * a far more effective point-defense grid. Scales with the system's effect level.
 */
class PDOverdrive : BaseShipSystemScript() {

    private val turnRateBonusPercent = 300f
    private val aimAccuracyFlat = 3f
    private val energyRangeBonusPercent = 25f

    override fun apply(
        stats: MutableShipStatsAPI,
        id: String,
        state: ShipSystemStatsScript.State,
        effectLevel: Float
    ) {
        stats.weaponTurnRateBonus.modifyPercent(id, turnRateBonusPercent * effectLevel)
        stats.beamWeaponTurnRateBonus.modifyPercent(id, turnRateBonusPercent * effectLevel)
        stats.autofireAimAccuracy.modifyFlat(id, aimAccuracyFlat * effectLevel)
        stats.energyWeaponRangeBonus.modifyPercent(id, energyRangeBonusPercent * effectLevel)

        val entity = stats.entity
        if (entity is ShipAPI && effectLevel > 0f) {
            entity.setJitter(this, Color(255, 150, 255, 180), effectLevel, 3, 6f)
        }
    }

    override fun unapply(stats: MutableShipStatsAPI, id: String) {
        stats.weaponTurnRateBonus.unmodify(id)
        stats.beamWeaponTurnRateBonus.unmodify(id)
        stats.autofireAimAccuracy.unmodify(id)
        stats.energyWeaponRangeBonus.unmodify(id)
    }
}
