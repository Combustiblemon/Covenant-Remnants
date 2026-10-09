package org.combustiblelemon.covenantremnants.weapons

import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin
import com.fs.starfarer.api.combat.WeaponAPI
import org.combustiblelemon.covenantremnants.plugins.TorpedoRenderPlugin

/**
 * Every-frame hook of the Plasma Bombardment (`everyFrameEffect` in its .wpn). Creates the combat's
 * [TorpedoRenderPlugin] while a Bombardment is on the field (the plugin picks the BALLISTIC rounds up
 * from the projectile list: the on-fire hook is not called for them) and feeds it the weapon's charge
 * level for the Reach AA gun's muzzle charge.
 */
class PlasmaBombardmentEffect : EveryFrameWeaponEffectPlugin {
    override fun advance(amount: Float, engine: CombatEngineAPI, weapon: WeaponAPI) {
        TorpedoRenderPlugin.forEngine(engine).charge(weapon)
    }
}
