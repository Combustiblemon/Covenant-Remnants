package org.combustiblelemon.covenantremnants.commands

import org.combustiblelemon.covenantremnants.systems.FleetSlipspaceJump
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.BaseCommand.CommandContext
import org.lazywizard.console.BaseCommand.CommandResult
import org.lazywizard.console.Console

/**
 * Dev tuning for the Fleet Slipspace Jump. Changes apply immediately (the CHARGE / FADE / ARRIVAL
 * timings from the next battle) and reset when the game restarts.
 *
 *  - `FleetJumpTune`                 lists every tunable: name = value (default) — description
 *  - `FleetJumpTune <name> <value>`  sets one
 *  - `FleetJumpTune reset [<name>]`  resets one or all
 */
class FleetJumpTune : BaseCommand {

    override fun runCommand(args: String, context: CommandContext): CommandResult {
        val parts = args.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

        if (parts.isEmpty()) {
            for (tunable in FleetSlipspaceJump.tunables) {
                val changed = if (tunable.value != tunable.default) " *" else ""
                Console.showMessage("${tunable.name} = ${format(tunable.value)} (${format(tunable.default)})$changed - ${tunable.description}")
            }
            Console.showMessage("* = changed. CHARGE/FADE/ARRIVAL apply from the next battle.")
            return CommandResult.SUCCESS
        }

        if (parts[0].equals("reset", ignoreCase = true)) {
            if (parts.size == 1) {
                FleetSlipspaceJump.resetAll()
                Console.showMessage("Fleet Slipspace Jump: all tunables reset.")
                return CommandResult.SUCCESS
            }
            val tunable = FleetSlipspaceJump.tunable(parts[1]) ?: return unknown(parts[1])
            tunable.reset()
            Console.showMessage("${tunable.name} = ${format(tunable.value)} (reset)")
            return CommandResult.SUCCESS
        }

        if (parts.size != 2) {
            return CommandResult.BAD_SYNTAX
        }

        val tunable = FleetSlipspaceJump.tunable(parts[0]) ?: return unknown(parts[0])
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
        Console.showMessage("Unknown tunable: $name. Run FleetJumpTune with no arguments to list them.")
        return CommandResult.ERROR
    }

    private fun format(value: Float): String =
        if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()
}
