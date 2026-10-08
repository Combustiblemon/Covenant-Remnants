package org.combustiblelemon.covenantremnants.plugins

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.BeamAPI
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.CombatEngineLayers
import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.CombatLayeredRenderingPlugin
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.combat.WeaponAPI
import org.combustiblelemon.covenantremnants.utils.render.projector.ProjectorEffect
import org.combustiblelemon.covenantremnants.utils.render.projector.ProjectorRenderer
import org.lwjgl.util.vector.Vector2f
import java.util.EnumSet

/**
 * One-per-combat owner of the Energy Projectors' Halo: Reach glassing look (see
 * [ProjectorEffect]). Each projector's `everyFrameEffect`
 * ([org.combustiblelemon.covenantremnants.weapons.EnergyProjectorEffect]) registers its weapon
 * every frame and its `beamEffect` ([org.combustiblelemon.covenantremnants.weapons.EnergyProjectorBeamEffect])
 * hands over the live beam with its collision-resolved endpoints. The plugin advances one effect
 * per weapon, plays the impact sound when a beam first lands, and draws everything above ships and
 * missiles. The engine beam itself (collision, damage, the pierce re-render, vanilla textures) is
 * untouched.
 */
class EnergyProjectorRenderPlugin private constructor() : CombatLayeredRenderingPlugin {
    companion object {
        private const val KEY = "covenant_remnants_energy_projector_render_plugin"
        const val SOUND_IMPACT = "covenant_remnants_energy_projector_impact"

        fun forEngine(engine: CombatEngineAPI): EnergyProjectorRenderPlugin {
            (engine.customData[KEY] as? EnergyProjectorRenderPlugin)?.let { return it }
            val plugin = EnergyProjectorRenderPlugin()
            engine.customData[KEY] = plugin
            engine.addLayeredRenderingPlugin(plugin)
            return plugin
        }
    }

    private val effects = LinkedHashMap<WeaponAPI, ProjectorEffect>()
    private val renderer = ProjectorRenderer()
    private var time = 0f

    /** Called by the weapon's everyFrameEffect; the effect is advanced in [advance]. */
    fun register(weapon: WeaponAPI) {
        if (!effects.containsKey(weapon)) effects[weapon] = ProjectorEffect(weapon)
    }

    /** Called by the beam effect each frame the beam lives (endpoints are collision-resolved there). */
    fun beam(weapon: WeaponAPI, beam: BeamAPI) {
        register(weapon)
        effects[weapon]?.snapshot(beam)
    }

    override fun advance(amount: Float) {
        val engine = Global.getCombatEngine() ?: return
        if (engine.isPaused) return
        time += amount
        val iterator = effects.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val effect = entry.value
            effect.advance(amount)
            if (effect.impactThisFrame) {
                val at = Vector2f(effect.hitPoint)
                Global.getSoundPlayer().playSound(SOUND_IMPACT, 1f, 1f, at, Vector2f())
            }
            val ship = entry.key.ship
            if (effect.isDone && (ship == null || !ship.isAlive)) iterator.remove()
        }
    }

    override fun render(layer: CombatEngineLayers, viewport: ViewportAPI) {
        renderer.render(viewport, effects.values, time)
    }

    override fun getActiveLayers(): EnumSet<CombatEngineLayers> =
        EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER)

    override fun getRenderRadius(): Float = Float.MAX_VALUE

    override fun init(entity: CombatEntityAPI) {}

    override fun cleanup() {}

    override fun isExpired(): Boolean = false
}
