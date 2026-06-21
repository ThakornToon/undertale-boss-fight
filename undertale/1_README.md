# Translation Progress Tracker

> **Status — initial release shipped + Muffet + Toriel.** Core engine, host loop,
> boss-select menu, and sound system are done. Playable: **Toriel · Papyrus · Sans ·
> Asgore · Undyne (NORMAL+GENOCIDE) · Mettaton (EX+NEO) · Muffet** — 7 bosses / 8 fight
> variants. `./gradlew build` → green, **44 tests** pass.
>
> **Only target left: Asriel** (scripted turn list + transform + SAVE graph). Optional
> polish: Mettaton `spr_mettstick` segmented-arm art, Muffet paper-distortion /
> telegram-spider sprites.
>
> Title menu seeds run state (no story): **BOSS**, **MAX HP** (LV presets), **MODE**
> (NORMAL / GENOCIDE — seeds `murderlv≥7`/`lv`/`at` so bosses fall in one hit and use
> genocide dialogue). Build difficulty: Papyrus < Asgore < Mettaton < Undyne < Sans.

---

## How the port works

**Controller/body split (every boss):**
- **Controller** (`obj_*b` → `Boss extends Monster`): runs `scr_monstersetup` (ctor) /
  `scr_monsterdefeat` (death), owns the turn state machine, dialogue, hurt-shudder.
- **Body** (`obj_*b_body` → `BossBody`): draws the multi-part sprite and **runs the
  bullet patterns**, dispatched by an integer selector the controller sets.

**Turn protocol** (`global.mnfight`): `99` setup/cutscene · `2` player menu · `3`
transition→enemy turn · `1` enemy turn running · `4` ACT/SPARE · `5` special/transform
· negatives = frozen during death cutscene. `global.myfight` mirrors the menu action
(0 fight / 2 act / 4 mercy); `global.turntimer` counts down the enemy turn.

**Frame loop (fixed 30 FPS** — every GML alarm/turntimer ports as-is):
`input → beginStep → boss → body → patterns → soul → turns → damage → menu → endStep`,
then render depth-sorted: board → bullets → bodies → soul → UI.

**Soul modes** (`obj_heart`): RED free-move · BLUE gravity+jump · GREEN directional
4-way block · YELLOW shoot · PURPLE web-lock (Muffet: bound to horizontal strands,
↑/↓ hops). BLUE jump is a 1:1 port of the GML four-band piecewise gravity
(launch −6; apex-hang; terminal 8; release-to-cut → full peak ≈64px, 3-frame tap ≈23px).

---

## Architecture

> Class names map 1:1 to files under `src/main/java/`. **Design rule that holds:**
> adding a boss requires *zero* edits to `core/` — only new `boss/` + `bullet/`
> subclasses and a registry entry.

```
src/main/java/
├── Main.java                  ← boot → BossSelectMenu → BattleScene
├── core/    Game (window + fixed-timestep loop), GlobalState (every global.*,
│            singleton), EntityManager (instance list: add/destroy/with/exists),
│            Entity, TurnManager (sole interpreter of mnfight), InputHandler,
│            AlarmTimer (alarm[0..11]/owner), EventUser, Sequence (cutscene counter)
├── battle/  BattleScene (wires Core), Soul/SoulMode, BulletBoard/BorderSetup
│            (idealborder[4] + SCR_BORDERSETUP presets), WebBoard (Muffet strands),
│            DamageSystem (scr_damagestandard + override hooks), KarmaTicker (Sans km),
│            MercySystem, BattleMenu, RatingsMaster (Mettaton EX meter),
│            ActDispatcher, BossSelectMenu (title screen, seeds run state)
├── boss/    Boss, BossBody, Monster, BossRegistry, ScreenFlash, AnnoyingDog,
│            {Papyrus,Sans,Asgore,Muffet,Toriel}{Boss,Body}, Undyne{Boss,Body}+UndyneXBody,
│            Mettaton{Ex,Neo}{Boss,Body}
├── bullet/  AttackPattern, Bullet, Generator, PlayerBullet, Shootable, + sub-packages:
│            bones/ · gaster/ · asgore/ · undyne/ · mettaton/ · muffet/ · toriel/
└── util/    GMLHelper, Assets, Audio (OGG→PCM, caster_* wrapper), Fonts
```

**`GlobalState`** holds every `global.*`: turn (`mnfight`/`myfight`/`turntimer`/
`firingrate`/`attacked`), player (`hp`/`maxhp`/`at`/`df`/`lv`/`xp`/`inv`/`km`), box
(`idealborder[4]`/`border`/`darkify`/`shakify`), monster stats by slot, presentation
(`faceemotion`/`flag[]`/`hurtanim`/`ratings`/`hope`), persistent (`kills`/`murderlv`/
`osflavor`/`battlegroup`). Reset: `newGame()`, `enterBattle(group)`.

**`DamageSystem`** override hooks: `forcedKill` (NEO), `playerDamageMultiplier` (Undyne
×21 min 600), `fivedamageThreshold` (Asgore kneel at HP≤500), `KarmaTicker` (Sans).

### Boss SPI

`Boss` (abstract, extends `Entity`). Core calls, boss fills: `setup()` (stats/soulmode/
border/overrides) · `update()` (phase gates) · `chooseAttack()` (on `mnfight==3`: pick
selector, set `turntimer`/`firingrate`/border) · `onAct` · `onSpare` · `onDamaged` ·
`onDefeat`. `BossBody`: field `attackSel`, `setAttack(int)`, `update()` runs the active
pattern.

### Per-boss wiring (selector → mechanic)

| Boss | Soul | Turn driver → selector | Win condition | Special hook |
|---|---|---|---|---|
| **Toriel** ✅ | RED | `mycommand` 0–100 → 5 attacks (fire helix / hands) | kill (HP≤150 → DEF −140) **or** refuse-to-fight spare | `conversation` counter → relent; Faltering & low-HP softening cut; border 6/7 |
| **Papyrus** ✅ | BLUE | `fighto` −1→16 (HP gates) | spare only (`mercymod=8000`) | blue-bone "only hurts if moving" |
| **Sans** ✅ | RED+BLUE | `hit_try`/`part`→`a_type`; `lac` special | turn-gated; spare = FIGHT while asleep | KARMA, custom i-frames, blue slams, sleep |
| **Asgore** ✅ | RED | `turns` 1→23 loop→20 → generator table | `fivedamage` (HP≤500 → kneel) | broken mercy (hide SPARE); border 29/30 |
| **Mettaton NEO** ✅ | none | `mercymod=−999999` ends turn instantly | forced one-hit kill | `forcedKill`, death monologue + explode |
| **Undyne NORMAL** ✅ | GREEN↔RED | `order` schedule: green shield / red windows | reform→scripted death (1500 HP grind) | green spear-block engine, two-phase reform |
| **Undyne GENOCIDE** ✅ | GREEN↔RED | `orderb` 0→7 red cycles (+rot ring, spiral) | HP 15000 (player ×21, min 600) | spikier `undynex` body; "world will live on" |
| **Mettaton EX** ✅ | YELLOW | `turns`→`attacktype`=29+turns | **RATINGS** ≥10000/12000 *or* HP→0 | ratings subsystem, `dancewait` tempo, arms-off |
| **Muffet** ✅ | PURPLE | `turnAmt` 0→15 → `SpiderBulletGen.type` (16) | survive 16 turns → telegram spare | web soul (3 strands); pet special turns 4/9/15 |
| **Asriel A** ⬜ | RED | `turns` 0→13 named-attack table | scripted (`turns==13` HYPER GONER) | ACT Hope/Dream; transform to Final |
| **Asriel B** ⬜ | RED | `flag[501]` stage + `flag[505–508]` friends | SAVE graph (all 4 → ending) | SAVE button after `tempvalue[12]≥4` |

---

## Remaining target: Asriel — two chained `obj_monsterparent` fights

> Object IDs in the GML are internal numbers; the names matter, not the numbers.

**Part A — "GOD of Hyperdeath"** (`obj_asrielb` + body). Unwinnable by damage; scripted
list driven by `turns` (persisted in `flag[504]`), `mercymod` hugely negative.
`turns==13` fires HYPER GONER → `mnfight=5` → transform to Part B. `h_mode=1` once
`turns≥8`. **ACT:** Hope/"Pray" (`hope=1`, cuts damage, drops Asriel ATK 8→6); Dream
(fills inventory, heals 4).

| `turns` | Attack | | `turns` | Attack |
|---|---|---|---|---|
| 0,4 | STAR BLAZING | | 5,7 | CHAOS BUSTER (gun) |
| 9 | GALACTA BLAZING | | 11 | CHAOS BLASTER (gun, hard) |
| 1,3 / 8,12 | SHOCKER BREAKER | | 10 | CHAOS SLICER (sword, hard) |
| 2,6 | CHAOS SABER (sword) | | 13 | HYPER GONER → Part B |

**Part B — final form / SAVE** (`obj_asrielfinal`). Driven by `flag[501]` (stage) +
`flag[505–508]` (four lost-soul friends). `501==0` take screen-fillers; after
`tempvalue[12]≥4` deaths the **SAVE** button appears → `501=1`. `501==1` each SAVE
routes to a friend's sub-battle flipping a flag. `501==2` all four saved → save Asriel.
`501==3` breakdown monologue (`turns 0→11`) → ending. Part-B attacks are minimal
(`dmg=1`) — emotional, not mechanical.

**Dialogue/Writer:** `OBJ_WRITER` typewriter + control codes is still a per-turn speech
queue; a real typewriter is deferred.

---

## Progress log (one line per session)

1. **Core engine (Reqs 1–9)** — GlobalState, Entity/EntityManager, AlarmTimer/EventUser/
   Sequence, Soul/SoulMode, BulletBoard/BorderSetup, TurnManager/InputHandler, BattleMenu/
   ActDispatcher, DamageSystem/KarmaTicker/MercySystem, GMLHelper/Assets.
2. **Host loop + Papyrus** — Game (fixed 30 FPS), boss SPI, exact `fighto` −1→16 bones,
   four-band jump, both routes (spare + genocide), Annoying-Dog steal, real sprites.
3. **Boss-select menu + genocide** — seeds run state; added `murderlv`.
4. **Sans** — `bullet/bones/*` + `bullet/gaster/*` + `util/Audio`; blue gauntlet → turning
   point → red phase → Special Attack → sleep/spare, KARMA wired. Many fidelity passes.
5. **Bone art** (`BoneArt`) + **6. Blue-soul jump rewrite** (exact piecewise gravity,
   `SoulJumpTest`) + **7. Player death animation** (Undertale shatter).
8. **Asgore** — `bullet/asgore/*`, 23-turn fire/hand/trident table, broken mercy,
   `fivedamage` kneel, intro + genocide tea→slice. `AsgoreBossTest`.
9. **MODE selector** — boss-select NORMAL/GENOCIDE (replaces SIN row); dead-code cleanup.
10. **Undyne NORMAL** (`obj_undyneboss`) — shared green/red spear engine in `bullet/undyne/`;
    "En guarde!", PA0–24, reform → scripted phase 2 → death. `UndyneBossTest`.
11. **Undyne GENOCIDE** (`obj_undyne_ex`) — spikier `UndyneXBody`, rot-ring + spiral gens,
    ×21/min-600 vs 15000 HP, "This world will live on...!". 7 fidelity passes vs `.mov`.
12. **Object-mapping correction** — the two Undyne objects were swapped (`obj_undyneboss`
    = NORMAL two-phase, `obj_undyne_ex` = short GENOCIDE).
13. **Code review + README condense** — `enterBox()` helper; trimmed done entries.
14–18. **Mettaton NEO + EX** — NEO cutscene one-shot (`forcedKill` + monologue/explode).
    EX: YELLOW shoot soul + `PlayerBullet`/`Shootable`, `RatingsMaster` meter, 20-turn taunt
    machine, both endings; full attack bank ported from `example_*.mov` (legs, parasols,
    plus-bombs, disco beams, heart-core lightning, …); part-origin fix (trimmed-bbox trap).
19. **Sound system** — vendored vorbisspi/jorbis/tritonus under `libs/`; `util/Audio`
    rewritten into OGG→PCM decoder + looping music bus + one-shot SFX + master volume.
    Per-boss themes, menu blips, hurt/strike SFX wired.
20. **Asgore trident/hand alignment** — rotated draws now pivot on real sprite origins;
    trident PNG trimmed 7px → origin (60,24) so the shaft runs through both fists.
21–23. **Pause/mute/menu-music/font/skill-check/SFX** — ESC pause, M global mute,
    boss-select music, OFL pixel fonts (`util/Fonts`), FIGHT-bar accuracy scales damage,
    yellow-soul fire gated to the enemy turn only.
24. **Initial-release milestone** — closed first-commit scope; top-level README rewritten.
25. **Sans spare-trap + Undyne arrow-overlap** — turning-point SPARE is now a death trap
    (`startSneakAttack`); same-side GREEN arrows stagger via `GreenSpear.spawnDist`.
26. **Muffet** (`obj_spiderb`) — **new `SoulMode.PURPLE` + `WebBoard`** (soul locked to 3
    web strands, ↑/↓ hop). `bullet/muffet/*` + `SpiderBulletGen` (all 16 patterns verbatim).
    `MuffetBoss` (16 story turns, ACT Check/Pay/Struggle, telegram spare, murder route),
    `MuffetBody` (full-canvas teapot). Registered MUFFET=7. `MuffetBossTest`.
27–28. **Muffet pet-special** (turns 4/9/15) — 3-beat port matched vs clips 5/10/16:
    slide → cupcake lunge/chomp (border 23) → climb (box grows tall + rocks, web rises
    border 22, vertspiders rappel). Dialogue auto-clears; box snaps back on turn-end frame.
29. **UX polish** — decorative title-screen **Monster Kid** (`MonsterKid`: walk cycle +
    `spr_mkid_trip` stumble gag, no gameplay effect) wired into `BossSelectMenu`; Undyne
    green-spear/block tweaks (`GreenSpear` spawn-dist refactor, `BlockBullet`/`BlockSpear2`).
30. **Toriel** (`obj_torielboss`) — the first boss in the menu. `bullet/toriel/*` (FireHelix +
    FireHelixGen, MiniHelix, ChaseFire, HandBullet; Faltering/`blt_avoidfire` and low-HP
    softening cut — see `TORIEL.md §0`). `TorielBoss` rolls `mycommand` 0–100 → fire-helix
    columns (tall border 7) / one-or-two sweeping homing hands (wide border 6); two endings —
    refuse-to-fight `conversation` route (spared) and the HP≤150 DEF-collapse kill trap, each
    with its own monologue; GENOCIDE one-hit cold death. `TorielBody` + `TorielBossTest`
    (9 cases). Registered TORIEL=8. Code review + README/slide-deck refresh (44 tests green).

---

## Known simplifications

- **Toriel:** full port of the fire/hands gauntlet (`TORIEL.md`). Five attacks roll off
  `mycommand` — fire helix / mini helix (tall border 7) · a sparser helix · one hand ·
  two converging hands (short border 6); the **Faltering** attack (`blt_avoidfire`) and all
  her low-HP damage-softening / early-turn-end logic are **cut** (bullets deal full damage,
  the player can die). The sine fire keeps the GML weave (fire helix on the shared `obj_time`
  clock with the per-flame sign braid; mini-helix on its own `h` counter), with two tweaks: the
  drop point **drifts slowly left↔right** so the whole weaving wall travels across the box (no
  fixed safe spot — harder), and the bullettype-10 `blt_floatfire` side columns are **cut**
  (they sat as static, safe-to-ignore fire hugging the walls), so type 10 is just the helix.
  **Spare route:** refuse to fight (any non-FIGHT action leaves her HP
  intact) — a `conversation` counter advances a guilt-trip line, and at 13 she stops
  attacking and runs the relent monologue → win by mercy. The relent begins inside
  `chooseAttack`, where `TurnManager` would re-assert `ENEMY_TURN`, so `updateEnding` re-pins
  `mnfight=-1` each frame. **Kill route:** HP≤150 drops her DEF to −140 (the next hit kills);
  her death monologue plays. GENOCIDE (`murderlv≥1`) → DEF −9999, one-hit cold death line.
  One sprite (`spr_torielboss`) drawn behind the box reads as "hands on box" (border 7) or
  full-body (border 6); body/face-split kneel sprites used for the death scene. `path_hand1/2`
  were missing from the GML export — the sweep is a reconstructed parabolic arc. Caveat: at
  NORMAL ATK 0 a FIGHT chips ~1 HP, so killing her by grinding 440 HP is impractical (as in
  vanilla LV1) — spare her, or use GENOCIDE for the one-shot.
- **Sans:** sentry shadows → sliding-wall corridors; random blaster phase omitted (fixed
  a_type 12/13); genocide `death_c` kill cutscene not ported (asleep FIGHT = spare ending).
- **Papyrus:** no FIGHT timing bar (can't be FIGHT-killed); `fighto 16` draws plain bones.
- **Asgore:** story omitted; trident is a static angled sprite (not live IK); FIGHT uses
  ×4/min-100 compensation (no weapon-ATK system) so 3500 HP is grindable.
- **Undyne:** dialogue-then-attack collapsed (taunt shows during attack); body jump/slash
  animations not ported (stands and sways); GENOCIDE uses a simplified phase schedule +
  fixed 15000 HP; green shield is a 4-side block check (GML line geometry simplified).
- **Mettaton:** all `.mov` attacks ported; remaining cosmetic gaps — exact per-pose leg
  offsets, the `spr_mettstick` segmented-arm chain (drawn as an outlined bar), ex2 column
  layout. Gun hits chip flat 8 HP / add no ratings; FIGHT ×6/min-40 grindable. NEO always
  shows the long monologue.
- **Muffet:** intro collapsed to a 2-line web trap; story line shows during attack. Pet
  special is a 3-beat port; the full screen-capture "pulled-paper" distortion is a
  visual-only tilt + ripple (live heart/bullets stay axis-aligned). Telegram cutscene is
  dialogue (no `obj_telegramspider`/`obj_sadspider` sprites). Bribe always succeeds.
- **Audio:** ✅ not mute. Still-silent polish — Asgore fire loop, Mettaton attack/gun SFX,
  bone spawns, shield block, spare/defeat jingles, typewriter blips.

---

## Workflow notes

- **Sprite origins:** PNGs under `resources/images/` are usually **trimmed to their content
  bbox** (mett/asgore), so Java origins must subtract `bbox_left`/`bbox_top` from the GML
  `xorig`/`yorigin`. Exception: Muffet sprites are full-canvas. See memory
  `sprite-trimmed-origins`.
- **Skills:** `render-boss-frames` (headless PNG render + GML sprite/code inspection),
  `compare-reference` (extract `.mov` frames + crop/zoom for pixel comparison),
  `check-code` (build + tests + smell scan, exact JUnit count).
