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
import org.combustiblelemon.covenantremnants.utils.render.torpedo.CannonFlight
import org.combustiblelemon.covenantremnants.utils.render.torpedo.TorpedoBurst
import org.combustiblelemon.covenantremnants.utils.render.torpedo.TorpedoFlight
import org.combustiblelemon.covenantremnants.utils.render.torpedo.TorpedoLook
import org.combustiblelemon.covenantremnants.utils.render.torpedo.TorpedoRenderer
import org.lwjgl.util.vector.Vector2f
import java.util.EnumSet
import kotlin.random.Random

/**
 * One-per-combat owner of the plasma torpedoes' Halo: Reach look (created on the first launch by
 * [org.combustiblelemon.covenantremnants.weapons.PlasmaTorpedoOnFire]). Follows every torpedo:
 * muzzle flash at launch, body/flare/trail and the in-flight hum while it flies, and when it is
 * gone the impact and explosion sound if it hit or was shot down (a torpedo that runs out of fuel
 * just fades). Also draws the Plasma Cannon rounds' energy aura ([CannonFlight]) and their hit
 * splash. Drawn above ships and missiles.
 */
class TorpedoRenderPlugin private constructor() : CombatLayeredRenderingPlugin {
    companion object {
        private const val KEY = "covenant_remnants_torpedo_render_plugin"
        const val SOUND_LOOP = "covenant_remnants_plasma_torpedo_loop"
        const val SOUND_IMPACT = "covenant_remnants_plasma_torpedo_impact"

        // Reach: the hum fades in and out over 0.2 s; the explosion pitches 0 to -2 semitones down.
        private const val LOOP_FADE = 0.2f
        private const val IMPACT_PITCH_MIN = 0.891f

        fun forEngine(engine: CombatEngineAPI): TorpedoRenderPlugin {
            (engine.customData[KEY] as? TorpedoRenderPlugin)?.let { return it }
            val plugin = TorpedoRenderPlugin()
            engine.customData[KEY] = plugin
            engine.addLayeredRenderingPlugin(plugin)
            return plugin
        }
    }

    private val flights = ArrayList<TorpedoFlight>()
    private val cannons = ArrayList<CannonFlight>()
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

    /** Follows a Plasma Cannon round with its energy aura ([org.combustiblelemon.covenantremnants.weapons.PlasmaCannonOnFire]). */
    fun trackCannonRound(projectile: DamagingProjectileAPI, unit: Float) {
        cannons.add(CannonFlight(projectile, unit))
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
        renderer.render(viewport, flights, cannons, bursts, time)
    }

    override fun getActiveLayers(): EnumSet<CombatEngineLayers> =
        EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER)

    override fun getRenderRadius(): Float = Float.MAX_VALUE

    override fun init(entity: CombatEntityAPI) {}

    override fun cleanup() {}

    override fun isExpired(): Boolean = false
}
