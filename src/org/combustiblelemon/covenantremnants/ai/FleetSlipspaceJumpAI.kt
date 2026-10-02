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
 * Developer design 2026-10-01, extended 2026-10-02; every threshold lives in [FleetSlipspaceJump].
 *
 * Checked every AI_INTERVAL while the system is ready, in order:
 *
 * 1. **Pull the fight home.** Enemy strength inside the grab radius is a real threat and an allied
 *    group at least MIN_RANGE away can take it (its strength plus what we carry ≥ PULL_MARGIN ×
 *    enemies there plus the enemies we carry). Lands beside the group on the side away from its
 *    enemies, so the carried enemies arrive inside allied fire.
 * 2. **Strand enemy heavies.** No capable allied group; slow enemy cruisers/capitals are inside
 *    the radius; any allied ships inside are frigates/destroyers only; and the carrier should
 *    survive the cooldown with them (their sustained DPS over the exposure vs. shield + regen share
 *    + armor + spare hull), or it can out-run every one of them. Jumps to the in-map point farthest
 *    from everyone staying behind. Afterwards it backs off until it is clear of them and never
 *    strands the same fight twice in a row.
 * 3. **Reinforce** (2026-10-02; only with no enemy ships inside the radius). An allied group is
 *    losing (its strength < REINFORCE_NEED × the enemies there, enemies ≥ REINFORCE_MIN_THREAT) and
 *    what we carry turns it (passes the PULL_MARGIN test); the most desperate one wins. Lands on
 *    the enemies' far side (pincer) if the carrier would survive the enemies around that spot
 *    (the strand survival test), else beside the allies facing the enemies, to draw fire.
 * 4. **Hunt** (2026-10-02; only with no enemy ships inside the radius). An enemy group that is
 *    weakened (≥ HUNT_MIN_WEAKNESS of its full strength lost to hull and flux) or holds a crippled
 *    ship (hull ≤ HUNT_CRIPPLED_HULL or flux ≥ HUNT_CRIPPLED_FLUX), worth ≥ HUNT_MIN_VALUE, and
 *    beatable (PULL_MARGIN test with the allies already there). Score = full strength × (weakness +
 *    HUNT_CRIPPLED_BONUS per crippled ship). Lands beside the target, away from the rest of its group.
 * 5. **Regroup.** No enemy ships inside the radius and the nearest capable allied group is farther
 *    than REGROUP_DISTANCE: jump beside it.
 *
 * Strength = deployment cost (supplies to recover) × hull level × (1 − flux × FLUX_PRESSURE), both
 * sides; overloaded or venting ships count as no flux, since that is over by the time the jump lands
 * (developer 2026-10-02). Fighters count FIGHTER_STRENGTH.
 *
 * Stations (2026-10-02): a friendly station (the station itself; its modules recover 0 supplies)
 * is an ally — an anchor for pull-home, reinforce and regroup, and strength in its group; an enemy
 * station counts against any group it sits in, so the carrier lands next to one only when the
 * allies there overwhelm it. Stations are never carried.
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

    private enum class Plan { PULL_HOME, STRAND, REINFORCE, HUNT, REGROUP }

    private class Situation(val carrier: ShipAPI, engine: CombatEngineAPI) {
        /** The grab circle's centre: the portal off the current bow. */
        val center: Vector2f = FleetSlipspaceJump.portalCenter(carrier)
        val radius = FleetSlipspaceJump.grabRadius.value
        val combatants: List<ShipAPI> = engine.ships.filter { it !== carrier && FleetSlipspaceJump.isCombatant(it) }
        val allies: List<ShipAPI> = combatants.filter { it.owner == carrier.owner }
        val enemies: List<ShipAPI> = combatants.filter { it.owner != carrier.owner && it.owner != 100 }
        val alliesInRadius: List<ShipAPI> = allies.filter { FleetSlipspaceJump.isWithin(it.location, center, radius) }
        val enemiesInRadius: List<ShipAPI> = enemies.filter { FleetSlipspaceJump.isWithin(it.location, center, radius) }

        private val stations: List<ShipAPI> = engine.ships.filter {
            it.isStation && it.parentStation == null && it.isAlive && !it.isHulk
        }

        /** Allies a group can form around or count: non-fighter allied ships plus friendly stations. */
        val anchors: List<ShipAPI> = allies.filter { !it.isFighter } + stations.filter { it.owner == carrier.owner }

        /** Everything hostile a group counts: enemy ships (fighters included) plus enemy stations. */
        val foes: List<ShipAPI> = enemies + stations.filter { it.owner != carrier.owner && it.owner != 100 }

        /** Whether [other] rides the jump if it fires now (stations never do). */
        fun isCarried(other: ShipAPI): Boolean = !other.isStation && FleetSlipspaceJump.isWithin(other.location, center, radius)

        /** Strength at full hull and no flux. */
        fun fullStrength(ship: ShipAPI): Float =
            if (ship.isFighter) FleetSlipspaceJump.fighterStrength.value else ship.hullSpec.suppliesToRecover

        /** Strength now: hull and flux discounted; overload/venting is over by the time the jump lands. */
        fun strength(ship: ShipAPI): Float {
            if (ship.isFighter) return FleetSlipspaceJump.fighterStrength.value
            val hull = fullStrength(ship) * ship.hullLevel.coerceAtLeast(0.1f)
            if (ship.fluxTracker.isOverloadedOrVenting) return hull
            return hull * (1f - ship.fluxLevel * FleetSlipspaceJump.fluxPressure.value).coerceAtLeast(0f)
        }

        fun strength(ships: List<ShipAPI>): Float = ships.fold(0f) { sum, s -> sum + strength(s) }

        /** What the carrier itself brings to a fight. */
        fun carrierStrength(): Float = strength(carrier)

        /** Allied strength (ships and stations) within CLUSTER_RADIUS of [point], not coming along. */
        fun alliedAt(point: Vector2f): Float =
            strength(near(anchors, point).filter { !isCarried(it) })

        /** Hostile ships and stations within CLUSTER_RADIUS of [point], not coming along. */
        fun foesAt(point: Vector2f): List<ShipAPI> = near(foes, point).filter { !isCarried(it) }

        fun near(ships: List<ShipAPI>, point: Vector2f): List<ShipAPI> {
            val clusterRadius = FleetSlipspaceJump.clusterRadius.value
            return ships.filter { Misc.getDistance(it.location, point) <= clusterRadius }
        }
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
        planReinforce(situation)?.let { fire(it, Plan.REINFORCE); return }
        planHunt(situation)?.let { fire(it, Plan.HUNT); return }
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
        for (anchor in s.anchors) {
            if (Misc.getDistance(anchor.location, s.center) < FleetSlipspaceJump.minRange.value) continue
            val surplus = clusterSurplus(s, anchor.location, carriedAllied, threat) ?: continue
            if (bestAnchor == null || surplus > bestSurplus) {
                bestAnchor = anchor
                bestSurplus = surplus
            }
        }
        val anchor = bestAnchor ?: return null
        return landingBeside(s, anchor, awayFromFoes = true)
    }

    /**
     * Allied strength at [point] (ships and friendly stations within CLUSTER_RADIUS that are not
     * coming along) plus what we carry, minus PULL_MARGIN × (hostiles there + enemies carried).
     * Null when the group cannot take it.
     */
    private fun clusterSurplus(s: Situation, point: Vector2f, carriedAllied: Float, carriedEnemy: Float): Float? {
        val enemy = s.strength(s.foesAt(point))
        val surplus = s.alliedAt(point) + carriedAllied - FleetSlipspaceJump.pullMargin.value * (enemy + carriedEnemy)
        return if (surplus >= 0f) surplus else null
    }

    /**
     * A valid landing spot RADIUS + the anchor's radius + 100 from [anchor]: on its side away from
     * the hostiles around it ([awayFromFoes]), or facing them.
     */
    private fun landingBeside(s: Situation, anchor: ShipAPI, awayFromFoes: Boolean): Vector2f? {
        val foes = s.foesAt(anchor.location).filter { it !== anchor }
        val baseAngle = if (foes.isNotEmpty()) {
            val foeCentroid = centroid(foes)
            if (awayFromFoes) Misc.getAngleInDegrees(foeCentroid, anchor.location)
            else Misc.getAngleInDegrees(anchor.location, foeCentroid)
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

    private fun centroid(ships: List<ShipAPI>): Vector2f {
        val c = Vector2f()
        for (other in ships) c.translate(other.location.x, other.location.y)
        c.scale(1f / ships.size)
        return c
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

    /**
     * Sustained hostile DPS (station modules' weapons included) over the exposure (cooldown +
     * arrival), discounted, vs. effective HP.
     */
    private fun survivesWith(hostiles: List<ShipAPI>): Boolean {
        var dps = 0f
        for (foe in hostiles) {
            dps += sustainedDps(foe)
            for (module in foe.childModulesCopy) dps += sustainedDps(module)
        }
        val exposure = FleetSlipspaceJump.cooldown.value + FleetSlipspaceJump.arrival.value
        val incoming = dps * exposure * FleetSlipspaceJump.hitFraction.value
        return incoming <= effectiveHp(exposure) / FleetSlipspaceJump.survivalSafety.value
    }

    private fun sustainedDps(other: ShipAPI): Float {
        var dps = 0f
        for (weapon in other.allWeapons) {
            if (weapon.isDecorative) continue
            dps += weapon.derivedStats?.sustainedDps ?: 0f
        }
        return dps
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

    // --- 3. Reinforce ------------------------------------------------------------------------

    private fun planReinforce(s: Situation): Vector2f? {
        if (s.enemiesInRadius.any { !it.isFighter }) return null

        // Only enemy fighters can be in the radius here; they come along.
        val carriedAllied = s.strength(s.alliesInRadius) + s.carrierStrength()
        val carriedEnemy = s.strength(s.enemiesInRadius)
        var bestAnchor: ShipAPI? = null
        var bestRatio = Float.MAX_VALUE
        for (anchor in s.anchors) {
            if (Misc.getDistance(anchor.location, s.center) < FleetSlipspaceJump.minRange.value) continue
            val enemy = s.strength(s.foesAt(anchor.location))
            if (enemy < FleetSlipspaceJump.reinforceMinThreat.value) continue
            val ratio = s.alliedAt(anchor.location) / enemy
            if (ratio >= FleetSlipspaceJump.reinforceNeed.value) continue
            if (clusterSurplus(s, anchor.location, carriedAllied, carriedEnemy) == null) continue
            if (ratio < bestRatio) {
                bestAnchor = anchor
                bestRatio = ratio
            }
        }
        val anchor = bestAnchor ?: return null
        return pincerLanding(s, anchor) ?: landingBeside(s, anchor, awayFromFoes = false)
    }

    /**
     * On the far side of the hostiles pressing [anchor]'s group, RADIUS + 100 beyond their centroid
     * (a few bearings around the line from the allies through them), if the carrier would survive
     * the hostiles within CLUSTER_RADIUS of the spot. Null if no such spot.
     */
    private fun pincerLanding(s: Situation, anchor: ShipAPI): Vector2f? {
        val foes = s.foesAt(anchor.location).filter { !it.isFighter }
        if (foes.isEmpty()) return null
        val foeCentroid = centroid(foes)
        val alliedCentroid = centroid(s.near(s.anchors, anchor.location).filter { !s.isCarried(it) }.ifEmpty { listOf(anchor) })
        val baseAngle = Misc.getAngleInDegrees(alliedCentroid, foeCentroid)

        val distance = s.radius + 100f
        for (offset in floatArrayOf(0f, 30f, -30f, 60f, -60f)) {
            val dir = Misc.getUnitVectorAtDegreeAngle(baseAngle + offset)
            val destination = Vector2f(foeCentroid.x + dir.x * distance, foeCentroid.y + dir.y * distance)
            if (FleetSlipspaceJump.destinationProblem(ship, destination, engine) != null) continue
            if (!survivesWith(s.foesAt(destination).filter { !it.isFighter })) continue
            return destination
        }
        return null
    }

    // --- 4. Hunt -----------------------------------------------------------------------------

    private fun planHunt(s: Situation): Vector2f? {
        if (s.enemiesInRadius.any { !it.isFighter }) return null

        val carriedAllied = s.strength(s.alliesInRadius) + s.carrierStrength()
        val carriedEnemy = s.strength(s.enemiesInRadius)
        var bestTarget: ShipAPI? = null
        var bestScore = 0f
        for (target in s.enemies) {
            if (target.isFighter) continue
            if (Misc.getDistance(target.location, s.center) < FleetSlipspaceJump.minRange.value) continue
            val group = s.foesAt(target.location)
            val full = group.fold(0f) { sum, it -> sum + s.fullStrength(it) }
            if (full < FleetSlipspaceJump.huntMinValue.value) continue
            val weakness = 1f - s.strength(group) / full
            val crippled = group.count { isCrippled(it) }
            if (weakness < FleetSlipspaceJump.huntMinWeakness.value && crippled == 0) continue
            if (clusterSurplus(s, target.location, carriedAllied, carriedEnemy) == null) continue
            val score = full * (weakness + FleetSlipspaceJump.huntCrippledBonus.value * crippled)
            if (bestTarget == null || score > bestScore) {
                bestTarget = target
                bestScore = score
            }
        }
        val target = bestTarget ?: return null
        return landingBeside(s, target, awayFromFoes = true)
    }

    /** Hull or flux low enough to finish off; overload/venting does not count (over by arrival). */
    private fun isCrippled(other: ShipAPI): Boolean {
        if (other.isFighter || other.isStation) return false
        if (other.hullLevel <= FleetSlipspaceJump.huntCrippledHull.value) return true
        return !other.fluxTracker.isOverloadedOrVenting && other.fluxLevel >= FleetSlipspaceJump.huntCrippledFlux.value
    }

    // --- 5. Regroup --------------------------------------------------------------------------

    private fun planRegroup(s: Situation): Vector2f? {
        if (s.enemiesInRadius.any { !it.isFighter }) return null

        // Distance to the fleet is measured from the carrier itself, not from its portal.
        val anchors = s.anchors
        val nearest = anchors.minByOrNull { Misc.getDistance(it.location, ship.location) } ?: return null
        if (Misc.getDistance(nearest.location, ship.location) <= FleetSlipspaceJump.regroupDistance.value) return null

        // Only enemy fighters can be in the radius here; they come along.
        val carriedAllied = s.strength(s.alliesInRadius) + s.carrierStrength()
        val carriedEnemy = s.strength(s.enemiesInRadius)
        val candidates = anchors
            .filter { Misc.getDistance(it.location, s.center) >= FleetSlipspaceJump.minRange.value }
            .filter { clusterSurplus(s, it.location, carriedAllied, carriedEnemy) != null }
            .sortedBy { Misc.getDistance(it.location, s.center) }
        for (anchor in candidates) {
            landingBeside(s, anchor, awayFromFoes = true)?.let { return it }
        }
        return null
    }
}
