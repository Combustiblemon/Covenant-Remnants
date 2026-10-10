package org.combustiblelemon.covenantremnants.weapons

import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.DamagingProjectileAPI
import com.fs.starfarer.api.combat.MissileAPI
import com.fs.starfarer.api.combat.OnHitEffectPlugin
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.listeners.ApplyDamageResultAPI
import com.fs.starfarer.api.impl.combat.DisintegratorEffect
import org.combustiblelemon.covenantremnants.hullmods.CovenantShield
import org.combustiblelemon.covenantremnants.plugins.TorpedoRenderPlugin
import org.combustiblelemon.covenantremnants.utils.render.torpedo.TorpedoSize
import org.lwjgl.util.vector.Vector2f
import kotlin.math.atan2

/**
 * On-hit hook of the three plasma torpedoes (`onHitEffect` in their .proj). Tells the
 * [TorpedoRenderPlugin] where the torpedo struck (the impact is drawn there) and starts the plasma
 * armor melt (launcher polish, developer 2026-10-09):
 *
 * - Trigger: some of the hit reached armor or hull (its damage result), so a hit a shield absorbed
 *   melts nothing — vanilla shields and the Covenant shield (a phase cloak that zeroes the damage) —
 *   while a shield-breaking hit, or a fading torpedo that still deals damage, does. Without a damage
 *   result: no shield hit and no raised Covenant shield.
 * - Pool: [MELT_FRACTION] of the torpedo's damage, times vanilla's armor damage multiplier
 *   ([DisintegratorEffect.getDamageTypeMult]: the target's armor damage taken, the source's damage
 *   to its hull size), as vanilla `BreachOnHitEffect`.
 * - Ships and fighters, anything with an armor grid; never hulks.
 */
class PlasmaTorpedoOnHit : OnHitEffectPlugin {
    companion object {
        const val MELT_FRACTION = 0.5f
    }

    override fun onHit(
        projectile: DamagingProjectileAPI, target: CombatEntityAPI?, point: Vector2f?, shieldHit: Boolean,
        damageResult: ApplyDamageResultAPI?, engine: CombatEngineAPI
    ) {
        val missile = projectile as? MissileAPI ?: return
        if (point == null) return
        val plugin = TorpedoRenderPlugin.forEngine(engine)
        plugin.torpedoHit(missile, point, shieldHit, damageResult?.totalDamageToArmor, damageResult?.damageToHull)

        val ship = target as? ShipAPI ?: return
        if (ship.isHulk || !ship.isAlive || ship.armorGrid == null) return
        val reached = if (damageResult != null) {
            !shieldHit && damageResult.totalDamageToArmor + damageResult.damageToHull > 0f
        } else {
            val cloak = ship.phaseCloak
            !shieldHit && !(cloak != null && cloak.id == CovenantShield.DEFENSE_ID && cloak.effectLevel > 0f)
        }
        if (!reached) return

        val size = TorpedoSize.forSpec(missile.projectileSpecId)
            ?: missile.weapon?.let { TorpedoSize.forWeapon(it.size) } ?: return
        val source = missile.source
        val pool = MELT_FRACTION * missile.damageAmount * DisintegratorEffect.getDamageTypeMult(source, ship)
        val velocity = missile.velocity
        val travelAngle = if (velocity.lengthSquared() > 1f) {
            Math.toDegrees(atan2(velocity.y, velocity.x).toDouble()).toFloat()
        } else missile.facing
        plugin.startMelt(ship, source, point, travelAngle, size, pool)
    }
}
