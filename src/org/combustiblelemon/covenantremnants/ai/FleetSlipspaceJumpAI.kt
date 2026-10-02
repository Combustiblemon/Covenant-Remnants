package org.combustiblelemon.covenantremnants.ai

import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ShipCommand
import com.fs.starfarer.api.combat.ShipSystemAIScript
import com.fs.starfarer.api.combat.ShipSystemAPI
import com.fs.starfarer.api.combat.ShipwideAIFlags
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags
import com.fs.starfarer.api.util.IntervalUtil
import com.fs.starfarer.api.util.Misc
import org.combustiblelemon.covenantremnants.systems.CovenantShieldsStats
import org.combustiblelemon.covenantremnants.systems.FleetSlipspaceJump
import org.lwjgl.util.vector.Vector2f
import kotlin.math.max

/**
 * AI for the Fleet Slipspace Jump (custom system AI, runs only while the carrier is AI-controlled).
 * Developer design 2026-10-01; every threshold lives in [FleetSlipspaceJump].
 *
 * Checked every AI_INTERVAL while the system is ready, in order:
 *
 * 1. **Pull the fight home.** Enemy strength inside the grab radius is a real threat and an allied
 *    cluster at least MIN_RANGE away can take it (its effective strength, pressured by flux, plus
 *    what we carry ≥ PULL_MARGIN × enemies there plus the enemies we carry). Lands beside the
 *    cluster on the side away from its enemies, so the carried enemies arrive inside allied fire.
 * 2. **Strand enemy heavies.** No capable allied cluster; slow enemy cruisers/capitals are inside
 *    the radius; any allied ships inside are frigates/destroyers only; and the carrier should
 *    survive the cooldown with them (their sustained DPS over the exposure vs. shield + regen share
 *    + armor + spare hull), or it can out-run every one of them. Jumps to the in-map point farthest
 *    from everyone staying behind. Afterwards it backs off until it is clear of them and never
 *    strands the same fight twice in a row.
 * 3. **Regroup.** No enemy ships inside the radius and the nearest capable allied cluster is farther
 *    than REGROUP_DISTANCE: jump beside it.
 *
 * Strength = deployment cost (supplies to recover) × hull level; fighters count FIGHTER_STRENGTH.
 *
 * "Inside the radius" means inside the grab circle around the portal off the carrier's current bow
 * ([FleetSlipspaceJump.portalCenter]); the AI does not turn to aim it (developer 2026-10-02).
 */
class FleetSlipspaceJumpAI : ShipSystemAIScript {
    private lateinit var ship: ShipAPI
    private lateinit var system: ShipSystemAPI
    private lateinit var flags: ShipwideAIFlags
    private lateinit var engine: CombatEngineAPI

    private val interval = IntervalUtil(
        FleetSlipspaceJump.aiInterval.value * 0.8f,
        FleetSlipspaceJump.aiInterval.value * 1.2f
    )

    // Set after a strand jump: back off from the stranded enemies and do not strand again until
    // clear of them.
    private var stranded = false

    private enum class Plan { PULL_HOME, STRAND, REGROUP }

    private class Situation(val carrier: ShipAPI, engine: CombatEngineAPI) {
        /** The grab circle's centre: the portal off the current bow. */
        val center: Vector2f = FleetSlipspaceJump.portalCenter(carrier)
        val radius = FleetSlipspaceJump.grabRadius.value
        val combatants: List<ShipAPI> = engine.ships.filter { it !== carrier && FleetSlipspaceJump.isCombatant(it) }
        val allies: List<ShipAPI> = combatants.filter { it.owner == carrier.owner }
        val enemies: List<ShipAPI> = combatants.filter { it.owner != carrier.owner && it.owner != 100 }
        val alliesInRadius: List<ShipAPI> = allies.filter { FleetSlipspaceJump.isWithin(it.location, center, radius) }
        val enemiesInRadius: List<ShipAPI> = enemies.filter { FleetSlipspaceJump.isWithin(it.location, center, radius) }

        fun strength(ship: ShipAPI): Float =
            if (ship.isFighter) FleetSlipspaceJump.fighterStrength.value
            else ship.hullSpec.suppliesToRecover * ship.hullLevel.coerceAtLeast(0.1f)

        fun strength(ships: List<ShipAPI>): Float = ships.fold(0f) { sum, s -> sum + strength(s) }

        /** What the carrier itself brings to a fight. */
        fun carrierStrength(): Float = carrier.hullSpec.suppliesToRecover * carrier.hullLevel.coerceAtLeast(0.1f)
    }

    override fun init(ship: ShipAPI, system: ShipSystemAPI, flags: ShipwideAIFlags, engine: CombatEngineAPI) {
        this.ship = ship
        this.system = system
        this.flags = flags
        this.engine = engine
    }

    override fun advance(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {
        if (!ship.isAlive) return
        FleetSlipspaceJump.resetIfNewCombat(engine)

        if (stranded) maintainBackOff()

        interval.advance(amount)
        if (!interval.intervalElapsed()) return

        // VISUAL_ONLY (temporary): the system jumps nothing, so the AI leaves it alone.
        if (FleetSlipspaceJump.isVisualOnly) return

        if (system.state != ShipSystemAPI.SystemState.IDLE || system.isCoolingDown) return
        if (ship.fluxTracker.isOverloadedOrVenting) return
        if (ship.fluxLevel >= FleetSlipspaceJump.fluxGate.value) return

        val situation = Situation(ship, engine)
        planPullHome(situation)?.let { fire(it, Plan.PULL_HOME); return }
        planStrand(situation)?.let { fire(it, Plan.STRAND); return }
        planRegroup(situation)?.let { fire(it, Plan.REGROUP); return }
    }

    private fun fire(destination: Vector2f, plan: Plan) {
        FleetSlipspaceJump.setPendingDestination(ship, destination, engine)
        ship.giveCommand(ShipCommand.USE_SYSTEM, destination, 0)
        stranded = plan == Plan.STRAND
        FleetSlipspaceJump.log("${ship.hullSpec.hullName} fleet jump: $plan to (${destination.x.toInt()}, ${destination.y.toInt()})")
    }

    // --- 1. Pull the fight home --------------------------------------------------------------

    private fun planPullHome(s: Situation): Vector2f? {
        val threat = s.strength(s.enemiesInRadius)
        if (threat < FleetSlipspaceJump.pullMinThreat.value) return null

        val carriedAllied = s.strength(s.alliesInRadius) + s.carrierStrength()
        var bestAnchor: ShipAPI? = null
        var bestSurplus = 0f
        for (anchor in s.allies) {
            if (anchor.isFighter) continue
            if (Misc.getDistance(anchor.location, s.center) < FleetSlipspaceJump.minRange.value) continue
            val surplus = clusterSurplus(s, anchor, carriedAllied, threat) ?: continue
            if (bestAnchor == null || surplus > bestSurplus) {
                bestAnchor = anchor
                bestSurplus = surplus
            }
        }
        val anchor = bestAnchor ?: return null
        return landingBeside(s, anchor)
    }

    /**
     * Allied strength at [anchor] (ships within CLUSTER_RADIUS that are not coming along, each
     * discounted by flux) plus what we carry, minus PULL_MARGIN × (enemies there + enemies carried).
     * Null when the cluster cannot take it.
     */
    private fun clusterSurplus(s: Situation, anchor: ShipAPI, carriedAllied: Float, carriedEnemy: Float): Float? {
        val clusterRadius = FleetSlipspaceJump.clusterRadius.value
        var allied = 0f
        for (ally in s.allies) {
            if (ally.isFighter) continue
            if (Misc.getDistance(ally.location, anchor.location) > clusterRadius) continue
            if (FleetSlipspaceJump.isWithin(ally.location, s.center, s.radius)) continue
            allied += s.strength(ally) * (1f - ally.fluxLevel * FleetSlipspaceJump.fluxPressure.value).coerceAtLeast(0f)
        }
        var enemy = 0f
        for (foe in s.enemies) {
            if (Misc.getDistance(foe.location, anchor.location) > clusterRadius) continue
            if (FleetSlipspaceJump.isWithin(foe.location, s.center, s.radius)) continue
            enemy += s.strength(foe)
        }
        val surplus = allied + carriedAllied - FleetSlipspaceJump.pullMargin.value * (enemy + carriedEnemy)
        return if (surplus >= 0f) surplus else null
    }

    /** A valid landing spot beside [anchor], on its side away from nearby enemies. */
    private fun landingBeside(s: Situation, anchor: ShipAPI): Vector2f? {
        val clusterRadius = FleetSlipspaceJump.clusterRadius.value
        val enemyCentroid = Vector2f()
        var enemyCount = 0
        for (foe in s.enemies) {
            if (Misc.getDistance(foe.location, anchor.location) > clusterRadius) continue
            if (FleetSlipspaceJump.isWithin(foe.location, s.center, s.radius)) continue
            enemyCentroid.translate(foe.location.x, foe.location.y)
            enemyCount++
        }
        val baseAngle = if (enemyCount > 0) {
            enemyCentroid.scale(1f / enemyCount)
            Misc.getAngleInDegrees(enemyCentroid, anchor.location)
        } else {
            Misc.getAngleInDegrees(anchor.location, s.center)
        }

        val distance = s.radius + anchor.collisionRadius + 100f
        for (offset in floatArrayOf(0f, 45f, -45f, 90f, -90f, 135f, -135f, 180f)) {
            val dir = Misc.getUnitVectorAtDegreeAngle(baseAngle + offset)
            val destination = Vector2f(anchor.location.x + dir.x * distance, anchor.location.y + dir.y * distance)
            if (FleetSlipspaceJump.destinationProblem(ship, destination, engine) == null) return destination
        }
        return null
    }

    // --- 2. Strand enemy heavies -------------------------------------------------------------

    private fun planStrand(s: Situation): Vector2f? {
        if (stranded) return null

        val heavies = s.enemiesInRadius.filter {
            !it.isFighter &&
                    (it.hullSize == ShipAPI.HullSize.CRUISER || it.hullSize == ShipAPI.HullSize.CAPITAL_SHIP) &&
                    it.mutableStats.maxSpeed.modifiedValue <= FleetSlipspaceJump.strandMaxSpeed.value
        }
        if (s.strength(heavies) < FleetSlipspaceJump.strandMinStrength.value) return null

        // Only fast, nimble allies may come along for the ride
        val heavyAlly = s.alliesInRadius.any {
            !it.isFighter && it.hullSize != ShipAPI.HullSize.FRIGATE && it.hullSize != ShipAPI.HullSize.DESTROYER
        }
        if (heavyAlly) return null

        if (!survivesWith(s.enemiesInRadius) && !canOutrun(s.enemiesInRadius)) return null

        return farthestPoint(s)
    }

    /** Sustained enemy DPS over the exposure (cooldown + arrival), discounted, vs. effective HP. */
    private fun survivesWith(enemies: List<ShipAPI>): Boolean {
        var dps = 0f
        for (foe in enemies) {
            for (weapon in foe.allWeapons) {
                if (weapon.isDecorative) continue
                dps += weapon.derivedStats?.sustainedDps ?: 0f
            }
        }
        val exposure = FleetSlipspaceJump.cooldown.value + FleetSlipspaceJump.arrival.value
        val incoming = dps * exposure * FleetSlipspaceJump.hitFraction.value
        return incoming <= effectiveHp(exposure) / FleetSlipspaceJump.survivalSafety.value
    }

    private fun effectiveHp(exposure: Float): Float {
        val shieldStats = ship.phaseCloak?.script as? CovenantShieldsStats
        val shield = shieldStats?.shieldHealth ?: 0f
        val regen = (shieldStats?.shieldStats?.shieldRegenRate ?: 0f) * exposure * FleetSlipspaceJump.regenShare.value
        val armor = ship.hullSpec.armorRating * FleetSlipspaceJump.armorFactor.value
        val hull = max(0f, ship.hitpoints - ship.maxHitpoints * FleetSlipspaceJump.minHullAfter.value)
        return shield + regen + armor + hull
    }

    private fun canOutrun(enemies: List<ShipAPI>): Boolean {
        val ownSpeed = ship.mutableStats.maxSpeed.modifiedValue
        return enemies.filter { !it.isFighter }.all { it.mutableStats.maxSpeed.modifiedValue < ownSpeed }
    }

    /** The valid in-map point farthest from everyone who stays behind. */
    private fun farthestPoint(s: Situation): Vector2f? {
        val margin = s.radius + ship.collisionRadius
        val halfWidth = engine.mapWidth / 2f - margin
        val halfHeight = engine.mapHeight / 2f - margin
        if (halfWidth <= 0f || halfHeight <= 0f) return null

        val stayingBehind = s.combatants.filter { !FleetSlipspaceJump.isWithin(it.location, s.center, s.radius) }

        var best: Vector2f? = null
        var bestScore = -1f
        val columns = 9
        val rows = 7
        for (i in 0 until columns) {
            for (j in 0 until rows) {
                val candidate = Vector2f(
                    -halfWidth + 2f * halfWidth * i / (columns - 1),
                    -halfHeight + 2f * halfHeight * j / (rows - 1)
                )
                var score = Float.MAX_VALUE
                for (other in stayingBehind) {
                    score = minOf(score, Misc.getDistance(candidate, other.location))
                }
                if (stayingBehind.isEmpty()) score = Misc.getDistance(candidate, s.center)
                if (score <= bestScore) continue
                if (FleetSlipspaceJump.destinationProblem(ship, candidate, engine) != null) continue
                best = candidate
                bestScore = score
            }
        }
        return best
    }

    /** After a strand: keep away from the stranded enemies until clear of them. */
    private fun maintainBackOff() {
        val radius = FleetSlipspaceJump.grabRadius.value
        var enemyNear = false
        var enemyClose = false
        for (other in engine.ships) {
            if (!FleetSlipspaceJump.isCombatant(other) || other.isFighter) continue
            if (other.owner == ship.owner || other.owner == 100) continue
            val distance = Misc.getDistance(other.location, ship.location)
            if (distance <= radius * 1.5f) enemyNear = true
            if (distance <= radius * 2f) enemyClose = true
        }
        if (enemyNear) {
            flags.setFlag(AIFlags.BACK_OFF, 1f)
            flags.setFlag(AIFlags.DO_NOT_PURSUE, 1f)
        } else if (!enemyClose) {
            stranded = false
        }
    }

    // --- 3. Regroup --------------------------------------------------------------------------

    private fun planRegroup(s: Situation): Vector2f? {
        if (s.enemiesInRadius.any { !it.isFighter }) return null

        // Distance to the fleet is measured from the carrier itself, not from its portal.
        val anchors = s.allies.filter { !it.isFighter }
        val nearest = anchors.minByOrNull { Misc.getDistance(it.location, ship.location) } ?: return null
        if (Misc.getDistance(nearest.location, ship.location) <= FleetSlipspaceJump.regroupDistance.value) return null

        // Only enemy fighters can be in the radius here; they come along.
        val carriedAllied = s.strength(s.alliesInRadius) + s.carrierStrength()
        val carriedEnemy = s.strength(s.enemiesInRadius)
        val candidates = anchors
            .filter { Misc.getDistance(it.location, s.center) >= FleetSlipspaceJump.minRange.value }
            .filter { clusterSurplus(s, it, carriedAllied, carriedEnemy) != null }
            .sortedBy { Misc.getDistance(it.location, s.center) }
        for (anchor in candidates) {
            landingBeside(s, anchor)?.let { return it }
        }
        return null
    }
}
