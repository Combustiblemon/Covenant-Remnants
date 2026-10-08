package org.combustiblelemon.covenantremnants.commands

import com.fs.starfarer.api.Global
import org.combustiblelemon.covenantremnants.campaign.abilities.SlipspaceJump
import org.combustiblelemon.covenantremnants.campaign.abilities.SlipspaceJumpRules
import org.combustiblelemon.covenantremnants.utils.render.rupture.ArkVariant
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
 *  - `SlipspaceJumpTune ark [cruiser|carrier]` one-shot Ark exit burst ahead of the fleet (look tuning)
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

        if (parts[0].equals("ark", ignoreCase = true)) {
            if (!context.isInCampaign) {
                Console.showMessage("The ark burst needs the campaign.")
                return CommandResult.WRONG_CONTEXT
            }
            val fleet = Global.getSector().playerFleet
            val ability = fleet.getAbility(SlipspaceJumpRules.ABILITY_ID) as? SlipspaceJump
            if (ability == null) {
                Console.showMessage("The player fleet has no Slipspace Jump ability (CovenantGiveAll grants it).")
                return CommandResult.ERROR
            }
            val variant = when {
                parts.size < 2 -> SlipspaceJumpRules.arkVariant(fleet)
                parts[1].equals("carrier", ignoreCase = true) -> ArkVariant.CARRIER
                parts[1].equals("cruiser", ignoreCase = true) -> ArkVariant.CRUISER
                else -> {
                    Console.showMessage("Variant must be cruiser or carrier.")
                    return CommandResult.BAD_SYNTAX
                }
            }
            ability.debugBurst(variant)
            Console.showMessage("Ark ${variant.name.lowercase()} burst ahead of the fleet.")
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
