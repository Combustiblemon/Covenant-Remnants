package org.combustiblelemon.covenantremnants.utils.render.shield

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

/**
 * The Halo: Reach noise maps the shield's plasma layer samples (`fx\bitmaps\plasma\plasma_clouds_512_a/b`,
 * shipped as `graphics/covenant_remnants/fx/shield/`, registered in `data/config/settings.json` under [CATEGORY]).
 * Same pattern as the rupture's `RuptureTexture`.
 */
internal enum class ShieldTexture(private val spriteId: String) {
    PLASMA_A("shield_plasma_clouds_a"),
    PLASMA_B("shield_plasma_clouds_b");

    private var textureId = -1
    private var configured = false

    /** Binds the texture to the active unit, setting tiling + linear sampling the first time (GL thread). */
    fun bind() {
        if (textureId < 0) textureId = Global.getSettings().getSprite(CATEGORY, spriteId).textureId
        glBindTexture(GL_TEXTURE_2D, textureId)
        if (!configured) {
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            configured = true
        }
    }

    companion object {
        const val CATEGORY = "covenant_remnants"
    }
}
