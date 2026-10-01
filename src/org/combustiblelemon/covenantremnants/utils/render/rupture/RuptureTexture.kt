package org.combustiblelemon.covenantremnants.utils.render.rupture

import com.fs.starfarer.api.Global
import org.lwjgl.opengl.GL11.GL_LINEAR
import org.lwjgl.opengl.GL11.GL_REPEAT
import org.lwjgl.opengl.GL11.GL_TEXTURE_2D
import org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER
import org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER
import org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_S
import org.lwjgl.opengl.GL11.GL_TEXTURE_WRAP_T
import org.lwjgl.opengl.GL11.glBindTexture
import org.lwjgl.opengl.GL11.glTexParameteri
import org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE

/**
 * The Halo: Reach bitmaps the rupture is drawn with, exported from the official Reach editing kit
 * (`graphics/fx/slipspace_rupture/`, registered in `data/config/settings.json` under
 * [CATEGORY]). Noise maps and sphere-wrapped maps tile; palettes and sprite shapes clamp.
 */
internal enum class RuptureTexture(private val spriteId: String, private val tiles: Boolean) {
    /** fx\particles\models\mesh_fx\slipspace_sphere\bitmaps\plasma_clouds01 */
    SPHERE_NOISE_A("rupture_plasma_clouds01", true),

    /** ...\slipspace_sphere\bitmaps\plasma_clouds02 */
    SPHERE_NOISE_B("rupture_plasma_clouds02", true),

    /** ...\slipspace_sphere\bitmaps\plasma_clouds: violet only near index 0 (the lightning web) */
    SPHERE_WEB_PALETTE("rupture_sphere_web_palette", false),

    /** ...\slipspace_sphere\bitmaps\slipspace_sphere_alpha: jagged fade at the poles */
    SPHERE_POLE_MASK("rupture_slipspace_sphere_alpha", true),

    /** fx\bitmaps\plasma\plasma_clouds_512_a */
    NOISE_512_A("rupture_plasma_clouds_512_a", true),

    /** fx\bitmaps\plasma\plasma_clouds_512_b */
    NOISE_512_B("rupture_plasma_clouds_512_b", true),

    /** fx\bitmaps\plasma\plasma_clouds_256_a */
    NOISE_256_A("rupture_plasma_clouds_256_a", true),

    /** fx\bitmaps\fire\fire_clouds_256 */
    FIRE_CLOUDS("rupture_fire_clouds_256", true),

    /** cinematics\045lf_jorge\fx\030\bitmaps\slipspace_init_gradient: blue band, one row per sprite */
    CHARGE_PALETTE("rupture_slipspace_init_gradient", false),

    /** cinematics\045lf_jorge\fx\030\bitmaps\slipspace_plasma_gradient */
    BURST_PALETTE("rupture_slipspace_plasma_gradient", false),

    /** cinematics\045lf_jorge\fx\030\bitmaps\slipspace_burst_sphere_gradient */
    SHELL_PALETTE("rupture_slipspace_burst_sphere_gradient", false),

    /** fx\particles\plasma\gradients\soft_plasma_blue */
    FLAME_PALETTE("rupture_soft_plasma_blue", false),

    /** fx\bitmaps\atmospheric\thin_smoke_large: charge sprite shape */
    THIN_SMOKE("rupture_thin_smoke_large", false),

    /** fx\bitmaps\atmospheric\smoke_fiery_large: burst cloud and smoke shape */
    FIERY_SMOKE("rupture_smoke_fiery_large", false),

    /** fx\bitmaps\fire\fire_large_vertical: plasma flame shape */
    FLAME_SHAPE("rupture_fire_large_vertical", false),

    /** fx\bitmaps\contrails\beam: band across v (the blast shell's ring) */
    BEAM("rupture_beam", true),

    /** fx\bitmaps\sparks\spark_ember */
    SPARK("rupture_spark_ember", false),

    /** fx\bitmaps\weather\glitter: 2x2 frames */
    GLITTER("rupture_glitter", false),

    /** fx\bitmaps\lens_flares\star_flare */
    STAR_FLARE("rupture_star_flare", false),

    /** fx\bitmaps\lens_flares\light_volume_glow */
    GLOW("rupture_light_volume_glow", false),

    /** fx\bitmaps\distortion\distortion_ring: RG = screen offset, 0.5 = none */
    DISTORTION_RING("rupture_distortion_ring", false);

    private var textureId = -1
    private var configured = false

    /** Binds the texture to the active unit, setting its sampling the first time (GL thread). */
    fun bind() {
        if (textureId < 0) textureId = Global.getSettings().getSprite(CATEGORY, spriteId).textureId
        glBindTexture(GL_TEXTURE_2D, textureId)
        if (!configured) {
            val wrap = if (tiles) GL_REPEAT else GL_CLAMP_TO_EDGE
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, wrap)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, wrap)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            configured = true
        }
    }

    companion object {
        const val CATEGORY = "covenant_remnants"
    }
}
