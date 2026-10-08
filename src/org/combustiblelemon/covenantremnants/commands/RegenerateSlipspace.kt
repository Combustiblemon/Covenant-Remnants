package org.combustiblelemon.covenantremnants.commands

import com.fs.starfarer.api.Global
import org.combustiblelemon.covenantremnants.campaign.systems.ensureSlipspaceSystem
import org.combustiblelemon.covenantremnants.campaign.systems.findSlipspaceSystem
import org.combustiblelemon.covenantremnants.utils.extensions.isInSlipspace
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.BaseCommand.CommandContext
import org.lazywizard.console.BaseCommand.CommandResult
import org.lazywizard.console.Console

/**
 * Dev: removes the slipspace pocket system (if any) and generates a fresh one. Refuses while the
 * player is inside it. Safe on saves that have no pocket yet.
 */
class RegenerateSlipspace : BaseCommand {

    override fun runCommand(args: String, context: CommandContext): CommandResult? {
        if (!context.isInCampaign) {
            Console.showMessage("RegenerateSlipspace can only be used in the campaign.")
            return CommandResult.WRONG_CONTEXT
        }
        val sector = Global.getSector()
        if (sector.playerFleet.isInSlipspace) {
            Console.showMessage("Leave slipspace first.")
            return CommandResult.ERROR
        }
        val existing = findSlipspaceSystem(sector)
        if (existing != null) sector.removeStarSystem(existing)
        ensureSlipspaceSystem(sector)
        Console.showMessage(if (existing != null) "Slipspace pocket regenerated." else "Slipspace pocket created.")
        return CommandResult.SUCCESS
    }
}
