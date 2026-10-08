package org.combustiblelemon.covenantremnants.utils.extensions

import com.fs.starfarer.api.campaign.CampaignFleetAPI
import org.combustiblelemon.covenantremnants.campaign.systems.SLIPSPACE_POCKET_NAME
import org.combustiblelemon.covenantremnants.campaign.systems.SLIPSPACE_POCKET_TAG

/** True while the fleet is inside the slipspace pocket system (tag first, legacy name as fallback). */
val CampaignFleetAPI.isInSlipspace: Boolean
    get() {
        val location = this.containingLocation ?: return false
        if (location.hasTag(SLIPSPACE_POCKET_TAG)) return true
        return location.nameWithNoType.equals(SLIPSPACE_POCKET_NAME, ignoreCase = true)
    }
