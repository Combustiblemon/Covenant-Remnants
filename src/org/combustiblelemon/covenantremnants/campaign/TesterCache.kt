package org.combustiblelemon.covenantremnants.campaign

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CargoAPI
import com.fs.starfarer.api.campaign.SectorAPI
import com.fs.starfarer.api.campaign.SpecialItemData
import com.fs.starfarer.api.combat.ShipAPI.HullSize
import com.fs.starfarer.api.combat.ShipHullSpecAPI
import com.fs.starfarer.api.fleet.FleetMemberType
import com.fs.starfarer.api.impl.campaign.ids.Submarkets
import org.combustiblelemon.covenantremnants.campaign.abilities.SlipspaceJumpRules
import org.combustiblelemon.covenantremnants.hullmods.CapacitorDischarge
import org.combustiblelemon.covenantremnants.hullmods.OverchargedEmitters
import org.combustiblelemon.covenantremnants.hullmods.TargetLattice

/**
 * Tester access while the quests (the real acquisition path, CLAUDE.md) do not exist yet: the storage
 * of vanilla's Abandoned Terraforming Platform in Corvus gets every Covenant ship, weapon, wing,
 * optional hullmod, the Slipspace Drive Schematics (the Slipspace Jump) and the Slipspace Navigation
 * System (its upgrade), once per save (developer rulings 2026-10-10, 2026-10-12).
 *
 * Runs from `onGameLoad`, so new and existing saves both get it; a sector memory flag stops repeats.
 * Saves whose cache predates the Navigation System get that item once, under a flag of its own; the
 * same goes for the modspecs of the 2026-10-17 weapon hullmods.
 * A random-sector game has no Corvus: the cache is skipped and logged. Content is found by the id
 * prefix, like `CovenantGiveAll`, so new content is picked up without editing this file.
 */
object TesterCache {
    /** Turn off for a real release once the quests hand out Covenant content. */
    const val ENABLED = true

    const val STATION_ID = "corvus_abandoned_station"
    const val ENGRAM_ID = "covenant_remnants_slipspace_engram"
    const val NAVIGATION_ID = SlipspaceJumpRules.NAVIGATION_ITEM_ID
    const val MEMORY_FLAG = "\$covenant_remnants_tester_cache_added"
    /** Set once the Navigation System is in the cache (new caches, and old ones topped up). */
    const val NAVIGATION_FLAG = "\$covenant_remnants_tester_cache_navigation_added"
    /** Set once the 2026-10-17 weapon hullmods' modspecs are in the cache (new caches, and old ones topped up). */
    const val WEAPON_HULLMODS_FLAG = "\$covenant_remnants_tester_cache_weapon_hullmods_added"
    private val WEAPON_HULLMOD_IDS = listOf(CapacitorDischarge.ID, TargetLattice.ID, OverchargedEmitters.ID)

    private const val PREFIX = "covenant_remnants_"
    private const val WEAPON_QTY = 30
    private const val WING_QTY = 30
    private const val MODSPEC_QTY = 1

    private val logger = Global.getLogger(TesterCache::class.java)

    /** Ships per hull: 1 supercap, 3 capital, 4 cruiser, 6 destroyer, 10 frigate. */
    private fun shipCount(spec: ShipHullSpecAPI): Int = when {
        spec.hullId.contains("supercap") -> 1
        spec.hullSize == HullSize.CAPITAL_SHIP -> 3
        spec.hullSize == HullSize.CRUISER -> 4
        spec.hullSize == HullSize.DESTROYER -> 6
        spec.hullSize == HullSize.FRIGATE -> 10
        else -> 0
    }

    private fun storageCargo(sector: SectorAPI): CargoAPI? =
        sector.getEntityById(STATION_ID)?.market?.getSubmarket(Submarkets.SUBMARKET_STORAGE)?.cargo

    fun addIfNeeded() {
        if (!ENABLED) return
        val sector = Global.getSector() ?: return
        val memory = sector.memoryWithoutUpdate
        if (memory.getBoolean(MEMORY_FLAG)) {
            addNavigationIfNeeded(sector)
            addWeaponHullmodsIfNeeded(sector)
            return
        }

        val cargo = storageCargo(sector)
        if (cargo == null) {
            logger.warn("Tester cache: no $STATION_ID storage in this sector (random sector?); skipped")
            return
        }
        val settings = Global.getSettings()

        // Ships: the standard fit of each hull, mothballed in storage
        var ships = 0
        val builtIns = HashSet<String>()
        for (spec in settings.allShipHullSpecs) {
            if (!spec.hullId.startsWith(PREFIX) || spec.hullSize == HullSize.FIGHTER || spec.isDefaultDHull) continue
            builtIns.addAll(spec.builtInMods)
            val count = shipCount(spec)
            if (count == 0) continue
            var variantId = spec.hullId + "_std"
            if (!settings.doesVariantExist(variantId)) {
                logger.warn("Tester cache: no $variantId, using the empty hull")
                variantId = spec.hullId + "_Hull"
            }
            repeat(count) { cargo.addMothballedShip(FleetMemberType.SHIP, variantId, null) }
            ships += count
        }

        // Weapons and wings (the nose covercaps have no prefix, so they never match)
        var weapons = 0
        for (spec in settings.allWeaponSpecs) {
            if (!spec.weaponId.startsWith(PREFIX)) continue
            cargo.addWeapons(spec.weaponId, WEAPON_QTY)
            weapons++
        }
        var wings = 0
        for (spec in settings.allFighterWingSpecs) {
            if (!spec.id.startsWith(PREFIX)) continue
            cargo.addFighters(spec.id, WING_QTY)
            wings++
        }

        // Hullmods: modspecs of the optional ones (built-ins and hidden ones are useless as modspecs)
        var hullmods = 0
        for (spec in settings.allHullModSpecs) {
            if (!spec.id.startsWith(PREFIX) || spec.isHidden || spec.isHiddenEverywhere || spec.id in builtIns) continue
            cargo.addSpecial(SpecialItemData("modspec", spec.id), MODSPEC_QTY.toFloat())
            hullmods++
        }

        cargo.addSpecial(SpecialItemData(ENGRAM_ID, null), 1f)
        cargo.addSpecial(SpecialItemData(NAVIGATION_ID, null), 1f)

        memory.set(MEMORY_FLAG, true)
        memory.set(NAVIGATION_FLAG, true)
        memory.set(WEAPON_HULLMODS_FLAG, true)
        logger.info(
            "Tester cache added to $STATION_ID storage: $ships ships, $weapons weapons x$WEAPON_QTY, " +
                "$wings wings x$WING_QTY, $hullmods modspecs, 1 $ENGRAM_ID, 1 $NAVIGATION_ID"
        )
    }

    /** A cache from before the Navigation System existed gets one, once (developer ruling 2026-10-12). */
    private fun addNavigationIfNeeded(sector: SectorAPI) {
        val memory = sector.memoryWithoutUpdate
        if (memory.getBoolean(NAVIGATION_FLAG)) return
        val cargo = storageCargo(sector)
        if (cargo == null) {
            logger.warn("Tester cache: no $STATION_ID storage for the $NAVIGATION_ID top-up; skipped")
            return
        }
        cargo.addSpecial(SpecialItemData(NAVIGATION_ID, null), 1f)
        memory.set(NAVIGATION_FLAG, true)
        logger.info("Tester cache: added 1 $NAVIGATION_ID to $STATION_ID storage (cache predates it)")
    }

    /** A cache from before the weapon hullmods existed gets their modspecs, once (developer ruling 2026-10-17). */
    private fun addWeaponHullmodsIfNeeded(sector: SectorAPI) {
        val memory = sector.memoryWithoutUpdate
        if (memory.getBoolean(WEAPON_HULLMODS_FLAG)) return
        val cargo = storageCargo(sector)
        if (cargo == null) {
            logger.warn("Tester cache: no $STATION_ID storage for the weapon hullmod top-up; skipped")
            return
        }
        for (id in WEAPON_HULLMOD_IDS) {
            cargo.addSpecial(SpecialItemData("modspec", id), MODSPEC_QTY.toFloat())
        }
        memory.set(WEAPON_HULLMODS_FLAG, true)
        logger.info("Tester cache: added modspecs $WEAPON_HULLMOD_IDS to $STATION_ID storage (cache predates them)")
    }
}
