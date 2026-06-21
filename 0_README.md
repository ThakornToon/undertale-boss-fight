# 0_README — Toriel boss: source-file map

Index of the **GML source files** that define the Toriel **Battle** (we skip Pre-Battle
overworld/cutscene content for now). Read these from `undertale/` before porting.
Full pattern details + the port's design decisions live in [TORIEL.md](TORIEL.md).

References:
- Wiki: https://undertale.fandom.com/wiki/Toriel/In_Battle
- Gameplay clips: `toriel_example/1.mov … 6.mov` (extract via the `compare-reference` skill).
  1/2/5/6 = sine-wave fire (tall box); 3/4 = two-hands + homing fire (wide-short box). See
  TORIEL.md §0b.

> **Port scope note:** the **Faltering** attack (`blt_avoidfire` / bullettype 9) and all of
> Toriel's "holding back" / death-prevention logic are **CUT** in this port — see TORIEL.md §0.

> Object IDs in `instance_create(...)` are internal GML numbers; the `/* name */`
> comments next to them are what matters. Numbers ≠ our Java IDs.

## Controller / body (the boss itself)

| File | Role |
|---|---|
| `objects/obj_torielboss.object.gmx` | **Boss controller** — `obj_monsterparent` child. Create runs `scr_monstersetup`; the big logic is **Alarm[2]** (turn machine, dialogue, attack picker) and **Step[0]** (hurt anim, mercy/spare, conversation counter, death/spare ending). `mercymod = -20000`. |
| `objects/obj_torielbody.object.gmx` | **Body** — `obj_friendparent` child. Draw event only: draws `spr_toriel_bodyonly` at xscale/yscale 2 + a **face sprite** chosen by `global.faceemotion` at `(x+40, y-52)`. Used in the kneel/death cutscene; the battle-stand pose is drawn by the controller's own `sprite_index`. |
| `objects/obj_torface.object.gmx` | Face object, `event_inherited()` only — not needed for battle. |

## Bullet generators (spawned by the controller each enemy turn)

| File | Role |
|---|---|
| `objects/obj_1sidegen.object.gmx` | **Main bullet spawner.** Create sets `firingspeed = global.firingrate`; **Alarm[0]** picks a `bullettype` and spawns the matching bullet, then re-arms `alarm[0] = firingspeed` (fires on a fixed cadence). Toriel uses **bullettype 7, 8, 9, 10**. |
| `objects/obj_torgen.object.gmx` | Cosmetic "Toriel turns to the side" sprite swap during the gentle/avoid attack. Self-destructs when the turn ends (`turntimer < 1`). |
| `objects/blt_handbullet1.object.gmx` | **Left hand** — follows `path_hand1`, every 4 frames drops `blt_chasefire1`. Sweeps in, then goes inactive at path end. |
| `objects/blt_handbullet2.object.gmx` | **Right hand** — follows `path_hand2`, drops `blt_chasefire2`. Same lifecycle as hand1. |

## Bullet objects (the actual projectiles)

| File | Role |
|---|---|
| `objects/blt_firehelix1.object.gmx` | Tall **sine-wave fire column** falling from the top; `hspeed = ±sin(time/10)*4`, gravity 0.12. |
| `objects/blt_minihelix.object.gmx` | Smaller/faster sine fire; `hspeed = ±sin(h/5)*8`, gravity 0.06, vspeed pulses with `sin`. |
| `objects/blt_floatfire.object.gmx` | Fire that drifts vertically at a random speed (`vspeed = random(8)-4`), dmg 4. Spawned at box edges alongside firehelix in bullettype 10. |
| `objects/blt_avoidfire.object.gmx` | **[CUT in this port]** The "fire that dodges you" — curves *away* from the heart, never hits. The Faltering attack (bullettype 9). Kept here for reference only; not ported. |
| `objects/blt_chasefire1.object.gmx` | Fire dropped by hand1: sits, then **homes** toward the heart (alarm[1] `move_towards_point`, alarm[2] nudges hspeed/vspeed), dmg ramps 4→5. |
| `objects/blt_chasefire2.object.gmx` | Fire dropped by hand2: waits until `blt_handbullet1.path_position == 1`, then homes once toward the heart, dmg 5. |
| `objects/obj_torheart.object.gmx` | **Spare/death cutscene** heart (Toriel kneels, heart cracks & shatters → `obj_theartshard`). Only the spare ending — not an attack. |

## Shared scripts / data

| File | Role |
|---|---|
| `scripts/scr_monstersetup.gml` | `global.monstertype == 10` → Toriel: **HP 440, ATK 6 (8 if `flag[6]==1`), DEF 1**, no XP/gold. (`murderlv ≥ 1` → DEF −9999, dies in one hit.) |
| `scripts/SCR_BORDER.gml` | Helper used by `obj_1sidegen`: `SCR_BORDER(side, margin)` → random spawn point `(xx, yy)` on a box edge (0=top, 1=bottom, 2=left, 3=right). |
| `scripts/SCR_BORDERSETUP.gml` | `global.border` presets. Toriel uses **border 6** `(227,407,250,385)` normally and **border 7** `(227,407,200,385)` (taller) for the vertical-fire attacks. |
| `scripts/scr_mercystandard.gml` | `mercy = monsterhp - at - wstrength + monsterdef - mercymod`. With `mercymod=-20000`, the SPARE button never lights normally — Toriel is spared only via the **conversation/refuse-to-fight** route (see TORIEL.md). |

## Not part of the Battle (skip)

`obj_torgen_house1`, `obj_toribuster`, `obj_torielflame_X`, `obj_torielcall*`,
`obj_torieltrigger*`, `obj_toroverworld*`, `obj_torinteractable*`, `scr_torcall`,
`scr_torface`, `obj_torboss_2ndtime` (the post-spare re-encounter) — all overworld /
cutscene. `path_hand1` / `path_hand2` are **not present** in this GML export; the hand
sweep must be reconstructed from the bullet step events (see TORIEL.md).
