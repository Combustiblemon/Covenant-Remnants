package org.combustiblelemon.covenantremnants.plugins

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.BeamAPI
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.CombatEngineLayers
import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.CombatLayeredRenderingPlugin
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.combat.WeaponAPI
import org.combustiblelemon.covenantremnants.utils.render.plasmabeam.PlasmaBeamLook
import org.combustiblelemon.covenantremnants.utils.render.plasmabeam.PlasmaBeamRenderer
import org.lwjgl.util.vector.Vector2f
import java.util.EnumSet

/**
 * One-per-combat owner of the Plasma Beam Emitters' (and the Suppressor Beam's) Halo: Reach focus
 * rifle look (see [PlasmaBeamLook]; sounds and gain per weapon id in [PlasmaBeamLook.Style]). Each emitter's `beamEffect`
 * ([org.combustiblelemon.covenantremnants.weapons.PlasmaBeamEmitterEffect]) hands over the live
 * beam with its collision-resolved endpoints every frame. The plugin advances one look per
 * weapon, plays the fire in / out pieces when a beam starts / stops and the impact sound when a
 * beam first lands, and draws everything above ships and missiles. The engine beam itself
 * (collision, damage, the fire loop, vanilla textures) is untouched.
 */
class PlasmaBeamRenderPlugin private constructor() : CombatLayeredRenderingPlugin {
    companion object {
        private const val KEY = "covenant_remnants_plasma_beam_render_plugin"

        fun forEngine(engine: CombatEngineAPI): PlasmaBeamRenderPlugin {
            (engine.customData[KEY] as? PlasmaBeamRenderPlugin)?.let { return it }
            val plugin = PlasmaBeamRenderPlugin()
            engine.customData[KEY] = plugin
            engine.addLayeredRenderingPlugin(plugin)
            return plugin
        }
    }

    private val looks = LinkedHashMap<WeaponAPI, PlasmaBeamLook>()
    private val renderer = PlasmaBeamRenderer()
    private var time = 0f

    /** Called by the beam effect each frame the beam lives (endpoints are collision-resolved there). */
    fun beam(weapon: WeaponAPI, beam: BeamAPI) {
        looks.getOrPut(weapon) { PlasmaBeamLook(weapon) }.snapshot(beam)
    }

    override fun advance(amount: Float) {
        val engine = Global.getCombatEngine() ?: return
        if (engine.isPaused) return
        time += amount
        val iterator = looks.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val look = entry.value
            look.advance(amount)
            val style = look.style
            if (look.startedThisFrame) play(style.fireIn, look.muzzle, entry.key.ship?.velocity)
            if (look.endedThisFrame) play(style.fireOut, look.muzzle, entry.key.ship?.velocity)
            if (look.impactThisFrame) play(style.impact, look.hitPoint, null)
            val ship = entry.key.ship
            if (look.isDone && (ship == null || !ship.isAlive)) iterator.remove()
        }
    }

    private fun play(id: String, at: Vector2f, velocity: Vector2f?) {
        Global.getSoundPlayer().playSound(id, 1f, 1f, Vector2f(at), velocity ?: Vector2f())
    }

    override fun render(layer: CombatEngineLayers, viewport: ViewportAPI) {
        renderer.render(viewport, looks.values, time)
    }

    override fun getActiveLayers(): EnumSet<CombatEngineLayers> =
        EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER)

    override fun getRenderRadius(): Float = Float.MAX_VALUE

    override fun init(entity: CombatEntityAPI) {}

    override fun cleanup() {}

    override fun isExpired(): Boolean = false
}
