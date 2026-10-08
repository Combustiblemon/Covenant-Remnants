package org.combustiblelemon.covenantremnants.campaign.entities

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignEngineLayers
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin
import java.awt.Color
import java.util.Random
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Pocket ambience (placeholder, developer 2026-10-07): the pocket is pitch black with a few small,
 * sparse light streams drifting past. One entity per pocket; streaks are spawned inside the
 * viewport while the player is there and drawn additively from the rupture beam sprite.
 */
class SlipspaceAmbienceEntity : BaseCustomEntityPlugin() {
    private companion object {
        const val MAX_STREAKS = 12
        const val SPAWN_PER_SECOND = 0.35f
        const val LENGTH_MIN = 150f
        const val LENGTH_MAX = 600f
        const val THICKNESS_MIN = 3f
        const val THICKNESS_MAX = 8f
        const val LIFE_MIN = 1.5f
        const val LIFE_MAX = 3.5f
        const val DRIFT_SPEED_MIN = 40f
        const val DRIFT_SPEED_MAX = 160f
        const val ALPHA_MAX = 0.35f
        const val SPRITE_CATEGORY = "covenant_remnants"
        const val SPRITE_ID = "rupture_beam"
    }

    private class Streak(
        val x0: Float, val y0: Float, val angle: Float, val length: Float, val thickness: Float,
        val life: Float, val speed: Float, val tint: Color
    ) {
        var age = 0f
        val x: Float get() = x0 + cos(Math.toRadians(angle.toDouble())).toFloat() * speed * age
        val y: Float get() = y0 + sin(Math.toRadians(angle.toDouble())).toFloat() * speed * age
        val alpha: Float
            get() {
                val t = age / life
                val fade = min(t / 0.25f, (1f - t) / 0.35f)
                return fade.coerceIn(0f, 1f) * ALPHA_MAX
            }
    }

    @Transient
    private var streaks: ArrayList<Streak>? = null

    @Transient
    private var random: Random? = null

    @Transient
    private var spawnAccumulator = 0f

    private fun list(): ArrayList<Streak> = streaks ?: ArrayList<Streak>().also { streaks = it }
    private fun rng(): Random = random ?: Random().also { random = it }

    override fun getRenderRange(): Float = Float.MAX_VALUE

    override fun advance(amount: Float) {
        val list = list()
        val it = list.iterator()
        while (it.hasNext()) {
            val s = it.next()
            s.age += amount
            if (s.age >= s.life) it.remove()
        }
        if (!entity.isInCurrentLocation || Global.getSector().isPaused) return

        spawnAccumulator += amount * SPAWN_PER_SECOND
        while (spawnAccumulator >= 1f && list.size < MAX_STREAKS) {
            spawnAccumulator -= 1f
            spawn(list)
        }
        if (spawnAccumulator > 1f) spawnAccumulator = 1f
    }

    private fun spawn(list: ArrayList<Streak>) {
        val viewport = Global.getSector().viewport ?: return
        val r = rng()
        val cx = viewport.center.x
        val cy = viewport.center.y
        val halfW = viewport.visibleWidth * 0.5f + LENGTH_MAX
        val halfH = viewport.visibleHeight * 0.5f + LENGTH_MAX
        val x = cx + (r.nextFloat() * 2f - 1f) * halfW
        val y = cy + (r.nextFloat() * 2f - 1f) * halfH
        val angle = r.nextFloat() * 360f
        val length = LENGTH_MIN + r.nextFloat() * (LENGTH_MAX - LENGTH_MIN)
        val thickness = THICKNESS_MIN + r.nextFloat() * (THICKNESS_MAX - THICKNESS_MIN)
        val life = LIFE_MIN + r.nextFloat() * (LIFE_MAX - LIFE_MIN)
        val speed = DRIFT_SPEED_MIN + r.nextFloat() * (DRIFT_SPEED_MAX - DRIFT_SPEED_MIN)
        val tint = if (r.nextBoolean()) Color(150, 170, 255) else Color(190, 150, 255)
        list.add(Streak(x, y, angle, length, thickness, life, speed, tint))
    }

    override fun render(layer: CampaignEngineLayers?, viewport: ViewportAPI?) {
        if (viewport == null) return
        val list = streaks ?: return
        if (list.isEmpty()) return
        val alphaMult = viewport.alphaMult
        if (alphaMult <= 0f) return
        val sprite = Global.getSettings().getSprite(SPRITE_CATEGORY, SPRITE_ID) ?: return
        sprite.setAdditiveBlend()
        for (s in list) {
            val x = s.x
            val y = s.y
            if (!viewport.isNearViewport(org.lwjgl.util.vector.Vector2f(x, y), s.length)) continue
            sprite.setSize(s.length, s.thickness)
            sprite.angle = s.angle
            sprite.color = s.tint
            sprite.alphaMult = s.alpha * alphaMult
            sprite.renderAtCenter(x, y)
        }
    }
}
