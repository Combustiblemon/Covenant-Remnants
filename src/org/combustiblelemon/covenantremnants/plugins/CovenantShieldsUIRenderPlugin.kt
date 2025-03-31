package org.combustiblelemon.covenantremnants.plugins

import com.fs.starfarer.api.combat.*
import org.magiclib.util.MagicUI
import java.util.*

class CovenantShieldsUIRenderPlugin(
    private val attachedTo: ShipAPI,
) : CombatLayeredRenderingPlugin {
    private var layer = CombatEngineLayers.JUST_BELOW_WIDGETS

    private var entity: CombatEntityAPI? = null

    var maxShieldHealth: Float = 1000f
    var shieldHealth: Float = 1000f
    var shieldCooldown: Float = 1f

    override fun init(entity: CombatEntityAPI?) {
        this.entity = entity
    }

    override fun cleanup() {

    }

    override fun isExpired(): Boolean {
        return false
    }

    override fun advance(amount: Float) {

    }

    override fun getActiveLayers(): EnumSet<CombatEngineLayers> {
        return EnumSet.of(layer)
    }

    override fun getRenderRadius(): Float {
        return Float.MAX_VALUE
    }

    override fun render(layer: CombatEngineLayers?, viewport: ViewportAPI?) {
        if (viewport == null) {
            return
        }

        MagicUI.drawInterfaceStatusBar(
            attachedTo, shieldHealth / maxShieldHealth,
            MagicUI.GREENCOLOR,
            MagicUI.GREENCOLOR,
            shieldHealth / maxShieldHealth,
            "Shield",
            shieldHealth.toInt()
        )

        MagicUI.drawSystemBar(
            attachedTo,
            MagicUI.BLUCOLOR,
            shieldCooldown,
            0f
        )
    }
}
