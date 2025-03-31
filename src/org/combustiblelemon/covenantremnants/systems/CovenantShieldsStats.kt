package org.combustiblelemon.covenantremnants.systems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.combat.entities.BeamWeaponRay
import org.combustiblelemon.covenantremnants.plugins.CovenantShieldsRenderPlugin
import org.combustiblelemon.covenantremnants.plugins.CovenantShieldsUIRenderPlugin
import org.combustiblelemon.covenantremnants.utils.Utils
import org.lwjgl.util.vector.Vector2f
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CovenantShieldsStats : BaseShipSystemScript() {
    private var statusKey = Any()
    private var renderPlugin: CovenantShieldsRenderPlugin? = null
    private var UIRenderPlugin: CovenantShieldsUIRenderPlugin? = null
    private var damageListener: ShieldDamageListener? = null
    private var shipListenerInstance: ShipListener? = null
    var ship: ShipAPI? = null
    var shipDimensions: Array<Vector2f>? = null

    var lastDamageTime: Float = 0f
    var shieldDamageRechargeCooldown: Float = 1f

    private var shieldHealth: Float = 5000f
        set(value) {
            if (breakCooldownActive) {
                field = 0f
                return
            }

            val shieldValue = max(0f, min(value, maxShieldHealth))

            if (field != 0f && shieldValue == 0f) {
                breakCooldownActive = true
                breakCooldownStartTime = Global.getCombatEngine().getTotalElapsedTime(false)
                field = 0f
                return
            }

            field = shieldValue
        }
    private val maxShieldHealth: Float = 5000f
    private val shieldRegen: Float = 300f
    var breakCooldownActive = false
        set(value) {
            renderPlugin?.breakCooldownActive = value

            if (value) {
                renderPlugin?.triggerShieldPop()
            }

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
            val elapsedTime = Global.getCombatEngine().getTotalElapsedTime(false)

            if (parent.breakCooldownActive) {
                val remainingTime = parent.breakCooldownStartTime + breakCooldownTime - elapsedTime

                if (remainingTime <= 0) {
                    parent.breakCooldownActive = false
                    parent.shieldCooldown = 1f

                    parent.renderPlugin.let {
                        it?.breakCooldownActive = false
                    }
                } else {
                    parent.shieldCooldown = max(0f, min(remainingTime / breakCooldownTime, breakCooldownTime))
                }
            } else {
                if (parent.shieldHealth < parent.maxShieldHealth && parent.lastDamageTime + parent.shieldDamageRechargeCooldown <= elapsedTime
                ) {
                    parent.shieldHealth = parent.shieldHealth + parent.shieldRegen
                }
            }

            parent.updateRenderers()
        }
    }

    override fun isUsable(system: ShipSystemAPI?, ship: ShipAPI?): Boolean {
        return shieldCooldown >= 1f
    }

    override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effLevel: Float) {
        var _ship: ShipAPI? = null
        var player: Boolean = false

        if (stats.entity is ShipAPI) {
            _ship = stats.entity as ShipAPI
            player = _ship === Global.getCombatEngine().playerShip
            ship = _ship
            shipDimensions = Utils.getShipDimensions(_ship)
        } else {
            return
        }

        if (renderPlugin == null) {
            renderPlugin = CovenantShieldsRenderPlugin(_ship, Utils.getShipDimensions(_ship))
            Global.getCombatEngine().addLayeredRenderingPlugin(renderPlugin)
        }

        if (damageListener == null) {
            damageListener = ShieldDamageListener(_ship, this)
            _ship.addListener(damageListener)
        }

        if (shipListenerInstance == null) {
            shipListenerInstance = ShipListener(_ship, this)
            _ship.addListener(shipListenerInstance)
        }

        if (player) {
            if (UIRenderPlugin == null) {
                UIRenderPlugin = CovenantShieldsUIRenderPlugin(_ship)
                Global.getCombatEngine().addLayeredRenderingPlugin(UIRenderPlugin)

                UIRenderPlugin.let {
                    it?.shieldHealth = shieldHealth
                    it?.maxShieldHealth = maxShieldHealth
                }
            }
            val system = getShield(_ship)

            Global.getCombatEngine()
                .maintainStatusForPlayerShip(statusKey, system.specAPI.iconSpriteName, "Shield active", "", false)
        }
    }

    class ShieldDamageListener(private val attachedTo: ShipAPI, private val parent: CovenantShieldsStats) :
        DamageTakenModifier {
        private val modifierKey = "covenant_remnants_covenant_shields"

        override fun modifyDamageTaken(
            param: Any?,
            target: CombatEntityAPI?,
            damage: DamageAPI?,
            point: Vector2f?,
            shieldHit: Boolean
        ): String {
            if (attachedTo.phaseCloak.isActive) {
                parent.lastDamageTime = Global.getCombatEngine().getTotalElapsedTime(false)

                val percentPoint = Utils.getPointAlongSegmentFromEnd(
                    listOf(
                        Vector2f(
                            parent.shipDimensions!![0].x,
                            attachedTo.location.y
                        ), Vector2f(
                            parent.shipDimensions!![1].x,
                            attachedTo.location.y
                        )
                    ).toTypedArray(),
                    attachedTo.phaseCloak.effectLevel
                )


                if (point === null || percentPoint === null) {
                    return modifierKey
                }

                val hitPastShield = Utils.hasPerpendicularIntersectionPoint(
                    listOf(
                        Vector2f(
                            parent.shipDimensions!![0].x,
                            attachedTo.location.y
                        ),
                        percentPoint

                    ).toTypedArray(), point
                )

                if (hitPastShield) {
                    return modifierKey
                }

                val damageNumber = damage?.damage ?: 0f

                if (param is BeamWeaponRay) {
                    parent.renderPlugin?.addStaticCircleAtPoint(point.x, point.y, 20f)
                } else {
                    parent.renderPlugin?.triggerRippleAtPoint(point.x, point.y)
                }

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

            return modifierKey
        }
    }

    fun dealShieldDamage(amount: Float) {
        this.shieldHealth -= abs(amount)
    }

    private fun getShield(ship: ShipAPI?): ShipSystemAPI {
        val system = ship!!.phaseCloak
        return if (system != null && system.id == "covenant_shield") system else ship.system
    }

}
