package org.combustiblelemon.covenantremnants

import com.fs.starfarer.api.BaseModPlugin
import com.fs.starfarer.api.Global
import org.combustiblelemon.covenantremnants.campaign.systems.generateSlipspaceSystem
import org.combustiblelemon.covenantremnants.hullmods.CovenantIncompatibilities
import org.combustiblelemon.covenantremnants.utils.initShieldData
import org.combustiblelemon.covenantremnants.utils.render.blink.BlinkRenderer
import org.combustiblelemon.covenantremnants.utils.render.rupture.RupturePrograms
import org.combustiblelemon.covenantremnants.utils.render.shield.ShieldProgram
import org.combustiblelemon.lemonlib.hullmods.HullmodIncompatibilityInstaller

class CovenantRemnantsPlugin : BaseModPlugin() {
    val logger = Global.getLogger(this.javaClass)

    companion object {
        const val MOD_ID = "combustiblelemon_covenant_remnants"
    }

    override fun onApplicationLoad() {
        super.onApplicationLoad()

        // Test that the .jar is loaded and working, using the most obnoxious way possible.
        val isMagicLibEnabled = Global.getSettings().modManager.isModEnabled("MagicLib")
        if (!isMagicLibEnabled) {
            throw RuntimeException("This mod requires MagicLib to work.")
        }

        val isLazyLibEnabled = Global.getSettings().modManager.isModEnabled("lw_lazylib")
        if (!isLazyLibEnabled) {
            throw RuntimeException("This mod requires LazyLib to work.")
        }

        logger.info("Loading shield_data.csv")
        val shieldData = Global.getSettings().loadCSV("data/hulls/shield_data.csv")
        initShieldData(shieldData)
        logger.info(shieldData.toString())

        HullmodIncompatibilityInstaller.install(CovenantIncompatibilities.registry())

        // Compile the (one, shared) Covenant shield program now, on the GL thread, instead of on the
        // first shield activation in combat (developer request 2026-10-02: ~250 ms per compile
        // stacked into a multi-second hitch when a fleet raised shields).
        ShieldProgram.warmUp()
        // Same for the rupture (fleet jump + blink portals) and the blink hull ripple.
        RupturePrograms.warmUp()
        BlinkRenderer.warmUp()
    }

    override fun onGameLoad(newGame: Boolean) {
        super.onGameLoad(newGame)
        HullmodIncompatibilityInstaller.verifyAndReinstall(CovenantIncompatibilities.registry())
    }

    override fun onNewGame() {
        generateSlipspaceSystem(Global.getSector())
    }
}
