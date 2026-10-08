package org.combustiblelemon.covenantremnants.utils.render.plasmabeam

import org.combustiblelemon.covenantremnants.utils.render.rupture.ReachColorCurve
import org.combustiblelemon.covenantremnants.utils.render.rupture.ReachCurve

/**
 * The Halo: Reach focus rifle's curves (`objects\weapons\rifle\focus_rifle\fx\`), 9 samples over
 * the input 0..1, from the official editing kit's tags. Extraction and decoding:
 * `assets/reference/plasma-beam/reach-data/spec.md`. Colours are Reach's own (developer ruling
 * 2026-10-08: no hue rotation).
 */
internal object PlasmaBeamCurves {
    // --- projectile_3p.beam_system: center (input: profile position = along the beam) ---
    val CORE_COLOR = ReachColorCurve(floatArrayOf(
        1f, 0.7647f, 0.4627f, 1f, 0.7608f, 0.4627f, 1f, 0.7608f, 0.4588f,
        1f, 0.749f, 0.4588f, 1f, 0.7373f, 0.451f, 1f, 0.7098f, 0.4431f,
        1f, 0.6824f, 0.4275f, 1f, 0.6431f, 0.4196f, 1f, 0.5882f, 0.4f
    ))
    val CORE_INTENSITY = ReachCurve(floatArrayOf(1f, 1f, 1f, 1f, 1f, 1f, 1f, 2.0855f, 4f))

    // --- projectile_3p.beam_system: pulse (input: beam age over each round's 0.2 s) ---
    val PULSE_THICKNESS = ReachCurve(floatArrayOf(0.048f, 0.0918f, 0.1308f, 0.1647f, 0.1933f, 0.2213f, 0.2371f, 0.2467f, 0.25f))
    val PULSE_COLOR = ReachColorCurve(floatArrayOf(
        1f, 0.6627f, 0.4039f, 1f, 0.6627f, 0.4039f, 1f, 0.6627f, 0.4039f,
        1f, 0.6627f, 0.4039f, 1f, 0.6627f, 0.4039f, 1f, 0.6157f, 0.3804f,
        0.9804f, 0.5137f, 0.4f, 0.6353f, 0.4627f, 0.8667f, 0.3529f, 0.4353f, 1f
    ))
    val PULSE_INTENSITY = ReachCurve(floatArrayOf(2f, 2f, 2f, 2f, 2f, 1.8417f, 1.4415f, 0.8203f, 0f))
    val PULSE_BLACK_POINT = ReachCurve(floatArrayOf(0f, 0f, 0f, 0f, 0f, 0.1912f, 0.5299f, 0.8548f, 1f))

    // --- firing_3p.light_volume_system inner_glow (input: profile position along the cone) ---
    val LIGHT_VOLUME_THICKNESS = ReachCurve(floatArrayOf(0.15f, 0.1232f, 0.1014f, 0.0842f, 0.071f, 0.0596f, 0.054f, 0.0509f, 0.05f))
    val LIGHT_VOLUME_INTENSITY = ReachCurve(floatArrayOf(2f, 1.7322f, 1.5144f, 1.342f, 1.2104f, 1.0962f, 1.0397f, 1.0092f, 1f))

    // --- firing.effect flash_large (input: particle age over 0.085 s) ---
    val FLASH_SCALE = ReachCurve(floatArrayOf(0.5f, 0.5606f, 0.6212f, 0.6818f, 0.7424f, 0.8182f, 0.8788f, 0.9394f, 1f))
    val FLASH_ALPHA = ReachCurve(floatArrayOf(0.5f, 0.4814f, 0.4318f, 0.3605f, 0.2769f, 0.1691f, 0.0917f, 0.0322f, 0f))

    // --- firing.effect spark_ember (input: particle age) ---
    val EMBER_TINT = ReachColorCurve(floatArrayOf(
        1f, 0.8941f, 0.5608f, 0.9922f, 0.8392f, 0.5294f, 0.9843f, 0.7922f, 0.502f,
        0.9804f, 0.7451f, 0.4706f, 0.9765f, 0.7059f, 0.4431f, 0.9725f, 0.6588f, 0.4196f,
        0.9725f, 0.6314f, 0.4039f, 0.9686f, 0.6118f, 0.3882f, 0.9686f, 0.6f, 0.3804f
    ))
    val EMBER_ALPHA = ReachCurve(floatArrayOf(1f, 0.7405f, 0.5267f, 0.355f, 0.2217f, 0.1037f, 0.0437f, 0.0103f, 0f))

    // --- muzzle_flash_plasma.particle (input: particle age) ---
    val MUZZLE_PLASMA_SCALE = ReachCurve(floatArrayOf(0.25f, 0.2915f, 0.3622f, 0.4547f, 0.5615f, 0.7036f, 0.815f, 0.9163f, 1f))
    val MUZZLE_PLASMA_BLACK_POINT_A = ReachCurve(floatArrayOf(0f, 0.0314f, 0.1164f, 0.2411f, 0.3917f, 0.5954f, 0.7539f, 0.8933f, 1f))
    val MUZZLE_PLASMA_BLACK_POINT_B = ReachCurve(floatArrayOf(0f, 0.0136f, 0.0507f, 0.1062f, 0.1748f, 0.2707f, 0.3493f, 0.4238f, 0.4889f))
    val MUZZLE_PLASMA_COLOR = ReachColorCurve(floatArrayOf(
        1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
        1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
        0.9922f, 0.9137f, 0.9137f, 0.9765f, 0.6667f, 0.6667f, 0.9686f, 0.3059f, 0.3059f
    ))
    /** Shared by the muzzle and impact plasma. */
    val PLASMA_ALPHA = ReachCurve(floatArrayOf(1f, 1f, 1f, 1f, 1f, 1f, 0.8091f, 0.3664f, 0f))

    // --- impact_plasma.particle (input: particle age) ---
    val IMPACT_PLASMA_SCALE = ReachCurve(floatArrayOf(0.5f, 0.7456f, 0.9089f, 1f, 1f, 1f, 1f, 1f, 1f))
    val IMPACT_PLASMA_ASPECT = ReachCurve(floatArrayOf(0f, 0.0947f, 0.2434f, 0.4165f, 0.5949f, 0.7813f, 0.9095f, 0.9796f, 1f))
    val IMPACT_PLASMA_BLACK_POINT_A = ReachCurve(floatArrayOf(0f, 0.017f, 0.0667f, 0.1472f, 0.2566f, 0.4311f, 0.5987f, 0.7889f, 1f))
    val IMPACT_PLASMA_BLACK_POINT_B = ReachCurve(floatArrayOf(0f, 0.0064f, 0.0259f, 0.0593f, 0.1072f, 0.1886f, 0.2717f, 0.3716f, 0.4889f))
    val IMPACT_PLASMA_COLOR = ReachColorCurve(floatArrayOf(
        1f, 1f, 1f, 1f, 0.9804f, 0.9804f, 1f, 0.9412f, 0.9294f,
        1f, 0.8784f, 0.851f, 1f, 0.7922f, 0.749f, 1f, 0.6706f, 0.5922f,
        1f, 0.5725f, 0.4431f, 1f, 0.4863f, 0.2824f, 1f, 0.4471f, 0.1804f
    ))

    // --- impact_flash.particle (input: particle age) ---
    val IMPACT_FLASH_INTENSITY = ReachCurve(floatArrayOf(5f, 3.7853f, 2.8442f, 2.142f, 1.644f, 1.2558f, 1.0906f, 1.0169f, 1f))
    val IMPACT_FLASH_ALPHA = ReachCurve(floatArrayOf(1f, 0.9925f, 0.966f, 0.9145f, 0.8322f, 0.6769f, 0.5034f, 0.2797f, 0f))

    // --- spark_long.particle at the impact (input: particle age) ---
    val SPARK_LONG_ASPECT = ReachCurve(floatArrayOf(1f, 9.8866f, 8.6609f, 7.4351f, 6.2094f, 4.6772f, 3.4515f, 2.2257f, 1f))
    val SPARK_LONG_SCALE = ReachCurve(floatArrayOf(0f, 0.2956f, 0.5913f, 0.8869f, 0.9992f, 0.9726f, 0.8985f, 0.7481f, 0.4944f))
    val SPARK_LONG_INTENSITY = ReachCurve(floatArrayOf(5f, 4.1359f, 3.3954f, 2.7692f, 2.2484f, 1.7318f, 1.415f, 1.1741f, 1f))
    val SPARK_LONG_ALPHA = ReachCurve(floatArrayOf(1f, 1f, 1f, 1f, 1f, 0.9044f, 0.5719f, 0.1996f, 0f))

    // --- impact.cheap_light intensity (input: age over the 0.1 s impact event) ---
    val IMPACT_LIGHT = ReachCurve(floatArrayOf(10f, 4.6904f, 3.4631f, 2.6176f, 1.9521f, 1.268f, 0.7992f, 0.3802f, 0f))
}
