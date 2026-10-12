package org.combustiblelemon.covenantremnants.plugins

import com.fs.starfarer.api.Global
import com.fs.starfarer.api.combat.CombatEngineAPI
import com.fs.starfarer.api.combat.CombatEngineLayers
import com.fs.starfarer.api.combat.CombatEntityAPI
import com.fs.starfarer.api.combat.CombatLayeredRenderingPlugin
import com.fs.starfarer.api.combat.DamagingProjectileAPI
import com.fs.starfarer.api.combat.MissileAPI
import com.fs.starfarer.api.combat.ShipAPI
import com.fs.starfarer.api.combat.ViewportAPI
import com.fs.starfarer.api.combat.WeaponAPI
import com.fs.starfarer.api.util.Misc
import org.combustiblelemon.covenantremnants.settings.CovenantSettings
import org.combustiblelemon.covenantremnants.utils.render.torpedo.ArtilleryBurst
import org.combustiblelemon.covenantremnants.utils.render.torpedo.AuraStyle
import org.combustiblelemon.covenantremnants.utils.render.torpedo.BombardCharge
import org.combustiblelemon.covenantremnants.utils.render.torpedo.BombardFlight
import org.combustiblelemon.covenantremnants.utils.render.torpedo.CannonFlight
import org.combustiblelemon.covenantremnants.utils.render.torpedo.MeltPatch
import org.combustiblelemon.covenantremnants.utils.render.torpedo.TorpedoBurst
import org.combustiblelemon.covenantremnants.utils.render.torpedo.TorpedoFlight
import org.combustiblelemon.covenantremnants.utils.render.torpedo.TorpedoRenderer
import org.combustiblelemon.covenantremnants.utils.render.torpedo.TorpedoSize
import org.combustiblelemon.covenantremnants.utils.render.torpedo.WraithFlight
import org.combustiblelemon.covenantremnants.weapons.MeltBurn
import org.combustiblelemon.covenantremnants.weapons.PlasmaBombardmentOnHit
import org.combustiblelemon.covenantremnants.weapons.PlasmaMortarOnHit
import org.combustiblelemon.lemonlib.geometry.isPointWithinBounds
import org.lwjgl.util.vector.Vector2f
import java.util.EnumSet
import kotlin.random.Random

/**
 * One-per-combat owner of the plasma torpedoes' Halo: Reach look (created by the launchers'
 * every-frame hook [org.combustiblelemon.covenantremnants.weapons.PlasmaTorpedoEffect]). Follows
 * every torpedo: muzzle flash at launch, body/flare/trail and the in-flight hum while it flies, and
 * when it is gone the impact and explosion sound if it hit or was shot down (a torpedo that runs
 * out of fuel just fades); runs and draws the plasma armor melt of their hits ([MeltBurn],
 * [MeltPatch]). Also draws the Plasma Cannon rounds' energy aura ([CannonFlight]), the Plasma Mortar
 * rounds' Reach Wraith look ([WraithFlight]), the Plasma Bombardment's Reach AA gun charge, firing
 * flash, round and detonation ([BombardCharge], [BombardFlight], [ArtilleryBurst]) and the hit
 * splashes. Drawn above ships and missiles.
 */
class TorpedoRenderPlugin private constructor() : CombatLayeredRenderingPlugin {
    companion object {
        private const val KEY = "covenant_remnants_torpedo_render_plugin"
        const val SOUND_IMPACT = "covenant_remnants_plasma_torpedo_impact"

        // Reach: the hum fades in and out over 0.2 s; the explosion pitches 0 to -2 semitones down.
        private const val LOOP_FADE = 0.2f
        private const val IMPACT_PITCH_MIN = 0.891f

        // Plasma Mortar rounds: give up on a round never seen in play after this long (its body already
        // fades with the round's own fade); log ends sooner than WRAITH_EARLY_END or never seen in play.
        private const val WRAITH_UNSEEN_TIMEOUT = 3f
        private const val WRAITH_EARLY_END = 0.2f
        private const val WRAITH_SHOT_ID = "covenant_remnants_plasmamortar_shot"
        private const val WRAITH_LOGGED_ENDS = 3

        // Plasma Bombardment rounds: the same tracking as the Wraith rounds (BALLISTIC: picked up from the
        // projectile list, the .wpn onFireEffect is not called for them).
        private const val BOMBARD_SHOT_ID = "covenant_remnants_plasmabombardment_shot"

        // Plasma torpedoes: give up on one never seen in play after this long (as the Wraith rounds);
        // log the first few hits (the melt trigger reads the hit's damage result).
        private const val TORPEDO_UNSEEN_TIMEOUT = 3f
        private const val TORPEDO_LOGGED_HITS = 3

        fun forEngine(engine: CombatEngineAPI): TorpedoRenderPlugin {
            (engine.customData[KEY] as? TorpedoRenderPlugin)?.let { return it }
            val plugin = TorpedoRenderPlugin()
            engine.customData[KEY] = plugin
            engine.addLayeredRenderingPlugin(plugin)
            return plugin
        }
    }

    private val log = Global.getLogger(TorpedoRenderPlugin::class.java)
    private val flights = ArrayList<TorpedoFlight>()
    private val torpedoes = HashMap<MissileAPI, TorpedoFlight>()
    private val torpedoPathsLogged = HashSet<String>()
    private var torpedoHitsLogged = 0
    private val melts = ArrayList<MeltSession>()
    private val meltPatches = ArrayList<MeltPatch>()
    private val meltDirty = LinkedHashSet<ShipAPI>()
    private val cannons = ArrayList<CannonFlight>()
    private val wraiths = ArrayList<WraithFlight>()
    private val wraithRounds = HashSet<DamagingProjectileAPI>()
    private var wraithFromFireLogged = false
    private var wraithFromScanLogged = false
    private var wraithEndsLogged = 0
    private val bombards = ArrayList<BombardFlight>()
    private val bombardRounds = HashSet<DamagingProjectileAPI>()
    private var bombardScanLogged = false
    private var bombardEndsLogged = 0
    private val charges = LinkedHashMap<WeaponAPI, BombardCharge>()
    private val artillery = ArrayList<ArtilleryBurst>()
    private val bursts = ArrayList<TorpedoBurst>()
    private val renderer = TorpedoRenderer()
    private var time = 0f

    /**
     * Starts following a plasma torpedo (muzzle flash + flight). Called by the launchers' on-fire
     * paths ([via] names the path for the log): the `.proj` onFireEffect and the `.wpn`
     * everyFrameEffect (the engine calls both, obf weapon fire), plus the missile-list scan here;
     * a torpedo already followed is skipped.
     */
    fun launch(missile: MissileAPI, weapon: WeaponAPI?, via: String) {
        val size = TorpedoSize.forSpec(missile.projectileSpecId)
            ?: weapon?.let { TorpedoSize.forWeapon(it.size) } ?: return
        register(missile, size, via, missile.location)
    }

    private fun register(missile: MissileAPI, size: TorpedoSize, via: String, muzzle: Vector2f) {
        if (missile in torpedoes) return
        if (torpedoPathsLogged.add(via)) {
            log.info("Plasma torpedo registered by $via: ${missile.projectileSpecId} at ${missile.location}")
        }
        val flight = TorpedoFlight(missile, size)
        torpedoes[missile] = flight
        flights.add(flight)
        bursts.add(TorpedoBurst.muzzle(muzzle, missile.facing, size.unit, size.style))
    }

    /** Torpedoes no on-fire path registered, picked up from the engine's missile list. */
    private fun scanTorpedoes(engine: CombatEngineAPI) {
        for (missile in engine.missiles) {
            val size = TorpedoSize.forSpec(missile.projectileSpecId) ?: continue
            if (missile in torpedoes || missile.didDamage() || missile.isFading || missile.isFizzling) continue
            register(missile, size, "the missile list", missile.spawnLocation)
        }
    }

    /**
     * A plasma torpedo struck at [point] ([org.combustiblelemon.covenantremnants.weapons.PlasmaTorpedoOnHit]):
     * its impact burst is drawn there.
     */
    fun torpedoHit(missile: MissileAPI, point: Vector2f, shieldHit: Boolean, toArmor: Float?, toHull: Float?) {
        torpedoes[missile]?.hitPoint = Vector2f(point)
        if (torpedoHitsLogged < TORPEDO_LOGGED_HITS) {
            torpedoHitsLogged++
            log.info(
                "Plasma torpedo hit (${missile.projectileSpecId}): shieldHit=$shieldHit damage to armor=$toArmor " +
                    "hull=$toHull tracked=${missile in torpedoes}"
            )
        }
    }

    /**
     * Starts the plasma armor melt of a torpedo hit at [point] on [target]: [pool] armor damage over
     * [MeltBurn.DURATION] s within the size's melt radius, and the molten patch ([MeltPatch]) for a
     * torpedo travelling at [travelAngle] degrees.
     */
    internal fun startMelt(target: ShipAPI, source: ShipAPI?, point: Vector2f, travelAngle: Float, size: TorpedoSize, pool: Float) {
        val grid = target.armorGrid ?: return
        val inHull = { p: Vector2f -> isPointWithinBounds(p, target) }
        val burn = MeltBurn.pick(grid, target.facing, point, size.meltRadius, pool, inHull) ?: return
        melts.add(MeltSession(target, source, burn, MeltPatch.forHit(target, point, travelAngle, size.meltRadius, inHull)))
    }

    /** One running melt: the burn (null once it stopped) and its patch. */
    private class MeltSession(val target: ShipAPI, val source: ShipAPI?, var burn: MeltBurn?, val patch: MeltPatch)

    private fun advanceMelts(engine: CombatEngineAPI, amount: Float) {
        val iterator = melts.iterator()
        while (iterator.hasNext()) {
            val melt = iterator.next()
            val target = melt.target
            val inPlay = engine.isEntityInPlay(target)
            val burn = melt.burn
            if (burn != null) {
                // Melting stops when the target dies (developer: the glow keeps cooling on the wreck).
                if (!inPlay || target.isHulk || !target.isAlive) {
                    showMelted(engine, melt, burn)
                    melt.burn = null
                } else {
                    if (burn.advance(amount)) showMelted(engine, melt, burn)
                    if (burn.isDone) melt.burn = null
                }
            }
            if (!inPlay) melt.patch.detach()
            melt.patch.advance(amount)
            if (melt.burn == null && melt.patch.isDone) iterator.remove()
        }
        for (ship in meltDirty) ship.syncWithArmorGridState()
        meltDirty.clear()
    }

    /** The armor melted since the last tick as a floating number; the ship's decals are re-synced. */
    private fun showMelted(engine: CombatEngineAPI, melt: MeltSession, burn: MeltBurn) {
        val amount = burn.takePending()
        if (amount <= 0f) return
        if (Misc.shouldShowDamageFloaty(melt.source, melt.target)) {
            engine.addFloatingDamageText(melt.patch.center, amount, Misc.FLOATY_ARMOR_DAMAGE_COLOR, melt.target, melt.source)
        }
        meltDirty.add(melt.target)
    }

    /**
     * The impact burst alone (no torpedo, no sound, no smoke) at [location], [unit] su per Reach
     * unit: the Plasma Mortar's hit splash ([org.combustiblelemon.covenantremnants.weapons.PlasmaMortarOnHit]).
     */
    fun splash(location: Vector2f, unit: Float) {
        bursts.add(TorpedoBurst.impact(location, unit, smoke = false))
    }

    /**
     * Follows a Plasma Cannon round with its energy aura
     * ([org.combustiblelemon.covenantremnants.weapons.PlasmaCannonOnFire], [AuraStyle.CANNON]).
     */
    internal fun trackCannonRound(projectile: DamagingProjectileAPI, unit: Float, style: AuraStyle = AuraStyle.CANNON) {
        cannons.add(CannonFlight(projectile, unit, style))
    }

    /**
     * Follows a Plasma Mortar round with the Reach Wraith mortar look
     * ([org.combustiblelemon.covenantremnants.weapons.PlasmaMortarOnFire]).
     */
    fun trackWraithRound(projectile: DamagingProjectileAPI, unit: Float) {
        if (!wraithFromFireLogged) {
            wraithFromFireLogged = true
            log.info("Wraith round registered by onFire: ${projectile.javaClass.simpleName} at ${projectile.location}")
        }
        addWraith(projectile, unit)
    }

    private fun addWraith(projectile: DamagingProjectileAPI, unit: Float) {
        if (wraithRounds.add(projectile)) wraiths.add(WraithFlight(projectile, unit))
    }

    /**
     * The Plasma Bombardment's Reach AA gun charge at [weapon]'s muzzle, fed every frame by
     * [org.combustiblelemon.covenantremnants.weapons.PlasmaBombardmentEffect]. A projectile weapon's
     * charge level rises 0 -> 1 over its chargeup and falls over its cooldown (when
     * cooldownRemaining > 0); only the rise is the charge.
     */
    fun charge(weapon: WeaponAPI) {
        val level = weapon.chargeLevel
        val charging = weapon.cooldownRemaining <= 0f && level > 0f && level < 1f
        val state = charges[weapon]
            ?: if (charging) BombardCharge(PlasmaBombardmentOnHit.CHARGE_UNIT).also { charges[weapon] = it } else return
        val point = weapon.getFirePoint(0)
        state.feed(level, charging, point.x, point.y, weapon.currAngle)
    }

    /**
     * The rebuilt `covenant_explosion_artillery_huge` at [location] (no sound), [unit] su per Reach unit,
     * with [sparks] sparks and a [lightLife] s blast light: the Plasma Bombardment's hit
     * ([org.combustiblelemon.covenantremnants.weapons.PlasmaBombardmentOnHit], the defaults) and the
     * Plasma Cannon's ([org.combustiblelemon.covenantremnants.weapons.PlasmaCannonOnHit]).
     */
    fun artilleryImpact(
        location: Vector2f,
        unit: Float,
        sparks: Int = ArtilleryBurst.SPARKS,
        lightLife: Float = ArtilleryBurst.BLAST_LIGHT_LIFE
    ) {
        artillery.add(ArtilleryBurst.impact(location, unit, sparks, lightLife))
    }

    /**
     * Rounds of the BALLISTIC rebuilt weapons, picked up from the projectile list: Plasma Mortar rounds
     * the on-fire hook did not register (see PlasmaMortarEffect), and every Plasma Bombardment round
     * (its firing flash starts here, at the round's spawn point).
     */
    private fun scanRounds(engine: CombatEngineAPI) {
        for (projectile in engine.projectiles) {
            when (projectile.projectileSpecId) {
                WRAITH_SHOT_ID -> {
                    if (projectile in wraithRounds || projectile.didDamage() || projectile.isFading) continue
                    if (!wraithFromScanLogged) {
                        wraithFromScanLogged = true
                        log.info("Wraith round picked up from the projectile list (onFire did not register it): " +
                            "${projectile.javaClass.simpleName} at ${projectile.location}")
                    }
                    addWraith(projectile, PlasmaMortarOnHit.ROUND_UNIT)
                }
                BOMBARD_SHOT_ID -> {
                    if (projectile in bombardRounds || projectile.didDamage() || projectile.isFading) continue
                    if (!bombardScanLogged) {
                        bombardScanLogged = true
                        log.info("Bombardment round picked up from the projectile list: " +
                            "${projectile.javaClass.simpleName} at ${projectile.location}")
                    }
                    bombardRounds.add(projectile)
                    bombards.add(BombardFlight(projectile, PlasmaBombardmentOnHit.ROUND_UNIT))
                    artillery.add(ArtilleryBurst.muzzle(projectile.spawnLocation, projectile.facing, PlasmaBombardmentOnHit.MUZZLE_UNIT))
                }
            }
        }
    }

    override fun advance(amount: Float) {
        val engine = Global.getCombatEngine() ?: return
        if (engine.isPaused) return
        time += amount

        scanTorpedoes(engine)
        val flightIterator = flights.iterator()
        while (flightIterator.hasNext()) {
            val flight = flightIterator.next()
            if (flight.flying) track(engine, flight)
            flight.advance(amount)
            if (flight.isDone) {
                flightIterator.remove()
                if (!engine.isEntityInPlay(flight.missile)) torpedoes.remove(flight.missile)
            }
        }
        advanceMelts(engine, amount)
        val cannonIterator = cannons.iterator()
        while (cannonIterator.hasNext()) {
            val cannon = cannonIterator.next()
            if (cannon.flying) {
                val round = cannon.projectile
                if (round.isExpired || round.didDamage() || !engine.isEntityInPlay(round)) cannon.end()
            }
            cannon.advance(amount)
            if (cannon.isDone) cannonIterator.remove()
        }
        scanRounds(engine)
        val wraithIterator = wraiths.iterator()
        while (wraithIterator.hasNext()) {
            val wraith = wraithIterator.next()
            if (wraith.flying) {
                // isExpired is not used: a BALLISTIC round may report it from spawn (2026-10-09: the
                // Wraith rounds never drew). A new round may not be in play yet: wait until seen.
                val round = wraith.projectile
                val inPlay = engine.isEntityInPlay(round)
                if (inPlay) wraith.seenInPlay = true
                val gone = (wraith.seenInPlay && !inPlay) || (!wraith.seenInPlay && wraith.age > WRAITH_UNSEEN_TIMEOUT)
                if (round.didDamage() || gone) {
                    if (wraith.age < WRAITH_EARLY_END || !wraith.seenInPlay || wraithEndsLogged < WRAITH_LOGGED_ENDS) {
                        wraithEndsLogged++
                        log.info(
                            "Wraith round ended after ${wraith.age} s, ${wraith.distance} su: didDamage=${round.didDamage()} " +
                                "inPlay=$inPlay seen=${wraith.seenInPlay} expired=${round.isExpired} fading=${round.isFading}"
                        )
                    }
                    wraith.end()
                }
            }
            wraith.advance(amount)
            if (wraith.isDone) {
                wraithIterator.remove()
                if (!engine.isEntityInPlay(wraith.projectile)) wraithRounds.remove(wraith.projectile)
            }
        }
        val bombardIterator = bombards.iterator()
        while (bombardIterator.hasNext()) {
            val bombard = bombardIterator.next()
            if (bombard.flying) {
                // As for the Wraith rounds: isExpired is not used, and a new round may not be in play yet.
                val round = bombard.projectile
                val inPlay = engine.isEntityInPlay(round)
                if (inPlay) bombard.seenInPlay = true
                val gone = (bombard.seenInPlay && !inPlay) || (!bombard.seenInPlay && bombard.age > WRAITH_UNSEEN_TIMEOUT)
                if (round.didDamage() || gone) {
                    if (bombard.age < WRAITH_EARLY_END || !bombard.seenInPlay || bombardEndsLogged < WRAITH_LOGGED_ENDS) {
                        bombardEndsLogged++
                        log.info(
                            "Bombardment round ended after ${bombard.age} s, ${bombard.distance} su: didDamage=${round.didDamage()} " +
                                "inPlay=$inPlay seen=${bombard.seenInPlay} fading=${round.isFading}"
                        )
                    }
                    bombard.end()
                }
            }
            bombard.advance(amount)
            if (bombard.isDone) {
                bombardIterator.remove()
                if (!engine.isEntityInPlay(bombard.projectile)) bombardRounds.remove(bombard.projectile)
            }
        }
        val chargeIterator = charges.values.iterator()
        while (chargeIterator.hasNext()) {
            val charge = chargeIterator.next()
            charge.advance(amount)
            if (charge.isDone) chargeIterator.remove()
        }
        val artilleryIterator = artillery.iterator()
        while (artilleryIterator.hasNext()) {
            val burst = artilleryIterator.next()
            burst.advance(amount)
            if (burst.isDone) artilleryIterator.remove()
        }
        val burstIterator = bursts.iterator()
        while (burstIterator.hasNext()) {
            val burst = burstIterator.next()
            burst.advance(amount)
            if (burst.isDone) burstIterator.remove()
        }
    }

    private fun track(engine: CombatEngineAPI, flight: TorpedoFlight) {
        val missile = flight.missile
        // A torpedo registered on fire may not be in play yet: it only ends once it was seen in play.
        val inPlay = engine.isEntityInPlay(missile)
        if (inPlay) flight.seenInPlay = true
        val gone = (flight.seenInPlay && !inPlay) || (!flight.seenInPlay && flight.age > TORPEDO_UNSEEN_TIMEOUT)
        if ((flight.seenInPlay && missile.isExpired) || gone) {
            flight.end()
            if (missile.didDamage() || missile.hitpoints <= 0f) {
                // At the on-hit point when known (the missile has moved on by a frame), no dark smoke
                // (developer 2026-10-09); impact pitch by launcher size.
                val location = Vector2f(flight.hitPoint ?: missile.location)
                bursts.add(TorpedoBurst.impact(location, flight.unit, smoke = false, style = flight.style))
                Global.getSoundPlayer().playSound(
                    SOUND_IMPACT, flight.pitch * (Random.nextFloat() * (1f - IMPACT_PITCH_MIN) + IMPACT_PITCH_MIN),
                    CovenantSettings.effectVolume, location, Vector2f()
                )
            }
            return
        }
        if (!missile.isFizzling && !missile.isFading) {
            Global.getSoundPlayer().playLoop(
                flight.size.loopId, missile, 1f, CovenantSettings.effectVolume, missile.location, missile.velocity, LOOP_FADE, LOOP_FADE
            )
        }
    }

    override fun render(layer: CombatEngineLayers, viewport: ViewportAPI) {
        meltPatches.clear()
        for (melt in melts) meltPatches.add(melt.patch)
        renderer.render(viewport, flights, cannons, wraiths, bombards, charges.values, artillery, bursts, meltPatches, time)
    }

    override fun getActiveLayers(): EnumSet<CombatEngineLayers> =
        EnumSet.of(CombatEngineLayers.ABOVE_SHIPS_AND_MISSILES_LAYER)

    override fun getRenderRadius(): Float = Float.MAX_VALUE

    override fun init(entity: CombatEntityAPI) {}

    override fun cleanup() {}

    override fun isExpired(): Boolean = false
}
