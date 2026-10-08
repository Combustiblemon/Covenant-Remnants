package org.combustiblelemon.covenantremnants.commands

import org.combustiblelemon.covenantremnants.campaign.abilities.SlipspaceJumpRules
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.BaseCommand.CommandContext
import org.lazywizard.console.BaseCommand.CommandResult
import org.lazywizard.console.Console

/**
 * Dev tuning / overrides for the campaign Slipspace Jump (same shape as [FleetJumpTune]). Changes
 * apply immediately (path length / wind only to pairs jumped for the first time) and reset when
 * the game restarts.
 *
 *  - `SlipspaceJumpTune`                      lists every tunable: name = value (default) — description
 *  - `SlipspaceJumpTune <name> <value>`       sets one
 *  - `SlipspaceJumpTune reset [<name>]`       resets one or all
 *  - `SlipspaceJumpTune NO_SHIP_RESTRICTION 1` lets any fleet jump (no drive-hullmod rule)
 *  - `SlipspaceJumpTune FREE 1`               no fuel cost, no CR hit
 */
class SlipspaceJumpTune : BaseCommand {

    override fun runCommand(args: String, context: CommandContext): CommandResult {
        val parts = args.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

        if (parts.isEmpty()) {
            for (tunable in SlipspaceJumpRules.tunables) {
                val changed = if (tunable.value != tunable.default) " *" else ""
                Console.showMessage("${tunable.name} = ${format(tunable.value)} (${format(tunable.default)})$changed - ${tunable.description}")
            }
            Console.showMessage("* = changed. PATH_* apply to pairs jumped for the first time; the rest apply at once.")
            return CommandResult.SUCCESS
        }

        if (parts[0].equals("reset", ignoreCase = true)) {
            if (parts.size == 1) {
                SlipspaceJumpRules.resetAll()
                Console.showMessage("Slipspace Jump: all tunables reset.")
                return CommandResult.SUCCESS
            }
            val tunable = SlipspaceJumpRules.tunable(parts[1]) ?: return unknown(parts[1])
            tunable.reset()
            Console.showMessage("${tunable.name} = ${format(tunable.value)} (reset)")
            return CommandResult.SUCCESS
        }

        if (parts.size != 2) {
            return CommandResult.BAD_SYNTAX
        }

        val tunable = SlipspaceJumpRules.tunable(parts[0]) ?: return unknown(parts[0])
        val value = parts[1].toFloatOrNull()
        if (value == null) {
            Console.showMessage("Not a number: ${parts[1]}")
            return CommandResult.BAD_SYNTAX
        }
        tunable.value = value
        Console.showMessage("${tunable.name} = ${format(value)} (default ${format(tunable.default)})")
        return CommandResult.SUCCESS
    }

    private fun unknown(name: String): CommandResult {
        Console.showMessage("Unknown tunable: $name. Run SlipspaceJumpTune with no arguments to list them.")
        return CommandResult.ERROR
    }

    private fun format(value: Float): String =
        if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()
}
