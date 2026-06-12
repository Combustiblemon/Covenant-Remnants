package org.combustiblelemon.covenantremnants.utils.extensions

import com.fs.starfarer.api.campaign.CampaignFleetAPI

val CampaignFleetAPI.isInSlipspace: Boolean
    get() = this.containingLocation.nameWithNoType.lowercase() == "slipspace"
