package org.combustiblelemon.covenantremnants.systems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.*
import com.fs.starfarer.api.combat.listeners.AdvanceableListener
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI
import com.fs.starfarer.api.combat.listeners.DamageListener
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript
import com.fs.starfarer.api.plugins.ShipSystemStatsScript
import com.fs.starfarer.combat.entities.BeamWeaponRay
import com.fs.starfarer.combat.entities.Missile
import org.combustiblelemon.covenantremnants.plugins.CovenantShieldsRenderPlugin
import org.combustiblelemon.covenantremnants.plugins.CovenantShieldsUIRenderPlugin
import org.combustiblelemon.covenantremnants.utils.Utils
import org.lwjgl.util.vector.Vector2f
import org.magiclib.util.MagicUI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CovenantShieldsStats : BaseShipSystemScript() {
    private var statusKey = Any()
    private var renderPlugin: CovenantShieldsRenderPlugin? = null
    private var UIRenderPlugin: CovenantShieldsUIRenderPlugin? = null
    private var damageListener: ShieldDamageListener? = null
    private var damageConverter: ShieldDamageConverter? = null
    private var shipListenerInstance: ShipListener? = null
    var ship: ShipAPI? = null
    var shipDimensions: Array<Vector2f>? = null

    private var shieldRegen: Float = 1250f
    private val shieldDamageRechargeCooldown: Float = 0.5f
    var shieldDamageMult: Float = 0.3f
    val breakCooldownTime = 9f

    var shieldCooldown: Float = 1f
        set(value) {
            UIRenderPlugin?.shieldCooldown = value
            field = value
        }

    var lastDamageTime: Float = 0f
    var lastDamageIsShield: Boolean = false
    var lastDamageToShield: Float = 0f
    var breakCooldownStartTime = 0f

    // first value is damage number and second is time it happened
    private var damageInstances: MutableList<List<Float>> = mutableListOf()

    private var maxShieldHealth: Float = 20000f
        set(value) {
            if (shieldHealth > maxShieldHealth) {
                shieldHealth = maxShieldHealth
            }
            field = value
        }
    private var shieldHealth: Float = maxShieldHealth
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
    var breakCooldownActive = false
        set(value) {
            renderPlugin?.breakCooldownActive = value

            if (value) {
                renderPlugin?.triggerShieldPop()
            }

            field = value
        }

    fun updateRenderers() {
        UIRenderPlugin.let {
            it?.shieldHealth = shieldHealth
        }

        renderPlugin.let {
            it?.breakCooldownActive = breakCooldownActive
        }

    }

    private class ShipListener(ship: ShipAPI, private val parent: CovenantShieldsStats) : AdvanceableListener {

        override fun advance(amount: Float) {
            val elapsedTime = Global.getCombatEngine().getTotalElapsedTime(false)

            if (parent.breakCooldownActive) {
                val remainingTime = parent.breakCooldownStartTime + parent.breakCooldownTime - elapsedTime


                if (remainingTime <= 0) {
                    parent.breakCooldownActive = false
                    parent.shieldCooldown = 1f

                    parent.renderPlugin.let {
                        it?.breakCooldownActive = false
                    }
                } else {
                    parent.shieldCooldown =
                        max(
                            0f,
                            min(remainingTime / parent.breakCooldownTime, parent.breakCooldownTime)
                        )
                }
            } else {
                if ((parent.shieldHealth < parent.maxShieldHealth)) {
                    parent.shieldHealth += 1000f * amount
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

            maxShieldHealth = _ship.hullSpec.shieldSpec.arc
            shieldRegen = _ship.hullSpec.shieldSpec.upkeepCost
            shieldDamageMult = _ship.hullSpec.shieldSpec.fluxPerDamageAbsorbed
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

        if (damageConverter == null) {
            damageConverter = ShieldDamageConverter(this)
            _ship.addListener(damageConverter)
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

    class ShieldDamageConverter(private val parent: CovenantShieldsStats) : DamageListener {
        override fun reportDamageApplied(source: Any?, target: CombatEntityAPI?, result: ApplyDamageResultAPI?) {

            if (!parent.ship!!.isAlive) {
                return
            }

            if (parent.lastDamageIsShield) {
                result?.damageToShields = parent.lastDamageToShield * 0.3f
            }

            parent.lastDamageIsShield = false
            parent.lastDamageToShield = 0f

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
            if (param === null) {
                return modifierKey
            }

            if (attachedTo.phaseCloak.effectLevel > 0) {
                val engine = Global.getCombatEngine()

                val percentPoint = if (attachedTo.phaseCloak.effectLevel < 1f) {
                    Utils.getPointAlongSegmentFromEnd(
                        listOf(
                            Utils.rotatePointByDegrees(
                                Vector2f(
                                    parent.shipDimensions!![1].x + attachedTo.location.x,
                                    parent.shipDimensions!![0].y + attachedTo.location.y
                                ), attachedTo.location, attachedTo.facing
                            ), Utils.rotatePointByDegrees(
                                Vector2f(
                                    parent.shipDimensions!![0].x + attachedTo.location.x,
                                    parent.shipDimensions!![0].y + attachedTo.location.y
                                ), attachedTo.location, attachedTo.facing
                            )
                        ).toTypedArray(),
                        attachedTo.phaseCloak.effectLevel
                    )
                } else null


                if (point === null || (attachedTo.phaseCloak.effectLevel < 1 && percentPoint === null)) {
                    return modifierKey
                }

                val hitPastShield = if (percentPoint !== null) {
                    Utils.hasPerpendicularIntersectionPoint(
                        listOf(
                            Utils.rotatePointByDegrees(
                                Vector2f(
                                    parent.shipDimensions!![1].x + attachedTo.location.x,
                                    parent.shipDimensions!![0].y + attachedTo.location.y
                                ), attachedTo.location, attachedTo.facing
                            ),
                            percentPoint
                        ).toTypedArray(), point
                    )
                } else false

                if (hitPastShield) {
                    return modifierKey
                }

                val damageNumber = (damage?.damage ?: 0f) * (parent.shieldDamageMult)

                var level = (damageNumber - 30f) / (300f - 30f)
                level = max(0f, min(1f, level))

                if (param !is BeamWeaponRay && level > 0.03f) {
                    parent.renderPlugin?.triggerRippleAtPoint(point.x, point.y, 100, level)
                }

                val source = when (param) {
                    is BeamWeaponRay -> {
                        param.source
                    }

                    is Missile -> {
                        param.source
                    }

                    is DamagingProjectileAPI -> {
                        param.source
                    }

                    else -> {
                        target
                    }
                }

                parent.lastDamageTime = engine.getTotalElapsedTime(false)
                parent.lastDamageIsShield = true

                if (parent.shieldHealth < damageNumber) {
                    parent.dealShieldDamage(parent.shieldHealth, engine.getTotalElapsedTime(false))

                    damage?.modifier?.modifyMult(
                        "covenant_remnants_covenant_shields",
                        1f - parent.shieldHealth / damageNumber
                    )

                    engine.addFloatingDamageText(
                        point,
                        parent.shieldHealth,
                        MagicUI.BLUCOLOR,
                        attachedTo,
                        source
                    )
                } else {
                    parent.dealShieldDamage(damageNumber, engine.getTotalElapsedTime(false))

                    damage?.modifier?.modifyMult("covenant_remnants_covenant_shields", 0f)

                    engine.addFloatingDamageText(
                        point,
                        damageNumber,
                        MagicUI.BLUCOLOR,
                        attachedTo,
                        source
                    )
                }
            }

            return modifierKey
        }
    }

    fun dealShieldDamage(amount: Float, time: Float) {
        this.damageInstances.add(listOf(amount, time))
        this.shieldHealth -= abs(amount)
        this.lastDamageToShield = abs(amount)
    }

    fun shouldRegen(): Boolean {
        if (breakCooldownActive) {
            return false
        }

        if (damageInstances.isEmpty()) {
            return true
        }

        val checkTime = Global.getCombatEngine().getTotalElapsedTime(false) + shieldDamageRechargeCooldown

        damageInstances = damageInstances.mapNotNull {
            if (it[1] <= checkTime) null else it
        }.toMutableList()

        if (damageInstances.isEmpty()) {
            return true
        }

        var sum = 0f

        damageInstances.forEach { sum += it[0] }

        return sum <= maxShieldHealth * 0.05f
    }

    private fun getShield(ship: ShipAPI?): ShipSystemAPI {
        val system = ship!!.phaseCloak
        return if (system != null && system.id == "covenant_shield") system else ship.system
    }

}
