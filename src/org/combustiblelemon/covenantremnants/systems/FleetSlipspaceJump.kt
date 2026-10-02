package org.combustiblelemon.covenantremnants.systems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipSystemAPI
import com.fs.starfarer.api.util.Misc
import org.combustiblelemon.covenantremnants.hullmods.CovenantShield
import org.combustiblelemon.covenantremnants.plugins.FleetSlipspaceJumpRenderPlugin
import org.combustiblelemon.covenantremnants.plugins.FleetSlipspaceJumpTargetingPlugin
import org.lwjgl.util.vector.Vector2f
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.abs

/**
 * Fleet Slipspace Jump — the CAS-class Assault Carrier's (covenant_remnants_supercap1) unique
 * ship system. The carrier opens a slipspace rupture off its bow ([portalCenter], PORTAL_AHEAD
 * collision radii ahead), lurches into it during the fade, and jumps everything inside the grab
 * radius around the portal — ships of any side, fighters, missiles, projectiles, hulks,
 * asteroids — to the destination in one frame, keeping every offset from the portal centre,
 * velocity, facing and target. The carrier always comes along and lands at the destination.
 *
 * This object holds the system id, every tuning constant (live-tunable through the `FleetJumpTune`
 * console command; values reset on restart), and the per-combat shared state used by the stats
 * script ([FleetSlipspaceJumpStats]), the jump session ([FleetSlipspaceJumpSession]), the AI
 * (ai/FleetSlipspaceJumpAI) and the renderer (plugins/FleetSlipspaceJumpRenderPlugin).
 */
object FleetSlipspaceJump {
    const val SYSTEM_ID = "covenant_remnants_fleet_slipspace_jump"

    /** One live-tunable constant. */
    class Tunable internal constructor(val name: String, val default: Float, val description: String) {
        var value: Float = default

        fun reset() {
            value = default
        }
    }

    private val registry = LinkedHashMap<String, Tunable>()

    private fun tune(name: String, default: Float, description: String): Tunable =
        Tunable(name, default, description).also { registry[name] = it }

    // --- Dev ---------------------------------------------------------------------------------

    // Temporary (developer 2026-10-01): until the look is dialled in, activating only plays the
    // rupture animation — nothing is grabbed, moved, damaged or charged flux, and the AI does not
    // use the system. Set to 0 for the full jump.
    val visualOnly = tune("VISUAL_ONLY", 0f, "1 = activation only plays the rupture animation; 0 = full jump")

    val isVisualOnly: Boolean get() = visualOnly.value >= 0.5f

    // --- Mechanics ---------------------------------------------------------------------------

    val grabRadius = tune("GRAB_RADIUS", 1000f, "Grab radius around the portal (off the bow), su")
    // Developer 2026-10-02: the portal opens off the bow and the carrier lurches into it.
    val portalAhead = tune("PORTAL_AHEAD", 2f, "Portal centre ahead of the carrier centre, in collision radii (2 = one ship length)")
    val minRange = tune("MIN_RANGE", 2000f, "Minimum jump distance from the portal centre, su (no maximum)")
    val fluxGate = tune("FLUX_GATE", 0.7f, "Max flux fraction at which the jump can start")
    val exitFlux = tune("EXIT_FLUX", 0.8f, "Carrier flux fraction after the jump")
    val chargeUp = tune("CHARGE", 8f, "Charge-up seconds (applies from the next battle)")
    val fade = tune("FADE", 2f, "Fade window seconds (applies from the next battle)")
    val arrival = tune("ARRIVAL", 3f, "Charge-down seconds (applies from the next battle)")
    val cooldown = tune("COOLDOWN", 60f, "Cooldown seconds")
    val damageHullFraction = tune("DAMAGE_HULL_FRACTION", 0.10f, "Max-hull fraction lost by unshielded cargo ships")
    val nudgeRings = tune("NUDGE_RINGS", 10f, "Blocked-arrival search: rings")
    val nudgeDirections = tune("NUDGE_DIRECTIONS", 12f, "Blocked-arrival search: directions per ring")

    // --- AI ----------------------------------------------------------------------------------

    val aiInterval = tune("AI_INTERVAL", 0.75f, "AI decision interval, seconds")
    val fighterStrength = tune("FIGHTER_STRENGTH", 1f, "Strength counted per fighter")
    val pullMinThreat = tune("PULL_MIN_THREAT", 15f, "Pull-home: min enemy strength in the radius")
    val clusterRadius = tune("CLUSTER_RADIUS", 1500f, "Allied cluster radius around an anchor, su")
    val pullMargin = tune("PULL_MARGIN", 1.5f, "Pull-home: allied strength needed per enemy strength")
    val fluxPressure = tune("FLUX_PRESSURE", 0.5f, "Strength lost per flux level, both sides (overload/venting counts as no flux)")
    val strandMaxSpeed = tune("STRAND_MAX_SPEED", 60f, "Strand: max speed of an enemy heavy")
    val strandMinStrength = tune("STRAND_MIN_STRENGTH", 15f, "Strand: min enemy heavy strength in the radius")
    val hitFraction = tune("HIT_FRACTION", 0.5f, "Strand survival: fraction of enemy DPS assumed to land")
    val minHullAfter = tune("MIN_HULL_AFTER", 0.4f, "Strand survival: hull fraction to keep")
    val survivalSafety = tune("SURVIVAL_SAFETY", 1.25f, "Strand survival: safety divisor on effective HP")
    val armorFactor = tune("ARMOR_FACTOR", 1f, "Strand survival: armor rating counted as HP, multiplier")
    val regenShare = tune("REGEN_SHARE", 0.5f, "Strand survival: shield regen share counted over the exposure")
    val regroupDistance = tune("REGROUP_DISTANCE", 4000f, "Regroup when the nearest allied cluster is farther than this, su")
    // Reinforce and Hunt (developer 2026-10-02); placeholders.
    val reinforceMinThreat = tune("REINFORCE_MIN_THREAT", 15f, "Reinforce: min hostile strength at the allied group")
    val reinforceNeed = tune("REINFORCE_NEED", 1f, "Reinforce: a group needs help below this allied/hostile strength ratio")
    val huntMinValue = tune("HUNT_MIN_VALUE", 15f, "Hunt: min full strength of the enemy group")
    val huntMinWeakness = tune("HUNT_MIN_WEAKNESS", 0.25f, "Hunt: min share of the group's full strength lost to hull/flux (unless a ship is crippled)")
    val huntCrippledHull = tune("HUNT_CRIPPLED_HULL", 0.3f, "Hunt: a ship at or below this hull level is crippled")
    val huntCrippledFlux = tune("HUNT_CRIPPLED_FLUX", 0.8f, "Hunt: a ship at or above this flux level is crippled (not overload/venting)")
    val huntCrippledBonus = tune("HUNT_CRIPPLED_BONUS", 0.25f, "Hunt: score bonus per crippled ship in the group")

    // --- Visual ------------------------------------------------------------------------------
    // The rupture's timings, sizes and colours come from the Halo: Reach slipspace bomb itself
    // (utils/render/rupture/ReachCurves); these only scale or bridge it.

    val exitSeedLead = tune("EXIT_SEED_LEAD", 2f, "Exit seed appears this many seconds before the fade")
    // Off for now (developer 2026-10-01).
    val chargeRing = tune("CHARGE_RING", 0f, "1 = thin violet ring marks the grab radius during the charge")
    val dissolveDuration = tune("DISSOLVE", 0.6f, "Exit glow sphere fade seconds")
    val remnantScale = tune("REMNANT_SCALE", 0.6f, "Burst/remnant size vs Reach (0.6: shell ends ~1.3 grab radii; 1: Reach, ~2.2)")
    val spriteScale = tune("SPRITE_SCALE", 1f, "Size multiplier for the charge, burst and spark sprites")
    val particleDensity = tune("PARTICLE_DENSITY", 1f, "Emission multiplier for every particle system")
    val exposure = tune("EXPOSURE", 1f, "Tone-mapping exposure for Reach's HDR colours")
    val distortion = tune("DISTORTION", 1f, "Heat-haze strength of the charge rings (0 = off)")
    // Developer 2026-10-02: the white ball leads, the darkness grows in inside it (Reach: together).
    val blackDelay = tune("BLACK_DELAY", 0.3f, "Opening: the black body's growth and fade-in start this many seconds after the white sphere's")

    val tunables: Collection<Tunable> get() = registry.values

    fun tunable(name: String): Tunable? = registry[name.uppercase()]

    fun resetAll() {
        registry.values.forEach { it.reset() }
    }

    // --- Per-combat state --------------------------------------------------------------------

    private class PendingDestination(val point: Vector2f, val setAt: Float)

    private const val PENDING_LIFETIME = 2f

    private var combatEngine: CombatEngineAPI? = null
    private val pendingDestinations = HashMap<String, PendingDestination>()

    /** Entities currently held by a jump session, so two carriers never grab the same one. */
    val inTransit: MutableSet<CombatEntityAPI> = Collections.newSetFromMap(IdentityHashMap())

    private var renderPlugin: FleetSlipspaceJumpRenderPlugin? = null
    private var targetingPlugin: FleetSlipspaceJumpTargetingPlugin? = null

    // Player targeting (developer 2026-10-01): the system key primes the jump, the aiming circles
    // follow the cursor, left click confirms, right click cancels. Works while paused.
    private var primedShip: ShipAPI? = null

    /** Where the player is aiming while primed, world coordinates (updated every frame, paused too). */
    val aimPoint = Vector2f()
    private val confirmedDestinations = HashMap<String, PendingDestination>()

    /** Drops all per-combat state when a new combat engine is seen. Call from every entry point. */
    fun resetIfNewCombat(engine: CombatEngineAPI) {
        if (combatEngine === engine) return
        combatEngine = engine
        pendingDestinations.clear()
        confirmedDestinations.clear()
        primedShip = null
        inTransit.clear()
        renderPlugin = null
        targetingPlugin = null
    }

    /** The one-per-combat rupture renderer, created and registered on first use. */
    fun renderPlugin(engine: CombatEngineAPI): FleetSlipspaceJumpRenderPlugin {
        resetIfNewCombat(engine)
        return renderPlugin ?: FleetSlipspaceJumpRenderPlugin().also {
            renderPlugin = it
            engine.addLayeredRenderingPlugin(it)
        }
    }

    /** The one-per-combat player targeting input handler, created and registered on first use. */
    fun ensureTargetingPlugin(engine: CombatEngineAPI) {
        resetIfNewCombat(engine)
        if (targetingPlugin == null) {
            targetingPlugin = FleetSlipspaceJumpTargetingPlugin().also { engine.addPlugin(it) }
        }
    }

    fun isPrimed(ship: ShipAPI): Boolean = primedShip === ship

    fun prime(ship: ShipAPI) {
        primedShip = ship
    }

    fun cancelPrime() {
        primedShip = null
    }

    /** The player confirmed [destination]; the activation that follows consumes it. */
    fun confirm(ship: ShipAPI, destination: Vector2f, engine: CombatEngineAPI) {
        resetIfNewCombat(engine)
        confirmedDestinations[ship.id] = PendingDestination(Vector2f(destination), engine.getTotalElapsedTime(false))
        primedShip = null
    }

    /** The player's confirmed destination, until the system activates (or it expires). */
    fun confirmedDestination(ship: ShipAPI, engine: CombatEngineAPI): Vector2f? {
        val confirmed = confirmedDestinations[ship.id] ?: return null
        // Game time: frozen while paused, so a jump confirmed while paused waits for the unpause.
        if (engine.getTotalElapsedTime(false) - confirmed.setAt > PENDING_LIFETIME) {
            confirmedDestinations.remove(ship.id)
            return null
        }
        return confirmed.point
    }

    fun clearConfirmedDestination(ship: ShipAPI) {
        confirmedDestinations.remove(ship.id)
    }

    /** AI → stats script handoff: the destination the AI wants for its next activation. */
    fun setPendingDestination(ship: ShipAPI, point: Vector2f, engine: CombatEngineAPI) {
        resetIfNewCombat(engine)
        pendingDestinations[ship.id] = PendingDestination(Vector2f(point), engine.getTotalElapsedTime(false))
    }

    fun pendingDestination(ship: ShipAPI, engine: CombatEngineAPI): Vector2f? {
        val pending = pendingDestinations[ship.id] ?: return null
        if (engine.getTotalElapsedTime(false) - pending.setAt > PENDING_LIFETIME) {
            pendingDestinations.remove(ship.id)
            return null
        }
        return pending.point
    }

    fun clearPendingDestination(ship: ShipAPI) {
        pendingDestinations.remove(ship.id)
    }

    // --- Shared helpers ----------------------------------------------------------------------

    fun system(ship: ShipAPI?): ShipSystemAPI? = ship?.system?.takeIf { it.id == SYSTEM_ID }

    fun effectLevel(ship: ShipAPI?): Float = system(ship)?.effectLevel ?: 0f

    /**
     * Player at the controls (not autopilot): the destination is the mouse cursor.
     *
     * Checked through the ship's AI, not `engine.isUIAutopilotOn`: that one returns the inverse of
     * the combat UI's autopilot flag (true while the player flies manually; 0.98a-RC8). Turning
     * autopilot off sets the player ship's AI to null, so a null AI means manual control.
     */
    fun isPlayerControlled(ship: ShipAPI, engine: CombatEngineAPI): Boolean =
        engine.playerShip === ship && ship.shipAI == null

    /**
     * Where the jump would go right now, or null if nothing has chosen a destination. Player: the
     * confirmed point, else the aim point while primed. AI: the destination it handed over.
     */
    fun destinationFor(ship: ShipAPI, engine: CombatEngineAPI): Vector2f? {
        if (!isPlayerControlled(ship, engine)) return pendingDestination(ship, engine)
        return confirmedDestination(ship, engine) ?: if (isPrimed(ship)) aimPoint else null
    }

    /**
     * Whether a ship rides the jump on its own. Stations and their modules never do; modules of
     * a modular ship ride with their parent (never grabbed alone); dead-and-removed ships don't.
     */
    fun isGrabbableShip(ship: ShipAPI): Boolean {
        if (ship.isStation || ship.parentStation != null) return false
        return ship.isAlive || ship.isHulk
    }

    /** Any live, top-level, non-station ship (fighters included) — the AI's view of a combatant. */
    fun isCombatant(ship: ShipAPI): Boolean =
        ship.isAlive && !ship.isHulk && !ship.isStation && ship.parentStation == null

    /** Where the carrier's portal opens: PORTAL_AHEAD collision radii off its bow, along its facing. */
    fun portalCenter(carrier: ShipAPI): Vector2f {
        val ahead = carrier.collisionRadius * portalAhead.value
        val dir = Misc.getUnitVectorAtDegreeAngle(carrier.facing)
        return Vector2f(carrier.location.x + dir.x * ahead, carrier.location.y + dir.y * ahead)
    }

    fun isWithin(location: Vector2f, center: Vector2f, radius: Float): Boolean {
        val dx = location.x - center.x
        val dy = location.y - center.y
        return dx * dx + dy * dy <= radius * radius
    }

    /** Vanilla shield on, or Covenant shield up and not broken. No other state counts. */
    fun hasShieldUp(ship: ShipAPI): Boolean {
        val shield = ship.shield
        if (shield != null && shield.isOn) return true
        val cloak = ship.phaseCloak ?: return false
        if (cloak.id != CovenantShield.DEFENSE_ID) return false
        val stats = cloak.script as? CovenantShieldsStats
        return cloak.isOn && stats?.breakCooldownActive != true
    }

    fun isInsideMap(point: Vector2f, margin: Float, engine: CombatEngineAPI): Boolean =
        abs(point.x) + margin <= engine.mapWidth / 2f && abs(point.y) + margin <= engine.mapHeight / 2f

    fun clampIntoMap(point: Vector2f, margin: Float, engine: CombatEngineAPI): Vector2f {
        val maxX = (engine.mapWidth / 2f - margin).coerceAtLeast(0f)
        val maxY = (engine.mapHeight / 2f - margin).coerceAtLeast(0f)
        return Vector2f(point.x.coerceIn(-maxX, maxX), point.y.coerceIn(-maxY, maxY))
    }

    /**
     * Whether a circle at [point] overlaps any live ship that is not in [ignore]. Fighters,
     * missiles, hulks and asteroids never block (developer 2026-10-02): at the warp-in hulks under
     * the carrier are shoved aside and asteroids under any arrival are destroyed.
     */
    fun isSpotBlocked(point: Vector2f, radius: Float, ignore: Set<CombatEntityAPI>, engine: CombatEngineAPI): Boolean {
        for (other in engine.ships) {
            if (other in ignore || other.isFighter || other.isHulk || !other.isAlive) continue
            if (Misc.getDistance(point, other.location) < radius + other.collisionRadius) return true
        }
        return false
    }

    /**
     * Ships (and asteroids) the carrier would take along right now — everything inside the grab
     * radius around its portal, plus the carrier itself. Ignored by the exit check.
     */
    fun currentCargo(carrier: ShipAPI, engine: CombatEngineAPI): Set<CombatEntityAPI> {
        val cargo: MutableSet<CombatEntityAPI> = Collections.newSetFromMap(IdentityHashMap())
        val radius = grabRadius.value
        val center = portalCenter(carrier)
        cargo.add(carrier)
        for (ship in engine.ships) {
            if (isGrabbableShip(ship) && isWithin(ship.location, center, radius)) cargo.add(ship)
        }
        for (asteroid in engine.asteroids) {
            if (isWithin(asteroid.location, center, radius)) cargo.add(asteroid)
        }
        return cargo
    }

    /**
     * Why [destination] is not a valid jump for [carrier], or null if it is. Shared by the stats
     * script's isUsable (shown as the system's info text) and the AI's destination picking.
     */
    fun destinationProblem(carrier: ShipAPI, destination: Vector2f, engine: CombatEngineAPI): String? {
        // The portal is what moves: the carrier lurches into its centre and lands at the destination.
        if (Misc.getDistance(portalCenter(carrier), destination) < minRange.value) return "TOO CLOSE"
        if (!isInsideMap(destination, carrier.collisionRadius, engine)) return "OFF MAP"
        if (isSpotBlocked(destination, carrier.collisionRadius, currentCargo(carrier, engine), engine)) return "EXIT BLOCKED"
        return null
    }

    fun log(message: String) {
        Global.getLogger(FleetSlipspaceJump::class.java).info(message)
    }
}
