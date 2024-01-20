package org.combustiblelemon.covenantremnants.systems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI
import com.fs.starfarer.api.combat.listeners.DamageListener
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier
import com.fs.starfarer.api.combat.listeners.HullDamageAboutToBeTakenListener
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.thoughtworks.xstream.mapper.Mapper.Null
import org.combustiblelemon.covenantremnants.plugins.CovenantShieldsRenderPlugin
import org.lwjgl.util.vector.Vector2f

class CovenantShieldsStats : BaseShipSystemScript() {
    private var statuskey = Any()
    private var renderPlugin: CovenantShieldsRenderPlugin? = null
    private var damageListener: ShieldDamageListener? = null

    override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effLevel: Float) {
        var ship: ShipAPI? = null
        var player = false

        if (stats.entity is ShipAPI) {
            ship = stats.entity as ShipAPI
            player = ship === Global.getCombatEngine().playerShip
        } else {
            return
        }

        if (renderPlugin == null) {
            renderPlugin = CovenantShieldsRenderPlugin(ship)
            Global.getCombatEngine().addLayeredRenderingPlugin(renderPlugin)
        }

        if (damageListener == null) {
            damageListener = ShieldDamageListener(ship)
            ship.addListener(damageListener)
        }

        if (player) {
            val system = getShield(ship)

            Global.getCombatEngine()
                .maintainStatusForPlayerShip(statuskey, system.specAPI.iconSpriteName, "Shield active", "", false)
        }
    }

    class ShieldDamageListener(private val attachedTo: ShipAPI) : DamageTakenModifier {

        override fun modifyDamageTaken(
            param: Any?,
            target: CombatEntityAPI?,
            damage: DamageAPI?,
            point: Vector2f?,
            shieldHit: Boolean
        ): String {
            if (attachedTo.phaseCloak.isActive) {
                val damageNumber = damage!!.damage * 0.6f


                if (attachedTo.fluxTracker.currFlux + damageNumber >= attachedTo.fluxTracker.maxFlux) {
                    attachedTo.fluxTracker.forceOverload(0f)
                }

                attachedTo.fluxTracker.increaseFlux(damageNumber, false)
                damage.modifier?.modifyMult("covenant_remnants_covenantshields", 0f)
            }

            return "covenant_remnants_covenantshields"
        }
    }

    override fun unapply(stats: MutableShipStatsAPI, id: String) {

    }

    private fun getShield(ship: ShipAPI?): ShipSystemAPI {
        val system = ship!!.phaseCloak
        return if (system != null && system.id == "covenant_shield") system else ship.system
    }

}
