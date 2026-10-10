package org.combustiblelemon.covenantremnants.settings

/**
 * Player options, shown on the LunaLib settings page (`data/config/LunaSettings.csv`).
 *
 * LunaLib is optional (ruling 2026-10-01): these defaults are what the mod runs with when it is
 * absent, and they match the mod's look and sound before the settings existed. [LunaSettingsBridge]
 * overwrites them at application load and on every save of the settings page. No LunaLib types in
 * here, so this object loads without LunaLib.
 */
object CovenantSettings {
    /** LunaLib's mod id, for the enabled check that gates [LunaSettingsBridge]. */
    const val LUNALIB_ID = "lunalib"

    /** Field ids in `LunaSettings.csv`. */
    const val PARTICLE_DENSITY_ID = "covenant_remnants_particle_density"
    const val HEAT_HAZE_ID = "covenant_remnants_heat_haze"
    const val SHIELD_DISTORTION_ID = "covenant_remnants_shield_distortion"
    const val EFFECT_VOLUME_ID = "covenant_remnants_effect_volume"

    const val PARTICLE_DENSITY_MIN = 0.1f
    const val PARTICLE_DENSITY_MAX = 1f
    const val EFFECT_VOLUME_MIN = 0f
    const val EFFECT_VOLUME_MAX = 1f

    /**
     * Particle multiplier for the rupture renderer (fleet jump, blink, Ark portals). Stacks with
     * `FleetJumpTune`'s `PARTICLE_DENSITY`, which stays a dev knob.
     */
    var particleDensity = 1f

    /** Heat-haze screen-copy sprites of the rupture renderer; off skips the full-screen copy. */
    var heatHaze = true

    /** Covenant shield hit refraction rings; off skips the per-ship screen copy. */
    var shieldDistortion = true

    /** Volume multiplier for sounds the mod plays from code. Weapon fire from `sounds.json` is not covered. */
    var effectVolume = 1f
}
