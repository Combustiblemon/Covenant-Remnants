package org.combustiblelemon.covenantremnants.utils.render.projector

import org.combustiblelemon.covenantremnants.utils.render.rupture.ReachColorCurve
import org.combustiblelemon.covenantremnants.utils.render.rupture.ReachCurve

/**
 * The Halo: Reach CCS cruiser glassing beam's curves (`objects\vehicles\covenant\covenant_fleet\
 * cruiser\fx\glassing\`, plus the m70 gameplay variants), 9 samples over the input 0..1, from the
 * official editing kit's tags. Extraction and decoding: `assets/reference/energy-projector/
 * reach-data/spec.md`. Colours are Reach's orange family hue-rotated to the mod's violet
 * (developer ruling 2026-10-08: white core, purple fringe); the rotation is in
 * `reach-data/tools/build_textures.py` and `tints.md`.
 */
internal object ProjectorCurves {
    // --- charge event: emp_charge (input: system age over the 3.5 s charge; particle age for scale/alpha) ---
    val EMP_SIZE = ReachCurve(floatArrayOf(0.5f, 0.7321f, 1.2295f, 1.9933f, 3.0242f, 4.69f, 6.3253f, 7f, 7f))
    val EMP_SCALE = ReachCurve(floatArrayOf(1f, 2.7229f, 4.0864f, 5.1311f, 5.8978f, 6.5271f, 6.817f, 6.961f, 7f))
    val EMP_ALPHA = ReachCurve(floatArrayOf(1f, 1f, 1f, 1f, 1f, 0.9049f, 0.5593f, 0.1791f, 0f))

    // --- charge event: explosive_flash (input: system age for size; particle age for intensity/alpha) ---
    val FLASH_SIZE = ReachCurve(floatArrayOf(0f, 0.1344f, 0.4179f, 0.8552f, 1.451f, 2.4255f, 3.3946f, 4f, 4f))
    val FLASH_INTENSITY = ReachCurve(floatArrayOf(15f, 15f, 15f, 15f, 14.3444f, 12.4162f, 9.6891f, 5.6348f, 0f))
    val FLASH_ALPHA = ReachCurve(floatArrayOf(1f, 1f, 1f, 1f, 1f, 0.8317f, 0.4894f, 0.1523f, 0f))

    // --- glassing_charge.lens_flare age brightness (input: age over its 11 s event) ---
    val CHARGE_FLARE_BRIGHTNESS = ReachCurve(floatArrayOf(0f, 0.3757f, 0.9254f, 1f, 1f, 0.8204f, 0.4798f, 0.1487f, 0f))

    // --- m70 glassing_charge.light intensity (input: age over 25 s; only the rise is used) ---
    val CHARGE_LIGHT = ReachCurve(floatArrayOf(0f, 0.0195f, 0.1152f, 0.2592f, 0.4312f, 0.6544f, 0.8147f, 0.9367f, 1f))

    // --- glow event: glassing_flicker_glow (input: particle age) ---
    val FLICKER_SCALE = ReachCurve(floatArrayOf(0.25f, 0.558f, 0.7735f, 0.9121f, 0.9896f, 1f, 1f, 1f, 1f))
    val FLICKER_TINT = ReachColorCurve(floatArrayOf(
        0.274f, 0.362f, 1f, 0.435f, 0.486f, 1f, 0.529f, 0.553f, 1f,
        0.592f, 0.592f, 1f, 0.655f, 0.639f, 1f, 0.713f, 0.682f, 1f,
        0.742f, 0.706f, 1f, 0.761f, 0.718f, 1f, 0.766f, 0.722f, 1f
    ))
    val FLICKER_ALPHA = ReachCurve(floatArrayOf(0.15f, 0.15f, 0.15f, 0.15f, 0.15f, 0.1456f, 0.0969f, 0.0323f, 0f))

    // --- flash_lens_flare event: glassing_glow.lens_flare at the far end (input: age over 1.25 s) ---
    val FAR_FLARE_BRIGHTNESS = ReachCurve(floatArrayOf(0f, 1.0526f, 4.0348f, 8.6837f, 14.736f, 23.8742f, 32.1222f, 40.9185f, 50f))
    val FAR_FLARE_AGE = ReachCurve(floatArrayOf(0.3f, 0.3f, 0.3f, 0.3f, 0.3f, 0.3f, 0.2868f, 0.1217f, 0f))

    // --- beam_impact event: glassing_bubble (input: particle age over 1.25 s) ---
    val BUBBLE_SCALE = ReachCurve(floatArrayOf(0.75f, 1.379f, 2.0155f, 2.6054f, 3.0944f, 3.5071f, 3.7089f, 3.8175f, 3.85f))
    val BUBBLE_TINT = ReachColorCurve(floatArrayOf(
        1f, 1f, 1f, 0.914f, 0.894f, 1f, 0.835f, 0.796f, 1f,
        0.875f, 0.714f, 0.878f, 0.635f, 0.813f, 0.922f, 0.557f, 0.686f, 0.965f,
        0.478f, 0.57f, 0.984f, 0.404f, 0.473f, 0.996f, 0.377f, 0.434f, 1f
    ))
    val BUBBLE_ALPHA = ReachCurve(floatArrayOf(0.7f, 0.7f, 0.7f, 0.7f, 0.5626f, 0.1713f, 0.0814f, 0.0222f, 0f))
    val BUBBLE_BLACK_POINT = ReachCurve(floatArrayOf(0f, 0f, 0f, 0f, 0.096f, 0.6379f, 0.8705f, 0.9755f, 1f))

    // --- beam_impact event: glow_soft_half (input: particle age over 1.5 s) ---
    val HALF_GLOW_SCALE = ReachCurve(floatArrayOf(1f, 1.0615f, 1.1178f, 1.1679f, 1.2111f, 1.2542f, 1.2791f, 1.2947f, 1.3f))
    val HALF_GLOW_TINT = ReachColorCurve(floatArrayOf(
        0.882f, 0.839f, 0.996f, 0.73f, 0.718f, 0.996f, 0.6f, 0.614f, 1f,
        0.494f, 0.526f, 1f, 0.404f, 0.451f, 1f, 0.306f, 0.358f, 1f,
        0.255f, 0.304f, 1f, 0.228f, 0.268f, 1f, 0.228f, 0.272f, 1f
    ))
    val HALF_GLOW_ALPHA = ReachCurve(floatArrayOf(1f, 0.8403f, 0.5496f, 0.3485f, 0.2055f, 0.0881f, 0.0341f, 0.0073f, 0f))

    // --- beam_impact event: glassing_plasma_burst (tube emitter; input: system age for radius, particle age otherwise) ---
    val BURST_RADIUS = ReachCurve(floatArrayOf(50f, 56.0606f, 62.1212f, 68.1818f, 74.2424f, 81.8182f, 87.8788f, 93.9394f, 100f))
    val BURST_SIZE = ReachCurve(floatArrayOf(20f, 20.468f, 21.9379f, 24.509f, 28.2801f, 34.8328f, 41.6667f, 50.0227f, 60f))
    val BURST_SCALE = ReachCurve(floatArrayOf(0.8f, 1.1549f, 1.4764f, 1.7608f, 2.0045f, 2.2462f, 2.3847f, 2.4706f, 2.5f))
    val BURST_ALPHA = ReachCurve(floatArrayOf(0f, 1f, 1f, 1f, 1f, 0.9435f, 0.6049f, 0.1976f, 0f))
    val BURST_BLACK_POINT = ReachCurve(floatArrayOf(0f, 0f, 0f, 0f, 0f, 0.0151f, 0.1417f, 0.354f, 0.6f))

    // --- vaporization event: glassing_plasma_huge (disc emitter; input: system age for radius/rate, particle age otherwise) ---
    val CLOUD_RADIUS = ReachCurve(floatArrayOf(50f, 116.6973f, 179.7923f, 237.7999f, 289.2353f, 342.025f, 373.2433f, 393.0633f, 400f))
    val CLOUD_RATE = ReachCurve(floatArrayOf(8f, 10.0606f, 12.1212f, 14.1818f, 16.2424f, 18.8182f, 20.8788f, 22.9394f, 25f))
    val CLOUD_SCALE = ReachCurve(floatArrayOf(1f, 1.463f, 1.8633f, 2.2019f, 2.4796f, 2.7423f, 2.886f, 2.9717f, 3f))
    val CLOUD_ALPHA = ReachCurve(floatArrayOf(0f, 0.7917f, 1f, 1f, 0.9307f, 0.6366f, 0.3444f, 0.1015f, 0f))
    val CLOUD_BLACK_POINT = ReachCurve(floatArrayOf(0f, 0f, 0f, 0f, 0.0025f, 0.1371f, 0.3637f, 0.6618f, 1f))

    // --- vaporization event: plasma_blue_huge (input: system age for rate, particle age otherwise) ---
    val BLUE_RATE = ReachCurve(floatArrayOf(10f, 13.0303f, 16.0606f, 19.0909f, 22.1212f, 25.9091f, 28.9394f, 31.9697f, 35f))
    val BLUE_SCALE = ReachCurve(floatArrayOf(2.5f, 2.6157f, 2.7158f, 2.8005f, 2.8699f, 2.9356f, 2.9715f, 2.9929f, 3f))
    val BLUE_BLACK_POINT = ReachCurve(floatArrayOf(1f, 0.2876f, 0.0002f, 0.2799f, 0.6148f, 0.7908f, 0.8877f, 0.9546f, 1f))
}
