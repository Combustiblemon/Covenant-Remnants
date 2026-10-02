package org.combustiblelemon.covenantremnants.utils.render.torpedo

import org.combustiblelemon.covenantremnants.utils.render.rupture.ReachColorCurve
import org.combustiblelemon.covenantremnants.utils.render.rupture.ReachCurve

/**
 * The Halo: Reach space Phantom torpedo's curves (m45, `objects\vehicles\covenant\space_phantom\
 * fx\weapon\space_phantom_turret\`), 9 samples over the input 0..1, from the official editing kit's
 * tags. Extraction and decoding: `assets/reference/plasma-torpedo/reach-data/spec.md`.
 */
internal object TorpedoCurves {
    // --- projectile.light_volume_system hot_core (input: profile position, 0 = tail, 1 = head) ---
    val BODY_THICKNESS = ReachCurve(floatArrayOf(0.4f, 0.4f, 0.4f, 0.4f, 0.402f, 0.421f, 0.455f, 0.498f, 0.4f))
    val BODY_COLOR = ReachColorCurve(floatArrayOf(
        1f, 0.482f, 0.365f, 0.988f, 0.486f, 0.408f, 0.961f, 0.502f, 0.506f,
        0.918f, 0.518f, 0.616f, 0.867f, 0.533f, 0.722f, 0.808f, 0.553f, 0.816f,
        0.745f, 0.573f, 0.894f, 0.686f, 0.584f, 0.957f, 0.639f, 0.596f, 1f
    ))
    val BODY_ALPHA = ReachCurve(floatArrayOf(0f, 0.025f, 0.101f, 0.226f, 0.4f, 0.622f, 0.892f, 0.824f, 0f))
    val BODY_INTENSITY = ReachCurve(floatArrayOf(0.5f, 0.709f, 1.136f, 1.678f, 2.233f, 2.697f, 2.968f, 2.265f, 0.5f))

    // --- projectile.contrail_system contrail_0 (input: profile age over its 1 s life) ---
    val TRAIL_SIZE = ReachCurve(floatArrayOf(1.5f, 1.502f, 1.567f, 1.716f, 1.927f, 2.181f, 2.458f, 2.738f, 3f))
    val TRAIL_COLOR = ReachColorCurve(floatArrayOf(
        1f, 0.729f, 0.326f, 0.745f, 0.655f, 0.851f, 0.608f, 0.62f, 0.977f,
        0.577f, 0.616f, 1f, 0.577f, 0.616f, 1f, 0.577f, 0.616f, 1f,
        0.577f, 0.616f, 1f, 0.577f, 0.616f, 1f, 0.577f, 0.616f, 1f
    ))
    val TRAIL_ALPHA = ReachCurve(floatArrayOf(0f, 1f, 0.983f, 0.771f, 0.456f, 0.199f, 0.08f, 0.018f, 0f))
    val TRAIL_BLACK_POINT = ReachCurve(floatArrayOf(0f, 0f, 0f, 0.001f, 0.058f, 0.19f, 0.379f, 0.605f, 0.85f))
    val TRAIL_INTENSITY = ReachCurve(floatArrayOf(3.5f, 1.93f, 1.016f, 1f, 1f, 1f, 1f, 1f, 1f))

    // --- firing.effect muzzle_flash_round (input: particle age) ---
    val MUZZLE_ROUND_TINT = ReachColorCurve(floatArrayOf(
        1f, 1f, 1f, 0.984f, 0.98f, 1f, 0.953f, 0.937f, 1f,
        0.898f, 0.875f, 1f, 0.839f, 0.788f, 1f, 0.769f, 0.69f, 1f,
        0.698f, 0.592f, 1f, 0.647f, 0.51f, 1f, 0.627f, 0.471f, 1f
    ))
    val MUZZLE_ROUND_ALPHA = ReachCurve(floatArrayOf(1f, 1f, 1f, 1f, 1f, 1f, 0.985f, 0.743f, 0f))
    val MUZZLE_ROUND_BLACK_POINT_HOT = ReachCurve(floatArrayOf(0f, 0.017f, 0.069f, 0.152f, 0.266f, 0.41f, 0.581f, 0.778f, 1f))
    val MUZZLE_ROUND_BLACK_POINT_DIM = ReachCurve(floatArrayOf(0f, 0.011f, 0.042f, 0.09f, 0.15f, 0.221f, 0.297f, 0.377f, 0.456f))

    // --- firing.effect flash_large (input: particle age) ---
    val MUZZLE_FLASH_ALPHA = ReachCurve(floatArrayOf(1f, 1f, 1f, 1f, 0.956f, 0.851f, 0.67f, 0.392f, 0f))

    // --- impact fireball_space_covenant (input: particle age; two curves = per-particle random range) ---
    val FIREBALL_SCALE_A = ReachCurve(floatArrayOf(0.8f, 1.055f, 1.249f, 1.39f, 1.488f, 1.549f, 1.583f, 1.597f, 1.6f))
    val FIREBALL_SCALE_B = ReachCurve(floatArrayOf(0.8f, 0.963f, 1.093f, 1.194f, 1.269f, 1.32f, 1.352f, 1.368f, 1.372f))
    val FIREBALL_TINT = ReachColorCurve(floatArrayOf(
        1f, 1f, 1f, 0.988f, 0.988f, 1f, 0.976f, 0.976f, 1f,
        0.965f, 0.961f, 1f, 0.945f, 0.937f, 1f, 0.914f, 0.906f, 1f,
        0.875f, 0.859f, 1f, 0.812f, 0.784f, 1f, 0.718f, 0.678f, 1f
    ))
    val FIREBALL_ALPHA = ReachCurve(floatArrayOf(1f, 1f, 0.978f, 0.736f, 0.4f, 0.172f, 0.058f, 0.01f, 0f))
    val FIREBALL_BLACK_POINT = ReachCurve(floatArrayOf(0f, 0f, 0.032f, 0.324f, 0.666f, 0.84f, 0.939f, 0.987f, 1f))
    val FIREBALL_INTENSITY = ReachCurve(floatArrayOf(25f, 17.195f, 14.406f, 12.685f, 11.548f, 10.798f, 10.329f, 10.077f, 10f))
    val FIREBALL_PALETTE_A = ReachCurve(floatArrayOf(0f, 0.206f, 0.396f, 0.567f, 0.713f, 0.834f, 0.924f, 0.98f, 1f))
    val FIREBALL_PALETTE_B = ReachCurve(floatArrayOf(0f, 0.038f, 0.115f, 0.224f, 0.358f, 0.51f, 0.672f, 0.838f, 1f))

    // --- impact plasma_blue_space_large (input: particle age) ---
    val PLASMA_SCALE = ReachCurve(floatArrayOf(0.5f, 0.692f, 0.852f, 0.981f, 1.083f, 1.159f, 1.211f, 1.241f, 1.25f))
    val PLASMA_COLOR = ReachColorCurve(floatArrayOf(
        0.392f, 0.282f, 0.918f, 0.839f, 0.828f, 0.973f, 0.984f, 0.98f, 0.996f,
        1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f,
        1f, 1f, 1f, 0.863f, 0.851f, 0.977f, 0.392f, 0.282f, 0.918f
    ))
    val PLASMA_ALPHA = ReachCurve(floatArrayOf(1f, 1f, 1f, 1f, 0.773f, 0.39f, 0.16f, 0.037f, 0f))
    val PLASMA_BLACK_POINT = ReachCurve(floatArrayOf(0f, 0f, 0f, 0.005f, 0.138f, 0.389f, 0.672f, 0.904f, 1f))

    // --- impact fireball_smoke (input: particle age) ---
    val SMOKE_SCALE = ReachCurve(floatArrayOf(1f, 1.384f, 1.703f, 1.963f, 2.167f, 2.318f, 2.422f, 2.481f, 2.5f))
    val SMOKE_ALPHA = ReachCurve(floatArrayOf(1f, 1f, 1f, 1f, 0.998f, 0.823f, 0.482f, 0.149f, 0f))
    val SMOKE_BLACK_POINT = ReachCurve(floatArrayOf(0f, 0f, 0f, 0.002f, 0.057f, 0.157f, 0.27f, 0.362f, 0.4f))

    // --- impact.lens_flare / impact_anamorphic.lens_flare (0.8 s; see TorpedoEffects for the input) ---
    val FLARE_RADIUS = ReachCurve(floatArrayOf(10.945f, 13.844f, 16.732f, 19.439f, 21.794f, 23.627f, 24.765f, 25f, 5f))
    val FLARE_BRIGHTNESS = ReachCurve(floatArrayOf(0f, 0.036f, 0.141f, 0.314f, 0.551f, 0.851f, 1.211f, 1.828f, 7f))
    val FLARE_COLOR = ReachColorCurve(floatArrayOf(
        0.573f, 0.616f, 1f, 0.577f, 0.616f, 1f, 0.58f, 0.608f, 1f,
        0.596f, 0.596f, 1f, 0.62f, 0.577f, 1f, 0.647f, 0.545f, 1f,
        0.69f, 0.506f, 0.992f, 0.745f, 0.467f, 0.992f, 1f, 0.894f, 0.706f
    ))
    val ANAMORPHIC_BRIGHTNESS = ReachCurve(floatArrayOf(0f, 0.001f, 0.006f, 0.013f, 0.023f, 0.037f, 0.053f, 0.114f, 0.25f))
}
