package org.combustiblelemon.covenantremnants.campaign.systems

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.campaign.CampaignTerrainAPI
import com.fs.starfarer.api.campaign.SectorAPI
import com.fs.starfarer.api.campaign.SectorEntityToken
import com.fs.starfarer.api.campaign.StarSystemAPI
import com.fs.starfarer.api.impl.campaign.ids.Tags
import com.fs.starfarer.api.impl.campaign.velfield.SlipstreamTerrainPlugin2
import com.fs.starfarer.api.impl.campaign.velfield.SlipstreamTerrainPlugin2.SlipstreamParams2
import com.fs.starfarer.api.util.Misc
import com.fs.starfarer.campaign.StarSystem
import org.combustiblelemon.covenantremnants.campaign.abilities.SlipspaceJumpRules
import org.combustiblelemon.covenantremnants.campaign.systems.scripts.SlipspaceSystemEFS
import org.lwjgl.util.vector.Vector2f
import java.awt.Color

// ---------------------------------------------------------------------------------------------
// Ids. The legacy ids (prototype) stay so old saves still resolve their entities.
// ---------------------------------------------------------------------------------------------

/** Legacy custom-entity type of the prototype's two portals (kept registered for old saves). */
const val SLIPSPACE_JUMP_POINT_ENTITY = "slipspace_jump_point"
const val SLIPSPACE_JUMP_POINT_ENTRANCE = "slipspace_jump_point_entrance"
const val SLIPSPACE_JUMP_POINT_EXIT = "slipspace_jump_point_exit"

/** Display / base name of the pocket system ("Slipspace"). Its id is this lower-cased. */
const val SLIPSPACE_POCKET_NAME = "Slipspace"

/** Tag on the pocket star system; [org.combustiblelemon.covenantremnants.utils.extensions.isInSlipspace] keys on it. */
const val SLIPSPACE_POCKET_TAG = "covenant_remnants_slipspace_pocket"

/** Custom-entity types (data/config/custom_entities.json). */
const val SLIPSPACE_ENTRANCE_TYPE = "covenant_remnants_slipspace_entrance"
const val SLIPSPACE_EXIT_TYPE = "covenant_remnants_slipspace_exit"
const val SLIPSPACE_AMBIENCE_TYPE = "covenant_remnants_slipspace_ambience"

/** Entity ids inside the pocket. */
const val SLIPSPACE_ENTRANCE_ID = "covenant_remnants_slipspace_entrance"
const val SLIPSPACE_EXIT_ID = "covenant_remnants_slipspace_exit"
const val SLIPSPACE_AMBIENCE_ID = "covenant_remnants_slipspace_ambience"

/** Terrain id of the pocket's path (data/campaign/terrain.json). */
const val SLIPSPACE_STREAM_TERRAIN = "covenant_remnants_slipspace_stream"

/** Pocket memory keys: the route currently laid out in it. */
const val SLIPSPACE_ROUTE_ORIGIN_KEY = "\$covenant_remnants_slipspace_route_origin"
const val SLIPSPACE_ROUTE_DEST_KEY = "\$covenant_remnants_slipspace_route_dest"
/** The plotted exit point in the destination (navigation upgrade); unset for an edge jump. */
const val SLIPSPACE_ROUTE_POINT_KEY = "\$covenant_remnants_slipspace_route_point"

// ---------------------------------------------------------------------------------------------
// Lookup / bootstrap
// ---------------------------------------------------------------------------------------------

/** The pocket system, by tag first and by the legacy name second; null if the save has none. */
fun findSlipspaceSystem(sector: SectorAPI = Global.getSector()): StarSystemAPI? {
    sector.starSystems.firstOrNull { it.hasTag(SLIPSPACE_POCKET_TAG) }?.let { return it }
    return sector.starSystems.firstOrNull { it.baseName.equals(SLIPSPACE_POCKET_NAME, ignoreCase = true) }
}

/**
 * Returns the pocket system, creating it when the save has none (saves from before the campaign
 * jump existed, or saves created before the mod was added) and migrating a prototype one (adds
 * the tag, swaps the legacy portal entities for the entrance / exit, adds the ambience entity).
 * Idempotent; called from `onGameLoad` and `onNewGame`.
 */
fun ensureSlipspaceSystem(sector: SectorAPI = Global.getSector()): StarSystemAPI {
    val existing = findSlipspaceSystem(sector)
    if (existing != null) {
        migrateSlipspaceSystem(existing)
        return existing
    }
    return generateSlipspaceSystem(sector)
}

fun generateSlipspaceSystem(sector: SectorAPI): StarSystemAPI {
    val system = sector.createStarSystem(SLIPSPACE_POCKET_NAME)
    system.backgroundTextureFilename = SlipspaceJumpRules.POCKET_BACKGROUND
    system.addScript(SlipspaceSystemEFS())

    system.location.set(0f, 0f) // position of the system on the map (hidden anyway)
    system.doNotShowIntelFromThisLocationOnMap = true
    system.addTag(Tags.DO_NOT_RESPAWN_PLAYER_IN)
    system.addTag(Tags.REPORT_NO_SYSTEM)
    system.addTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)
    system.addTag(Tags.HIDE_IN_CODEX)
    system.addTag(Tags.NO_TOPOGRAPHY_SCANS)
    system.addTag(Tags.NO_SIM)
    system.addTag(Tags.TEMPORARY_LOCATION)
    system.addTag(Tags.STORY_CRITICAL)
    system.addTag(SLIPSPACE_POCKET_TAG)

    system.initNonStarCenter()
    system.lightColor = Color(25, 25, 60) // light color in entire system, affects all entities

    addPocketEntities(system)

    system.setBackgroundOffset(0f, 0f)
    hideBackgroundParticles(system)
    (system as StarSystem).displayName = SLIPSPACE_POCKET_NAME
    return system
}

/**
 * Background star / dust particle layer in the pocket (developer 2026-10-08): the base colour is
 * set transparent here, but the engine only applies the colour to the particles while a shift is
 * active — the real work is the per-frame shift in [SlipspaceSystemEFS]. Idempotent.
 */
private fun hideBackgroundParticles(system: StarSystemAPI) {
    system.backgroundParticleColorShifter.base = Color(0, 0, 0, 0)
}

private fun migrateSlipspaceSystem(system: StarSystemAPI) {
    if (!system.hasTag(SLIPSPACE_POCKET_TAG)) system.addTag(SLIPSPACE_POCKET_TAG)
    if (system.backgroundTextureFilename != SlipspaceJumpRules.POCKET_BACKGROUND) {
        system.backgroundTextureFilename = SlipspaceJumpRules.POCKET_BACKGROUND
    }
    hideBackgroundParticles(system)
    // Prototype portals: replaced by the typed entrance / exit.
    system.getEntityById(SLIPSPACE_JUMP_POINT_ENTRANCE)?.let { system.removeEntity(it) }
    system.getEntityById(SLIPSPACE_JUMP_POINT_EXIT)?.let { system.removeEntity(it) }
    addPocketEntities(system)
    reorientExistingRoute(system)
}

private fun addPocketEntities(system: StarSystemAPI) {
    if (system.getEntityById(SLIPSPACE_ENTRANCE_ID) == null) {
        system.addCustomEntity(SLIPSPACE_ENTRANCE_ID, "Slipspace aperture", SLIPSPACE_ENTRANCE_TYPE, "neutral")
    }
    if (system.getEntityById(SLIPSPACE_EXIT_ID) == null) {
        system.addCustomEntity(SLIPSPACE_EXIT_ID, "Slipspace exit", SLIPSPACE_EXIT_TYPE, "neutral")
    }
    if (system.getEntityById(SLIPSPACE_AMBIENCE_ID) == null) {
        system.addCustomEntity(SLIPSPACE_AMBIENCE_ID, "Slipspace", SLIPSPACE_AMBIENCE_TYPE, "neutral")
    }
}

fun slipspaceEntrance(system: StarSystemAPI): SectorEntityToken? = system.getEntityById(SLIPSPACE_ENTRANCE_ID)
fun slipspaceExit(system: StarSystemAPI): SectorEntityToken? = system.getEntityById(SLIPSPACE_EXIT_ID)

// ---------------------------------------------------------------------------------------------
// Route
// ---------------------------------------------------------------------------------------------

/**
 * Lays out the pocket for one jump: removes the previous path terrain, builds the winding path
 * for the (origin, destination) pair (same pair → same path, see [SlipspacePaths]), adds the
 * slipstream terrain along it, and moves the entrance to the path start and the exit to its end.
 * The route is remembered in the pocket's memory so the exit knows where it leads.
 *
 * @param burnMult transit-speed multiplier (Slipspace Tuning), applied to the stream's burn level.
 * @param exitPoint the plotted arrival point in the destination (navigation upgrade); null = the
 *   system's edge. Always written, so a plotted point never carries over into a later edge jump.
 */
fun prepareSlipspaceRoute(system: StarSystemAPI, originId: String, destId: String, burnMult: Float, exitPoint: Vector2f? = null) {
    for (terrain in system.terrainCopy) {
        if (terrain.type == SLIPSPACE_STREAM_TERRAIN) system.removeEntity(terrain)
    }

    val record = SlipspacePaths.recordFor(originId, destId)
    val points = SlipspacePaths.buildPolyline(record)

    val params = SlipstreamParams2()
    params.name = "Slipspace"
    params.enteringSlipstreamTextOverride = ""
    params.baseWidth = SlipspaceJumpRules.streamWidth.value
    params.widthForMaxSpeed = SlipspaceJumpRules.streamWidth.value
    params.edgeWidth = SlipspaceJumpRules.streamEdgeWidth.value
    params.burnLevel = Math.round(SlipspaceJumpRules.streamBurnLevel.value * burnMult)
    params.accelerationMult = SlipspaceJumpRules.streamAccelMult.value
    params.slowDownInWiderSections = false
    params.minSpeed = Misc.getSpeedForBurnLevel(params.burnLevel - params.burnLevel / 8f)
    params.maxSpeed = Misc.getSpeedForBurnLevel(params.burnLevel + params.burnLevel / 8f)
    params.minColor = SlipspaceJumpRules.STREAM_MIN_COLOR
    params.maxColor = SlipspaceJumpRules.STREAM_MAX_COLOR

    val terrain = system.addTerrain(SLIPSPACE_STREAM_TERRAIN, params) as CampaignTerrainAPI
    terrain.addTag(Tags.SLIPSTREAM_VISIBLE_IN_ABYSS)
    terrain.setLocation(points[0].x, points[0].y)
    val plugin = terrain.plugin as SlipstreamTerrainPlugin2
    // Width tapers toward both ends so the corridor opens and closes gracefully instead of
    // starting and stopping at full width.
    val total = (points.size - 1) * SlipspaceJumpRules.pathPointSpacing.value
    val taper = SlipspaceJumpRules.streamTaper.value.coerceAtMost(total * 0.5f).coerceAtLeast(1f)
    val endFrac = SlipspaceJumpRules.streamEndWidthFrac.value.coerceIn(0.05f, 1f)
    for ((i, p) in points.withIndex()) {
        val along = i * SlipspaceJumpRules.pathPointSpacing.value
        val fromEnd = minOf(along, total - along).coerceAtLeast(0f)
        val f = (fromEnd / taper).coerceIn(0f, 1f)
        val smooth = f * f * (3f - 2f * f)
        val width = SlipspaceJumpRules.streamWidth.value * (endFrac + (1f - endFrac) * smooth)
        plugin.addSegment(Vector2f(p), width)
    }
    plugin.recomputeIfNeeded()

    val start = points.first()
    val end = points.last()
    slipspaceEntrance(system)?.setLocation(start.x, start.y)
    slipspaceExit(system)?.setLocation(end.x, end.y)
    orientSlipspacePortals(system, points)
    // The exit sits past the end of the wind (the last segment carries no current) so the fleet
    // coasts up to it instead of being pushed through it.

    val memory = system.memoryWithoutUpdate
    memory.set(SLIPSPACE_ROUTE_ORIGIN_KEY, originId)
    memory.set(SLIPSPACE_ROUTE_DEST_KEY, destId)
    if (exitPoint != null) memory.set(SLIPSPACE_ROUTE_POINT_KEY, Vector2f(exitPoint)) else memory.unset(SLIPSPACE_ROUTE_POINT_KEY)
}

/**
 * Points the portals the way a fleet leaves their tube (the Ark look: the tube extends behind the
 * mouth): the entrance along the path's first segment, the exit back up the path's last segment
 * (its mouth toward the arriving fleet, the tube past the path end).
 */
fun orientSlipspacePortals(system: StarSystemAPI, points: List<Vector2f>) {
    if (points.size < 2) return
    slipspaceEntrance(system)?.facing = Misc.getAngleInDegrees(points[0], points[1])
    slipspaceExit(system)?.facing = Misc.getAngleInDegrees(points[points.size - 1], points[points.size - 2])
}

/** Re-orients the portals of a pocket that already has a route (saves from before the Ark look). */
private fun reorientExistingRoute(system: StarSystemAPI) {
    val originId = system.memoryWithoutUpdate.getString(SLIPSPACE_ROUTE_ORIGIN_KEY) ?: return
    val destId = system.memoryWithoutUpdate.getString(SLIPSPACE_ROUTE_DEST_KEY) ?: return
    val points = SlipspacePaths.buildPolyline(SlipspacePaths.recordFor(originId, destId))
    orientSlipspacePortals(system, points)
}

/** The destination system of the route currently laid out in the pocket, or null. */
fun slipspaceRouteDestination(system: StarSystemAPI): StarSystemAPI? {
    val id = system.memoryWithoutUpdate.getString(SLIPSPACE_ROUTE_DEST_KEY) ?: return null
    return Global.getSector().getStarSystem(id)
}

/** The origin system of the route currently laid out in the pocket, or null. */
fun slipspaceRouteOrigin(system: StarSystemAPI): StarSystemAPI? {
    val id = system.memoryWithoutUpdate.getString(SLIPSPACE_ROUTE_ORIGIN_KEY) ?: return null
    return Global.getSector().getStarSystem(id)
}

/** The plotted exit point of the route laid out in the pocket (a copy), or null for an edge jump. */
fun slipspaceRoutePoint(system: StarSystemAPI): Vector2f? =
    (system.memoryWithoutUpdate.get(SLIPSPACE_ROUTE_POINT_KEY) as? Vector2f)?.let { Vector2f(it) }

/** Forgets the plotted exit point once the jump has landed. */
fun clearSlipspaceRoutePoint(system: StarSystemAPI) {
    system.memoryWithoutUpdate.unset(SLIPSPACE_ROUTE_POINT_KEY)
}
