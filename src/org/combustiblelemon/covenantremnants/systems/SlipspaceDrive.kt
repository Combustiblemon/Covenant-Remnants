package org.combustiblelemon.covenantremnants.systems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.MutableShipStatsAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipSystemAPI
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import org.combustiblelemon.covenantremnants.plugins.SlipspaceDriveRenderPlugin
import org.combustiblelemon.covenantremnants.utils.CubicCurve2D
import org.combustiblelemon.covenantremnants.utils.getVelocityVectorInFacing
import org.combustiblelemon.covenantremnants.utils.normalize
import org.lazywizard.lazylib.MathUtils
import org.lazywizard.lazylib.combat.CombatUtils
import org.lwjgl.util.vector.Vector2f
import java.awt.Color

class SlipspaceDrive : BaseShipSystemScript() {
    private var portalExit: Vector2f? = null
    private var facing: Float = 0f
    private var renderPlugin: SlipspaceDriveRenderPlugin? = null
    private val cubicFunction = CubicCurve2D(1f, 0f, 0f, 1f)

    //    IN
    private var isIn = false

    //    ACTIVE
    private var isActive: Boolean = false
    private var launchStartTime: Float = 0f
    private val launchSpeed: Float = 1500f
    private val portalDuration: Float = 14f
    private var portalDistance: Float = 1000f
    private var portalRadius: Float = 500f
    private var systemUseDistance: Float = 1000f

    //    OUT
    private var isOut: Boolean = false
    override fun isUsable(system: ShipSystemAPI, ship: ShipAPI): Boolean {
        // Distance to exit is larger than SYSTEM_USE_DISTANCE
        // No ships within the exit radius
        return !system.isActive &&
                MathUtils.getDistance(
                    ship,
                    ship.mouseTarget
                ) > systemUseDistance &&

                ship.fluxTracker.currFlux / ship.fluxTracker.maxFlux < 0.7 &&
                CombatUtils.getShipsWithinRange(
                    ship.mouseTarget,
                    ship.shieldRadiusEvenIfNoShield
                ).size == 0
    }

    override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effectLevel: Float) {
        var ship: ShipAPI? = null
        ship = if (stats.entity is ShipAPI) {
            stats.entity as ShipAPI
        } else {
            return
        }
        val currentTime = Global.getCombatEngine().getTotalElapsedTime(false)

        if (renderPlugin == null) {
            renderPlugin = SlipspaceDriveRenderPlugin(ship, portalDuration, portalDistance, portalRadius)
            Global.getCombatEngine().addLayeredRenderingPlugin(renderPlugin)
        }

        if (state == ShipSystemStatsScript.State.IN && !isIn) {
            isIn = true
            facing = ship.facing
            portalExit = Vector2f(ship.mouseTarget)
            ship.angularVelocity = 0f
            ship.velocity.set(Vector2f(0f, 0f))
            stats.combatEngineRepairTimeMult.modifyMult(id, 0f)
            stats.maxSpeed.modifyFlat(id, 300f)
        }

        if (state == ShipSystemStatsScript.State.ACTIVE) {
            if (!isActive) {
                isActive = true
                launchStartTime = currentTime
            } else {
                val number = normalize((currentTime - launchStartTime).toDouble(), 0.0, 4.0)
                ship.velocity.set(getVelocityVectorInFacing(ship, launchSpeed * cubicFunction.getTime(number)))
            }
        }
        if (state == ShipSystemStatsScript.State.OUT) {
            if (!isOut) {
                ship.fluxTracker.currFlux = ship.fluxTracker.maxFlux * 0.8f
                isOut = true
                stats.maxSpeed.unmodify(id)
                stats.acceleration.unmodify(id)
                stats.combatEngineRepairTimeMult.unmodify(id)
                ship.location.set(portalExit)
                ship.velocity.set(getVelocityVectorInFacing(ship, 15f))
            }
            ship.setJitter(this, Color.white, ship.system.effectLevel, 4, 40f)
        }
    }

    override fun unapply(stats: MutableShipStatsAPI, id: String) {
        stats.maxSpeed.unmodify(id)
        stats.acceleration.unmodify(id)
        isActive = false
        isIn = false
        isOut = false
    }
}
