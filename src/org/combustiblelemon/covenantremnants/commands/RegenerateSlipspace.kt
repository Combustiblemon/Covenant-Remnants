package org.combustiblelemon.covenantremnants.commands

import com.fs.starfarer.api.Global
import org.combustiblelemon.covenantremnants.campaign.systems.generateSlipspaceSystem
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.BaseCommand.CommandContext
import org.lazywizard.console.BaseCommand.CommandResult

class RegenerateSlipspace : BaseCommand {

    override fun runCommand(args: String, context: CommandContext): CommandResult? {
        Global.getSector().removeStarSystem(Global.getSector().getStarSystem("Slipspace"))

        generateSlipspaceSystem(Global.getSector())

        return CommandResult.SUCCESS
    }
}