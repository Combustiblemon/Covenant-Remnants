package org.combustiblelemon.covenantremnants.campaign.systems

import com.fs.starfarer.api.campaign.SectorAPI
import com.fs.starfarer.api.impl.campaign.ids.Tags
import org.combustiblelemon.covenantremnants.campaign.systems.scripts.SlipspaceSystemEFS
import java.awt.Color

const val SLIPSPACE_JUMP_POINT_ENTITY = "slipspace_jump_point"
const val SLIPSPACE_JUMP_POINT_ENTRANCE = "slipspace_jump_point_entrance"
const val SLIPSPACE_JUMP_POINT_EXIT = "slipspace_jump_point_exit"

fun generateSlipspaceSystem(sector: SectorAPI) {

    val system = sector.createStarSystem("Slipspace")
    system.addScript(SlipspaceSystemEFS())

    system.location.set(0f, 0f) //position of the system on the map
    system.doNotShowIntelFromThisLocationOnMap = true
    system.addTag(Tags.DO_NOT_RESPAWN_PLAYER_IN)
    system.addTag(Tags.REPORT_NO_SYSTEM)
    system.addTag(Tags.SYSTEM_ABYSSAL)
    system.addTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)
    system.addTag(Tags.HIDE_IN_CODEX)
    system.addTag(Tags.NO_TOPOGRAPHY_SCANS)
    system.addTag(Tags.NO_SIM)
    system.addTag(Tags.TEMPORARY_LOCATION)
    system.addTag(Tags.STORY_CRITICAL)

    system.backgroundTextureFilename = "graphics/covenant_remnants/backgrounds/slipspace_bg.png"
    system.initNonStarCenter()
    system.lightColor = Color(25, 25, 60) // light color in entire system, affects all entities

    system.addCustomEntity(SLIPSPACE_JUMP_POINT_ENTRANCE, "Jump point", SLIPSPACE_JUMP_POINT_ENTITY, "neutral")
    system.addCustomEntity(SLIPSPACE_JUMP_POINT_EXIT, "Jump point", SLIPSPACE_JUMP_POINT_ENTITY, "neutral")

//        val planet1 = system.addPlanet(
//            "planet1",
//            my_system_star, //what it's orbiting
//            "My Planet", //name
//            "barren", //the planet type (look in planets.json for more)
//            900f, //angle
//            130f, //radius
//            planet1Dist, //distance from star
//            700f
//        ) //how many days to orbit
//        planet1.customDescriptionId = "planet1" //for custom descriptions

//        system.autogenerateHyperspaceJumpPoints(true, true) //generates jump points

//        val plugin =
//            Misc.getHyperspaceTerrain().plugin as HyperspaceTerrainPlugin //these lines clear the hyperspace clouds around the system
//        val editor = NebulaEditor(plugin)
//        val minRadius = plugin.tileSize * 2f
//
//        val radius = system.maxRadiusInHyperspace
//        editor.clearArc(system.location.x, system.location.y, 0f, radius + minRadius, 0f, 360f)
//        editor.clearArc(system.location.x, system.location.y, 0f, radius + minRadius, 0f, 360f, 0.25f)

}
