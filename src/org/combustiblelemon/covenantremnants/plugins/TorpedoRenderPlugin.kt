package org.combustiblelemon.covenantremnants.plugins

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.CombatEngineLayers
import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.CombatLayeredRenderingPlugin
import com.fs.starfarer.api.combat.DamagingProjectileAPI
import com.fs.starfarer.api.combat.MissileAPI
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.combat.WeaponAPI
import org.combustiblelemon.covenantremnants.utils.render.torpedo.AuraStyle
import org.combustiblelemon.covenantremnants.utils.render.torpedo.CannonFlight
import org.combustiblelemon.covenantremnants.utils.render.torpedo.TorpedoBurst
import org.combustiblelemon.covenantremnants.utils.render.torpedo.TorpedoFlight
import org.combustiblelemon.covenantremnants.utils.render.torpedo.TorpedoLook
import org.combustiblelemon.covenantremnants.utils.render.torpedo.TorpedoRenderer
import org.combustiblelemon.covenantremnants.utils.render.torpedo.WraithFlight
import org.lwjgl.util.vector.Vector2f
import java.util.EnumSet
import kotlin.random.Random

/**
 * One-per-combat owner of the plasma torpedoes' Halo: Reach look (created on the first launch by
 * [org.combustiblelemon.covenantremnants.weapons.PlasmaTorpedoOnFire]). Follows every torpedo:
 * muzzle flash at launch, body/flare/trail and the in-flight hum while it flies, and when it is
 * gone the impact and explosion sound if it hit or was shot down (a torpedo that runs out of fuel
 * just fades). Also draws the Plasma Cannon rounds' energy aura ([CannonFlight]), the Plasma Mortar
 * rounds' Reach Wraith look ([WraithFlight]) and their hit splashes. Drawn above ships and missiles.
 */
class TorpedoRenderPlugin private constructor() : CombatLayeredRenderingPlugin {
    companion object {
        private const val KEY = "covenant_remnants_torpedo_render_plugin"
        const val SOUND_LOOP = "covenant_remnants_plasma_torpedo_loop"
        const val SOUND_IMPACT = "covenant_remnants_plasma_torpedo_impact"

        // Reach: the hum fades in and out over 0.2 s; the explosion pitches 0 to -2 semitones down.
        private const val LOOP_FADE = 0.2f
        private const val IMPACT_PITCH_MIN = 0.891f

        // Plasma Mortar rounds: give up on a round never seen in play after this long (its body already
        // fades with the round's own fade); log ends sooner than WRAITH_EARLY_END or never seen in play.
        private const val WRAITH_UNSEEN_TIMEOUT = 3f
        private const val WRAITH_EARLY_END = 0.2f
        private const val WRAITH_SHOT_ID = "covenant_remnants_plasmamortar_shot"
        private const val WRAITH_LOGGED_ENDS = 3

        fun forEngine(engine: CombatEngineAPI): TorpedoRenderPlugin {
            (engine.customData[KEY] as? TorpedoRenderPlugin)?.let { return it }
            val plugin = TorpedoRenderPlugin()
            engine.customData[KEY] = plugin
            engine.addLayeredRenderingPlugin(plugin)
            return plugin
        }
    }

    private val log = Global.getLogger(TorpedoRenderPlugin::class.java)
    private val flights = ArrayList<TorpedoFlight>()
    private val cannons = ArrayList<CannonFlight>()
    private val wraiths = ArrayList<WraithFlight>()
    private val wraithRounds = HashSet<DamagingProjectileAPI>()
    private var wraithFromFireLogged = false
    private var wraithFromScanLogged = false
    private var wraithEndsLogged = 0
    private val bursts = ArrayList<TorpedoBurst>()
    private val renderer = TorpedoRenderer()
    private var time = 0f

    fun launch(missile: MissileAPI, weapon: WeaponAPI) {
        val unit = TorpedoLook.unitFor(weapon.size)
        flights.add(TorpedoFlight(missile, unit))
        bursts.add(TorpedoBurst.muzzle(missile.location, missile.facing, unit))
    }

    /**
     * The impact burst alone (no torpedo, no sound, no smoke) at [location], [unit] su per Reach
     * unit: the Plasma Cannon's hit splash ([org.combustiblelemon.covenantremnants.weapons.PlasmaCannonOnHit]).
     */
    fun splash(location: Vector2f, unit: Float) {
        bursts.add(TorpedoBurst.impact(location, unit, smoke = false))
    }

    /**
     * Follows a Plasma Cannon round with its energy aura
     * ([org.combustiblelemon.covenantremnants.weapons.PlasmaCannonOnFire], [AuraStyle.CANNON]).
     */
    internal fun trackCannonRound(projectile: DamagingProjectileAPI, unit: Float, style: AuraStyle = AuraStyle.CANNON) {
        cannons.add(CannonFlight(projectile, unit, style))
    }

    /**
     * Follows a Plasma Mortar round with the Reach Wraith mortar look
     * ([org.combustiblelemon.covenantremnants.weapons.PlasmaMortarOnFire]).
     */
    fun trackWraithRound(projectile: DamagingProjectileAPI, unit: Float) {
        if (!wraithFromFireLogged) {
            wraithFromFireLogged = true
            log.info("Wraith round registered by onFire: ${projectile.javaClass.simpleName} at ${projectile.location}")
        }
        addWraith(projectile, unit)
    }

    private fun addWraith(projectile: DamagingProjectileAPI, unit: Float) {
        if (wraithRounds.add(projectile)) wraiths.add(WraithFlight(projectile, unit))
    }

    /** Plasma Mortar rounds the on-fire hook did not register (see PlasmaMortarEffect). */
    private fun scanWraithRounds(engine: CombatEngineAPI) {
        for (projectile in engine.projectiles) {
            if (projectile.projectileSpecId != WRAITH_SHOT_ID || projectile in wraithRounds) continue
            if (projectile.didDamage() || projectile.isFading) continue
            if (!wraithFromScanLogged) {
                wraithFromScanLogged = true
                log.info("Wraith round picked up from the projectile list (onFire did not register it): " +
                    "${projectile.javaClass.simpleName} at ${projectile.location}")
            }
            addWraith(projectile, org.combustiblelemon.covenantremnants.weapons.PlasmaMortarOnHit.ROUND_UNIT)
        }
    }

    override fun advance(amount: Float) {
        val engine = Global.getCombatEngine() ?: return
        if (engine.isPaused) return
        time += amount

        val flightIterator = flights.iterator()
        while (flightIterator.hasNext()) {
            val flight = flightIterator.next()
            if (flight.flying) track(engine, flight)
            flight.advance(amount)
            if (flight.isDone) flightIterator.remove()
        }
        val cannonIterator = cannons.iterator()
        while (cannonIterator.hasNext()) {
            val cannon = cannonIterator.next()
            if (cannon.flying) {
                val round = cannon.projectile
                if (round.isExpired || round.didDamage() || !engine.isEntityInPlay(round)) cannon.end()
            }
            cannon.advance(amount)
            if (cannon.isDone) cannonIterator.remove()
        }
        scanWraithRounds(engine)
        val wraithIterator = wraiths.iterator()
        while (wraithIterator.hasNext()) {
            val wraith = wraithIterator.next()
            if (wraith.flying) {
                // isExpired is not used: a BALLISTIC round may report it from spawn (2026-10-09: the
                // Wraith rounds never drew). A new round may not be in play yet: wait until seen.
                val round = wraith.projectile
                val inPlay = engine.isEntityInPlay(round)
                if (inPlay) wraith.seenInPlay = true
                val gone = (wraith.seenInPlay && !inPlay) || (!wraith.seenInPlay && wraith.age > WRAITH_UNSEEN_TIMEOUT)
                if (round.didDamage() || gone) {
                    if (wraith.age < WRAITH_EARLY_END || !wraith.seenInPlay || wraithEndsLogged < WRAITH_LOGGED_ENDS) {
                        wraithEndsLogged++
                        log.info(
                            "Wraith round ended after ${wraith.age} s, ${wraith.distance} su: didDamage=${round.didDamage()} " +
                                "inPlay=$inPlay seen=${wraith.seenInPlay} expired=${round.isExpired} fading=${round.isFading}"
                        )
                    }
                    wraith.end()
                }
            }
            wraith.advance(amount)
            if (wraith.isDone) {
                wraithIterator.remove()
                if (!engine.isEntityInPlay(wraith.projectile)) wraithRounds.remove(wraith.projectile)
            }
        }
        val burstIterator = bursts.iterator()
        while (burstIterator.hasNext()) {
            val burst = burstIterator.next()
            burst.advance(amount)
            if (burst.isDone) burstIterator.remove()
        }
    }

    private fun track(engine: CombatEngineAPI, flight: TorpedoFlight) {
        val missile = flight.missile
        if (missile.isExpired || !engine.isEntityInPlay(missile)) {
            flight.end()
            if (missile.didDamage() || missile.hitpoints <= 0f) {
                val location = Vector2f(missile.location)
                bursts.add(TorpedoBurst.impact(location, flight.unit))
                Global.getSoundPlayer().playSound(
                    SOUND_IMPACT, Random.nextFloat() * (1f - IMPACT_PITCH_MIN) + IMPACT_PITCH_MIN, 1f, location, Vector2f()
                )
            }
            return
        }
        if (!missile.isFizzling && !missile.isFading) {
            Global.getSoundPlayer().playLoop(
                SOUND_LOOP, missile, 1f, 1f, missile.location, missile.velocity, LOOP_FADE, LOOP_FADE
            )
        }
    }

    override fun render(layer: CombatEngineLayers, viewport: ViewportAPI) {
        renderer.render(viewport, flights, cannons, wraiths, bursts, time)
    }

    override fun getActiveLayers(): EnumSet<CombatEngineLayers> =
        EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER)

    override fun getRenderRadius(): Float = Float.MAX_VALUE

    override fun init(entity: CombatEntityAPI) {}

    override fun cleanup() {}

    override fun isExpired(): Boolean = false
}
