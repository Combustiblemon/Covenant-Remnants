package org.combustiblelemon.covenantremnants.commands

import org.lazywizard.console.BaseCommand

class DebugHook : BaseCommand {
    override fun runCommand(
        args: String,
        context: BaseCommand.CommandContext
    ): BaseCommand.CommandResult? {

        return BaseCommand.CommandResult.SUCCESS
    }
}