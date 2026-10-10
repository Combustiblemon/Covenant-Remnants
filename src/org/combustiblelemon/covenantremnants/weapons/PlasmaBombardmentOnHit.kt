package org.combustiblelemon.covenantremnants.weapons

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CollisionClass
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.DamageType
import com.fs.starfarer.api.combat.DamagingProjectileAPI
import com.fs.starfarer.api.combat.OnHitEffectPlugin
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI
import com.fs.starfarer.api.loading.DamagingExplosionSpec
import org.combustiblelemon.covenantremnants.plugins.TorpedoRenderPlugin
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import kotlin.random.Random

/**
 * On-hit hook of the Plasma Bombardment (`onHitEffect` in its .proj), developer rulings 2026-10-09:
 * the Halo: Reach Covenant AA gun round's detonation. The direct hit is the engine's
 * (weapon_data.csv); on top of it a high-explosive splash ([SPLASH_DAMAGE] inside [SPLASH_CORE],
 * falling to 0 at [SPLASH_RADIUS]: Reach's `bfg_round` damage has an AOE core 0.75 of its 0.75-1.75 WU
 * radius, the Plasma Mortar's mapping), the rebuilt `covenant_explosion_artillery_huge` and the
 * `cov_damage_large` explosion sound.
 */
class PlasmaBombardmentOnHit : OnHitEffectPlugin {
    companion object {
        /**
         * Impact scale in su per Reach unit: the blast's main cloud (~5 WU) then fills about the
         * splash radius, its gas and sparks run past it (placeholder; harness 2026-10-09).
         */
        const val UNIT = 20f

        /**
         * The round's look in su per Reach unit: at the Mortar's 8 the 0.3 WU-thick AA bolt read as a
         * needle thinner than the Mortar round (harness); 12 gives it the Mortar's width and a ~80 su
         * body (placeholder).
         */
        const val ROUND_UNIT = 12f

        /** The firing flash in su per Reach unit (placeholder). */
        const val MUZZLE_UNIT = 6f

        /** The muzzle charge in su per Reach unit: at 6 it was a ~10 su sparkle (harness; placeholder). */
        const val CHARGE_UNIT = 10f

        const val SPLASH_DAMAGE = 400f
        const val SPLASH_RADIUS = 150f
        const val SPLASH_CORE = 64f

        private const val SOUND_EXPLOSION = "covenant_remnants_plasma_bombardment_explosion"

        // A little pitch spread keeps a salvo from phasing (as on the Mortar).
        private const val PITCH_MIN = 0.94f
        private const val PITCH_MAX = 1.04f
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

        val spec = DamagingExplosionSpec(
            0.1f, SPLASH_RADIUS, SPLASH_CORE, SPLASH_DAMAGE, 0f,
            CollisionClass.PROJECTILE_FF, CollisionClass.PROJECTILE_FIGHTER,
            0f, 0f, 0f, 0, Color.WHITE, Color.WHITE
        )
        spec.setDamageType(DamageType.HIGH_EXPLOSIVE)
        spec.setShowGraphic(false)
        engine.spawnDamagingExplosion(spec, projectile.source, location, false)

        TorpedoRenderPlugin.forEngine(engine).artilleryImpact(location, UNIT)
        Global.getSoundPlayer().playSound(
            SOUND_EXPLOSION, Random.nextFloat() * (PITCH_MAX - PITCH_MIN) + PITCH_MIN, 1f, location, Vector2f()
        )
    }
}
