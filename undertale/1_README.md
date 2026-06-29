# Translation Progress Tracker

> **Status — ALL TARGET BOSSES COMPLETE.** Core engine, host loop, boss-select menu and
> sound system are done. Playable: **Toriel · Papyrus · Sans · Asgore · Undyne (NORMAL +
> GENOCIDE) · Mettaton (EX + NEO) · Muffet · Asriel** — 8 bosses / 10 fight variants.
> `./gradlew build` → green, **50 tests** pass.
>
> Title menu seeds run state (no story): **BOSS**, **MAX HP** (LV presets), **MODE**
> (NORMAL / GENOCIDE — seeds `murderlv≥7`/`lv`/`at` so bosses fall in one hit and use
> genocide dialogue). Difficulty: Papyrus < Asgore < Mettaton < Undyne < Sans.
>
> Optional polish only — see **Known simplifications**.

---

## How the port works

**Controller/body split (every boss):**
- **Controller** (`obj_*b` → `Boss extends Monster`): runs `scr_monstersetup` (ctor) /
  `scr_monsterdefeat` (death), owns the turn state machine, dialogue, hurt-shudder.
- **Body** (`obj_*b_body` → `BossBody`): draws the multi-part sprite and **runs the bullet
  patterns**, dispatched by an integer selector the controller sets.

**Turn protocol** (`global.mnfight`): `99` setup/cutscene · `2` player menu · `3`
transition→enemy turn · `1` enemy turn running · `4` ACT/SPARE · `5` special/transform ·
negatives = frozen during a cutscene. `global.myfight` mirrors the menu action (0 fight /
2 act / 4 mercy); `global.turntimer` counts down the enemy turn.

**Frame loop (fixed 30 FPS** — every GML alarm/turntimer ports as-is):
`input → beginStep → boss → body → patterns → soul → turns → damage → menu → endStep`,
then render depth-sorted: board → bullets → bodies → soul → UI.

**Soul modes** (`obj_heart`): RED free-move · BLUE gravity+jump · GREEN directional 4-way
block · YELLOW shoot · PURPLE web-lock (Muffet: bound to horizontal strands, ↑/↓ hops).
BLUE jump is a 1:1 port of the GML four-band piecewise gravity (launch −6; apex-hang;
terminal 8; release-to-cut → full peak ≈64px, 3-frame tap ≈23px).

---

## Architecture

> Class names map 1:1 to files under `src/main/java/`. **Design rule that holds:** adding a
> boss requires *zero* edits to `core/` — only new `boss/` + `bullet/` subclasses and a
> registry entry.

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
│            Mettaton{Ex,Neo}{Boss,Body}, Asriel{Boss,Body,Background,FinalBody},
│            asriel/ (LostSoul framework + 4 friends)
├── bullet/  AttackPattern, Bullet, Generator, PlayerBullet, Shootable, + sub-packages:
│            bones/ · gaster/ · asgore/ · undyne/ · mettaton/ · muffet/ · toriel/ · asriel/
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
`onDefeat`. Asriel adds `onLethalDamage` ("But it refused.") and `wantsSaveButton`.
`BossBody`: field `attackSel`, `setAttack(int)`, `update()` runs the active pattern.

### Per-boss wiring (selector → mechanic)

| Boss | Soul | Turn driver → selector | Win condition | Special hook |
|---|---|---|---|---|
| **Toriel** | RED | `mycommand` 0–100 → 5 attacks (fire helix / hands) | kill (HP≤150 → DEF −140) **or** refuse-to-fight spare | `conversation` counter → relent; border 6/7 |
| **Papyrus** | BLUE | `fighto` −1→16 (HP gates) | spare only (`mercymod=8000`) | blue-bone "only hurts if moving" |
| **Sans** | RED+BLUE | `hit_try`/`part`→`a_type`; `lac` special | turn-gated; spare = FIGHT while asleep | KARMA, custom i-frames, blue slams, sleep |
| **Asgore** | RED | `turns` 1→23 loop→20 → generator table | `fivedamage` (HP≤500 → kneel) | broken mercy (hide SPARE); border 29/30 |
| **Mettaton NEO** | none | `mercymod=−999999` ends turn instantly | forced one-hit kill | `forcedKill`, death monologue + explode |
| **Undyne NORMAL** | GREEN↔RED | `order` schedule: green shield / red windows | reform→scripted death (1500 HP grind) | green spear-block engine, two-phase reform |
| **Undyne GENOCIDE** | GREEN↔RED | `orderb` 0→7 red cycles (+rot ring, spiral) | HP 15000 (player ×21, min 600) | spikier `undynex` body; "world will live on" |
| **Mettaton EX** | YELLOW | `turns`→`attacktype`=29+turns | **RATINGS** ≥10000/12000 *or* HP→0 | ratings subsystem, `dancewait` tempo, arms-off |
| **Muffet** | PURPLE | `turnAmt` 0→15 → `SpiderBulletGen.type` (16) | survive 16 turns → telegram spare | web soul (3 strands); pet special turns 4/9/15 |
| **Asriel A** | RED | `turns` 0→13 named-attack table | scripted (`turns==13` HYPER GONER) | ACT Hope/Dream; Star Blazing · Shocker Breaker · Chaos Saber · Chaos Buster · Hyper Goner → transform |
| **Asriel B** | RED/GREEN/YELLOW/BLUE | `flag[501]` stage + `flag[505–508]` friends | SAVE graph (all 4 → ending) | death-loop · SAVE unlock · 4 Lost Souls · SAVE Asriel · Angel-of-Death finale (HP→1, never 0) · victory card |

---

## Progress log (one line per session)

1. **Core engine** — GlobalState, Entity/EntityManager, AlarmTimer/EventUser/Sequence,
   Soul/SoulMode, BulletBoard/BorderSetup, TurnManager/InputHandler, BattleMenu/
   ActDispatcher, DamageSystem/KarmaTicker/MercySystem, GMLHelper/Assets.
2. **Host loop + Papyrus** — Game (fixed 30 FPS), boss SPI, exact `fighto` bones, four-band
   jump, spare + genocide routes, Annoying-Dog steal.
3. **Boss-select menu + genocide** — seeds run state; added `murderlv`.
4–7. **Sans** (`bullet/bones/*` + `bullet/gaster/*` + `util/Audio`: blue gauntlet → turning
   point → red phase → Special Attack → sleep/spare, KARMA), **BoneArt**, **blue-jump rewrite**
   (exact piecewise gravity, `SoulJumpTest`), **player death shatter**.
8–9. **Asgore** (23-turn fire/hand/trident table, broken mercy, `fivedamage` kneel, intro +
   genocide; `AsgoreBossTest`) + **MODE selector** (NORMAL/GENOCIDE).
10–12. **Undyne** — NORMAL two-phase (shared green/red spear engine `bullet/undyne/`, reform →
   scripted death) + GENOCIDE (`UndyneXBody`, rot-ring/spiral, ×21/min-600 vs 15000 HP);
   object-mapping correction (the two Undyne objects were swapped).
13. **Code review + README condense** — `enterBox()` helper; trimmed done entries.
14–18. **Mettaton NEO + EX** — NEO one-shot (`forcedKill` + monologue/explode); EX YELLOW
   shoot soul + `PlayerBullet`/`Shootable`, `RatingsMaster` meter, 20-turn taunt machine, full
   attack bank from `example_*.mov`, part-origin (trimmed-bbox) fix.
19–20. **Sound system** (vendored vorbisspi/jorbis/tritonus, `util/Audio` OGG→PCM + music
   bus + SFX + master volume, per-boss themes) + Asgore trident/hand origin alignment.
21–24. **Pause/mute/menu-music/font/skill-check/SFX** (ESC pause, M mute, OFL fonts
   `util/Fonts`, FIGHT-bar accuracy) + initial-release milestone (top-level README rewrite).
25. **Sans spare-trap + Undyne arrow-overlap** — turning-point SPARE is a death trap;
   same-side GREEN arrows stagger via `GreenSpear.spawnDist`.
26–28. **Muffet** — new `SoulMode.PURPLE` + `WebBoard` (soul on 3 strands), `bullet/muffet/*`
   + `SpiderBulletGen` (16 patterns), `MuffetBoss`/`MuffetBody`, pet-special (turns 4/9/15).
29. **UX polish** — title-screen **Monster Kid** decoration; Undyne green-spear/block tweaks.
30. **Toriel** (`obj_torielboss`) — first boss in the menu. `bullet/toriel/*` (fire helix /
   mini helix / hands), refuse-to-fight spare + HP≤150 DEF-collapse kill, GENOCIDE one-shot;
   `TorielBody` + `TorielBossTest`. Registered TORIEL=8.
31–43. **Asriel** (`obj_asrielb` + `obj_asrielfinal`) — the full two-part fight, built up and
   then fidelity-passed against 30+ reference clips. **Part A:** floating multi-part God body
   + cosmic backdrop, calm Fire-Magic intro, `turns` 0→13 gauntlet (STAR/GALACTA BLAZING ·
   SHOCKER BREAKER II · CHAOS SABER/SLICER · CHAOS BUSTER/BLASTER · HYPER GONER), per-turn
   taunts, ACT Hope/Dream → transform. **Part B:** winged Angel-of-Death form, stage-0
   death-loop → SAVE unlock (rainbow SAVE button, `Boss.onLethalDamage`/`wantsSaveButton`,
   `BattleMenu.saveButton`), the four **Lost-Soul mini-fights** (`boss/asriel/`: Undyne GREEN ·
   Alphys YELLOW · Sans&Papyrus BLUE · Toriel&Asgore RED — reusing existing bullet engines),
   SAVE-ing Asriel ("Someone else") → Angel-of-Death finale (homing comets multiply HP toward
   but never to 0) → short victory card. `bullet/asriel/*` for every named attack;
   `AsrielBossTest`. **Asriel complete.**
44. **Papyrus finale climb fix** — the super-bone climb (`PapyrusBoss.climbStep`) now unlocks
    the frame a `coolbus` bone crosses into the box (`x < idealborder[1]`), faithful to GML
    `blt_coolbus`, instead of guessing from the super bone's position (`+400`); tracks the
    coolbus instances + added `blt_superbone.appear` y-snap. `climbUnlocksWhenCoolbusEntersBox`.
45. **Dead-code / access-modifier cleanup** — removed unused fields (`Soul.shieldDir`,
    `BulletBoard.solid`, `ActDispatcher.checkText`, `BorderSetup.VIEW_W/H`,
    `AsgoreBody.swipeFrame/swipeCue`), uncalled public methods (`DamageSystem.playerHurt`,
    `MercySystem.addMercy`, `BulletBoard.shrinkTo`, `SansBone.rebase`,
    `LostSoul.alreadyFreedLine`), dead params (`AsgoreBody.restore` alpha, `BlockSpear2`
    rating) + their call sites, and an unused import. Build green, 50 tests.

---

## Known simplifications

- **Toriel:** the **Faltering** attack (`blt_avoidfire`) and all low-HP damage-softening are
  **cut** (bullets deal full damage). Fire helix keeps the GML weave but the drop point drifts
  L↔R (no fixed safe spot) and the static side columns (`blt_floatfire`) are cut. At NORMAL
  ATK 0 a FIGHT chips ~1 HP, so grinding 440 HP is impractical — spare, or use GENOCIDE.
- **Sans:** sentry shadows → sliding-wall corridors; random blaster phase omitted (fixed
  a_type 12/13); genocide `death_c` kill cutscene not ported (asleep FIGHT = spare ending).
- **Papyrus:** no FIGHT timing bar (can't be FIGHT-killed); `fighto 16` draws plain bones.
- **Asgore:** story omitted; trident is a static angled sprite (not live IK); FIGHT uses
  ×4/min-100 compensation so 3500 HP is grindable. Opening cutscene is **locked to the
  "Bergentrückung" prelude** (played once via `Audio.playMusicOnce`): the narration/farewell
  beats are paced across the track's real playback position and the cut to the fight (→
  `mus_vsasgore`) fires the instant it ends. Headless renders/tests fall back to a fixed
  690-frame budget; the neutral intro is no longer Z-skippable.
- **Undyne:** dialogue-then-attack collapsed (taunt shows during attack); body jump/slash
  animations not ported; GENOCIDE uses a simplified phase schedule + fixed 15000 HP; green
  shield is a 4-side block check (GML line geometry simplified).
- **Mettaton:** all `.mov` attacks ported; cosmetic gaps — exact per-pose leg offsets, the
  `spr_mettstick` segmented-arm chain (drawn as a bar), ex2 column layout. Gun hits chip flat
  8 HP / no ratings; FIGHT ×6/min-40 grindable. NEO always shows the long monologue.
- **Muffet:** intro collapsed to a 2-line web trap; pet special is a 3-beat port; the
  "pulled-paper" distortion is a visual-only tilt + ripple; telegram cutscene is dialogue
  (no spider sprites). Bribe always succeeds.
- **Asriel:** Chaos Buster draws one centre gun (clip shows two) + a glow-orb charge meter
  (not the segmented `spr_asriel_gunarm_meter`); Chaos Saber's body-lean flourish + hand-
  lightning arm sparks aren't drawn. Part B: Lost Souls are lightened silhouettes of overworld
  sprites (no bespoke ghost art) reusing existing bullet engines; the struggle counter
  increments on any FIGHT/Check (GML counts only Check); the breakdown monologue is shortened
  to 3 lines (matches the short victory-card ending); the scrolling starfield uses the shared
  cosmic backdrop. **No Asriel music wired** (Hopes and Dreams / Burn in Despair / SAVE the World).
- **Audio:** ✅ not mute. Still-silent polish — Asgore fire loop, Mettaton attack/gun SFX,
  bone spawns, shield block, spare/defeat jingles, typewriter blips.

---

## Workflow notes

- **Sprite origins:** PNGs under `resources/images/` are usually **trimmed to their content
  bbox** (mett/asgore), so Java origins must subtract `bbox_left`/`bbox_top` from the GML
  `xorig`/`yorigin`. Exceptions: Muffet and Asriel part sprites are full-canvas. See memory
  `sprite-trimmed-origins`.
- **Skills:** `render-boss-frames` (headless PNG render + GML sprite/code inspection),
  `compare-reference` (extract `.mov` frames + crop/zoom for pixel comparison),
  `check-code` (build + tests + smell scan, exact JUnit count).
</content>
</invoke>
