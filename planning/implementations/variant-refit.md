# Implementation — variant refit after the ship-editor pass (2026-10-10)

The developer finished ship bounds and weapon slots in the ship editor (hulls committed in
`13af425`). The 16 standard variants still followed the old slot layouts:

- one variant referenced a slot that no longer exists, which crashes the game;
- several had weapons in slots of the wrong size;
- one carrier had wings but no launch bay;
- most new slots were empty.

This pass refits all 16 standard variants and makes three small hull edits the developer ruled
on. The wing variants (`wing1_fighter`, `wing1_bomber`, `wing2`, `wing3`) are untouched. The
built-in fix found in the same check is in [editor-pass-builtins.md](editor-pass-builtins.md).

## Problems found (read-only check, 2026-10-10)

| Variant | Problem |
| --- | --- |
| destroyer1 | Pulse Laser in `WS0004`, a slot the hull no longer has. The engine throws `Slot id [...] not found on hull` at fleet render and `slot is null` in combat (obf `campaign.fleet.java` ~3427, `combat.entities.java` ~11816). |
| destroyer2 | 2 wings, but the hull has no `LAUNCH_BAY` slot. 56 OP of 55. |
| destroyer3 | Medium Plasma Cannons in SMALL `WS0001` / `WS0002`; a small Pulse Laser in MEDIUM `WS0003`. |
| cruiser1 | Small Pulse Lasers in MEDIUM `WS0004` / `WS0005`. |
| supercap1 | Medium Beam Emitters in LARGE `WS0005`–`WS0008`; small Pulse Lasers in MEDIUM `WS0009` / `WS0010`; 3 wings but 1 `LAUNCH_BAY`. |
| supercap2 | Medium Beam Emitter in LARGE `WS0006`; 9 wings but 5 `LAUNCH_BAY`. |
| capital3 (file) | The file `covenant_remnants_capital3_std.variant` holds variantId `covenant_remnants_cruiser5_std` (hull cruiser5). |
| capital1 (hull) | `WS0013` at [6, −40] is MEDIUM, but its mirror `WS0014` at [6, 40] is SMALL. |
| all | Many new slots empty (capital1 / capital2 / cruiser5 / cruiser1–4 / destroyer2–4 / frigate1 / supercap1–2). |

## Developer rulings (2026-10-10)

| Topic | Decision |
| --- | --- |
| Slot fill | Role first: fit each hull's core role weapons. Empty slots are OK. |
| Weapon pool | Core set (Pulse Laser, Beam Emitter, Plasma Cannon, Energy Projector, Plasma Lance, Plasma Autocannon, torpedoes in SYNERGY slots) plus Mortar and Bombardment. No Suppressor Beam (beyond frigate1's built-in) and no Plasma Repeater (it stays the wings' gun). |
| Spare OP | Vents / capacitors, then hullmods proposed per variant. |
| destroyer2 bays | I write 2 `LAUNCH_BAY` slots into the `.ship`; the developer confirms placement in the editor. |
| supercap1 / supercap2 | Keep wings = csv fighter bays (3 and 9), as in vanilla (Heron 3 bays / 2 slots, Condor 2 / 1). |
| capital3 file | Rename to `covenant_remnants_cruiser5_std.variant`. |
| capital1 `WS0014` | I make it MEDIUM, to mirror `WS0013`. |
| Fits | All 16 per-variant fits approved as proposed (frigates, destroyers, cruisers, capitals in four groups). |

Choices made inside those rulings (stated in the ruling round or here; say if any is wrong):

- **Weapon groups:** one group per weapon type, all `LINKED`, in this order:
  1. heavy guns (Projector, Lance, Autocannon, Bombardment);
  2. Plasma Cannon;
  3. Beam Emitter;
  4. torpedoes;
  5. Pulse Laser.
- **Autofire:** Pulse Lasers autofire. Beam Emitters keep the variant's old autofire flag (on only on
  supercap2). Everything else is manual.
- **Groups kept:** variants whose weapons did not change (destroyer1, destroyer2, frigate2, frigate3)
  keep their weapon groups. destroyer1 only loses the dead `WS0004` entry.
- **frigate1's built-in Suppressor Beam** is listed in its own weapon group, as vanilla does for
  built-ins (the Onslaught's TPCs in `onslaught_Standard.variant`). Built-ins cost no OP.
- **Unchanged fields:** `goalVariant`, `quality` and `displayName`.
- **Vents / caps:** split as in the table. Base caps are 10 / 20 / 30 / 50 (`CoreAutofitPlugin.getBaseMax`;
  skills can raise them).
- **Hullmods:** none of the picks is in the Covenant incompatibility registry
  ([vanilla-hullmod-incompatibility.md](vanilla-hullmod-incompatibility.md)). Slipspace Tuning needs the
  Slipspace Drive hullmod, which every hull has built in.
- **destroyer2 bay positions:** `LB 1` at [−5, 15] and [−20, 15]; `LB 2` at [−5, −15] and [−20, −15].
  They sit on the dark hangar openings either side of the spine in the sprite. The format (`LARGE`,
  `LAUNCH_BAY`, `HIDDEN`, arc 360, angle 0) is copied from cruiser2.

## The fits

OP is used / budget. PL = Pulse Laser Turret, BE = Plasma Beam Emitter. "Kept" means the weapon was
already in that slot.

| Variant | Weapons | Vents / caps | Hullmods | OP |
| --- | --- | --- | --- | --- |
| frigate1 Rasho Escort | built-in Suppressor Beam `WS0001`; `WS0002` PL | 10 / 10 | Slipspace Tuning, Huragok Crew, Advanced Optics, Plasma Capacitors, Hardened Subsystems | 50 / 50 |
| frigate2 Ceudar Gunboat | unchanged (Plasma Cannon, PL × 2) | 10 / 10 | Huragok Crew, Plasma Capacitors | 55 / 55 |
| frigate3 Landing Ship | unchanged (PL) | 10 / 10 | Huragok Crew, Slipspace Tuning, Reinforced Bulkheads | 40 / 40 |
| destroyer1 SDV PD screen | `WS0004` entry dropped; BE × 2, PL × 3 kept | 20 / 20 | Huragok Crew, Integrated Point Defense AI, Advanced Optics | 102 / 110 |
| destroyer2 CPV Escort Carrier | PL `WS0001`–`WS0004` kept, `WS0005` / `WS0006` empty; 2 × Type-26 Banshee kept | 7 / 8 | — | 55 / 55 |
| destroyer3 CRS Strike | `WS0003` Plasma Cannon, `WS0004` Plasma Torpedo, `WS0001` / `WS0002` PL; `WS0005` / `WS0006` empty | 20 / 20 | Huragok Crew | 87 / 90 |
| destroyer4 Ket Assault | `WS0001` Lance kept; PL `WS0002`–`WS0005`; `WS0006` / `WS0007` empty | 20 / 20 | Huragok Crew, Plasma Capacitors, Armored Weapon Mounts | 110 / 110 |
| cruiser1 CCS Battlecruiser | `WS0001` Projector and `WS0002` / `WS0003` BE kept; `WS0006` Lance; `WS0004` / `WS0005` Plasma Cannon; `WS0007` / `WS0008` PL; `WS0009` / `WS0010` empty | 23 / 24 | Huragok Crew | 170 / 170 |
| cruiser2 DDS Escort Carrier | 3 × Type-26, BE, PL × 3 kept; `WS0005` / `WS0006` PL; `WS0007`–`WS0010` empty | 13 / 14 | Expanded Deck Crew | 105 / 105 |
| cruiser3 ORS Light Cruiser | `WS0001` Lance kept; `WS0004` / `WS0005` Autocannon; `WS0006` / `WS0007` BE; `WS0010` / `WS0011` PL; the broadside Plasma Cannons (`WS0002` / `WS0003`) removed; rest empty | 14 / 14 | Huragok Crew | 135 / 135 |
| cruiser4 RCS Armored Cruiser | Lance, BE × 2, PL × 2 kept; `WS0007` Autocannon; `WS0006` Bombardment; `WS0012` / `WS0013` PL; rear `WS0008`–`WS0011` empty | 26 / 26 | Huragok Crew | 170 / 170 |
| cruiser5 CPV Heavy Destroyer | Projector, Autocannon `WS0002`, BE × 2, PL × 2 kept; `WS0007` Autocannon; PL `WS0008`–`WS0011`; `WS0012` empty | 30 / 30 | Huragok Crew, Plasma Capacitors, Hardened Subsystems, Advanced Optics | 248 / 280 |
| capital1 Reverence Battleship | `WS0008` Projector (moved from `WS0001`); `WS0007` Lance; `WS0001` / `WS0002` Autocannon; BE × 2 kept; `WS0013` / `WS0014` Plasma Cannon; PL `WS0005` / `WS0006` kept + `WS0011` / `WS0012` + `WS0016` / `WS0017`; `WS0009` / `WS0010` / `WS0015` empty | 50 / 50 | Huragok Crew, Plasma Capacitors, Hardened Subsystems | 349 / 360 |
| capital2 ORS Heavy Cruiser | Projector, Autocannon × 2, BE × 2, rear PL × 2 kept; `WS0008` Lance; `WS0017` / `WS0018` Plasma Cannon; PL `WS0011`–`WS0014`; `WS0009` / `WS0010` empty | 45 / 45 | Huragok Crew, Plasma Capacitors | 314 / 320 |
| supercap1 CAS Assault Carrier | `WS0008` Projector (moved from `WS0001`); `WS0001`–`WS0004` Autocannon; `WS0005` / `WS0006` Lance; `WS0007` Bombardment; `WS0009` / `WS0010` Plasma Torpedo; PL `WS0011`–`WS0016` + `WS0023`–`WS0026`; `WS0017`–`WS0022` empty; 3 × Seraph kept | 50 / 50 | Huragok Crew, Expanded Deck Crew | 446 / 460 |
| supercap2 CVS Supercarrier | `WS0006` Autocannon (replaces the BE); heavy torpedoes × 2, BE × 3, PL × 6 kept; `WS0013` / `WS0014` PL; `WS0015`–`WS0020` empty; 9 wings kept | 50 / 50 | Huragok Crew, Expanded Deck Crew, Expanded Missile Racks, Hardened Subsystems | 460 / 520 |

The ruling-round preview for cruiser2 showed one wrong subtotal ("57" for the kept part; it is 51). The
total of 105 / 105 was right and is what was built.

## Engine facts (starsector-knowledge, 0.98a-RC8)

- **Slot types.** SYNERGY takes ENERGY or MISSILE weapons; ENERGY takes ENERGY only (refit picker,
  obf `coreui.refit.java` ~5924).
- **Bays.** Each launch bay object pairs a wing with a `LAUNCH_BAY` slot (obf `combat.entities.java`
  ~12202). Every launch path loops over the ship's launch bays.
- **Carrier AI.** The AI treats a ship as a carrier when `launchBays.size() >= fighterBays × 0.66`
  (~7335).
- **Not readable.** The bay factory (`loading.specs`) is not in the skill bundle. So it is not proven
  how wings share slots when there are fewer slots than bays. Vanilla ships with fewer slots than bays
  (Heron, Condor, Odyssey, Apex) and the Tempest (1 bay, 0 slots) exist; supercap1 / supercap2 follow
  that precedent.

## Files

- `data/hulls/covenant_remnants_destroyer2.ship` — `LB 1` and `LB 2` added.
- `data/hulls/covenant_remnants_capital1.ship` — `WS0014` size `SMALL` → `MEDIUM`.
- `data/variants/covenant_remnants_capital3_std.variant` → `covenant_remnants_cruiser5_std.variant`
  (`git mv`, then refit).
- `data/variants/covenant_remnants_{capital1,capital2,cruiser1-5,destroyer1-4,frigate1-3,supercap1,supercap2}_std.variant`
  — weapons, vents / caps, hullmods, groups.
- Tools (session scratchpad, not kept):
  - `gen_variants.py` writes the 16 files from the fit table, keeping each file's ids, name, wings and
    line endings.
  - `check_fits.py` runs the checks listed below.

## Verification done

`check_fits.py` (loose-JSON parser: `#` comments, trailing commas, bare values) over all 16 combat
hulls and standard variants reports 0 problems:

- every hull carries the four built-ins;
- the file name matches the variantId;
- every slot id exists on the hull;
- every weapon matches its slot's size and type (built-in only in its own `BUILT_IN` slot);
- at most 7 weapon groups, and no slot appears in two groups;
- OP total equals the approved table and is within budget;
- vents / caps are at most the base cap;
- wings are at most the csv bays, and a variant with wings has `LAUNCH_BAY` slots;
- every hullmod id exists in the vanilla or mod `hull_mods.csv`, and none is a blocked one.

All touched `.ship` / `.variant` files parse.

## In-game checklist

Spawn each hull from the campaign (`CovenantGiveAll` or the console `AddShip <variantId>`), or use the
simulator.

1. **Refit screens.** No red OP; weapons in the slots listed above; hullmods listed and none greyed out
   (Slipspace Tuning on frigate1 / frigate3, Expanded Deck Crew on the carriers).
2. **destroyer1 (SDV).** It shows in the fleet screen and deploys without a crash.
3. **destroyer2 (CPV Escort Carrier).**
   - Its 2 Banshee wings launch and recover.
   - They come out of the two hangar openings beside the spine. If they do not, move `LB 1` / `LB 2`
     in the ship editor and re-check `builtInMods` after the save.
4. **supercap1 / supercap2.**
   - All 3 / 9 wings launch.
   - The AI uses them as carriers: wings sent at targets, the ship holds back.
5. **capital1 (Reverence).** The Energy Projector fires from the nose slot (`WS0008`); `WS0014` shows
   as a medium slot with a Plasma Cannon.
6. **AI handling.** The AI keeps fighting with the new groups: torpedoes fired, Pulse Lasers on
   autofire, Beam Emitters used.

## Open items

- destroyer2 bay positions are placeholders until the developer checks them in the editor.
- Whether the carrier AI treats supercap1 (3 bays, 1 slot) and supercap2 (9 bays, 5 slots) as
  carriers. Check in game; the fix would be more `LAUNCH_BAY` slots in the editor.
- cruiser5 (CPV Heavy Destroyer) has 280 OP. Its slots use 248 of it with every ruled hullmod;
  balance-pass question.
- The fits are role-first placeholders until the balance pass.
