package org.combustiblelemon.covenantremnants

import com.fs.starfarer.api.BaseModPlugin
import com.fs.starfarer.api.Global
import org.combustiblelemon.covenantremnants.campaign.TesterCache
import org.combustiblelemon.covenantremnants.campaign.abilities.SlipspaceJumpRules
import org.combustiblelemon.covenantremnants.campaign.entities.SlipspaceCampaignPlugin
import org.combustiblelemon.covenantremnants.campaign.systems.ensureSlipspaceSystem
import org.combustiblelemon.covenantremnants.hullmods.CovenantIncompatibilities
import org.combustiblelemon.covenantremnants.settings.CovenantSettings
import org.combustiblelemon.covenantremnants.settings.LunaSettingsBridge
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

        // Player options. LunaLib is optional: without it CovenantSettings keeps its defaults, and the
        // bridge class (the only one importing lunalib) is never loaded.
        if (Global.getSettings().modManager.isModEnabled(CovenantSettings.LUNALIB_ID)) {
            LunaSettingsBridge.install()
        }

        // Compile the (one, shared) Covenant shield program now, on the GL thread, instead of on the
        // first shield activation in combat (developer request 2026-10-02: ~250 ms per compile
        // stacked into a multi-second hitch when a fleet raised shields).
        ShieldProgram.warmUp()
        // Same for the rupture (fleet jump + blink portals) and the blink hull ripple.
        RupturePrograms.warmUp()
        BlinkRenderer.warmUp()

        // Pocket background (solid black placeholder, developer 2026-10-07).
        Global.getSettings().loadTexture(SlipspaceJumpRules.POCKET_BACKGROUND)
    }

    override fun onGameLoad(newGame: Boolean) {
        super.onGameLoad(newGame)
        HullmodIncompatibilityInstaller.verifyAndReinstall(CovenantIncompatibilities.registry())

        // Campaign slipspace jump: the pocket system exists in every save (created or migrated),
        // and the exit portal's dialog is routed by a transient campaign plugin.
        ensureSlipspaceSystem(Global.getSector())
        Global.getSector().registerPlugin(SlipspaceCampaignPlugin())

        // Testers get all Covenant content in Corvus storage until the quests exist (once per save)
        TesterCache.addIfNeeded()
    }

    override fun onNewGame() {
        ensureSlipspaceSystem(Global.getSector())
    }
}
