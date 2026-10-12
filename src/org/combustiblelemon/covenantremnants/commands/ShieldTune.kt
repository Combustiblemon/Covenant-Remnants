package org.combustiblelemon.covenantremnants.commands

import org.combustiblelemon.covenantremnants.systems.CovenantShieldsStats
import org.combustiblelemon.covenantremnants.utils.render.shield.ShieldOutline
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.BaseCommand.CommandContext
import org.lazywizard.console.BaseCommand.CommandResult
import org.lazywizard.console.Console

/**
 * Dev tuning for the Covenant shield: the outline ([ShieldOutline]) and the regen flux falloff
 * ([CovenantShieldsStats.regenTunables]). Changes apply on the next frame and reset when the game
 * restarts.
 *
 *  - `ShieldTune`                 lists every tunable: name = value (default) — description
 *  - `ShieldTune <name> <value>`  sets one
 *  - `ShieldTune reset [<name>]`  resets one or all
 */
class ShieldTune : BaseCommand {

    private val all: List<ShieldOutline.Tunable>
        get() = ShieldOutline.tunables + CovenantShieldsStats.regenTunables

    private fun tunable(name: String): ShieldOutline.Tunable? = all.firstOrNull { it.name.equals(name, ignoreCase = true) }

    override fun runCommand(args: String, context: CommandContext): CommandResult {
        val parts = args.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

        if (parts.isEmpty()) {
            for (tunable in all) {
                val changed = if (tunable.value != tunable.default) " *" else ""
                Console.showMessage("${tunable.name} = ${format(tunable.value)} (${format(tunable.default)})$changed - ${tunable.description}")
            }
            Console.showMessage("* = changed. Values apply on the next frame.")
            return CommandResult.SUCCESS
        }

        if (parts[0].equals("reset", ignoreCase = true)) {
            if (parts.size == 1) {
                all.forEach { it.reset() }
                Console.showMessage("Shield: all tunables reset.")
                return CommandResult.SUCCESS
            }
            val tunable = tunable(parts[1]) ?: return unknown(parts[1])
            tunable.reset()
            Console.showMessage("${tunable.name} = ${format(tunable.value)} (reset)")
            return CommandResult.SUCCESS
        }

        if (parts.size != 2) {
            return CommandResult.BAD_SYNTAX
        }

        val tunable = tunable(parts[0]) ?: return unknown(parts[0])
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
        Console.showMessage("Unknown tunable: $name. Run ShieldTune with no arguments to list them.")
        return CommandResult.ERROR
    }

    private fun format(value: Float): String =
        if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()
}
