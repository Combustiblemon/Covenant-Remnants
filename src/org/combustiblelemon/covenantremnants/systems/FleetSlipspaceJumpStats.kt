package org.combustiblelemon.covenantremnants.systems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipSystemAPI
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript

/**
 * Stats script of the Fleet Slipspace Jump ([FleetSlipspaceJump.SYSTEM_ID]).
 *
 * System states: IN = charge-up (carrier locked, rupture preview grows), ACTIVE = fade window
 * (everything inside the radius is phased and faded under the opaque rupture), OUT = arrival
 * (the jump happens on the first OUT frame). The work itself lives in
 * [FleetSlipspaceJumpSession], one per activation, so it keeps running (and can clean up) if the
 * carrier dies mid-jump.
 *
 * The .system sets `runScriptWhileIdle`, so [apply] also runs while idle: that is what creates the
 * per-combat render and targeting plugins before the first jump. With that flag the engine never
 * calls `unapply`; the session watches the system state itself.
 *
 * Player activation (developer 2026-10-01): the system key primes, left click confirms the cursor
 * point, right click cancels (FleetSlipspaceJumpTargetingPlugin). Usable when: flux is below
 * FLUX_GATE and the destination (the confirmed / aimed point for the player, the AI-chosen point
 * otherwise) is at least MIN_RANGE away, inside the map, with the carrier's landing spot clear of
 * ships that are not coming along.
 */
class FleetSlipspaceJumpStats : BaseShipSystemScript() {
    private companion object {
        // Every state must last at least a few frames, or the OUT frame the jump waits for could be
        // skipped entirely.
        const val MIN_STATE_DURATION = 0.1f
    }

    // The stats script instance may outlive a battle; drop per-battle state on a new engine.
    private var combatEngine: CombatEngineAPI? = null
    private var session: FleetSlipspaceJumpSession? = null

    // Timings are read by the engine once per ship per battle, so console changes apply from the
    // next battle. Cooldown is applied per activation (see apply).
    override fun getInOverride(ship: ShipAPI?): Float =
        FleetSlipspaceJump.chargeUp.value.coerceAtLeast(MIN_STATE_DURATION)

    override fun getActiveOverride(ship: ShipAPI?): Float =
        FleetSlipspaceJump.fade.value.coerceAtLeast(MIN_STATE_DURATION)

    override fun getOutOverride(ship: ShipAPI?): Float =
        FleetSlipspaceJump.arrival.value.coerceAtLeast(MIN_STATE_DURATION)

    override fun isUsable(system: ShipSystemAPI?, ship: ShipAPI?): Boolean {
        if (system == null || ship == null) return false
        if (system.isActive) return false
        return usabilityProblem(ship) == null
    }

    /** Shown in the ship info widget while the system is idle (null = "READY"). */
    override fun getInfoText(system: ShipSystemAPI?, ship: ShipAPI?): String? {
        if (system == null || ship == null || system.isActive || system.isCoolingDown) return null
        return usabilityProblem(ship)
    }

    /**
     * Why the jump cannot start right now, or null if it can. Computed live: the engine only
     * calls isUsable on activation attempts, while the info widget asks every frame.
     *
     * Player: idle and not aiming is "ready" (the key primes; FleetSlipspaceJumpTargetingPlugin
     * keeps the engine from starting the system until a click confirms); while aiming, the problem
     * with the aim point. AI: the destination it handed over.
     */
    private fun usabilityProblem(ship: ShipAPI): String? {
        val engine = Global.getCombatEngine() ?: return "NO COMBAT"
        FleetSlipspaceJump.resetIfNewCombat(engine)
        if (ship.fluxLevel >= FleetSlipspaceJump.fluxGate.value) return "FLUX TOO HIGH"
        val destination = FleetSlipspaceJump.destinationFor(ship, engine)
        if (destination == null) {
            return if (FleetSlipspaceJump.isPlayerControlled(ship, engine)) null else "NO TARGET"
        }
        return FleetSlipspaceJump.destinationProblem(ship, destination, engine)
    }

    override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
        val ship = stats.entity as? ShipAPI ?: return
        val engine = Global.getCombatEngine() ?: return
        if (combatEngine !== engine) {
            combatEngine = engine
            session = null
        }
        // Creates the per-combat renderer and player targeting on first use (also resets per-combat
        // state on a new engine)
        FleetSlipspaceJump.renderPlugin(engine)
        FleetSlipspaceJump.ensureTargetingPlugin(engine)
        val system = FleetSlipspaceJump.system(ship) ?: return

        when (state) {
            ShipSystemStatsScript.State.IN -> if (session?.isFinished != false) {
                val destination = FleetSlipspaceJump.destinationFor(ship, engine) ?: ship.mouseTarget ?: return
                FleetSlipspaceJump.clearPendingDestination(ship)
                FleetSlipspaceJump.clearConfirmedDestination(ship)
                FleetSlipspaceJump.cancelPrime()
                system.cooldown = FleetSlipspaceJump.cooldown.value
                session = FleetSlipspaceJumpSession.start(ship, system, destination, engine)
            }
            // The jump happens on the first OUT frame (the session also checks, as a backup)
            ShipSystemStatsScript.State.OUT -> session?.onOutState()
            else -> {}
        }
    }

    override fun getStatusData(
        index: Int,
        state: ShipSystemStatsScript.State,
        effectLevel: Float
    ): ShipSystemStatsScript.StatusData? {
        if (index != 0) return null
        if (FleetSlipspaceJump.isVisualOnly) {
            return when (state) {
                ShipSystemStatsScript.State.IN,
                ShipSystemStatsScript.State.ACTIVE,
                ShipSystemStatsScript.State.OUT -> ShipSystemStatsScript.StatusData("visual only: nothing jumps", false)
                else -> null
            }
        }
        return when (state) {
            ShipSystemStatsScript.State.IN -> ShipSystemStatsScript.StatusData("slipspace rupture forming", false)
            ShipSystemStatsScript.State.ACTIVE -> ShipSystemStatsScript.StatusData("everything in the rupture is phased", false)
            ShipSystemStatsScript.State.OUT -> ShipSystemStatsScript.StatusData("fleet jump complete", false)
            else -> null
        }
    }
}
