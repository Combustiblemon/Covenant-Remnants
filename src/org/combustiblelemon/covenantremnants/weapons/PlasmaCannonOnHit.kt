package org.combustiblelemon.covenantremnants.weapons

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.DamagingProjectileAPI
import com.fs.starfarer.api.combat.OnHitEffectPlugin
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI
import org.combustiblelemon.covenantremnants.plugins.TorpedoRenderPlugin
import org.combustiblelemon.covenantremnants.settings.CovenantSettings
import org.lwjgl.util.vector.Vector2f
import kotlin.random.Random

/**
 * On-hit hook of the Plasma Cannon (`onHitEffect` in its .proj): Reach detonates the corvette
 * ball-turret round with `covenant_explosion_artillery_huge`, the same blast the Plasma Bombardment
 * rebuilt, so the Cannon draws that rebuilt impact at a cannon-sized unit with fewer sparks and a
 * shorter blast light, plus a quiet, higher-pitched copy of the Bombardment's `cov_damage_large`
 * explosion (developer rulings 2026-10-09 and 2026-10-10). Visual and sound only: damage is the
 * engine's, no area damage.
 */
class PlasmaCannonOnHit : OnHitEffectPlugin {
    companion object {
        /** Scale of the in-flight aura in su per Reach unit (PlasmaCannonOnFire). */
        const val UNIT = 4f

        /**
         * The hit's artillery impact in su per Reach unit (the Bombardment uses 20): flash core and
         * fireballs ~25 su, sparks to ~50 su, the old torpedo-burst splash's footprint (harness 2026-10-10).
         */
        const val IMPACT_UNIT = 8f

        /** Sparks per hit; the Bombardment keeps 60. */
        const val IMPACT_SPARKS = 30

        /** Blast light life in seconds (Bombardment 2), so a Cannon firing once a second never stacks two. */
        const val IMPACT_LIGHT_LIFE = 1f

        private const val SOUND_EXPLOSION = "covenant_remnants_plasma_cannon_explosion"

        // Pitched up from the Bombardment's boom to read as a smaller gun; a little spread keeps two
        // Cannons from phasing.
        private const val PITCH_MIN = 1.14f
        private const val PITCH_MAX = 1.26f
    }

    override fun onHit(
        projectile: DamagingProjectileAPI,
        target: CombatEntityAPI?,
        point: Vector2f?,
        shieldHit: Boolean,
        damageResult: ApplyDamageResultAPI?,
        engine: CombatEngineAPI
    ) {
        val location = Vector2f(point ?: projectile.location)
        TorpedoRenderPlugin.forEngine(engine).artilleryImpact(location, IMPACT_UNIT, IMPACT_SPARKS, IMPACT_LIGHT_LIFE)
        Global.getSoundPlayer().playSound(
            SOUND_EXPLOSION, Random.nextFloat() * (PITCH_MAX - PITCH_MIN) + PITCH_MIN, CovenantSettings.effectVolume, location, Vector2f()
        )
    }
}
