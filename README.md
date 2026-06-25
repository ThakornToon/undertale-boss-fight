# Undertale Boss Fight

A standalone **boss-rush** reimplementation of *Undertale*'s combat, translating the
original **Game Maker Language (GML)** boss fights into **Java 2D (Swing/`Graphics2D`)**.

There is no story, no overworld, no rooms, and no save/load — you launch the game, pick
a boss from a menu, seed your own stats, and fight. The goal is a faithful port of the
*battle engine* and each boss's attack patterns, not the surrounding adventure.

> This is a non-commercial fan project for learning/preservation purposes. *Undertale* and
> all of its assets (sprites, audio, music, fonts) and the original GML source are  © Toby Fox

---

## Table of contents

- [Status & boss roster](#status--boss-roster)
- [Background — why this exists](#background--why-this-exists)
- [How this was built (with Claude Code)](#how-this-was-built-with-claude-code)
- [Quick start](#quick-start)
- [Controls](#controls)
- [How it works (architecture)](#how-it-works-architecture)
  - [The Core ↔ Boss split](#the-core--boss-split)
  - [The turn state machine](#the-turn-state-machine-globalmnfight)
  - [GML → Java translation rules](#gml--java-translation-rules)
- [Project layout](#project-layout)
- [Testing & verification](#testing--verification)
- [Assets](#assets)
- [Lessons learned & limitations](#lessons-learned--limitations)
- [Non-goals / constraints](#non-goals--constraints)
- [License](#license)

---

## Status & boss roster

The Core battle engine, host loop, boss-select menu, and sound system are complete.
The **initial-release milestone** shipped five bosses — Papyrus, Sans, Asgore, Undyne,
and Mettaton — then **Muffet** was added (bringing the new **PURPLE web soul**), then
**Toriel**, who now sits at the top of the menu as the first boss, and finally the
two-part **Asriel Dreemurr** finale. Across their NORMAL/GENOCIDE variants that's **ten
fightable encounters**. They were built roughly in order of difficulty (Papyrus < Asgore 
< Muffet < Mettaton < Undyne < Sans <= Asriel), with Asriel last as the most elaborate set-piece.

| Boss                   | Soul mode | Route | Status                                           |
|------------------------|---|---|--------------------------------------------------|
| **Toriel**             | red | any | ✅ Playable — fire-helix & sweeping hands, refuse-to-fight spare
| **Papyrus**            | blue (gravity/jump) | NORMAL | ✅ Playable                                       |
| **Sans**               | red + blue | any | ✅ Playable                                       |
| **Undyne**             | green ↔ red | NORMAL (`obj_undyneboss`) | ✅ Playable                                       |
| **Undyne the Undying** | green ↔ red | GENOCIDE (`obj_undyne_ex`) | ✅ Playable                                       |
| **Asgore**             | red | any | ✅ Playable                                       |
| **Mettaton EX**        | yellow (shoot) | NORMAL | ✅ Playable — ratings meter, 20-turn show, full attack bank, both endings |
| **Mettaton NEO**       | — | GENOCIDE | ✅ Playable — one-strike cutscene                 |
| **Muffet**             | **purple (web)** | any | ✅ Playable — 3-strand web, 16-pattern bullet engine, pet special, telegram spare |
| **Asriel Dreemurr**    | red + all modes | any | ✅ Playable — two parts: the GOD of Hyperdeath gauntlet → the Angel-of-Death SAVE finale (4 Lost-Soul mini-fights) |
| **Omega Flowey**       | — | — | ⬜ No way!                                        |
| **Other**              | — | — | ⬜ If I have time                                 |


Mettaton is a single menu entry that branches by route: **NORMAL → EX**,
**GENOCIDE → NEO**. The same applies to Undyne (NORMAL vs. the Undying).


---

## Background — why this exists

This is a personal, just-for-fun project. It grew out of vibe-coding little games with
Claude (Sonnet, on the free tier) until one day I really wanted to fight the *Undertale*
bosses again — but didn't feel like replaying the whole story just to reach them. So I
decided to build the boss fights as something you can jump straight into.

I chose **Java** because it's the language I'm most comfortable building games in. But
reverse-engineering the original GML from
[fachinformatiker/undertale](https://github.com/fachinformatiker/undertale) by hand was a
heavy lift for me, so I subscribed to **Claude Pro** and built the port with Claude Code.

The first five boss took about **one week** of work and burned through roughly **two
weeks' worth of quota**. It covers five bosses — Asgore, Mettaton, Papyrus, Sans, and
Undyne — built in rough order of how hard each one was:
**Papyrus < Asgore < Mettaton < Undyne < Sans**.

**Muffet** was added afterward as the sixth boss. She was the first to need a brand-new
soul mode — the **PURPLE web soul**, where the heart is locked to horizontal strands and
hops between them — so she exercised the "add a boss without touching Core" design in a
new way (a new `SoulMode`, a `WebBoard`, and the spider/donut/croissant bullet engine).

**Toriel** came next, and now opens the roster as the first boss you meet. She reuses the
plain **RED soul** but brings her own `bullet/toriel/` family — the weaving fire-helix
columns and the sweeping homing hands — plus a second, non-combat way to win: refuse to
fight long enough and she relents. This port deliberately drops her "holding back" logic,
so unlike vanilla her bullets deal full damage and the player can actually die.

**Asriel Dreemurr** closed out the roster as by far the biggest set-piece — a single
boss that is really two chained fights. **Part A**, the GOD of Hyperdeath, is a scripted,
un-winnable-by-damage gauntlet that walks a fixed table of named attacks (STAR/GALACTA
BLAZING, SHOCKER BREAKER, CHAOS SABER, CHAOS BUSTER, HYPER GONER) while you survive and
soften the run with ACT Hope/Dream. **Part B** is the Angel-of-Death SAVE finale: you
can't win by fighting, only by SAVE-ing — a death-loop unlocks the rainbow SAVE button,
which opens four **Lost-Soul mini-fights** (each exercising a *different* soul mode and
reusing an earlier boss's bullet engine: Undyne's GREEN shield, Alphys's YELLOW parasols,
the skelebros' BLUE bones, the Dreemurrs' RED spiral fire), then SAVE-ing Asriel himself.
It pulled together nearly every system the earlier bosses had introduced.

---

## How this was built (with Claude Code)

**Models & budget.** The main model was **Opus 4.8** at *high* and *extra* effort.
**Sonnet 4.6** also works well if you can describe what you want in enough detail. A
single boss tended to use around **~800K tokens** of context and roughly **one to two
days of quota**.

**Overall flow:**

1. Read the GML source and write a summary doc of the important files.
2. Design the overall system.
3. Build the boss-select menu.
4. Implement bosses one at a time.
5. After finishing each part, **review and clean up the code before moving on**.
6. Add the sound system.

**Two prompts do most of the work for each boss; everything else is small fixes.**

- **Prompt 1 — the loose pass.** Give the rough shape first: the dialogue order, what
  actions the player can take, the broad turn flow.
- **Prompt 2 — the detailed attack patterns.** This is where the real work is. I
  recommend recording **short videos of each attack** and dropping them into the project,
  then having Claude read through them frame by frame. You have to explain what's
  happening in the attack — what shapes the objects are, how they move, and how they
  behave.

These two prompts take the most time and matter the most. After that it's iteration: go
through, find what doesn't match expectations, and refine. My references were the
[Undertale wiki](https://undertale.fandom.com/wiki/Main_Page) and boss-fight videos on
YouTube.

---

## Quick start

Requires a JDK (developed on **JDK 24**; Java 17+ should compile). No external runtime
dependencies — it's pure Java 2D / Swing, with JUnit 5 for tests only.

```bash
# Build everything
./gradlew build

# Run the test suite (JUnit 5)
./gradlew test

# Run a single test class
./gradlew test --tests "boss.UndyneBossTest"

# Compile only (skip tests)
./gradlew compileJava
```

The entry point is [`Main`](src/main/java/Main.java): it boots the [`Game`](src/main/java/core/Game.java)
host window and opens the [`BossSelectMenu`](src/main/java/battle/BossSelectMenu.java).
There is no Gradle `run` task configured (the build uses only the `java` plugin), so
launch `Main` from your IDE, or after a build run it on the classpath:

```bash
./gradlew compileJava processResources
java -cp build/classes/java/main:build/resources/main Main
```

---

## Controls

**Boss-select menu**

| Key | Action |
|---|---|
| ↑ / ↓ | Move between rows (BOSS / MAX HP / MODE) |
| ← / → | Change the selected row's value |
| Z | Start the fight |

`MODE` chooses NORMAL or GENOCIDE; GENOCIDE seeds a high `murderlv` (≥7) so bosses fall
in one hit and switch to their genocide dialogue. `MAX HP` picks an LV preset. A purely
cosmetic **Monster Kid** paces across the title screen and periodically trips on his face
([`MonsterKid`](src/main/java/battle/MonsterKid.java)) — no gameplay effect.

**Battle**

| Key | Action |
|---|---|
| Arrow keys | Move the SOUL (during the enemy turn) |
| Z | Confirm / FIGHT timing hit |
| X | Cancel |
| ← / → + Z | Navigate FIGHT / ACT / ITEM / MERCY |
| ↑ / ↓ | Navigate submenus (ACT options, items) |
| Hold ↑ | Jump the **blue** soul during a bone wave |

---

## How it works (architecture)

The whole project is organized around one idea: **GML runs at a fixed 30 steps/second
with timers expressed in frames, so the Java loop ticks logic at exactly 30 FPS** and
every ported `turntimer` / `alarm[n]` value carries over verbatim. Rendering is
decoupled and just draws the latest state ([`Game`](src/main/java/core/Game.java)).

### The Core ↔ Boss split

Every boss is two classes — a **controller** and a **body** — so that Core never
references concrete bosses and adding a boss never touches Core:

- **Controller** ([`Boss`](src/main/java/boss/Boss.java) subclass, GML `obj_*boss`):
  the turn-logic half. Owns the [`Monster`](src/main/java/boss/Monster.java) stat block
  (HP/AT/DF/EXP from `scr_monstersetup`), the dialogue, the universal `turns` counter,
  and the per-turn attack choice. Core injects the battle systems it needs (soul, board,
  damage, mercy, ACT) before calling `setup()`.
- **Body** ([`BossBody`](src/main/java/boss/BossBody.java) subclass, GML `obj_*_body`):
  draws the multi-part sprite and, for bosses whose patterns live in the body, runs the
  active pattern. The controller pushes it an integer attack selector
  (`a_type` / `fighto` / `attacktype`).

[`BattleScene`](src/main/java/battle/BattleScene.java) wires the two halves to the Core
systems and to [`TurnManager`](src/main/java/core/TurnManager.java) once, via hooks, so
nothing in `core/` ever imports a boss class.

```
                       ┌──────────────────────────────┐
                       │           Game (30 FPS)      │
                       │   fixed-timestep host loop   │
                       └───────────────┬──────────────┘
                                       │ update()/render()
                       ┌───────────────▼────────────────┐
   BossSelectMenu ───► │          BattleScene           │ ◄── InputHandler
                       │  wires Core systems + hooks    │
                       └───┬───────────┬───────────┬────┘
                           │           │           │
            ┌──────────────▼───┐  ┌────▼─────┐  ┌──▼──────────────┐
            │   TurnManager    │  │   Boss   │  │  Battle systems │
            │ (global.mnfight  │  │  (ctrl)  │  │  Soul, Board,   │
            │  state machine)  │  └────┬─────┘  │  Damage, Mercy, │
            └──────────────────┘       │        │  Menu, Ratings  │
                                 ┌─────▼─────┐  └─────────────────┘
                                 │ BossBody  │ ──► spawns Bullets / Generators
                                 └───────────┘     (bullet/…)
```

**All shared state lives on one singleton.** Every `global.*` variable from GML is a
field on [`GlobalState`](src/main/java/core/GlobalState.java) (`GlobalState.get()`).
No other class caches a copy — the soul, the combat box, the damage system, and every
bullet read/write the same `global.hp`, `global.idealborder[0..3]`, `global.mnfight`,
etc. directly, exactly as the GML objects shared `global.*`.

### The turn state machine (`global.mnfight`)

[`TurnManager`](src/main/java/core/TurnManager.java) is the single interpreter of the
`global.mnfight` phase constant and owns the boilerplate every boss repeats (the
spawn-once guard reset, the `turntimer` countdown, and "timer hits 0 → back to menu").
Phases:

| `mnfight` | Meaning |
|---|---|
| `SETUP` (99) | intro / cutscene |
| `MENU` (2) | player turn — FIGHT/ACT/ITEM/MERCY |
| `ENTER_ENEMY` (3) | hand-off into the enemy turn (attack is chosen here) |
| `ENEMY_TURN` (1) | bullets flying; the soul dodges |
| negative | death / transform cutscene |

The combat box itself is [`BulletBoard`](src/main/java/battle/BulletBoard.java): it lerps
an animated rectangle toward a target and writes the bounds into
`GlobalState.idealborder` every frame, so bosses can shrink/slide/snap the box live and
the [`Soul`](src/main/java/battle/Soul.java) + bullets immediately respect it.
[`BorderSetup`](src/main/java/battle/BorderSetup.java) ports `SCR_BORDERSETUP`'s presets.

### GML → Java translation rules

| GML | Java |
|---|---|
| Create event | constructor |
| Step event | `update()` |
| Draw event | `render(Graphics2D g)` |
| `alarm[n]` | [`AlarmTimer`](src/main/java/core/AlarmTimer.java) (`alarm[n] == -1` = off) |
| `event_user(n)` | [`EventUser`](src/main/java/core/EventUser.java) hook |
| `global.*` | a field on [`GlobalState`](src/main/java/core/GlobalState.java) |
| `instance_create(...)` | `EntityManager.add(new …)` |
| `instance_destroy()` | remove from the entity list |
| `with(obj) { … }` | loop over that class's instances |
| `lengthdir_x/y`, `point_direction`, `random_range`, `choose`, … | [`GMLHelper`](src/main/java/util/GMLHelper.java) |

`GMLHelper` preserves GML's angle convention: **degrees**, screen-space, `0°` = right and
`90°` = **up** (trig on `y` is negated, since `y` grows downward). Ported attack formulas
copy over verbatim.

---

## Project layout

```
UndertaleBossFight/
├── Main.java …………………………… in src/; boots Game + BossSelectMenu
├── src/main/java/
│   ├── core/        Host loop & GML primitives
│   │   ├── Game, Scene, EntityManager, Entity   ← loop, scenes, object lists
│   │   ├── GlobalState                          ← every global.* variable
│   │   ├── TurnManager                          ← the mnfight state machine
│   │   ├── AlarmTimer, EventUser, Sequence      ← GML alarm[] / event_user / scripted steps
│   │   └── InputHandler
│   ├── battle/      The combat box & menus
│   │   ├── BattleScene        ← assembles a fight, wires Core ↔ Boss
│   │   ├── BossSelectMenu      ← title menu (BOSS / MAX HP / MODE)
│   │   ├── MonsterKid          ← decorative title-screen walk-and-trip gag
│   │   ├── Soul, SoulMode      ← obj_heart (red/blue/green/yellow/purple)
│   │   ├── BulletBoard, WebBoard  ← idealborder combat box + Muffet's purple web
│   │   ├── BattleMenu, ActDispatcher
│   │   ├── DamageSystem, MercySystem, KarmaTicker   ← scr_damagestandard / mercy / KR
│   │   ├── RatingsMaster       ← Mettaton EX's ratings meter
│   │   └── BorderSetup         ← SCR_BORDERSETUP presets
│   ├── boss/        Controllers + bodies (one pair per boss)
│   │   ├── Boss, BossBody, Monster, BossRegistry
│   │   ├── PapyrusBoss/Body, SansBoss/Body, AsgoreBoss/Body
│   │   ├── UndyneBoss/Body, UndyneXBody, Mettaton{Ex,Neo}Boss/Body
│   │   ├── MuffetBoss/Body, TorielBoss/Body
│   │   ├── Asriel{Boss,Body,Background,FinalBody}, asriel/  ← two-part fight + Lost-Soul framework
│   │   └── AnnoyingDog, ScreenFlash
│   ├── bullet/      Attack patterns & projectiles
│   │   ├── Bullet, PlayerBullet, AttackPattern, Generator, Shootable
│   │   └── asgore/ · bones/ · gaster/ · mettaton/ · muffet/ · toriel/ · undyne/ · asriel/  ← per-boss bullets
│   └── util/        Assets (sprite/sound cache), Audio, GMLHelper
├── src/main/resources/
│   ├── images/      ~7,500 sprite frames (white line-art on transparent)
│   ├── audio/       ~440 sound effects / music (.wav + .ogg; OGG decoded via libs/)
│   └── fonts/       Undertale bitmap fonts (fnt_*)
└── src/test/java/   JUnit 5 tests (per-boss + soul-jump + Sans red phase)

```

---

## Testing & verification

```bash
./gradlew test                               # full suite — 49 tests, JUnit 5
./gradlew test --tests "boss.AsgoreBossTest" # one class
```

---

## Assets

Sprites are stored as **white line-art on a transparent background** (so they look
"blank" on white — composite on black to view them). Sprite **origins and hitboxes**
come from `undertale/sprites/spr_*.sprite.gmx` (`<xorig>`, `<yorig>`, `bbox_*`).

> ⚠️ **Gotcha:** *some* exported PNGs are *trimmed to their content bbox*, while the GML
> origins refer to the original untrimmed canvas. When a sprite is trimmed, use
> `(xorig − bbox_left, yorig − bbox_top)`. **But it isn't universal** — many sprite sets
> (e.g. all of Muffet's `spr_spiderb_*` / bullet sprites) are full-canvas, so their
> origins are used as-is. Always compare the actual PNG dimensions to the gmx canvas
> first: if they match, no subtraction; if `bbox_bottom`/`bbox_right` exceeds the canvas
> size, the PNG is trimmed and the raw origin is wrong. (The trimmed case was the bug
> behind Mettaton EX's torso drawing on top of its head; the full-canvas case is why
> Muffet's body lined up first try.)

[`Assets`](src/main/java/util/Assets.java) caches sprites/sounds by name;
[`Audio`](src/main/java/util/Audio.java) plays SFX/music.

---

## Lessons learned & limitations

A few honest notes from porting this with an AI assistant:

- **Wrong / missing sprites.** The assistant often grabbed the wrong image file or
  couldn't find the right one — sometimes it had to draw the sprite itself, or I had to
  track one down and hand it over.
- **Edit thrash in long sessions.** When too many changes pile up in a single session,
  it can get confused and flip the same fix back and forth.
- **Frame extraction & image comparison are still shaky.** Pulling frames out of the
  reference videos and lining them up for pixel comparison often came out misaligned
  (could be partly on me).
- **Detail isn't enough on its own.** Beyond the reference videos you still have to hand
  over a lot of context and explanation.
- **Audio.** The project decodes both `.wav` and `.ogg` (an OGG decoder is vendored
  under `libs/`), but I mostly leaned on `.wav` while wiring sound, so right now only
  some moments have audio and for a lot of them I'm still not sure which file is right.
- **Some patterns are trimmed.** Where I lacked detailed reference, a few attack patterns
  were simplified or cut down.

---

## Non-goals / constraints

- **No story content** — no overworld movement, room traversal, dialogue scenes outside
  the fight, or save/load.
- **All `global.*` state lives on `GlobalState`** — never scattered across classes.
- Sprites/sounds live in `src/main/resources/`; the GML under `undertale/` is read-only.
- Filenames may abbreviate boss names: `pap` = Papyrus, `und` = Undyne, `mett` =
  Mettaton, `asg` = Asgore.

---

## License

The original source code I wrote — the Java under [`src/main/java/`](src/main/java/) and
[`src/test/java/`](src/test/java/), the build files, the `.claude/` skills, and this
documentation — is released under the **MIT License** (see [`LICENSE`](LICENSE)).

Everything from the game itself — sprites, audio, music, fonts, and the original GML
source — is **© Toby Fox**, is **not** covered by the MIT License, and is **not**
included in this repository. It is git-ignored and must be supplied locally from a
legally obtained copy of *Undertale*. This project is non-commercial and is not
affiliated with or endorsed by Toby Fox.

---
