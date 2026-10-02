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
 * The Halo: Reach bitmaps the rupture and the plasma torpedoes are drawn with, exported from the
 * official Reach editing kit (`graphics/fx/slipspace_rupture/`, `graphics/fx/plasma_torpedo/`,
 * registered in `data/config/settings.json` under [CATEGORY]). Noise maps and sphere-wrapped maps
 * tile; palettes and sprite shapes clamp.
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
    DISTORTION_RING("rupture_distortion_ring", false),

    // --- Plasma torpedo (m45 space Phantom torpedo) ---

    /** fx\bitmaps\contrails\hunter_beam: band along u (the trail's length) */
    TORPEDO_TRAIL("torpedo_hunter_beam", true),

    /** fx\bitmaps\weapon_fire\muzzle_flash_round_fiery: 2 frames, repacked as a 2x2 atlas */
    TORPEDO_MUZZLE_ROUND("torpedo_muzzle_flash_round", false),

    /** fx\bitmaps\weapon_fire\gradients\muzzle_flare_phantom_turret: violet to white */
    TORPEDO_MUZZLE_PALETTE("torpedo_muzzle_flare_phantom_turret", false),

    /** fx\bitmaps\flash\flash_large */
    TORPEDO_FLASH("torpedo_flash_large", false),

    /** fx\bitmaps\atmospheric_rolling\fireball_plate: the fireball's palette index */
    TORPEDO_FIREBALL_PLATE("torpedo_fireball_plate", true),

    /** fx\particles\_gradients\fireball_covenant: rows animate over the fireball's life */
    TORPEDO_FIREBALL_PALETTE("torpedo_fireball_covenant", false),

    /** fx\bitmaps\atmospheric_rolling\fireball_mask (shape moved into alpha) */
    TORPEDO_FIREBALL_MASK("torpedo_fireball_mask", false),

    /** fx\bitmaps\plasma\plasma_clouds_256_b */
    TORPEDO_NOISE_256_B("torpedo_plasma_clouds_256_b", true),

    /** fx\particles\plasma\gradients\blue_plasma */
    TORPEDO_PLASMA_PALETTE("torpedo_blue_plasma", false),

    /** fx\bitmaps\atmospheric\smoke_fiery_small: the impact plasma's shape */
    TORPEDO_SMOKE_SMALL("torpedo_smoke_fiery_small", false),

    /** fx\bitmaps\atmospheric\soft_smoke_large */
    TORPEDO_SMOKE_LARGE("torpedo_soft_smoke_large", false),

    /** fx\bitmaps\newticles\spark_long_round: the impact's cheap-particle sparks */
    TORPEDO_SPARK("torpedo_spark_long_round", false),

    /** fx\bitmaps\lens_flares\simple_flare */
    TORPEDO_FLARE_SIMPLE("torpedo_simple_flare", false),

    /** fx\bitmaps\lens_flares\anamorphic_flare: a streak along v */
    TORPEDO_FLARE_ANAMORPHIC("torpedo_anamorphic_flare", false);

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
