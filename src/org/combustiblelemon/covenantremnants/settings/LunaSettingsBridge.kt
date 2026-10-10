package org.combustiblelemon.covenantremnants.settings

import com.fs.starfarer.api.Global
import lunalib.lunaSettings.LunaSettings
import lunalib.lunaSettings.LunaSettingsListener
import org.combustiblelemon.covenantremnants.CovenantRemnantsPlugin

/**
 * The only class that touches LunaLib. LunaLib is optional, so this class must only be loaded after
 * [CovenantRemnantsPlugin.onApplicationLoad] has checked that LunaLib is enabled; anything else would
 * fail with `NoClassDefFoundError` without it. Copies the settings page into [CovenantSettings] once
 * at load and again whenever the page is saved.
 */
class LunaSettingsBridge : LunaSettingsListener {
    companion object {
        fun install() {
            val bridge = LunaSettingsBridge()
            bridge.read()
            // Listeners are not persisted; guard anyway in case the application load runs twice.
            if (!LunaSettings.hasSettingsListenerOfClass(LunaSettingsBridge::class.java)) {
                LunaSettings.addSettingsListener(bridge)
            }
        }
    }

    override fun settingsChanged(modID: String) {
        if (modID == CovenantRemnantsPlugin.MOD_ID) read()
    }

    /** A missing value (null) keeps the current one; out-of-range values from a hand-edited file are clamped. */
    private fun read() {
        val mod = CovenantRemnantsPlugin.MOD_ID
        with(CovenantSettings) {
            LunaSettings.getFloat(mod, PARTICLE_DENSITY_ID)?.let {
                particleDensity = it.coerceIn(PARTICLE_DENSITY_MIN, PARTICLE_DENSITY_MAX)
            }
            LunaSettings.getBoolean(mod, HEAT_HAZE_ID)?.let { heatHaze = it }
            LunaSettings.getBoolean(mod, SHIELD_DISTORTION_ID)?.let { shieldDistortion = it }
            LunaSettings.getFloat(mod, EFFECT_VOLUME_ID)?.let {
                effectVolume = it.coerceIn(EFFECT_VOLUME_MIN, EFFECT_VOLUME_MAX)
            }
            Global.getLogger(LunaSettingsBridge::class.java).info(
                "Covenant settings: particleDensity=$particleDensity heatHaze=$heatHaze " +
                    "shieldDistortion=$shieldDistortion effectVolume=$effectVolume"
            )
        }
    }
}
