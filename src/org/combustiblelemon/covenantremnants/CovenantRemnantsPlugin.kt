package org.combustiblelemon.covenantremnants

import com.fs.starfarer.api.BaseModPlugin
import com.fs.starfarer.api.Global

class CovenantRemnantsPlugin : BaseModPlugin() {

    override fun onApplicationLoad() {
        super.onApplicationLoad()

        // Test that the .jar is loaded and working, using the most obnoxious way possible.
        val isMagicLibEnabled = Global.getSettings().modManager.isModEnabled("MagicLib")
        if (!isMagicLibEnabled) {
            throw RuntimeException("This mod requires MagicLib to work.")
        }

        val isLazyLibEnabled = Global.getSettings().modManager.isModEnabled("LazyLib")
        if (!isLazyLibEnabled) {
            throw RuntimeException("This mod requires LazyLib to work.")
        }
    }
}
