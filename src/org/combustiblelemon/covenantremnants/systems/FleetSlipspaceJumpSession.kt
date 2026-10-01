package org.combustiblelemon.covenantremnants.systems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.DamageType
import com.fs.starfarer.api.combat.MissileAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipCommand
import com.fs.starfarer.api.combat.ShipSystemAPI
import com.fs.starfarer.api.input.InputEventAPI
import com.fs.starfarer.api.util.Misc
import org.combustiblelemon.covenantremnants.utils.render.rupture.RuptureEffect
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * One activation of the Fleet Slipspace Jump, from charge-up to arrival.
 *
 * Runs as its own every-frame plugin so it keeps advancing — and can restore everything it
 * touched — even if the carrier dies or the system is interrupted mid-jump.
 *
 * - IN (charge-up): the carrier is held in place; the entry rupture's charging core tracks the
 *   charge; the exit seed appears EXIT_SEED_LEAD seconds before the fade and builds toward the jump.
 * - ACTIVE (fade): every grabbable ship inside the radius is held — phased (no collisions, not
 *   targetable by the engine), faded, jittered, and unable to fire. Ships that leave the radius are
 *   released. Shots and asteroids are untouched until the jump.
 * - First OUT frame: the jump. The carrier lands first (nudged to a free spot if needed); every
 *   other cargo entity lands at its offset from the carrier's actual landing spot — ships largest
 *   first, then fighters, hulks, asteroids (nudged), then shots (not nudged). Child modules ride with
 *   their parent. Unshielded cargo ships lose DAMAGE_HULL_FRACTION of max hull, ignoring armor. The
 *   carrier's flux is set to EXIT_FLUX.
 * - Rest of OUT: arrival jitter on the cargo; then the session ends.
 */
class FleetSlipspaceJumpSession private constructor(
    private val carrier: ShipAPI,
    private val system: ShipSystemAPI,
    destination: Vector2f,
    private val engine: CombatEngineAPI
) : BaseEveryFrameCombatPlugin() {

    companion object {
        private val JITTER_COLOR = Color(150, 90, 255, 200)
        private val DAMAGE_TEXT_COLOR = Color(220, 120, 255, 255)
        private const val HELD_ALPHA_DROP = 0.6f

        // Sounds (data/config/sounds.json), cut from Halo: Reach audio: the m45 Covenant power hum,
        // the m45 ending foley (rupture opening; collapse and boom) and the Seraph slipspace jumps.
        private const val SOUND_CHARGE_LOOP = "covenant_remnants_fleet_jump_charge_loop"
        private const val SOUND_OPEN = "covenant_remnants_fleet_jump_open"
        private const val SOUND_COLLAPSE = "covenant_remnants_fleet_jump_collapse"
        private const val SOUND_ARRIVAL = "covenant_remnants_fleet_jump_arrival"

        // The charge hum rises in pitch and volume with the charge; the exit seed's is quieter.
        private const val HUM_PITCH_START = 0.7f
        private const val HUM_PITCH_END = 1.25f
        private const val HUM_VOLUME_START = 0.35f
        private const val HUM_VOLUME_END = 1f
        private const val EXIT_HUM_VOLUME = 0.7f

        fun start(carrier: ShipAPI, system: ShipSystemAPI, destination: Vector2f, engine: CombatEngineAPI): FleetSlipspaceJumpSession {
            val session = FleetSlipspaceJumpSession(carrier, system, destination, engine)
            engine.addPlugin(session)
            return session
        }
    }

    private class HeldMember(val ship: ShipAPI, val wasPhased: Boolean, val alpha: Float)

    private val destination = Vector2f(destination)

    // Frozen at activation so a console change mid-jump cannot desync the visual from the grab.
    private val radius = FleetSlipspaceJump.grabRadius.value

    // VISUAL_ONLY (frozen at activation): play both ruptures, touch nothing else.
    private val visualOnly = FleetSlipspaceJump.isVisualOnly

    private val renderPlugin = FleetSlipspaceJump.renderPlugin(engine)
    private val entryEffect: RuptureEffect = renderPlugin.addEffect(RuptureEffect.entry(carrier.location, radius))
    private var exitEffect: RuptureEffect? = null

    // Top-level ship -> itself plus its child modules, with the state to restore.
    private val held = IdentityHashMap<ShipAPI, List<HeldMember>>()

    var isFinished = false
        private set
    private var jumped = false
    private var activeTime = 0f
    private var exitSeedSpan = 0f
    private var openSoundPlayed = false
    private var arrivalTime = 0f
    private var cargoShips: List<ShipAPI> = emptyList()

    override fun advance(amount: Float, events: List<InputEventAPI>?) {
        if (isFinished || engine.isPaused) return

        if (!jumped) {
            if (!carrier.isAlive || carrier.isHulk) {
                abort()
                return
            }
            when (system.state) {
                ShipSystemAPI.SystemState.IN -> {
                    holdCarrier()
                    startExitSeedIfDue(false)
                    updateExitSeed()
                    playChargeHum(carrier, carrier.location, system.effectLevel, 1f)
                    playExitHum()
                }
                ShipSystemAPI.SystemState.ACTIVE -> {
                    activeTime += amount
                    holdCarrier()
                    startExitSeedIfDue(true)
                    updateExitSeed()
                    entryEffect.startFade()
                    if (!openSoundPlayed) {
                        openSoundPlayed = true
                        Global.getSoundPlayer().playSound(SOUND_OPEN, 1f, 1f, carrier.location, carrier.velocity)
                    }
                    playExitHum()
                    if (!visualOnly) markStep()
                }
                // Normally the stats script already jumped on its OUT frame; this is the backup.
                ShipSystemAPI.SystemState.OUT -> executeJump()
                // IDLE / COOLDOWN before the jump: the system was interrupted.
                else -> abort()
            }
            return
        }

        arrivalTime += amount
        val level = 1f - arrivalTime / FleetSlipspaceJump.arrival.value.coerceAtLeast(0.01f)
        if (level <= 0f) {
            finish()
            return
        }
        for (ship in cargoShips) {
            if (ship.isAlive) ship.setJitter(this, JITTER_COLOR, level, 6, 0f, 8f + 16f * level)
        }
    }

    /** Signal from the stats script: the system is in OUT — jump now if not done yet. */
    fun onOutState() {
        if (!jumped && !isFinished) {
            if (!carrier.isAlive || carrier.isHulk) abort() else executeJump()
        }
    }

    // --- Charge-up ---------------------------------------------------------------------------

    private fun holdCarrier() {
        carrier.velocity.set(0f, 0f)
        carrier.angularVelocity = 0f
        entryEffect.center.set(carrier.location)
        entryEffect.chargeLevel = system.effectLevel
    }

    private fun startExitSeedIfDue(force: Boolean) {
        if (exitEffect != null) return
        val remaining = (1f - system.effectLevel) * system.chargeUpDur
        if (force || remaining <= FleetSlipspaceJump.exitSeedLead.value) {
            exitEffect = renderPlugin.addEffect(RuptureEffect.exit(destination, radius))
            exitSeedSpan = timeToJump()
        }
    }

    /** The charge hum at [location], keyed by [source]; must be called every frame it should play. */
    private fun playChargeHum(source: Any, location: Vector2f, level: Float, volumeMult: Float) {
        val pitch = HUM_PITCH_START + (HUM_PITCH_END - HUM_PITCH_START) * level
        val volume = (HUM_VOLUME_START + (HUM_VOLUME_END - HUM_VOLUME_START) * level) * volumeMult
        Global.getSoundPlayer().playLoop(SOUND_CHARGE_LOOP, source, pitch, volume, location, Vector2f())
    }

    /** The exit seed hums toward the jump at the destination. */
    private fun playExitHum() {
        val exit = exitEffect ?: return
        playChargeHum(exit, exit.center, exit.chargeLevel, EXIT_HUM_VOLUME)
    }

    /** The exit seed builds toward the jump the way the entry core builds over the charge-up. */
    private fun updateExitSeed() {
        val exit = exitEffect ?: return
        exit.chargeLevel = if (exitSeedSpan <= 0f) 1f else 1f - timeToJump() / exitSeedSpan
    }

    /** Seconds until the jump (the first OUT frame). */
    private fun timeToJump(): Float = when (system.state) {
        ShipSystemAPI.SystemState.IN -> (1f - system.effectLevel) * system.chargeUpDur + system.chargeActiveDur
        ShipSystemAPI.SystemState.ACTIVE -> max(0f, system.chargeActiveDur - activeTime)
        else -> 0f
    }

    // --- Fade --------------------------------------------------------------------------------

    private fun markStep() {
        val progress = (activeTime / system.chargeActiveDur.coerceAtLeast(0.01f)).coerceIn(0f, 1f)
        val center = carrier.location

        val inside: MutableSet<ShipAPI> = Collections.newSetFromMap(IdentityHashMap())
        for (ship in engine.ships) {
            if (!FleetSlipspaceJump.isGrabbableShip(ship)) continue
            if (!FleetSlipspaceJump.isWithin(ship.location, center, radius)) continue
            // Another carrier's cargo stays theirs.
            if (ship !in held && ship in FleetSlipspaceJump.inTransit) continue
            inside.add(ship)
            if (ship !in held) hold(ship)
        }

        val iterator = held.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in inside) {
                release(entry.value)
                iterator.remove()
            }
        }

        val alpha = 1f - HELD_ALPHA_DROP * progress
        for (members in held.values) {
            for (member in members) {
                val ship = member.ship
                ship.setExtraAlphaMult(alpha)
                ship.setJitter(this, JITTER_COLOR, progress, 8, 0f, 4f + 16f * progress)
                ship.setHoldFireOneFrame(true)
                ship.blockCommandForOneFrame(ShipCommand.FIRE)
            }
        }
    }

    private fun hold(ship: ShipAPI) {
        val members = ArrayList<HeldMember>()
        for (member in listOf(ship) + ship.childModulesCopy) {
            members.add(HeldMember(member, member.isPhased, member.extraAlphaMult))
            if (!member.isPhased) member.setPhased(true)
        }
        held[ship] = members
        FleetSlipspaceJump.inTransit.add(ship)
    }

    private fun release(members: List<HeldMember>) {
        for (member in members) {
            if (!member.wasPhased && member.ship.isPhased) member.ship.setPhased(false)
            member.ship.setExtraAlphaMult(member.alpha)
        }
        members.firstOrNull()?.let { FleetSlipspaceJump.inTransit.remove(it.ship) }
    }

    private fun releaseAll() {
        held.values.forEach { release(it) }
        held.clear()
    }

    // --- Jump --------------------------------------------------------------------------------

    private fun executeJump() {
        jumped = true
        if (visualOnly) {
            playRupturesOnly()
            return
        }
        val origin = Vector2f(carrier.location)

        // Cargo: everything inside the radius at this moment.
        val ships = engine.ships.filter {
            FleetSlipspaceJump.isGrabbableShip(it) &&
                    FleetSlipspaceJump.isWithin(it.location, origin, radius) &&
                    (it in held || it !in FleetSlipspaceJump.inTransit)
        }
        val shots = engine.projectiles.filter { FleetSlipspaceJump.isWithin(it.location, origin, radius) }
        val asteroids = engine.asteroids.filter { FleetSlipspaceJump.isWithin(it.location, origin, radius) }

        val cargo: MutableSet<CombatEntityAPI> = Collections.newSetFromMap(IdentityHashMap())
        cargo.addAll(ships)
        cargo.addAll(asteroids)
        cargo.addAll(shots)

        // What landing spots must avoid: everything staying behind (fighters never block), plus
        // cargo as it lands.
        val obstacles = ArrayList<CombatEntityAPI>()
        for (ship in engine.ships) {
            if (ship !in cargo && !ship.isFighter && (ship.isAlive || ship.isHulk)) obstacles.add(ship)
        }
        for (asteroid in engine.asteroids) {
            if (asteroid !in cargo) obstacles.add(asteroid)
        }

        // Carrier first; every other offset is kept relative to where it actually lands.
        val carrierFinal = findFreeSpot(destination, carrier.collisionRadius, obstacles)
        val delta = Vector2f(carrierFinal.x - origin.x, carrierFinal.y - origin.y)
        moveShip(carrier, carrierFinal)
        obstacles.add(carrier)

        val order = ships.filter { it !== carrier }
            .sortedWith(compareBy<ShipAPI> { placementRank(it) }.thenByDescending { it.collisionRadius })
        for (ship in order) {
            val target = Vector2f(ship.location.x + delta.x, ship.location.y + delta.y)
            val final = if (ship.isFighter) {
                FleetSlipspaceJump.clampIntoMap(target, ship.collisionRadius, engine)
            } else {
                findFreeSpot(target, ship.collisionRadius, obstacles)
            }
            moveShip(ship, final)
            if (!ship.isFighter) obstacles.add(ship)
        }
        for (asteroid in asteroids) {
            val target = Vector2f(asteroid.location.x + delta.x, asteroid.location.y + delta.y)
            asteroid.location.set(findFreeSpot(target, asteroid.collisionRadius, obstacles))
            obstacles.add(asteroid)
        }
        for (shot in shots) {
            val missile = shot as? MissileAPI
            missile?.interruptContrail()
            shot.location.translate(delta.x, delta.y)
            missile?.interruptContrail()
        }

        for (ship in ships) {
            if (ship.isAlive && !ship.isHulk) applyRuptureDamage(ship)
        }

        val flux = carrier.fluxTracker
        flux.currFlux = max(flux.currFlux, flux.maxFlux * FleetSlipspaceJump.exitFlux.value)

        releaseAll()
        cargoShips = ships

        entryEffect.center.set(origin)
        entryEffect.startCollapse()
        val exit = exitEffect ?: renderPlugin.addEffect(RuptureEffect.exit(destination, radius)).also { exitEffect = it }
        exit.center.set(carrierFinal)
        exit.startDissolve()

        playJumpSounds(origin, carrierFinal)
    }

    /** The collapse and boom where the fleet left, the slipspace arrival where it lands. */
    private fun playJumpSounds(from: Vector2f, to: Vector2f) {
        val sounds = Global.getSoundPlayer()
        sounds.playSound(SOUND_COLLAPSE, 1f, 1f, from, Vector2f())
        sounds.playSound(SOUND_ARRIVAL, 1f, 1f, to, Vector2f())
    }

    /** VISUAL_ONLY jump: entry collapses where the carrier stands, exit dissolves at the destination. */
    private fun playRupturesOnly() {
        entryEffect.center.set(carrier.location)
        entryEffect.startCollapse()
        val exit = exitEffect ?: renderPlugin.addEffect(RuptureEffect.exit(destination, radius)).also { exitEffect = it }
        exit.startDissolve()
        playJumpSounds(Vector2f(carrier.location), destination)
    }

    /** Alive ships first (largest first within a rank), then fighters, then hulks. */
    private fun placementRank(ship: ShipAPI): Int = when {
        ship.isHulk -> 2
        ship.isFighter -> 1
        else -> 0
    }

    private fun moveShip(ship: ShipAPI, to: Vector2f) {
        val dx = to.x - ship.location.x
        val dy = to.y - ship.location.y
        ship.location.set(to)
        for (module in ship.childModulesCopy) {
            module.location.translate(dx, dy)
        }
    }

    /**
     * [target] if it is free and inside the map; otherwise the nearest free spot found on rings of
     * NUDGE_DIRECTIONS points around it (vanilla teleporter style); otherwise [target] clamped into
     * the map.
     */
    private fun findFreeSpot(target: Vector2f, radius: Float, obstacles: List<CombatEntityAPI>): Vector2f {
        if (FleetSlipspaceJump.isInsideMap(target, radius, engine) && isFree(target, radius, obstacles)) {
            return Vector2f(target)
        }
        val rings = FleetSlipspaceJump.nudgeRings.value.toInt().coerceAtLeast(1)
        val directions = FleetSlipspaceJump.nudgeDirections.value.toInt().coerceAtLeast(1)
        val step = max(radius, 50f)
        for (ring in 1..rings) {
            val distance = ring * step
            // Alternate rings offset by half a slot so the search does not repeat the same bearings.
            val stagger = if (ring % 2 == 0) 180f / directions else 0f
            for (i in 0 until directions) {
                val angle = Math.toRadians((i * 360f / directions + stagger).toDouble())
                val candidate = Vector2f(
                    target.x + distance * cos(angle).toFloat(),
                    target.y + distance * sin(angle).toFloat()
                )
                if (FleetSlipspaceJump.isInsideMap(candidate, radius, engine) && isFree(candidate, radius, obstacles)) {
                    return candidate
                }
            }
        }
        return FleetSlipspaceJump.clampIntoMap(target, radius, engine)
    }

    private fun isFree(point: Vector2f, radius: Float, obstacles: List<CombatEntityAPI>): Boolean =
        obstacles.none { Misc.getDistance(point, it.location) < radius + it.collisionRadius }

    /**
     * Unshielded cargo loses DAMAGE_HULL_FRACTION of max hull directly (armor ignored). A lethal
     * hit is routed through the engine so the kill is attributed to the carrier (the carrier
     * itself included: developer decision, the CAS takes it too if its shield is down).
     */
    private fun applyRuptureDamage(ship: ShipAPI) {
        if (FleetSlipspaceJump.hasShieldUp(ship)) return
        val fraction = FleetSlipspaceJump.damageHullFraction.value
        if (fraction <= 0f) return

        val damage = ship.maxHitpoints * fraction
        val point = Vector2f(ship.location)
        if (ship.hitpoints <= damage) {
            // The ship is put at 1 HP first, so the engine hit only has to get through armor. The
            // engine reports this hit itself, so no floating text of our own.
            ship.hitpoints = 1f
            val lethal = max(damage, ship.hullSpec.armorRating * 5f + 10f)
            engine.applyDamage(ship, point, lethal, DamageType.OTHER, 0f, true, false, carrier, true)
        } else {
            // Direct hull loss bypasses the damage pipeline, so show the number ourselves.
            ship.hitpoints = ship.hitpoints - damage
            engine.addFloatingDamageText(point, damage, DAMAGE_TEXT_COLOR, ship, carrier)
        }
    }

    // --- End ---------------------------------------------------------------------------------

    private fun abort() {
        releaseAll()
        entryEffect.abort()
        exitEffect?.abort()
        finish()
    }

    private fun finish() {
        isFinished = true
        engine.removePlugin(this)
    }
}
