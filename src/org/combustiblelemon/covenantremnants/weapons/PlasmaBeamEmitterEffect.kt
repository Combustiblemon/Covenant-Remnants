package org.combustiblelemon.covenantremnants.weapons

import com.fs.starfarer.api.combat.BeamAPI
import com.fs.starfarer.api.combat.BeamEffectPlugin
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.impl.combat.GravitonBeamEffect
import org.combustiblelemon.covenantremnants.plugins.PlasmaBeamRenderPlugin
import org.lwjgl.util.vector.Vector2f

/**
 * Plasma Beam Emitter — beam effect. Wired as the `beamEffect` of
 * `data/weapons/covenant_remnants_plasmabeam.wpn`.
 *
 * 1. Hands the live beam to [PlasmaBeamRenderPlugin] (the Halo: Reach focus rifle look), here
 *    where `getTo()` is the collision point.
 * 2. Vanilla Graviton Beam shield suppression (+5 / 8 / 10 % shield damage taken for 1 s per one /
 *    two / three beams), **extended to Covenant shields** (developer ruling 2026-10-08): vanilla
 *    [GravitonBeamEffect] only counts a hit on a vanilla shield's arc, and Covenant hulls carry
 *    their shield as the phase cloak, so it never triggered against them. The multiplier side
 *    already worked — vanilla's listener writes `shieldDamageTakenMult`, which the Covenant shield
 *    honours — so the vanilla listener is reused; only the trigger is ours. A Covenant shield
 *    counts as hit while its cloak is at any effect level above 0.
 */
class PlasmaBeamEmitterEffect : BeamEffectPlugin {
    private companion object {
        const val COVENANT_SHIELD_ID = "covenant_remnants_covenantshield"
    }

    private var wasZero = true

    override fun advance(amount: Float, engine: CombatEngineAPI, beam: BeamAPI) {
        if (engine.isPaused) return
        PlasmaBeamRenderPlugin.forEngine(engine).beam(beam.weapon, beam)

        val target = beam.damageTarget as? ShipAPI ?: return
        if (beam.brightness < 1f || beam.weapon == null) return

        // Beam tick detection as in vanilla: dpsDuration is only > 0 on the frames a damage tick
        // lands (and is not reset every frame under fast time, hence wasZero).
        var dur = beam.damage.dpsDuration
        if (!wasZero) dur = 0f
        wasZero = beam.damage.dpsDuration <= 0f
        if (dur <= 0f) return
        if (!shieldedAt(target, beam.to)) return

        if (!target.hasListenerOfClass(GravitonBeamEffect.GravitonBeamDamageTakenMod::class.java)) {
            target.addListener(GravitonBeamEffect.GravitonBeamDamageTakenMod(target))
        }
        val listeners = target.getListeners(GravitonBeamEffect.GravitonBeamDamageTakenMod::class.java)
        if (listeners.isEmpty()) return
        listeners[0].notifyHit(beam.weapon)
    }

    /** Vanilla: the shield's arc covers [point]. Covenant: the shield cloak is up at all. */
    private fun shieldedAt(target: ShipAPI, point: Vector2f): Boolean {
        val shield = target.shield
        if (shield != null && shield.isWithinArc(point)) return true
        val cloak = target.phaseCloak
        return cloak != null && cloak.id == COVENANT_SHIELD_ID && cloak.effectLevel > 0f
    }
}
