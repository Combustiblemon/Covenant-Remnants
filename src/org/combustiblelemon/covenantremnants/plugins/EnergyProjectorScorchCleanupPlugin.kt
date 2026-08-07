package org.combustiblelemon.covenantremnants.plugins

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.input.InputEventAPI
import kotlin.math.min

/**
 * Fades out and removes the Energy Projector's synthetic exit-scorch decals a few seconds after the
 * last burn on each ship.
 *
 * [org.combustiblelemon.covenantremnants.weapons.EnergyProjectorExitBurn] creates REAL vanilla
 * decals but restores the armor behind them, so the game would keep them all combat. Vanilla decal
 * opacity is re-derived from the armor grid by `syncWithArmorGridState()` (only for cells below 90%
 * armor), which makes a smooth fade drivable through public API alone. Per burned cell the burn
 * records the **post-hit** armor value (the depth the decal was stamped at); the fade then runs,
 * each frame: write `lerp(postHit -> real value, t)` into the cell, sync (the renderer re-reads the
 * fraction and lightens the decal), write the real value back. Cells with genuine damage converge
 * exactly to their true look — no pop at all; pristine cells stop updating at the renderer's 90%
 * gate (~10% alpha) and the final `clearDamageDecals()` + sync removes the leftover speck.
 *
 * Ember handling: sync marks decals as "hit this frame", which freezes their ember decay — a hot
 * ember would survive the whole fade and pop at the end. So the fade STARTS with one
 * clear + sync (with the fade's cell values applied): every decal on the ship is reborn from grid
 * state — ours at its current darkness with the ember zeroed, genuine ones at their true look.
 * Cost, accepted with the developer: at that instant the ship's other decals re-roll their random
 * frame/rotation and lose any active embers.
 *
 * Engine-wide singleton via `engine.customData` — with two projectors firing at one ship, a single
 * fade per ship avoids double clear/resync. Both the plugin list and customData are reset by the
 * engine per combat, so no cross-battle leakage.
 */
class EnergyProjectorScorchCleanupPlugin : BaseEveryFrameCombatPlugin() {

    companion object {
        private const val DATA_KEY = "covenant_remnants_ep_scorch_cleanup"
        private const val FADE_DELAY = 3f   // seconds after the last burn before the fade starts
        private const val FADE_TIME = 2.5f  // fade-out duration

        /**
         * Registers one synthetically burned cell on [ship]; (re)starts the ship's fade timer.
         * [postHit] is the cell's armor value right after the synthetic hit, before the restore.
         */
        fun noteBurnedCell(engine: CombatEngineAPI, ship: ShipAPI, x: Int, y: Int, postHit: Float) {
            var plugin = engine.customData[DATA_KEY] as? EnergyProjectorScorchCleanupPlugin
            if (plugin == null) {
                plugin = EnergyProjectorScorchCleanupPlugin()
                engine.customData[DATA_KEY] = plugin
                engine.addPlugin(plugin)
            }
            val scorch = plugin.ships.getOrPut(ship) { ShipScorch() }
            val key = (x shl 16) or y
            val cell = scorch.cells[key]
            if (cell == null) {
                scorch.cells[key] = BurnedCell(x, y, postHit)
            } else if (postHit < cell.postHit) {
                cell.postHit = postHit  // deepest burn wins: that's the darkness the decal shows
            }
            // A fresh burn re-darkens the decal, so any in-progress fade restarts from the top.
            scorch.sinceBurn = 0f
            scorch.fading = false
            scorch.fadeT = 0f
        }
    }

    private class BurnedCell(val x: Int, val y: Int, var postHit: Float)

    private class ShipScorch {
        val cells = HashMap<Int, BurnedCell>()
        var sinceBurn = 0f
        var fading = false
        var fadeT = 0f
    }

    private val ships = HashMap<ShipAPI, ShipScorch>()

    override fun advance(amount: Float, events: MutableList<InputEventAPI>?) {
        if (ships.isEmpty()) return
        val engine = Global.getCombatEngine() ?: return
        if (engine.isPaused) return

        val it = ships.entries.iterator()
        while (it.hasNext()) {
            val (ship, scorch) = it.next()
            if (!engine.isEntityInPlay(ship) || ship.armorGrid == null) {
                it.remove()
                continue
            }

            if (!scorch.fading) {
                scorch.sinceBurn += amount
                if (scorch.sinceBurn >= FADE_DELAY) {
                    scorch.fading = true
                    scorch.fadeT = 0f
                    // Rebirth pass: kills the (decay-frozen) embers before the fade — see class doc.
                    fadeFrame(ship, scorch, 0f, clearFirst = true)
                }
                continue
            }

            scorch.fadeT += amount
            val t = min(1f, scorch.fadeT / FADE_TIME)
            fadeFrame(ship, scorch, t, clearFirst = false)
            if (t >= 1f) {
                // Cell values equal reality now; this only purges the sub-90%-gate leftovers.
                ship.clearDamageDecals()
                ship.syncWithArmorGridState()
                it.remove()
            }
        }
    }

    /**
     * One fade step: write the interpolated values into the burned cells, resync the decal renderer
     * from them, write the real values back. Never sets a cell above its real value, so decals over
     * genuine damage are never lightened past their true look.
     */
    private fun fadeFrame(ship: ShipAPI, scorch: ShipScorch, t: Float, clearFirst: Boolean) {
        val grid = ship.armorGrid ?: return
        val cells = scorch.cells.values
        val real = FloatArray(cells.size)
        var i = 0
        for (c in cells) {
            val r = grid.getArmorValue(c.x, c.y)
            real[i++] = r
            var temp = c.postHit + (r - c.postHit) * t
            if (temp > r) temp = r
            grid.setArmorValue(c.x, c.y, temp)
        }
        if (clearFirst) ship.clearDamageDecals()
        ship.syncWithArmorGridState()
        i = 0
        for (c in cells) grid.setArmorValue(c.x, c.y, real[i++])
    }
}
