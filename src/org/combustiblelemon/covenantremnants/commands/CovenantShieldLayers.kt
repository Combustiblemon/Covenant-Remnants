package org.combustiblelemon.covenantremnants.commands

import org.combustiblelemon.covenantremnants.utils.render.ShieldShader
import org.combustiblelemon.covenantremnants.utils.render.shield.BorderLayer
import org.combustiblelemon.covenantremnants.utils.render.shield.DebugLayer
import org.combustiblelemon.covenantremnants.utils.render.shield.FillLayer
import org.combustiblelemon.covenantremnants.utils.render.shield.HoneycombLayer
import org.combustiblelemon.covenantremnants.utils.render.shield.RippleLayer
import org.combustiblelemon.covenantremnants.utils.render.shield.SweepLayer
import org.lazywizard.console.BaseCommand
import org.lazywizard.console.BaseCommand.CommandContext
import org.lazywizard.console.BaseCommand.CommandResult
import org.lazywizard.console.Console

/**
 * Dev tool: restrict the composed shield shader to a subset of its layers so a
 * single layer can be inspected in isolation. Disabled layers compile as
 * neutral GLSL stubs; every shield shader instance rebuilds its program on its
 * next render.
 *
 *  - `CovenantShieldLayers`              — show available layer names + current override
 *  - `CovenantShieldLayers border`       — render only the border layer
 *  - `CovenantShieldLayers fill,ripple`  — render only those layers
 *  - `CovenantShieldLayers all`          — reset to all layers
 */
class CovenantShieldLayers : BaseCommand {

    // Layer classes are plain state holders (no GL in constructors), so this is
    // a safe way to get the canonical layer names without hardcoding them.
    private val knownLayers =
        listOf(FillLayer(), BorderLayer(), SweepLayer(), RippleLayer(), HoneycombLayer(), DebugLayer())
            .map { it.layerName }

    override fun runCommand(args: String, context: CommandContext): CommandResult? {
        val trimmed = args.trim()

        if (trimmed.isEmpty()) {
            val current = ShieldShader.globalEnabledLayers
            val state = if (current.isNullOrEmpty()) "all" else current.joinToString(",")
            Console.showMessage("Shield layers: ${knownLayers.joinToString(", ")}. Currently enabled: $state.")
            return CommandResult.SUCCESS
        }

        if (trimmed.equals("all", ignoreCase = true)) {
            // Empty set = no restriction; shader coerces it to all layers
            ShieldShader.globalEnabledLayers = emptySet()
            Console.showMessage("Shield layers reset: all enabled.")
            return CommandResult.SUCCESS
        }

        val requested = trimmed.split(',', ' ').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val unknown = requested.filter { it !in knownLayers }
        if (unknown.isNotEmpty()) {
            Console.showMessage(
                "Unknown layer(s): ${unknown.joinToString(", ")}. Available: ${knownLayers.joinToString(", ")}."
            )
            return CommandResult.BAD_SYNTAX
        }

        ShieldShader.globalEnabledLayers = requested.toSet()
        Console.showMessage("Shield layers enabled: ${requested.joinToString(", ")}.")
        return CommandResult.SUCCESS
    }
}
