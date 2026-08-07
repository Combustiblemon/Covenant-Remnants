package org.combustiblelemon.covenantremnants.weapons

import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.DamageType
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.listeners.HullDamageAboutToBeTakenListener
import org.combustiblelemon.covenantremnants.plugins.EnergyProjectorScorchCleanupPlugin
import org.combustiblelemon.covenantremnants.utils.reflection.ReflectionUtils
import org.lwjgl.util.vector.Vector2f
import kotlin.math.max
import kotlin.math.min

/**
 * REAL vanilla armor-scorch decal at the Energy Projector's pierce exit point, via
 * **damage-then-restore**: snapshot the exit area's state, run a synthetic hit through the real
 * `engine.applyDamage` path — which makes the game's own decal renderer produce an exact vanilla
 * scorch + ember (stencil-clipped, slot-covered, the works) — then restore every touched value in
 * the same call stack. Net gameplay change: zero. Restoring armor via [setArmorValue] is invisible
 * to the decal renderer (raw write, no listener), and its rebuild path skips cells above 90%
 * armor, so the decal survives the restore until [EnergyProjectorScorchCleanupPlugin] clears it.
 *
 * Safety — the synthetic hit must never be able to kill or disable anything:
 *  - Hull damage is forced to exactly 0 by two layered guards: `setNextHitHullDamageThresholdMult(0,0)`
 *    (consumed and auto-reset by the engine inside the hit) plus a [HullDamageAboutToBeTakenListener]
 *    that vetoes the damage — the veto is applied after all other listeners and cannot be overridden,
 *    which matters because e.g. vanilla elite Damage Control overwrites the threshold mid-hit.
 *  - Weapon/engine disables are deferred to the components' own interval-gated advance, never fired
 *    synchronously inside applyDamage — restoring their health in the same call stack means a
 *    disable can never trigger. `repair()` restore is kept as pure insurance.
 *  - Damage floaty numbers are gated on the obf `Ship.setSuppressFloaties(boolean)` (public, stable
 *    name — the same switch vanilla flips around its own synthetic damage in `Ship.disable()`),
 *    called reflectively; if that call ever fails, the whole feature turns itself off rather than
 *    show phantom numbers.
 */
internal object EnergyProjectorExitBurn {

    /** Synthetic damage aimed at ~half-depth scorch on pristine armor: 1.0 * armor rating. */
    private const val DESIRED_FRACTION = 1f

    /** Per-cell overflow safety margin on the caps. */
    private const val CAP_FRACTION = 0.9f

    /**
     * Never size below this fraction of armor rating: stripped exit cells would cap the damage to 0
     * and the ember would die while the lance still burns. The grid notifies the decal renderer
     * with the attempted damage even on empty cells, so this keeps the ember flaring; the hull
     * overflow it causes is neutralized by the guards + restore.
     */
    private const val FLARE_FLOOR_FRACTION = 0.2f

    private const val MIN_DAMAGE = 1f

    /** Set when the suppress-floaties reflection breaks: feature off, never phantom numbers. */
    private var broken = false

    private val hullVeto = object : HullDamageAboutToBeTakenListener {
        override fun notifyAboutToTakeHullDamage(
            param: Any?, ship: ShipAPI?, point: Vector2f?, damageAmount: Float
        ): Boolean = true
    }

    /** One synthetic burn at [exit]; call once per beam damage tick. */
    fun burnAt(engine: CombatEngineAPI, target: ShipAPI, exit: Vector2f) {
        if (broken) return
        // Paranoia: a hit on an alive-at-0-hull ship would reach the death branch even with the
        // hull damage itself zeroed.
        if (!target.isHulk && target.hitpoints <= 0f) return
        val grid = target.armorGrid ?: return
        val cell = grid.getCellAtLocation(exit) ?: return
        val rating = grid.armorRating
        if (rating <= 0f) return

        // Damage spread is 5x5 minus corners: center + inner ring take dmg/15, outer ring dmg/30
        // (all after armor damage reduction, which only shrinks them). Cap so no in-bounds cell can
        // overflow into hull, then floor so stripped cells still flare.
        // ArmorGridAPI has no width/height getters; the backing array is [width][height].
        val cells = grid.grid
        val w = cells.size
        val h = if (w > 0) cells[0].size else 0
        var minInner = Float.MAX_VALUE
        var minOuter = Float.MAX_VALUE
        forEachPatternCell(cell[0], cell[1]) { x, y, inner ->
            if (x in 0 until w && y in 0 until h) {
                val v = grid.getArmorValue(x, y)
                if (inner) minInner = min(minInner, v) else minOuter = min(minOuter, v)
            }
        }
        var dmg = rating * DESIRED_FRACTION
        if (minInner != Float.MAX_VALUE) dmg = min(dmg, CAP_FRACTION * 15f * minInner)
        if (minOuter != Float.MAX_VALUE) dmg = min(dmg, CAP_FRACTION * 30f * minOuter)
        dmg = max(dmg, rating * FLARE_FLOOR_FRACTION)
        if (dmg < MIN_DAMAGE) return

        // Snapshots. NaN marks out-of-bounds pattern slots.
        val armorSnap = FloatArray(21) { Float.NaN }
        var i = 0
        forEachPatternCell(cell[0], cell[1]) { x, y, _ ->
            if (x in 0 until w && y in 0 until h) armorSnap[i] = grid.getArmorValue(x, y)
            i++
        }
        val hullSnap = target.hitpoints
        val weapons = target.allWeapons
        val weaponHealth = FloatArray(weapons.size) { weapons[it].currHealth }
        val weaponDisabled = BooleanArray(weapons.size) { weapons[it].isDisabled }
        val engines = target.engineController?.shipEngines ?: emptyList()
        val engineHealth = FloatArray(engines.size) { engines[it].hitpoints }
        val engineDisabled = BooleanArray(engines.size) { engines[it].isDisabled }

        // Before any state change, so bailing out needs no restore.
        try {
            ReflectionUtils.invoke("setSuppressFloaties", target, true)
        } catch (_: Throwable) {
            broken = true
            return
        }

        try {
            target.addListener(hullVeto)
            target.setNextHitHullDamageThresholdMult(0f, 0f)
            engine.applyDamage(
                target, exit, dmg, DamageType.ENERGY, 0f,
                true /*bypassShields*/, false /*dealsSoftFlux*/, null /*source*/, false /*playSound*/
            )
        } finally {
            target.removeListener(hullVeto)

            // Post-hit values = the armor depth the decal was stamped at; the cleanup plugin fades
            // the decal from this depth back to the cell's real value. Captured before the restore.
            i = 0
            forEachPatternCell(cell[0], cell[1]) { x, y, _ ->
                val saved = armorSnap[i]
                if (!saved.isNaN()) {
                    EnergyProjectorScorchCleanupPlugin.noteBurnedCell(engine, target, x, y, grid.getArmorValue(x, y))
                    grid.setArmorValue(x, y, saved)
                }
                i++
            }
            if (target.hitpoints != hullSnap) target.hitpoints = hullSnap
            // repair() first: it clears a (theoretical) fresh disable but also fills health to max,
            // so the saved health must be written after it.
            for (j in weapons.indices) {
                val wpn = weapons[j]
                if (!weaponDisabled[j] && wpn.isDisabled) wpn.repair()
                if (wpn.currHealth != weaponHealth[j]) wpn.currHealth = weaponHealth[j]
            }
            for (j in engines.indices) {
                val eng = engines[j]
                if (!engineDisabled[j] && eng.isDisabled) eng.repair()
                if (eng.hitpoints != engineHealth[j]) eng.hitpoints = engineHealth[j]
            }

            try {
                ReflectionUtils.invoke("setSuppressFloaties", target, false)
            } catch (_: Throwable) {
                broken = true
            }
        }
    }

    /** Visits the 21-cell damage pattern (5x5 minus corners) around ([cx],[cy]). */
    private inline fun forEachPatternCell(cx: Int, cy: Int, visit: (x: Int, y: Int, inner: Boolean) -> Unit) {
        for (dx in -2..2) {
            for (dy in -2..2) {
                if ((dx == 2 || dx == -2) && (dy == 2 || dy == -2)) continue
                visit(cx + dx, cy + dy, dx in -1..1 && dy in -1..1)
            }
        }
    }
}
