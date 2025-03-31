package org.combustiblelemon.covenantremnants.systems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import org.combustiblelemon.covenantremnants.plugins.CovenantShieldsRenderPlugin
import org.combustiblelemon.covenantremnants.plugins.CovenantShieldsUIRenderPlugin
import org.combustiblelemon.covenantremnants.utils.Utils
import org.lwjgl.util.vector.Vector2f
import kotlin.math.max
import kotlin.math.min

class CovenantShieldsStats : BaseShipSystemScript() {
    private var statusKey = Any()
    private var renderPlugin: CovenantShieldsRenderPlugin? = null
    private var UIRenderPlugin: CovenantShieldsUIRenderPlugin? = null
    private var damageListener: ShieldDamageListener? = null
    private var shipListenerInstance: ShipListener? = null

    private var shieldHealth: Float = 5000f
        set(value) {
            val shieldValue = max(0f, min(value, maxShieldHealth))

            if (field != 0f && shieldValue == 0f) {
                breakCooldownActive = true
                breakCooldownStartTime = Global.getCombatEngine().getTotalElapsedTime(false)
            }

            field = shieldValue
        }
    private val maxShieldHealth: Float = 5000f
    private val shieldRegen: Float = 300f
    var breakCooldownActive = false
        set(value) {
            if (value) {
                renderPlugin?.triggerShieldPop()
            }

            renderPlugin?.breakCooldownActive = value
            field = value
        }
    var breakCooldownStartTime = 0f
    var shieldCooldown: Float = 1f
        set(value) {
            val v = max(0f, min(value, 1f))

            UIRenderPlugin?.shieldCooldown = v
            field = v
        }

    fun updateRenderers() {
        UIRenderPlugin.let {
            it?.shieldHealth = shieldHealth
        }

        renderPlugin.let {
            it?.breakCooldownActive = breakCooldownActive
        }

    }

    private class ShipListener(ship: ShipAPI, parent: CovenantShieldsStats) : AdvanceableListener {
        private val breakCooldownTime = if (ship.phaseCloak.ammoPerSecond != null) ship.phaseCloak.ammoPerSecond else 9f
        private val parent = parent

        override fun advance(amount: Float) {
            if (parent.breakCooldownActive) {
                val remainingTime = parent.breakCooldownStartTime + breakCooldownTime - Global.getCombatEngine()
                    .getTotalElapsedTime(false)

                if (remainingTime <= 0) {
                    parent.breakCooldownActive = false
                    parent.shieldCooldown = 1f
                } else {
                    parent.shieldCooldown = max(0f, min(remainingTime / breakCooldownTime, breakCooldownTime))
                }
            } else {
                if (parent.shieldHealth < parent.maxShieldHealth) {
                    parent.shieldHealth = (parent.shieldHealth + parent.shieldRegen) * amount
                }
            }

            parent.updateRenderers()
        }
    }

    override fun isUsable(system: ShipSystemAPI?, ship: ShipAPI?): Boolean {
        return shieldCooldown >= 1f
    }

    override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effLevel: Float) {
        var ship: ShipAPI? = null
        var player: Boolean = false

        if (stats.entity is ShipAPI) {
            ship = stats.entity as ShipAPI
            player = ship === Global.getCombatEngine().playerShip
        } else {
            return
        }

        if (renderPlugin == null) {
            renderPlugin = CovenantShieldsRenderPlugin(ship, Utils.getShipDimensions(ship))
            Global.getCombatEngine().addLayeredRenderingPlugin(renderPlugin)
        }

        if (damageListener == null) {
            damageListener = ShieldDamageListener(ship, this)
            ship.addListener(damageListener)
        }

        if (shipListenerInstance == null) {
            shipListenerInstance = ShipListener(ship, this)
            ship.addListener(shipListenerInstance)
        }

        if (player) {
            if (UIRenderPlugin == null) {
                UIRenderPlugin = CovenantShieldsUIRenderPlugin(ship)
                Global.getCombatEngine().addLayeredRenderingPlugin(UIRenderPlugin)

                UIRenderPlugin.let {
                    it?.shieldHealth = shieldHealth
                    it?.maxShieldHealth = maxShieldHealth
                }
            }
            val system = getShield(ship)

            Global.getCombatEngine()
                .maintainStatusForPlayerShip(statusKey, system.specAPI.iconSpriteName, "Shield active", "", false)
        }
    }

    class ShieldDamageListener(private val attachedTo: ShipAPI, private val parent: CovenantShieldsStats) :
        DamageTakenModifier {

        override fun modifyDamageTaken(
            param: Any?,
            target: CombatEntityAPI?,
            damage: DamageAPI?,
            point: Vector2f?,
            shieldHit: Boolean
        ): String {
            if (attachedTo.phaseCloak.isActive) {
                val damageNumber = damage?.damage ?: 0f




                if (parent.shieldHealth < damageNumber) {
                    parent.dealShieldDamage(parent.shieldHealth)

                    damage?.modifier?.modifyMult(
                        "covenant_remnants_covenant_shields",
                        1f - parent.shieldHealth / damageNumber
                    )
                } else {
                    parent.dealShieldDamage(damageNumber)

                    damage?.modifier?.modifyMult("covenant_remnants_covenant_shields", 0f)
                }
            }

            return "covenant_remnants_covenant_shields"
        }
    }

    fun dealShieldDamage(amount: Float) {
        this.shieldHealth -= amount
    }

    private fun getShield(ship: ShipAPI?): ShipSystemAPI {
        val system = ship!!.phaseCloak
        return if (system != null && system.id == "covenant_shield") system else ship.system
    }

}
