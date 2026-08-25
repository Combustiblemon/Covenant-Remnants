package org.combustiblelemon.covenantremnants.utils

import com.fs.starfarer.api.Global

// Generic math/geometry/ship/shader helpers moved to LemonLib
// (org.combustiblelemon.lemonlib.geometry / .ship / .render).

fun logText(text: String?) {
    println(text)
}

fun getUnapplicableReasonForHullmod(hullmodId: String): String? {
    return "Cannot be installed alongside ${Global.getSettings().getHullModSpec(hullmodId).displayName}"
}
