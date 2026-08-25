package org.combustiblelemon.covenantremnants.plugins

import com.fs.starfarer.api.combat.BeamAPI
import com.fs.starfarer.api.combat.CombatEngineLayers
import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.CombatLayeredRenderingPlugin
import com.fs.starfarer.api.combat.ViewportAPI
import org.combustiblelemon.lemonlib.reflection.ReflectionUtils
import org.lwjgl.util.vector.Vector2f
import java.util.EnumSet

/**
 * Draws the Energy Projector's pierce beam — two segments of the lance beyond its entry hit:
 *   - **entry → exit**, on [CombatEngineLayers.UNDER_SHIPS_LAYER]: the part travelling through the
 *     target's body. Drawn under all ship sprites, so it only shows through transparent pixels —
 *     holes/gaps in the hull sprite — and is covered everywhere else.
 *   - **exit → end**, on [CombatEngineLayers.ABOVE_SHIPS_LAYER]: the part that punches out the far
 *     side and continues until blocked.
 *
 * Rather than hand-draw a lookalike, this **re-renders the real beam** along each segment, so
 * the pierced part is pixel-identical to the actual lance (same ROUGH texture, scroll phase, width,
 * core/fringe, charge/fade brightness). The live [BeamAPI] object is a `LayeredRenderable` that draws
 * itself from `getFrom()` to `getTo()`; we temporarily point those at the segment, invoke its
 * own `render(CombatEngineLayers, CombatViewport)`, then restore the real endpoints.
 *
 * Update-independence (per the [org.combustiblelemon.covenantremnants.hullmods.HullmodManager] shield
 * swap): no obfuscated class/field/method names are referenced. The only reflected name is the
 * interface method `"render"`, and the endpoints are moved through the public [BeamAPI] getters.
 *
 * [EnergyProjectorBeamEffect] calls [refresh] every exposed frame while piercing; when it stops
 * (beam ends / target re-shields / armor recovers) the plugin is no longer refreshed and self-expires.
 */
class EnergyProjectorPierceRenderPlugin : CombatLayeredRenderingPlugin {

    /** Grace after the last [refresh] before we expire — a couple of frames, so brief gaps don't pop. */
    private val holdTime = 0.1f

    private var beam: BeamAPI? = null
    private val entry = Vector2f()
    private val exit = Vector2f()
    private val end = Vector2f()
    private var sinceRefresh = holdTime * 2f

    /** Called by the beam effect each frame it is piercing. */
    fun refresh(beam: BeamAPI, entry: Vector2f, exit: Vector2f, end: Vector2f) {
        this.beam = beam
        this.entry.set(entry)
        this.exit.set(exit)
        this.end.set(end)
        this.sinceRefresh = 0f
    }

    override fun advance(amount: Float) {
        sinceRefresh += amount
    }

    override fun isExpired(): Boolean = sinceRefresh > holdTime || beam == null

    override fun getActiveLayers(): EnumSet<CombatEngineLayers> =
        EnumSet.of(CombatEngineLayers.UNDER_SHIPS_LAYER, CombatEngineLayers.ABOVE_SHIPS_LAYER)

    override fun getRenderRadius(): Float = Float.MAX_VALUE

    override fun init(entity: CombatEntityAPI?) {}

    override fun cleanup() {}

    override fun render(layer: CombatEngineLayers, viewport: ViewportAPI) {
        val b = beam ?: return
        if (sinceRefresh > holdTime) return

        when (layer) {
            // Through-ship segment: under all ship sprites, so it only shows in sprite holes/gaps.
            CombatEngineLayers.UNDER_SHIPS_LAYER -> renderSegment(b, layer, viewport, entry, exit)
            // Pierce-out segment: the lance re-emerging past the far side.
            CombatEngineLayers.ABOVE_SHIPS_LAYER -> renderSegment(b, layer, viewport, exit, end)
            else -> {}
        }
    }

    private fun renderSegment(
        b: BeamAPI, layer: CombatEngineLayers, viewport: ViewportAPI,
        segStart: Vector2f, segEnd: Vector2f
    ) {
        // The beam's own from/to are the live vectors the engine renders and re-collides against.
        // Point them at the segment, let the beam draw itself, then put them back — the swap
        // window is only this synchronous invoke, so the engine's own beam self-render is unaffected.
        val from = b.from
        val to = b.to
        val fx = from.x; val fy = from.y
        val tx = to.x; val ty = to.y

        from.set(segStart)
        to.set(segEnd)
        try {
            ReflectionUtils.invoke("render", b, layer, viewport)
        } catch (_: Exception) {
            // If the engine ever renames render(), silently skip the pierce visual rather than crash.
        } finally {
            from.set(fx, fy)
            to.set(tx, ty)
        }
    }
}
