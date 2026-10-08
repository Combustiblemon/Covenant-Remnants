package org.combustiblelemon.covenantremnants.campaign.abilities

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignFleetAPI
import com.fs.starfarer.api.campaign.PersistentUIDataAPI.AbilitySlotAPI
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.fleet.FleetMemberAPI
import com.fs.starfarer.api.impl.campaign.abilities.EmergencyBurnAbility
import com.fs.starfarer.api.impl.campaign.ids.Stats
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.impl.campaign.procgen.themes.BaseThemeGenerator
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.api.util.Misc.FleetMemberDamageLevel
import org.combustiblelemon.covenantremnants.campaign.systems.SLIPSPACE_POCKET_TAG
import org.combustiblelemon.covenantremnants.hullmods.SlipspaceDriveHullmod
import org.combustiblelemon.covenantremnants.hullmods.SlipspaceTuning
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Rules and numbers of the campaign Slipspace Jump (gating, costs, destinations, Tuning bonus).
 * Every number is a live-tunable placeholder until the tuning pass (developer 2026-10-02: tuning
 * waits until all gameplay systems are done): `SlipspaceJumpTune` (console) lists / sets / resets
 * them, same as `FleetJumpTune` for the combat jump. Values reset on game restart.
 */
object SlipspaceJumpRules {
    const val ABILITY_ID = "covenant_remnants_slipspace_jump"

    const val POCKET_BACKGROUND = "graphics/covenant_remnants/backgrounds/slipspace_bg.jpg"

    /** One live-tunable constant (console `SlipspaceJumpTune`). */
    class Tunable internal constructor(val name: String, val default: Float, val description: String) {
        var value: Float = default

        val isOn: Boolean get() = value >= 0.5f

        fun reset() {
            value = default
        }
    }

    private val registry = LinkedHashMap<String, Tunable>()

    private fun tune(name: String, default: Float, description: String): Tunable =
        Tunable(name, default, description).also { registry[name] = it }

    val tunables: Collection<Tunable> get() = registry.values

    fun tunable(name: String): Tunable? = registry[name.uppercase()]

    fun resetAll() {
        registry.values.forEach { it.reset() }
    }

    // --- Dev overrides ----------------------------------------------------------------------------

    /** 1 = ignore the slipspace-drive hullmod rule (any fleet may jump). Testing only. */
    val noShipRestriction = tune("NO_SHIP_RESTRICTION", 0f, "1 = ignore the slipspace-drive hullmod rule (any fleet may jump)")

    /** 1 = no fuel and no CR cost. Testing only. */
    val free = tune("FREE", 0f, "1 = no fuel cost, no CR hit")

    // --- Costs (vanilla transverse-jump analogs) --------------------------------------------------

    /** Fuel per light-year of hyperspace distance, as a multiple of the fleet's fuel/LY. */
    val fuelPerLyMult = tune("FUEL_PER_LY_MULT", 1f, "Fuel per light-year, × the fleet's fuel/LY")

    /** CR lost by every ship, as a fraction of its deployment cost (FractureJump: 0.1). */
    val crCostMult = tune("CR_COST_MULT", 0.1f, "CR lost per ship, fraction of its deployment cost")

    /** Multiplier on the csv charge-up time (activationDays 0.4 d). */
    val chargeTimeMult = tune("CHARGE_TIME_MULT", 1f, "× the charge-up time (csv activationDays)")

    /** Multiplier on the csv cooldown (deactivationCooldown 0.5 d). */
    val cooldownMult = tune("COOLDOWN_MULT", 1f, "× the cooldown (csv deactivationCooldown)")

    // --- Timings (seconds, real time) -----------------------------------------------------------

    val brakeSeconds = tune("BRAKE", 0.5f, "Velocity bled off over this long at the start of the charge, s")
    val lurchSeconds = tune("LURCH", 2f, "Lurch from the hold point into the portal, s")
    val exitLurchSeconds = tune("EXIT_LURCH", 1f, "Lurch into the pocket exit, s")
    val warpOutHoldSeconds = tune("WARP_OUT_HOLD", 0.6f, "Hold after the fleet vanishes (collapse shows) before the screen fades, s")
    val warpInHoldSeconds = tune("WARP_IN_HOLD", 0.2f, "Hold at the destination before the exit flash reveals the fleet, s")
    val fadeSeconds = tune("FADE", 0.25f, "Screen fade in / out around the location switch, s")
    val warpInSeconds = tune("WARP_IN", 1.2f, "Fleet member views fade back in over this long after arriving, s")

    // --- Portal geometry --------------------------------------------------------------------------

    val portalAheadRadii = tune("PORTAL_AHEAD", 2f, "Portal centre ahead of the fleet, in fleet radii")
    val turnRate = tune("TURN_RATE", 120f, "Turn toward the portal while charging, degrees/s")
    val ruptureRadiusMult = tune("RUPTURE_RADIUS_MULT", 1.5f, "Rupture radius = fleet radius × this")
    val ruptureRadiusMin = tune("RUPTURE_RADIUS_MIN", 40f, "Rupture radius floor, su")

    // --- Pocket path ------------------------------------------------------------------------------

    val pathLengthMin = tune("PATH_LENGTH_MIN", 6000f, "Shortest pocket path, su (new pairs only)")
    val pathLengthMax = tune("PATH_LENGTH_MAX", 14000f, "Longest pocket path, su (new pairs only)")
    val pathPointSpacing = tune("PATH_SPACING", 100f, "Path point spacing, su")
    val pathWindMaxDegrees = tune("PATH_WIND", 75f, "Heading-wobble budget of the three sine waves, degrees (< 90)")

    val streamWidth = tune("STREAM_WIDTH", 768f, "Path width, su")
    val streamEdgeWidth = tune("STREAM_EDGE", 256f, "Path edge width, su")
    val streamBurnLevel = tune("STREAM_BURN", 30f, "Current strength, burn level")
    val streamAccelMult = tune("STREAM_ACCEL", 1f, "Current acceleration multiplier")
    val streamTaper = tune("STREAM_TAPER", 1500f, "Path narrows toward each end over this length, su")
    val streamEndWidthFrac = tune("STREAM_END_WIDTH", 0.3f, "Path width at the ends, fraction of STREAM_WIDTH")
    val STREAM_MIN_COLOR = Color(0.45f, 0.35f, 0.9f, 0.12f)
    val STREAM_MAX_COLOR = Color(0.55f, 0.65f, 1f, 0.35f)

    val pathPushAccel = tune("PATH_PUSH", 150f, "Push-back toward the path when outside it, su/s² per fleet radius of excess")
    val pathClampRadii = tune("PATH_CLAMP_RADII", 2f, "Hard clamp: at most this many fleet radii outside the path edge (2 = one fleet length)")

    // --- Arrival ----------------------------------------------------------------------------------

    val arrivalEdgePad = tune("ARRIVAL_PAD", 500f, "Arrival distance beyond the destination's outer radius, su")
    val arrivalFallbackRadius = tune("ARRIVAL_FALLBACK", 6000f, "Outer radius used when a system reports none, su")

    // --- Slipspace Tuning campaign bonus ----------------------------------------------------------

    val tuningTransitPerShip = tune("TUNING_TRANSIT_PER_SHIP", 0.10f, "Transit speed bonus per ship with Tuning")
    val tuningTransitMaxMult = tune("TUNING_TRANSIT_MAX", 1.5f, "Transit speed multiplier cap")
    val tuningSModPerShip = tune("TUNING_SMOD_PER_SHIP", 0.10f, "Charge-up and cooldown cut per S-modded Tuning")
    val tuningSModMinMult = tune("TUNING_SMOD_MIN", 0.5f, "Charge-up and cooldown multiplier floor")

    // --- Sounds (shared with the combat Fleet Slipspace Jump) ------------------------------------

    const val SOUND_CHARGE_LOOP = "covenant_remnants_fleet_jump_charge_loop"
    const val SOUND_OPEN = "covenant_remnants_fleet_jump_open"
    const val SOUND_COLLAPSE = "covenant_remnants_fleet_jump_collapse"
    const val SOUND_ARRIVAL = "covenant_remnants_fleet_jump_arrival"

    // =========================================================================================
    // Gating
    // =========================================================================================

    /** Non-fighter members (mothballed and civilian included — developer ruling 2026-10-07). */
    fun shipsThatCount(fleet: CampaignFleetAPI): List<FleetMemberAPI> =
        fleet.fleetData.membersListCopy.filter { !it.isFighterWing }

    /**
     * Ships that must carry the Slipspace Drive hullmod but do not. Rule: if the fleet has any
     * cruiser or capital, every cruiser and capital needs the drive (smaller ships ride along);
     * otherwise every destroyer and frigate needs it. Empty when `NO_SHIP_RESTRICTION` is on.
     */
    fun missingDriveShips(fleet: CampaignFleetAPI): List<FleetMemberAPI> {
        if (noShipRestriction.isOn) return emptyList()
        val ships = shipsThatCount(fleet)
        val big = ships.filter { it.hullSpec.hullSize == HullSize.CRUISER || it.hullSpec.hullSize == HullSize.CAPITAL_SHIP }
        val pool = if (big.isNotEmpty()) big else ships
        return pool.filter { !it.variant.hasHullMod(SlipspaceDriveHullmod.ID) }
    }

    /** Why the jump cannot start from here, or null when it can (fuel is checked separately). */
    fun blockReason(fleet: CampaignFleetAPI): String? {
        val location = fleet.containingLocation
        if (location == null || fleet.isInHyperspace) return "Cannot be used in hyperspace."
        if (location.hasTag(SLIPSPACE_POCKET_TAG)) return "Already in slipspace — follow the current to the exit."
        if (location !is StarSystemAPI) return "Can only be used inside a star system."
        if (fleet.isInHyperspaceTransition) return "Cannot be used while jumping."
        if (shipsThatCount(fleet).isEmpty()) return "No ships."
        if (missingDriveShips(fleet).isNotEmpty()) return "Not every ship that must carry a slipspace drive has one."
        return null
    }

    // =========================================================================================
    // Destinations and costs
    // =========================================================================================

    /** Hyperspace anchors of every system the picker offers from [origin] (all systems, fuel-gated at confirm). */
    fun destinations(origin: StarSystemAPI): List<SectorEntityToken> {
        val result = ArrayList<SectorEntityToken>()
        for (system in Global.getSector().starSystems) {
            if (system === origin) continue
            if (system.hasTag(SLIPSPACE_POCKET_TAG)) continue
            if (system.hasTag(Tags.THEME_HIDDEN)) continue
            if (system.isDeepSpace) continue
            val anchor = system.hyperspaceAnchor ?: continue
            if (Misc.getStarSystemForAnchor(anchor) == null) continue
            result.add(anchor)
        }
        return result
    }

    fun distanceLY(origin: StarSystemAPI, dest: StarSystemAPI): Float =
        Misc.getDistanceLY(origin.location, dest.location)

    fun fuelCost(fleet: CampaignFleetAPI, origin: StarSystemAPI, dest: StarSystemAPI): Int {
        if (free.isOn) return 0
        val perLY = fleet.logistics.fuelCostPerLightYear * fuelPerLyMult.value
        return ceil(distanceLY(origin, dest) * perLY).toInt()
    }

    /** Fuel-range ring on the picker map: how many LY one unit of fuel/LY buys (0 hides the ring). */
    fun fuelRangeMult(): Float = if (free.isOn || fuelPerLyMult.value <= 0f) 0f else 1f / fuelPerLyMult.value

    fun crCostMult(fleet: CampaignFleetAPI): Float =
        if (free.isOn) 0f else fleet.stats.dynamic.getValue(Stats.DIRECT_JUMP_CR_MULT)

    /** Ships whose CR is below what the jump takes — they risk damage on activation. */
    fun nonReadyShips(fleet: CampaignFleetAPI): List<FleetMemberAPI> {
        val mult = crCostMult(fleet)
        if (mult <= 0f) return emptyList()
        return fleet.fleetData.membersListCopy.filter { member ->
            val crLoss = member.deployCost * crCostMult.value * mult
            (member.repairTracker.cr * 100f).roundToInt() < (crLoss * 100f).roundToInt()
        }
    }

    /** Supplies it will take to recover the CR hit. */
    fun supplyCost(fleet: CampaignFleetAPI): Float {
        val mult = crCostMult(fleet)
        return fleet.fleetData.membersListCopy.sumOf { (it.deploymentCostSupplies * crCostMult.value * mult).toDouble() }.toFloat()
    }

    /** Applies the CR hit and the low-CR damage roll (transverse-jump style). */
    fun applyCrHit(fleet: CampaignFleetAPI) {
        val mult = crCostMult(fleet)
        if (mult <= 0f) return
        for (member in nonReadyShips(fleet)) {
            if (Math.random().toFloat() < EmergencyBurnAbility.ACTIVATION_DAMAGE_PROB) {
                Misc.applyDamage(
                    member, null, FleetMemberDamageLevel.LOW, false, null, null,
                    true, null, member.shipName + " suffers damage from slipspace jump activation"
                )
            }
        }
        for (member in fleet.fleetData.membersListCopy) {
            val crLoss = member.deployCost * crCostMult.value * mult
            member.repairTracker.applyCREvent(-crLoss, "Slipspace jump")
        }
    }

    // =========================================================================================
    // Slipspace Tuning campaign bonus
    // =========================================================================================

    fun tunedCount(fleet: CampaignFleetAPI): Int =
        shipsThatCount(fleet).count { it.variant.hasHullMod(SlipspaceTuning.ID) }

    fun sModTunedCount(fleet: CampaignFleetAPI): Int =
        shipsThatCount(fleet).count { it.variant.sMods.contains(SlipspaceTuning.ID) || it.variant.sModdedBuiltIns.contains(SlipspaceTuning.ID) }

    /** Multiplier on the pocket current's burn level. */
    fun transitBurnMult(fleet: CampaignFleetAPI): Float =
        min(1f + tuningTransitPerShip.value * tunedCount(fleet), tuningTransitMaxMult.value)

    /** Multiplier on the charge-up time (S-mod Tuning). */
    fun chargeMult(fleet: CampaignFleetAPI): Float =
        max(1f - tuningSModPerShip.value * sModTunedCount(fleet), tuningSModMinMult.value)

    /** Multiplier on the cooldown (S-mod Tuning). */
    fun cooldownMult(fleet: CampaignFleetAPI): Float = chargeMult(fleet)

    // =========================================================================================
    // Geometry
    // =========================================================================================

    /** Rupture radius for a fleet. */
    fun ruptureRadius(fleet: CampaignFleetAPI): Float = max(fleet.radius * ruptureRadiusMult.value, ruptureRadiusMin.value)

    /**
     * The arrival point in [dest]: on its outer edge, on the side that faces [origin]
     * (direction from the destination's hyperspace position toward the origin's).
     */
    fun arrivalPoint(origin: StarSystemAPI, dest: StarSystemAPI): Vector2f {
        var outer = BaseThemeGenerator.getOuterRadius(dest)
        if (outer <= 0f) outer = arrivalFallbackRadius.value
        val dir = Vector2f.sub(origin.location, dest.location, Vector2f())
        val angle = if (dir.lengthSquared() < 1f) 0f else Misc.getAngleInDegrees(dir)
        val unit = Misc.getUnitVectorAtDegreeAngle(angle)
        unit.scale(outer + arrivalEdgePad.value)
        val center = dest.center?.location ?: Vector2f(0f, 0f)
        return Vector2f.add(center, unit, Vector2f())
    }

    // =========================================================================================
    // Grant
    // =========================================================================================

    /**
     * Gives the player the ability (character data + fleet + a free ability-bar slot), like the
     * vanilla `AddAbility` rule command. Used by `CovenantGiveAll` now and the granting quest later.
     * @return false if the player already had it.
     */
    fun grantToPlayer(): Boolean {
        val sector = Global.getSector()
        val fleet = sector.playerFleet
        val had = fleet.hasAbility(ABILITY_ID)
        sector.characterData.addAbility(ABILITY_ID)
        if (!fleet.hasAbility(ABILITY_ID)) fleet.addAbility(ABILITY_ID)
        if (!had) assignToFreeSlot()
        sector.characterData.memoryWithoutUpdate.set("\$ability:$ABILITY_ID", true, 0f)
        return !had
    }

    private fun assignToFreeSlot() {
        val slots = Global.getSector().uiData.abilitySlotsAPI
        val currBar = slots.currBarIndex
        try {
            for (bar in 0 until 5) {
                slots.currBarIndex = bar
                val barSlots: List<AbilitySlotAPI> = slots.currSlotsCopy
                if (barSlots.any { it.abilityId == ABILITY_ID }) return
            }
            outer@ for (bar in 0 until 5) {
                slots.currBarIndex = bar
                for (slot in slots.currSlotsCopy) {
                    if (slot.abilityId == null) {
                        slot.abilityId = ABILITY_ID
                        break@outer
                    }
                }
            }
        } finally {
            slots.currBarIndex = currBar
        }
    }
}
