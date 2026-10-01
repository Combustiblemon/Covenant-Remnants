package org.combustiblelemon.covenantremnants.plugins

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipCommand
import com.fs.starfarer.api.combat.ShipSystemAPI
import com.fs.starfarer.api.input.InputEventAPI
import org.combustiblelemon.covenantremnants.systems.FleetSlipspaceJump
import org.lwjgl.input.Keyboard
import org.lwjgl.util.vector.Vector2f

/**
 * Player targeting for the Fleet Slipspace Jump (developer 2026-10-01): the system key primes the
 * jump and brings up the aiming circles at the cursor; left click confirms (the system activates
 * toward the cursor), right click cancels. One per combat ([FleetSlipspaceJump.ensureTargetingPlugin]).
 *
 * Runs in [processInputPreCoreControls], which the engine calls every frame, paused or not, before
 * the player-ship controls (those do not run at all while paused — why the system key used to do
 * nothing then). Consumed events never reach the ship controls. While the player flies a CAS, the
 * engine's own "use system" command is blocked unless a jump was just confirmed here, so the key
 * primes instead of firing the system straight away.
 */
class FleetSlipspaceJumpTargetingPlugin : BaseEveryFrameCombatPlugin() {
    private companion object {
        // Control enum name of the ship-system key (Settings.getControlStringForEnumName).
        const val SYSTEM_CONTROL = "SHIP_USE_SYSTEM"

        // Halo: Reach concussion rifle power-up (data/config/sounds.json).
        const val PRIME_SOUND = "covenant_remnants_fleet_jump_prime"
    }

    private val systemKey: Int = run {
        val name = Global.getSettings().getControlStringForEnumName(SYSTEM_CONTROL)
        val code = if (name.isNullOrBlank()) Keyboard.KEY_NONE else Keyboard.getKeyIndex(name.trim().uppercase())
        if (code == Keyboard.KEY_NONE) {
            FleetSlipspaceJump.log("fleet jump: system key '$name' is not a keyboard key; priming with F")
            Keyboard.KEY_F
        } else {
            code
        }
    }

    override fun processInputPreCoreControls(amount: Float, events: List<InputEventAPI>) {
        val engine = Global.getCombatEngine() ?: return
        val ship = engine.playerShip
        val system = FleetSlipspaceJump.system(ship)
        if (ship == null || system == null || !ship.isAlive || !FleetSlipspaceJump.isPlayerControlled(ship, engine)) {
            FleetSlipspaceJump.cancelPrime()
            return
        }

        val ready = isReady(ship, system)
        val confirmed = FleetSlipspaceJump.confirmedDestination(ship, engine)
        when {
            confirmed != null -> {
                // Keep asking each unpaused frame until it activates (a command given while paused
                // may not survive the pause; asking while paused would only queue duplicates). The
                // stats script consumes the confirmed point on the first IN frame.
                if (!engine.isPaused && system.state == ShipSystemAPI.SystemState.IDLE && !system.isCoolingDown) {
                    ship.useSystem()
                }
            }
            FleetSlipspaceJump.isPrimed(ship) -> if (ready) aim(ship, engine, events) else FleetSlipspaceJump.cancelPrime()
            ready -> primeOnKey(ship, engine, events)
        }

        // The system key primes here; the engine may only start the system for a confirmed jump.
        if (FleetSlipspaceJump.confirmedDestination(ship, engine) == null) {
            ship.blockCommandForOneFrame(ShipCommand.USE_SYSTEM)
        }
    }

    private fun isReady(ship: ShipAPI, system: ShipSystemAPI): Boolean =
        system.state == ShipSystemAPI.SystemState.IDLE && !system.isCoolingDown &&
                !ship.fluxTracker.isOverloadedOrVenting

    private fun primeOnKey(ship: ShipAPI, engine: CombatEngineAPI, events: List<InputEventAPI>) {
        for (event in events) {
            if (event.isConsumed || !event.isKeyDownEvent || event.eventValue != systemKey) continue
            event.consume()
            FleetSlipspaceJump.prime(ship)
            updateAim(engine)
            Global.getSoundPlayer().playUISound(PRIME_SOUND, 1f, 1f)
            return
        }
    }

    /** Primed: the aim follows the cursor; left click confirms, right click cancels. */
    private fun aim(ship: ShipAPI, engine: CombatEngineAPI, events: List<InputEventAPI>) {
        updateAim(engine)
        // The clicks belong to the targeting, not to the guns or the shield.
        ship.blockCommandForOneFrame(ShipCommand.FIRE)
        ship.blockCommandForOneFrame(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK)

        for (event in events) {
            if (event.isConsumed) continue
            when {
                event.isRMBDownEvent -> {
                    event.consume()
                    FleetSlipspaceJump.cancelPrime()
                    return
                }
                event.isLMBDownEvent -> {
                    event.consume()
                    val destination = Vector2f(FleetSlipspaceJump.aimPoint)
                    val valid = ship.fluxLevel < FleetSlipspaceJump.fluxGate.value &&
                            FleetSlipspaceJump.destinationProblem(ship, destination, engine) == null
                    if (valid) {
                        FleetSlipspaceJump.confirm(ship, destination, engine)
                        ship.useSystem()
                    }
                    return
                }
                // The system key while aiming does nothing (left click confirms, right click cancels).
                event.isKeyDownEvent && event.eventValue == systemKey -> event.consume()
            }
        }
    }

    private fun updateAim(engine: CombatEngineAPI) {
        val viewport = engine.viewport
        val settings = Global.getSettings()
        FleetSlipspaceJump.aimPoint.set(
            viewport.convertScreenXToWorldX(settings.mouseX.toFloat()),
            viewport.convertScreenYToWorldY(settings.mouseY.toFloat())
        )
    }
}
