package org.combustiblelemon.covenantremnants.campaign

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.SectorAPI
import com.fs.starfarer.renderers.JitterRenderer
import org.combustiblelemon.lemonlib.reflection.ReflectionUtils
import org.lwjgl.util.vector.Vector2f

/**
 * Save repair for saves made before Starsector 0.98.5a.
 *
 * 0.98.5a added `JitterRenderer.collatedOffsets`, initialised where it is declared. XStream does not
 * run that initialiser, so a renderer read from an older save comes back with the list null, and
 * the first campaign frame dies in `JitterRenderer.render` (NullPointerException on `clear()`).
 * A fleet member view keeps its renderer once `setJitter` has run on it. Slipspace Jump jitters the
 * player fleet's views on every arrival (vanilla Generate Slipsurge does too), so most saves that
 * used it carry such a renderer.
 *
 * On load this gives every stored renderer with a missing list an empty one. The renderer class is
 * `DoNotObfuscate`, so the field is found by name; the view's renderer field is found by type.
 */
object JitterSaveRepair {

    private const val OFFSETS_FIELD = "collatedOffsets"

    private val log = Global.getLogger(JitterSaveRepair::class.java)

    /** Renderer fields per view class, looked up once (every fleet view shares one class). */
    private val fieldsByClass = HashMap<Class<*>, List<ReflectionUtils.ReflectedField>>()

    fun repair(sector: SectorAPI) {
        var repaired = 0
        try {
            val fleets = LinkedHashSet<CampaignFleetAPI>()
            sector.playerFleet?.let { fleets.add(it) }
            for (location in sector.allLocations) fleets.addAll(location.fleets)
            for (fleet in fleets) {
                for (view in fleet.views ?: continue) {
                    if (view != null) repaired += repairView(view)
                }
            }
        } catch (t: Throwable) {
            // Never block a load over this; the worst case is the vanilla crash it guards against.
            log.warn("Covenant Remnants: jitter save repair failed", t)
            return
        }
        if (repaired > 0) {
            log.info("Covenant Remnants: repaired $repaired pre-0.98.5a fleet jitter renderer(s) in this save")
        }
    }

    private fun repairView(view: Any): Int {
        val fields = fieldsByClass.getOrPut(view.javaClass) {
            ReflectionUtils.findFieldsAssignableTo(view, JitterRenderer::class.java)
        }
        var repaired = 0
        for (field in fields) {
            val renderer = field.get(view) ?: continue
            if (ReflectionUtils.get(OFFSETS_FIELD, renderer) == null) {
                ReflectionUtils.set(OFFSETS_FIELD, renderer, ArrayList<Vector2f>())
                repaired++
            }
        }
        return repaired
    }
}
