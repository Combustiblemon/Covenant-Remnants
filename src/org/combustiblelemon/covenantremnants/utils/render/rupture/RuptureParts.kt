package org.combustiblelemon.covenantremnants.utils.render.rupture

/**
 * Reach's animated texture transform for a noise map: a uniform scale and a translation that loops
 * with its period (`travel` texture widths per period; 0 period = static).
 */
internal class NoiseTransform(
    private val scale: Float,
    private val periodX: Float = 0f,
    private val travelX: Float = 0f,
    private val periodY: Float = 0f,
    private val travelY: Float = 0f
) {
    /** Writes (scale, scale, offset x, offset y) at [time] into [out]. */
    fun write(time: Float, out: FloatArray) {
        out[0] = scale
        out[1] = scale
        out[2] = loop(time, periodX) * travelX
        out[3] = loop(time, periodY) * travelY
    }

    private fun loop(time: Float, period: Float): Float {
        if (period <= 0f) return 0f
        val x = time / period
        return x - kotlin.math.floor(x)
    }
}

/**
 * How a batch of sprites is drawn: the Reach particle shader it stands for, as a
 * ruptureSprite.frag mode plus its textures and parameters.
 */
internal enum class SpriteMaterial(
    val mode: Int,
    val blend: Blend,
    val baseMap: RuptureTexture,
    val noiseB: RuptureTexture? = null,
    val palette: RuptureTexture? = null,
    val alphaMap: RuptureTexture? = null,
    val xformA: NoiseTransform? = null,
    val xformB: NoiseTransform? = null,
    val warp: Float = 0f,
    val atlasCells: Int = 1
) {
    /** slipspace_init.particle: palettized_2d_plasma, sphere warp -1, black point. */
    CHARGE_PLASMA(
        MODE_PLASMA, Blend.ADDITIVE, RuptureTexture.NOISE_512_A, RuptureTexture.NOISE_512_B,
        RuptureTexture.CHARGE_PALETTE, RuptureTexture.THIN_SMOKE,
        NoiseTransform(0.75f, 1f, 1f, 1f, -1f), NoiseTransform(0.75f, 1f, -1f, 1f, 1f), warp = -1f
    ),

    /** slipspace_burst_plasma.particle: palettized_2d_plasma, sphere warp 0.35, black point. */
    BURST_PLASMA(
        MODE_PLASMA, Blend.ADDITIVE, RuptureTexture.NOISE_512_A, RuptureTexture.NOISE_512_B,
        RuptureTexture.BURST_PALETTE, RuptureTexture.FIERY_SMOKE,
        NoiseTransform(0.7f, 8f, 1f, 9f, 1f), NoiseTransform(0.6f, 7f, -1f, 6f, 1f), warp = 0.35f
    ),

    /** plasma_vertical.particle: palettized_2d_plasma flames, sphere warp 0.5. */
    PLASMA_FLAME(
        MODE_PLASMA, Blend.ADDITIVE, RuptureTexture.NOISE_256_A, RuptureTexture.FIRE_CLOUDS,
        RuptureTexture.FLAME_PALETTE, RuptureTexture.FLAME_SHAPE,
        NoiseTransform(0.5f, 8f, 1f, 9f, -1f), NoiseTransform(0.5f, 9f, -1f, 9f, 1f), warp = 0.5f
    ),

    /** slipspace_charge_sparks.particle: spark_ember, additive. */
    SPARK(MODE_TEXTURE_ADD, Blend.ADDITIVE, RuptureTexture.SPARK),

    /** slipspace_twinkle.particle: glitter (2x2 frames), alpha blend. */
    TWINKLE(MODE_TEXTURE_ALPHA, Blend.ALPHA, RuptureTexture.GLITTER, atlasCells = 2),

    /** smoke_fiery_large.particle: white base shaped by smoke_fiery_large, additive. */
    SMOKE(MODE_SMOKE, Blend.ADDITIVE, RuptureTexture.FIERY_SMOKE, alphaMap = RuptureTexture.FIERY_SMOKE),

    /** Lens flares (slip_space_secondary_star / slip_space_collapse). */
    FLARE_STAR(MODE_TEXTURE_ADD, Blend.ADDITIVE, RuptureTexture.STAR_FLARE),
    FLARE_GLOW(MODE_TEXTURE_ADD, Blend.ADDITIVE, RuptureTexture.GLOW),

    /** distortion_ring.particle: heat-haze ring over a copy of the screen. */
    DISTORTION(MODE_DISTORTION, Blend.ALPHA, RuptureTexture.DISTORTION_RING),

    // --- Plasma torpedo (m45 space Phantom torpedo, see utils/render/torpedo) ---

    /** projectile.light_volume_system hot_core: light_volume_glow glows along the body. */
    TORPEDO_BODY(MODE_TEXTURE_ADD, Blend.ADDITIVE, RuptureTexture.GLOW),

    /** projectile.lens_flare: star_flare. */
    TORPEDO_STAR(MODE_TEXTURE_ADD, Blend.ADDITIVE, RuptureTexture.STAR_FLARE),

    /** projectile.contrail_system: hunter_beam ribbon, black point. */
    TORPEDO_TRAIL(MODE_TEXTURE_ADD, Blend.ADDITIVE, RuptureTexture.TORPEDO_TRAIL),

    /** muzzle_flash_round.particle: palettized_plus_sprite_alpha, 2 frames. */
    TORPEDO_MUZZLE_ROUND(
        MODE_PALETTE_ADD, Blend.ADDITIVE, RuptureTexture.TORPEDO_MUZZLE_ROUND,
        palette = RuptureTexture.TORPEDO_MUZZLE_PALETTE, atlasCells = 2
    ),

    /** flash_large.particle: diffuse_only flash. */
    TORPEDO_FLASH(MODE_TEXTURE_ADD, Blend.ADDITIVE, RuptureTexture.TORPEDO_FLASH),

    /** fireball_space_covenant.particle: palettized_plus_billboard_alpha, sphere warp 0.8. */
    TORPEDO_FIREBALL(
        MODE_PALETTE_ALPHA, Blend.ALPHA, RuptureTexture.TORPEDO_FIREBALL_PLATE,
        palette = RuptureTexture.TORPEDO_FIREBALL_PALETTE, alphaMap = RuptureTexture.TORPEDO_FIREBALL_MASK,
        warp = 0.8f
    ),

    /** plasma_blue_space_large.particle: palettized_2d_plasma, sphere warp 0.45. */
    TORPEDO_PLASMA(
        MODE_PLASMA, Blend.ADDITIVE, RuptureTexture.NOISE_256_A, RuptureTexture.TORPEDO_NOISE_256_B,
        RuptureTexture.TORPEDO_PLASMA_PALETTE, RuptureTexture.TORPEDO_SMOKE_SMALL,
        NoiseTransform(1.8f, 5f, 1f, 4f, 1f), NoiseTransform(1.5f, 2f, -1f, 3f, 1f), warp = 0.45f
    ),

    /** fireball_smoke.particle: dark smoke, pre-multiplied alpha (drawn alpha-blended). */
    TORPEDO_SMOKE(MODE_TEXTURE_ALPHA, Blend.ALPHA, RuptureTexture.TORPEDO_SMOKE_LARGE),

    /** impact.cheap_particle_emitter: spark_orange_space streaks. */
    TORPEDO_SPARK(MODE_TEXTURE_ADD, Blend.ADDITIVE, RuptureTexture.TORPEDO_SPARK),

    /** impact.lens_flare and impact_anamorphic.lens_flare. */
    TORPEDO_FLARE_SIMPLE(MODE_TEXTURE_ADD, Blend.ADDITIVE, RuptureTexture.TORPEDO_FLARE_SIMPLE),
    TORPEDO_FLARE_ANAMORPHIC(MODE_TEXTURE_ADD, Blend.ADDITIVE, RuptureTexture.TORPEDO_FLARE_ANAMORPHIC);

    enum class Blend { ADDITIVE, ALPHA }
}

internal const val MODE_PLASMA = 0
internal const val MODE_TEXTURE_ADD = 1
internal const val MODE_SMOKE = 2
internal const val MODE_TEXTURE_ALPHA = 3
internal const val MODE_DISTORTION = 4
internal const val MODE_PALETTE_ADD = 5
internal const val MODE_PALETTE_ALPHA = 6

/** Which Reach particle system a sprite comes from; picks its material and its update rules. */
internal enum class SpriteKind(val material: SpriteMaterial) {
    CHARGE(SpriteMaterial.CHARGE_PLASMA),
    DISTORTION(SpriteMaterial.DISTORTION),
    SPARK(SpriteMaterial.SPARK),
    BURST_PLASMA(SpriteMaterial.BURST_PLASMA),
    PLASMA_FLAME(SpriteMaterial.PLASMA_FLAME),
    TWINKLE(SpriteMaterial.TWINKLE),
    SMOKE(SpriteMaterial.SMOKE),
    HAZE(SpriteMaterial.SMOKE),

    // Plasma torpedo (utils/render/torpedo)
    TORPEDO_BODY(SpriteMaterial.TORPEDO_BODY),
    TORPEDO_STAR(SpriteMaterial.TORPEDO_STAR),
    TORPEDO_MUZZLE_ROUND(SpriteMaterial.TORPEDO_MUZZLE_ROUND),
    TORPEDO_FLASH(SpriteMaterial.TORPEDO_FLASH),
    TORPEDO_FIREBALL(SpriteMaterial.TORPEDO_FIREBALL),
    TORPEDO_PLASMA(SpriteMaterial.TORPEDO_PLASMA),
    TORPEDO_SMOKE(SpriteMaterial.TORPEDO_SMOKE),
    TORPEDO_SPARK(SpriteMaterial.TORPEDO_SPARK),
    TORPEDO_FLARE_SIMPLE(SpriteMaterial.TORPEDO_FLARE_SIMPLE),
    TORPEDO_FLARE_ANAMORPHIC(SpriteMaterial.TORPEDO_FLARE_ANAMORPHIC)
}

/**
 * One particle. Spawn state and physics are set by [RuptureEffect]; the render values below are
 * refreshed by it every frame (sizes in world units, colour already multiplied by intensity).
 */
internal class RuptureSprite(val kind: SpriteKind) {
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var drag = 0f
    var age = 0f
    var life = 1f
    var random = 0f
    var random2 = 0f
    var baseSize = 0f
    var baseAlpha = 1f
    var frame = 0
    var phase = 0f

    var halfWidth = 0f
    var halfLength = 0f
    var angle = 0f
    var offsetAlong = 0f
    val color = FloatArray(3)
    var alpha = 0f
    var paletteV = 0f
    var blackPoint = 0f
    var uvScale = 1f
    var noiseOffset = 0f

    val isDead: Boolean get() = age >= life
}

/** How a sphere mode's output combines with the screen. */
internal enum class SphereBlend { ALPHA, ADD, MULTIPLY }

/** Sphere modes of ruptureSphere.frag (see there). */
internal enum class SphereMode(val id: Int, val blend: SphereBlend) {
    GLOW(0, SphereBlend.ALPHA),
    BLACK(1, SphereBlend.MULTIPLY),
    WEB(2, SphereBlend.ADD),
    SHELL(3, SphereBlend.ADD),
    PLASMA_SHELL(4, SphereBlend.ADD),
    RING(5, SphereBlend.ADD),
    LIGHT(6, SphereBlend.ADD),
    CORE(7, SphereBlend.ALPHA)
}

/** One analytic sphere to draw this frame; filled by [RuptureEffect]. Radius in world units. */
internal class SphereDraw(val mode: SphereMode) {
    var active = false
    var radius = 0f
    val color = floatArrayOf(1f, 1f, 1f)
    var intensity = 1f
    var alpha = 1f
    var halo = 0f
    var haloWidth = 0.1f
    var ringWidth = 0.012f
    var lightStart = 0.8f
    /** BLACK: Reach's particle angle fade, degrees from edge-on (0 range = off). */
    var angleFadeCutoff = 0f
    var angleFadeRange = 0f
    /** BLACK: the glow sphere under it (HDR colour × intensity, radius in this sphere's radii). */
    val underHdr = floatArrayOf(0f, 0f, 0f)
    var underRadius = 0f
    var noiseA: RuptureTexture? = null
    var noiseB: RuptureTexture? = null
    var palette: RuptureTexture? = null
    var alphaMap: RuptureTexture? = null
    var xformA: NoiseTransform? = null
    var xformB: NoiseTransform? = null
    var paletteV = 0f
    var uOffset = 0f
}

/** One lens flare to draw this frame (an additive textured sprite at the rupture center). */
internal class FlareDraw(val material: SpriteMaterial) {
    var active = false
    var radius = 0f
    val color = floatArrayOf(1f, 1f, 1f)
    var brightness = 1f
}
