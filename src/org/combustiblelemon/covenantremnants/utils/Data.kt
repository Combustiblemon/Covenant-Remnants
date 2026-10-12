package org.combustiblelemon.covenantremnants.utils

import org.json.JSONArray

data class ShieldData(
    val shieldHealth: Float,
    val shieldRegenRate: Float,
    val shieldRegenDelay: Float,
    val brokenShieldCooldown: Float,
)

val shieldData = mutableMapOf<String, ShieldData>()

fun generateDefaultShieldData(): ShieldData {
    return ShieldData(
        shieldHealth = 3000f,
        shieldRegenRate = 1f,
        shieldRegenDelay = 1f,
        brokenShieldCooldown = 1f,
    )
}

fun initShieldData(data: JSONArray) {
    try {
        for (i in 0 until data.length()) {
            val row = data.getJSONObject(i)
            shieldData[row.getString("hullId")] = ShieldData(
                shieldHealth = row.getString("shield hp").toFloat(),
                shieldRegenRate = row.getString("shield regen").toFloat(),
                shieldRegenDelay = row.getString("shield regen delay").toFloat(),
                brokenShieldCooldown = row.getString("broken shield cooldown").toFloat(),
            )
        }
    } catch (e: Exception) {
        throw Exception("shield_data.csv has missing columns. make sure all the columns are filled in.")
    }

}
