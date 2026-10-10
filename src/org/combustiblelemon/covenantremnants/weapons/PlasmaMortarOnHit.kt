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
import org.combustiblelemon.covenantremnants.settings.CovenantSettings
import org.lwjgl.util.vector.Vector2f
import java.awt.Color
import kotlin.random.Random

/**
 * On-hit hook of the Plasma Mortar (`onHitEffect` in its .proj), developer rulings 2026-10-09:
 * the Halo: Reach Wraith mortar detonation. The direct hit is the engine's (weapon_data.csv); on top
 * of it a high-explosive splash ([SPLASH_DAMAGE] inside [SPLASH_CORE], falling to 0 at
 * [SPLASH_RADIUS], like Reach's `wraith_mortar_round` damage: core 0.6 of 0.75-2.25 WU, lower bound
 * 0), the torpedo-family Reach impact burst at a mortar-sized unit (no smoke, as on the cannon), and
 * the Wraith explosion sound.
 */
class PlasmaMortarOnHit : OnHitEffectPlugin {
    companion object {
        /** Impact splash scale in su per Reach unit (the torpedoes use 3 / 5 / 12). */
        const val UNIT = 6f

        /** The round's look in su per Reach unit: Reach proportions read small at 6 (placeholder). */
        const val ROUND_UNIT = 8f

        const val SPLASH_DAMAGE = 150f
        const val SPLASH_RADIUS = 75f
        const val SPLASH_CORE = 20f

        private const val SOUND_EXPLOSION = "covenant_remnants_plasma_mortar_explosion"

        // Reach plays wraith_proj_expl unpitched; a little spread keeps a barrage from phasing.
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

        TorpedoRenderPlugin.forEngine(engine).splash(location, UNIT)
        Global.getSoundPlayer().playSound(
            SOUND_EXPLOSION, Random.nextFloat() * (PITCH_MAX - PITCH_MIN) + PITCH_MIN, CovenantSettings.effectVolume, location, Vector2f()
        )
    }
}
