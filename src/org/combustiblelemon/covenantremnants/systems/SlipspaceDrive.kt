package org.combustiblelemon.covenantremnants.systems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript.StatusData
import org.combustiblelemon.covenantremnants.plugins.SlipspaceDriveRenderPlugin
import org.combustiblelemon.covenantremnants.utils.render.blink.BlinkRipple
import org.combustiblelemon.covenantremnants.utils.render.rupture.RuptureEffect
import org.lwjgl.util.vector.Vector2f
import kotlin.math.cos
import kotlin.math.sin

/**
 * Slipspace Drive — the short-range blink of frigate1 / frigate3 / destroyer2
 * (`covenant_remnants_slipspacedrive`). The jump itself is the engine's TELEPORTER type (see the
 * .system file): at the end of the chargeup the ship is moved to the targeted point, clamped to the
 * range. This script only drives the visuals, after the Halo 3: ODST carrier jump:
 *
 * - Chargeup (IN, 1.5 s): from 0 to [LURCH_AT] the hull ripple sweeps bow to stern while the entry
 *   portal opens off the bow (along the facing, not toward the destination: the ship goes in nose
 *   first, developer 2026-10-02); from [LURCH_AT] to 1 the ship "lurches"
 *   into the portal — the real ship goes invisible and a ghost slides forward into the portal,
 *   fading as it goes. The engine then teleports the ship.
 * - Chargedown (OUT, 0.5 s): the exit portal opens at the landing point; a ghost slides out of it
 *   nose first, along the ship's current facing, while the real ship fades in. Both portals close afterwards.
 *
 * No flux gate, no exit check, no flux cost (developer rulings 2026-10-02). All numbers are
 * placeholders — see planning/implementations/slipspace-drive-blink.md.
 */
class SlipspaceDrive : BaseShipSystemScript() {
    companion object {
        const val SYSTEM_ID = "covenant_remnants_slipspacedrive"

        /** Fraction of the chargeup at which the lurch starts (ripple + portal before, lurch after). */
        const val LURCH_AT = 2f / 3f

        /** Portal radius = collision radius x this. */
        const val PORTAL_RADIUS_MULT = 1.3f

        /** Entry portal centre sits collision radius x this off the bow, along the facing (one ship length). */
        const val PORTAL_AHEAD_MULT = 2f

        /** The exit ghost starts collision radius x this inside the exit portal. */
        const val EXIT_START_INSIDE_MULT = 1f

        /** The exit portal opens this fast (s) as the jump lands. */
        const val EXIT_OPEN_DURATION = 0.15f

        /** Ghost alpha fades to zero over this last share of the lurch. */
        const val LURCH_FADE_SHARE = 0.4f

        private var combatEngine: CombatEngineAPI? = null
        private var renderPlugin: SlipspaceDriveRenderPlugin? = null

        /** The one-per-combat blink renderer, created and registered on first use. */
        fun renderPlugin(engine: CombatEngineAPI): SlipspaceDriveRenderPlugin {
            if (combatEngine !== engine) {
                combatEngine = engine
                renderPlugin = null
            }
            return renderPlugin ?: SlipspaceDriveRenderPlugin().also {
                renderPlugin = it
                engine.addLayeredRenderingPlugin(it)
            }
        }
    }

    private enum class Phase { IDLE, CHARGING, LANDED }

    private var engineSeen: CombatEngineAPI? = null
    private var phase = Phase.IDLE
    private val direction = Vector2f(1f, 0f)
    private val origin = Vector2f()
    private val landing = Vector2f()
    private val ghost = Vector2f()
    private var entryPortal: RuptureEffect? = null
    private var exitPortal: RuptureEffect? = null
    private var ripple: BlinkRipple? = null

    override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
        val ship = stats.entity as? ShipAPI ?: return
        val engine = Global.getCombatEngine() ?: return
        if (engineSeen !== engine) {
            engineSeen = engine
            clearEffects()
            phase = Phase.IDLE
        }
        val plugin = renderPlugin(engine)
        val radius = ship.collisionRadius

        when (state) {
            ShipSystemStatsScript.State.IN -> {
                if (phase != Phase.CHARGING) startCharge(ship, plugin, radius)
                val lurch = ((effectLevel - LURCH_AT) / (1f - LURCH_AT)).coerceIn(0f, 1f)
                if (lurch > 0f) {
                    // Ease in: the ship accelerates into the portal.
                    val eased = lurch * lurch
                    ghost.set(
                        origin.x + direction.x * radius * PORTAL_AHEAD_MULT * eased,
                        origin.y + direction.y * radius * PORTAL_AHEAD_MULT * eased
                    )
                    val ghostAlpha = (1f - (lurch - (1f - LURCH_FADE_SHARE)) / LURCH_FADE_SHARE).coerceIn(0f, 1f)
                    ship.extraAlphaMult = 0f
                    ship.setCopyLocation(ghost, ghostAlpha, ship.facing)
                }
            }

            ShipSystemStatsScript.State.ACTIVE, ShipSystemStatsScript.State.OUT -> {
                if (phase == Phase.CHARGING) land(ship, plugin, radius)
                if (phase != Phase.LANDED) return
                // effectLevel runs 1 -> 0 through the chargedown.
                val t = if (state == ShipSystemStatsScript.State.OUT) (1f - effectLevel).coerceIn(0f, 1f) else 0f
                val eased = 1f - (1f - t) * (1f - t) // ease out: the ship decelerates out of the portal
                val inside = radius * EXIT_START_INSIDE_MULT * (1f - eased)
                // Along the current facing (the engine turns the ship toward its target during the
                // chargedown), so the ghost always comes out nose first.
                facingDirection(ship, direction)
                ghost.set(landing.x - direction.x * inside, landing.y - direction.y * inside)
                ship.extraAlphaMult = t
                ship.setCopyLocation(ghost, 1f - t, ship.facing)
            }

            else -> {
                if (phase != Phase.IDLE) finish(ship)
            }
        }
    }

    override fun unapply(stats: MutableShipStatsAPI, id: String) {
        val ship = stats.entity as? ShipAPI ?: return
        finish(ship)
    }

    override fun getStatusData(index: Int, state: ShipSystemStatsScript.State, effectLevel: Float): StatusData? {
        if (index != 0) return null
        return when (state) {
            ShipSystemStatsScript.State.IN -> StatusData("slipspace translation", false)
            ShipSystemStatsScript.State.OUT -> StatusData("emerging from slipspace", false)
            else -> null
        }
    }

    private fun startCharge(ship: ShipAPI, plugin: SlipspaceDriveRenderPlugin, radius: Float) {
        phase = Phase.CHARGING
        origin.set(ship.location)
        // Off the bow: turning is locked through the chargeup, so the facing holds until the jump.
        facingDirection(ship, direction)
        val preLurch = ship.system.chargeUpDur * LURCH_AT
        entryPortal = plugin.addPortal(
            Vector2f(
                origin.x + direction.x * radius * PORTAL_AHEAD_MULT,
                origin.y + direction.y * radius * PORTAL_AHEAD_MULT
            ),
            radius * PORTAL_RADIUS_MULT,
            preLurch
        )
        ripple = plugin.addRipple(ship, preLurch)
    }

    /** First frame after the engine moved the ship: close the entry, open the exit at the landing point. */
    private fun land(ship: ShipAPI, plugin: SlipspaceDriveRenderPlugin, radius: Float) {
        phase = Phase.LANDED
        landing.set(ship.location)
        entryPortal?.close()
        ripple?.cancel()
        exitPortal = plugin.addPortal(Vector2f(landing), radius * PORTAL_RADIUS_MULT, EXIT_OPEN_DURATION)
    }

    private fun finish(ship: ShipAPI) {
        ship.extraAlphaMult = 1f
        ship.setCopyLocation(null, 1f, ship.facing)
        clearEffects()
        phase = Phase.IDLE
    }

    private fun clearEffects() {
        entryPortal?.close()
        exitPortal?.close()
        ripple?.cancel()
        entryPortal = null
        exitPortal = null
        ripple = null
    }

    /** Unit vector along the ship's facing (the bow direction). */
    private fun facingDirection(ship: ShipAPI, out: Vector2f) {
        val radians = Math.toRadians(ship.facing.toDouble())
        out.set(cos(radians).toFloat(), sin(radians).toFloat())
    }
}
