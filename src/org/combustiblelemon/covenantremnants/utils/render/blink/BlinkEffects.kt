package org.combustiblelemon.covenantremnants.utils.render.blink

import com.fs.starfarer.api.combat.ShipAPI

/**
 * The hull ripple of one blinking ship, after the Halo 3: ODST carrier jump (c100_intro
 * `radiation_flash.effect` / `radiation_crackle.effect`): the wavefront runs from the bow (1) to
 * the stern (0) over [duration] — ODST emits sparks and plasma puffs from hull points in that
 * order — then the lingering glow fades over [FADE_DURATION] while a soft flash blooms at the bow
 * (ODST's `flash` event, 2.5 s after the ripple). [cancel] fades everything out early, with no flash.
 */
class BlinkRipple(val ship: ShipAPI, private val duration: Float) {
    companion object {
        const val FADE_DURATION = 0.35f
        const val CANCEL_DURATION = 0.15f
    }

    var time = 0f
        private set

    /** Wavefront position along the hull, 1 = bow .. 0 = stern (keeps running below 0 while fading). */
    val front: Float get() = 1f - (if (duration <= 0f) 1f else time / duration)

    /** Master brightness, 1 while sweeping, fading to 0 afterwards. */
    var intensity = 1f
        private set

    /** Bow flash strength 0..1 (only after a completed sweep). */
    var flash = 0f
        private set

    private var fadeTime = 0f
    private var fadeDuration = FADE_DURATION
    private var fading = false
    private var completed = false

    val isDone: Boolean get() = fading && fadeTime >= fadeDuration

    fun cancel() {
        if (!fading) {
            fading = true
            fadeDuration = CANCEL_DURATION
            fadeTime = 0f
        }
    }

    fun advance(amount: Float) {
        time += amount
        if (!fading && time >= duration) {
            fading = true
            completed = true
            fadeDuration = FADE_DURATION
            fadeTime = 0f
        }
        if (fading) {
            fadeTime += amount
            val left = (1f - fadeTime / fadeDuration).coerceIn(0f, 1f)
            intensity = left
            flash = if (completed) left * left else 0f
        }
    }
}
