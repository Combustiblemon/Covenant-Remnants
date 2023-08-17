package org.Combustiblelemon.CovenantRemnants.ai

import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import org.Combustiblelemon.CovenantRemnants.utils.Utils
import org.lwjgl.util.vector.Vector2f

class PlasmaBarrageAI : ShipSystemAIScript {
    private var ship: ShipAPI? = null
    private var engine: CombatEngineAPI? = null
    private var flags: ShipwideAIFlags? = null
    private var system: ShipSystemAPI? = null
    private val tracker = IntervalUtil(0.5f, 1f)
    override fun init(ship: ShipAPI, system: ShipSystemAPI, flags: ShipwideAIFlags, engine: CombatEngineAPI) {
        this.ship = ship
        this.flags = flags
        this.engine = engine
        this.system = system
    }

    override fun advance(amount: Float, missileDangerDir: Vector2f, collisionDangerDir: Vector2f, target: ShipAPI) {
        if (target == null) {
            return
        }
        val targetInRange = Misc.getDistance(ship!!.location, target.location) < RANGE
        if (targetInRange && (!Utils.isShieldFacingShip(ship!!, target) || target.shield.isOff)) {
            ship!!.useSystem()
        }
    }

    companion object {
        protected var RANGE = 1500f
    }
}
