package org.combustiblelemon.covenantremnants.weapons

import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.DamagingProjectileAPI
import com.fs.starfarer.api.combat.OnHitEffectPlugin
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI
import org.combustiblelemon.covenantremnants.plugins.TorpedoRenderPlugin
import org.lwjgl.util.vector.Vector2f

/**
 * On-hit hook of the Plasma Cannon (`onHitEffect` in its .proj): draws the Halo: Reach plasma
 * splash at the hit point. Reach detonates the corvette ball-turret round with
 * `covenant_explosion_artillery_huge` (flash, Covenant fireball, blue plasma cloud, smoke, sparks,
 * glow flare) — the same family the plasma torpedoes already carry, so the torpedo impact burst is
 * reused at a cannon-sized unit, without its dark smoke (developer rulings 2026-10-09). Damage is
 * untouched (engine).
 */
class PlasmaCannonOnHit : OnHitEffectPlugin {
    companion object {
        /** Scale of the aura and splash in su per Reach unit; the torpedoes use 3 / 5 / 8 for S / M / L. */
        const val UNIT = 4f
    }

    override fun onHit(
        projectile: DamagingProjectileAPI,
        target: CombatEntityAPI?,
        point: Vector2f?,
        shieldHit: Boolean,
        damageResult: ApplyDamageResultAPI?,
        engine: CombatEngineAPI
    ) {
        val location = point ?: projectile.location
        TorpedoRenderPlugin.forEngine(engine).splash(Vector2f(location), UNIT)
    }
}
