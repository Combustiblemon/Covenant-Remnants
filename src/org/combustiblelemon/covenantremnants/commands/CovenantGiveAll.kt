package org.combustiblelemon.covenantremnants.commands

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.fleet.FleetMemberType
import org.combustiblelemon.covenantremnants.campaign.abilities.SlipspaceJumpRules
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.BaseCommand.CommandContext
import org.lazywizard.console.BaseCommand.CommandResult
import org.lazywizard.console.Console

/**
 * Dev cheat: grants all Covenant Remnants content in one call.
 *
 * Content is discovered dynamically by the canonical id prefix, so new ships/weapons/fighters/hullmods
 * are picked up automatically without editing this command.
 *
 *  - Ships:    one empty-hull fleet member of each Covenant hull (fighter hulls excluded).
 *  - Weapons:  [QTY] of each to cargo.
 *  - Fighters: [QTY] of each LPC to cargo.
 *  - Hullmods: learned (installable in refit); hidden internal hullmods skipped.
 *  - Ability:  the campaign Slipspace Jump (quest-granted once the quest exists).
 */
class CovenantGiveAll : BaseCommand {

    private companion object {
        const val PREFIX = "covenant_remnants_"
        const val QTY = 30
    }

    override fun runCommand(args: String, context: CommandContext): CommandResult? {
        if (!context.isInCampaign) {
            Console.showMessage("CovenantGiveAll can only be used in the campaign.")
            return CommandResult.WRONG_CONTEXT
        }

        val fleet = Global.getSector().playerFleet
        val cargo = fleet.cargo
        val playerFaction = Global.getSector().playerFaction
        val settings = Global.getSettings()

        var ships = 0
        var weapons = 0
        var fighters = 0
        var hullmods = 0

        // Ships — spawn an empty hull of each Covenant ship (skip fighter hulls; those are deployed as wings).
        for (spec in settings.allShipHullSpecs) {
            if (!spec.hullId.startsWith(PREFIX)) continue
            if (spec.hullSize == ShipAPI.HullSize.FIGHTER) continue
            if (spec.isDefaultDHull) continue

            val variant = settings.createEmptyVariant(spec.hullId + "_giveall", spec)
            val member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant)
            fleet.fleetData.addFleetMember(member)
            ships++
        }

        // Weapons — QTY of each to cargo.
        for (spec in settings.allWeaponSpecs) {
            if (!spec.weaponId.startsWith(PREFIX)) continue
            cargo.addWeapons(spec.weaponId, QTY)
            weapons++
        }

        // Fighters — QTY of each LPC to cargo.
        for (spec in settings.allFighterWingSpecs) {
            if (!spec.id.startsWith(PREFIX)) continue
            cargo.addFighters(spec.id, QTY)
            fighters++
        }

        // Hullmods — learn each (skip hidden internal manager).
        for (spec in settings.allHullModSpecs) {
            if (!spec.id.startsWith(PREFIX)) continue
            if (spec.isHidden || spec.isHiddenEverywhere) continue
            playerFaction.addKnownHullMod(spec.id)
            hullmods++
        }

        // Campaign Slipspace Jump ability — quest-granted later; the cheat grants it meanwhile.
        val abilityGranted = SlipspaceJumpRules.grantToPlayer()

        Console.showMessage(
            "Covenant: $ships ships (fleet), $weapons weapons + $fighters fighters (x$QTY, cargo), " +
                "$hullmods hullmods learned, Slipspace Jump ability " +
                (if (abilityGranted) "granted." else "already known.")
        )
        return CommandResult.SUCCESS
    }
}
