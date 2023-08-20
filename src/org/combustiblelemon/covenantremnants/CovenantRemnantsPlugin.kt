package org.combustiblelemon.covenantremnants

import com.fs.starfarer.api.BaseModPlugin
import com.fs.starfarer.api.Global

class CovenantRemnantsPlugin : BaseModPlugin() {
    @Throws(Exception::class)
    override fun onApplicationLoad() {
        super.onApplicationLoad()

        // Test that the .jar is loaded and working, using the most obnoxious way possible.
        val isMagicLibEnabled = Global.getSettings().modManager.isModEnabled("MagicLib")
        if (!isMagicLibEnabled) {
            throw RuntimeException("This mod required MagicLib to work.")
        }
    }

    override fun onNewGame() {
        super.onNewGame()

        // The code below requires that Nexerelin is added as a library (not a dependency, it's only needed to compile the mod).
//        boolean isNexerelinEnabled = Global.getSettings().getModManager().isModEnabled("nexerelin");

//        if (!isNexerelinEnabled || SectorManager.getManager().isCorvusMode()) {
//                    new MySectorGen().generate(Global.getSector());
        // Add code that creates a new star system (will only run if Nexerelin's Random (corvus) mode is disabled).
//        }
    }
}
