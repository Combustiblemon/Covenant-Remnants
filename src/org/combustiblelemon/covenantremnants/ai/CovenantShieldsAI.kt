package org.combustiblelemon.covenantremnants.ai

import com.fs.starfarer.api.combat.*
import org.lwjgl.util.vector.Vector2f

class CovenantShieldsAI : ShipSystemAIScript {
    private var ship: ShipAPI? = null
    private var engine: CombatEngineAPI? = null
    private var flags: ShipwideAIFlags? = null
    private var system: ShipSystemAPI? = null

    override fun init(ship: ShipAPI, system: ShipSystemAPI, flags: ShipwideAIFlags, engine: CombatEngineAPI) {
        this.ship = ship
        this.flags = flags
        this.engine = engine
        this.system = system

    }

    override fun advance(amount: Float, missileDangerDir: Vector2f?, collisionDangerDir: Vector2f?, target: ShipAPI?) {
        if (this.ship == null) {
            return
        }

        if (this.ship!!.phaseCloak.effectLevel == 0f && this.ship!!.phaseCloak.canBeActivated()) {
            this.ship!!.giveCommand(ShipCommand.TOGGLE_SHIELD_OR_PHASE_CLOAK, null, 0)
        }
    }
}