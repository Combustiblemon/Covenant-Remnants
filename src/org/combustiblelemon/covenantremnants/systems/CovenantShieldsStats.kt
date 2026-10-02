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
import org.combustiblelemon.covenantremnants.utils.*
import org.combustiblelemon.lemonlib.geometry.getPointAlongSegmentFromEnd
import org.combustiblelemon.lemonlib.geometry.hasPerpendicularIntersectionPoint
import org.combustiblelemon.lemonlib.geometry.rotatePointByDegrees
import org.combustiblelemon.lemonlib.ship.getShipDimensions
import org.lwjgl.util.vector.Vector2f
import org.magiclib.util.MagicUI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class CovenantShieldsStats : BaseShipSystemScript() {
    companion object {
        // After the regen delay ends, regen runs at REGEN_SLOW_MULT for REGEN_SLOW_DURATION seconds
        const val REGEN_SLOW_MULT = 0.5f
        const val REGEN_SLOW_DURATION = 3f
    }

    private var statusKey = Any()
    private val regenStatusKey = Any()
    private var renderPlugin: CovenantShieldsRenderPlugin? = null
    private var UIRenderPlugin: CovenantShieldsUIRenderPlugin? = null
    private var damageListener: ShieldDamageListener? = null
    private var damageConverter: ShieldDamageConverter? = null
    private var shipListenerInstance: ShipListener? = null
    var ship: ShipAPI? = null
    var shipDimensions: Array<Vector2f>? = null
    var shieldStats: ShieldData = generateDefaultShieldData()

    private var shieldRegen: Float = 1250f

    // Any shield hit pauses regen for the hull's shield regen delay (shield_data.csv), then regen
    // runs at REGEN_SLOW_MULT for REGEN_SLOW_DURATION seconds before returning to full rate.
    private var lastShieldDamageTime: Float = Float.NEGATIVE_INFINITY

    // Fortress Shield (see FortressShield): its damage reduction reaches the shield through
    // stats.shieldDamageTakenMult (honoured in ShieldDamageListener); ShipListener freezes regen
    // while it is active and gates it on the shield being up.
    private var wasFortressActive = false

    // Regen paused -> running edge (post-hit pause ending, Fortress ending): fires the recharge wave.
    private var wasRegenActive = false

    var shieldDamageMult: Float = 0.3f
    var breakCooldownTime = 9f

    var shieldCooldown: Float = 1f
        set(value) {
            UIRenderPlugin?.shieldCooldown = value
            field = value
        }

    var lastDamageTime: Float = 0f
    var lastDamageIsShield: Boolean = false
    var lastDamageToShield: Float = 0f
    var breakCooldownStartTime = 0f

    var shieldInit: Boolean = false

    // The stats script instance may outlive a single battle; used to detect a new combat and
    // drop per-battle state (plugins, listeners, shield state) so everything re-initializes.
    private var combatEngine: CombatEngineAPI? = null

    var maxShieldHealth: Float = 1f
        private set(value) {
            if (shieldHealth > maxShieldHealth) {
                shieldHealth = maxShieldHealth
            }
            field = value
        }
    var shieldHealth: Float = maxShieldHealth
        private set(value) {
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

    private class ShipListener(private val ship: ShipAPI, private val parent: CovenantShieldsStats) :
        AdvanceableListener {

        override fun advance(amount: Float) {
            val elapsedTime = Global.getCombatEngine().getTotalElapsedTime(false)

            // Fortress Shield is only usable while the Covenant shield is up; dropping or breaking
            // the shield forces it off.
            val fortress = FortressShield.system(ship)
            if (fortress != null && (ship.phaseCloak?.isOn != true || parent.breakCooldownActive)) {
                ship.blockCommandForOneFrame(ShipCommand.USE_SYSTEM)
                if (fortress.isOn) {
                    fortress.deactivate()
                }
            }

            // No regen while Fortress is active (incl. charge-up/down); once it is fully off, regen
            // resumes at full rate, skipping the post-hit pause and the slow stage.
            val fortressActive = FortressShield.effectLevel(ship) > 0f
            if (parent.wasFortressActive && !fortressActive) {
                parent.lastShieldDamageTime = Float.NEGATIVE_INFINITY
            }
            parent.wasFortressActive = fortressActive

            if (parent.breakCooldownActive) {
                val remainingTime = parent.breakCooldownStartTime + parent.breakCooldownTime - elapsedTime


                if (remainingTime <= 0) {
                    parent.breakCooldownActive = false
                    parent.shieldCooldown = parent.shieldStats.shieldRegenDelay

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
            } else if (!fortressActive) {
                if ((parent.shieldHealth < parent.maxShieldHealth)) {
                    parent.shieldHealth += parent.shieldRegen * parent.regenMult() * amount
                }
            }

            // Recharge wave whenever regen goes from paused to running while the shield is visible.
            // Edge-tracked independent of visibility, so raising the shield mid-regen fires nothing;
            // the post-break wave is the render plugin's (fires once the activation sweep completes).
            val regenActive = !parent.breakCooldownActive && !fortressActive &&
                    parent.shieldHealth < parent.maxShieldHealth && parent.regenMult() > 0f
            if (regenActive && !parent.wasRegenActive && (ship.phaseCloak?.effectLevel ?: 0f) > 0f) {
                parent.renderPlugin?.triggerRechargeWave()
            }
            parent.wasRegenActive = regenActive

            parent.maintainRegenStatus(ship, fortressActive)
            parent.updateRenderers()
        }
    }

    override fun isUsable(system: ShipSystemAPI?, ship: ShipAPI?): Boolean {
        return shieldCooldown >= shieldStats.shieldRegenDelay
    }

    override fun apply(stats: MutableShipStatsAPI, id: String, state: ShipSystemStatsScript.State, effLevel: Float) {
        var _ship: ShipAPI?
        var player: Boolean

        if (stats.entity is ShipAPI) {
            _ship = stats.entity as ShipAPI
            player = _ship === Global.getCombatEngine().playerShip
            ship = _ship
            shipDimensions = getShipDimensions(_ship)

            val engine = Global.getCombatEngine()
            if (combatEngine !== engine) {
                combatEngine = engine
                renderPlugin = null
                UIRenderPlugin = null
                damageListener = null
                damageConverter = null
                shipListenerInstance = null
                lastShieldDamageTime = Float.NEGATIVE_INFINITY
                wasFortressActive = false
                wasRegenActive = false
                breakCooldownActive = false
                shieldInit = false
            }

            if (!shieldInit) {
                shieldInit = true
                shieldStats = shieldData.getOrElse(ship?.hullSpec?.baseHullId ?: "", { generateDefaultShieldData() })
                shieldRegen = shieldStats.shieldRegenRate
                breakCooldownTime = shieldStats.brokenShieldCooldown
                maxShieldHealth = shieldStats.shieldHealth
                shieldHealth = shieldStats.shieldHealth
                shieldCooldown = shieldStats.shieldRegenDelay
            }
        } else {
            return
        }

        if (renderPlugin == null) {
            renderPlugin = CovenantShieldsRenderPlugin(_ship, this)
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

            // Consume the shield hit recorded by ShieldDamageListener for this damage instance, so
            // later non-shield hits are not reported as shield damage.
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
                    getPointAlongSegmentFromEnd(
                        listOf(
                            rotatePointByDegrees(
                                Vector2f(
                                    parent.shipDimensions!![1].x + attachedTo.location.x,
                                    parent.shipDimensions!![0].y + attachedTo.location.y
                                ), attachedTo.location, attachedTo.facing
                            ), rotatePointByDegrees(
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
                    hasPerpendicularIntersectionPoint(
                        listOf(
                            rotatePointByDegrees(
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

                // Beam damage is DPS, applied in ticks of dpsDuration seconds.
                val rawDamage = when {
                    damage == null -> 0f
                    damage.isDps -> damage.damage * damage.dpsDuration
                    else -> damage.damage
                }
                // Every source damages the shield equally: no damage-type multipliers, no per-type
                // shield stats. Only the general shieldDamageTakenMult applies (Fortress Shield,
                // skills, CR malus, ...).
                val damageNumber = rawDamage * parent.shieldDamageMult *
                        attachedTo.mutableStats.shieldDamageTakenMult.modifiedValue

                // Hit flare strength (Reach: every hit flares; the plugin floors weak ones)
                var level = (damageNumber - 30f) / (300f - 30f)
                level = max(0f, min(1f, level))

                if (!parent.breakCooldownActive) {
                    parent.renderPlugin?.triggerShieldHit(point.x, point.y, level)
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
                    // Capture before dealing damage: the breaking hit zeroes shieldHealth.
                    val absorbed = parent.shieldHealth
                    parent.dealShieldDamage(absorbed, engine.getTotalElapsedTime(false))

                    damage?.modifier?.modifyMult(
                        "covenant_remnants_covenant_shields",
                        1f - absorbed / damageNumber
                    )

                    engine.addFloatingDamageText(
                        point,
                        absorbed,
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
        this.lastShieldDamageTime = time
        this.shieldHealth -= abs(amount)
        this.lastDamageToShield = abs(amount)
    }

    /**
     * Player ship only, while the shield is below full and not broken: a status line naming the
     * regen state (developer 2026-10-02) - frozen under Fortress Shield, paused after a hit, the
     * slow stage, or full rate.
     */
    private fun maintainRegenStatus(ship: ShipAPI, fortressActive: Boolean) {
        val engine = Global.getCombatEngine()
        if (ship !== engine.playerShip || breakCooldownActive || shieldHealth >= maxShieldHealth) return
        val mult = regenMult()
        val (state, isDebuff) = when {
            fortressActive -> "frozen by Fortress Shield" to true
            mult <= 0f -> "paused" to true
            mult < 1f -> "${(mult * 100f).roundToInt()}% rate" to true
            else -> "full rate" to false
        }
        engine.maintainStatusForPlayerShip(regenStatusKey, getShield(ship).specAPI.iconSpriteName, "Shield regen", state, isDebuff)
    }

    fun regenMult(): Float {
        val sinceHit = Global.getCombatEngine().getTotalElapsedTime(false) - lastShieldDamageTime
        return when {
            sinceHit < shieldStats.shieldRegenDelay -> 0f
            sinceHit < shieldStats.shieldRegenDelay + REGEN_SLOW_DURATION -> REGEN_SLOW_MULT
            else -> 1f
        }
    }

    private fun getShield(ship: ShipAPI?): ShipSystemAPI {
        val system = ship!!.phaseCloak
        return if (system != null && system.id == "covenant_remnants_covenantshield") system else ship.system
    }

}
