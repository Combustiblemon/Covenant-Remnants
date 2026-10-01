package org.combustiblelemon.covenantremnants.systems

import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipSystemAPI

/**
 * Covenant-side integration of the vanilla "fortressshield" ship system (Reverence-class
 * battleship, covenant_remnants_capital1).
 *
 * Damage reduction needs no code here: vanilla FortressShieldStats modifies
 * stats.shieldDamageTakenMult, which the Covenant shield honours in
 * [CovenantShieldsStats.ShieldDamageListener]. This object covers what the vanilla system
 * cannot do on a hull without a vanilla shield: the shield-up gate and regen freeze
 * (CovenantShieldsStats), the AI (ai/FortressShieldAI), and the visual cue
 * (CovenantShieldsRenderPlugin + FortressLayer).
 */
object FortressShield {
    const val SYSTEM_ID = "fortressshield"

    // AI (ai/FortressShieldAI): projected shield loss over AI_LOOKAHEAD seconds, as a fraction
    // of max shield HP. ON at >= AI_ON_FRACTION (or >= remaining HP); OFF once below
    // AI_OFF_FRACTION for AI_OFF_HOLD seconds, never before AI_MIN_ON_TIME seconds on.
    const val AI_ON_FRACTION = 0.05f
    const val AI_OFF_FRACTION = 0.02f
    const val AI_OFF_HOLD = 1f
    const val AI_MIN_ON_TIME = 1f
    const val AI_LOOKAHEAD = 1f
    const val AI_SCAN_RANGE = 2500f

    // Visual: shield fill/border colours are lerped from the base colours toward these by the
    // system's effect level (vanilla Fortress look: magenta inner, white ring).
    // r, g, b on 0-255; a on 0-100 (the scale ShieldShader.updateColor/updateBorder use).
    val TINT_FILL = intArrayOf(255, 100, 255, 45)
    val TINT_BORDER = intArrayOf(255, 255, 255, 100)

    /** The ship's Fortress Shield system, or null if the ship's system is something else. */
    fun system(ship: ShipAPI?): ShipSystemAPI? = ship?.system?.takeIf { it.id == SYSTEM_ID }

    /** 0 when the ship has no Fortress Shield or it is off; ramps with charge-up/down. */
    fun effectLevel(ship: ShipAPI?): Float = system(ship)?.effectLevel ?: 0f
}
