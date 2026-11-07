# Elemental Legacy — Game Design Outline

## Pillars

1. **Elemental identity:** one permanent path per player per world.
2. **Readable action controls:** a few consistent inputs, configurable in Controls, with a radial selector for the long tail of abilities.
3. **RPG mastery:** 50 levels, earned through meaningful use; levels unlock techniques and improve existing techniques.
4. **Multiplayer-first authority:** the server owns element choice, XP, energy, unlocks, cooldowns, and hit validation.
5. **Original expression:** original names, mechanics, visual language, sounds, models, and code.

## Paths

| Internal path | Display name | Combat identity | Design space |
|---|---|---|---|
| `tide` | Tide | Adaptive control | Flow, pressure, ice-like constructs, rescue and repositioning |
| `stone` | Stone | Space control and defense | Impact, terrain shaping, barriers, stagger |
| `ember` | Ember | Aggressive pressure | Heat bursts, focused projectiles, area denial, propulsion |
| `gale` | Gale | Mobility and disruption | Gusts, dashes, deflection, lift and displacement |

The internal names are deliberately distinct from franchise terminology. They describe broad elemental fantasy themes without importing protected lore or assets.

## Permanent selection

- A player with no path sees a first-run selection screen on entering a world.
- Each path card explains strengths, weaknesses, play style, and difficulty.
- The confirmation step explicitly warns that the path is permanent for that player in that world.
- Selection is committed by the server and synchronized to the client.
- Disconnecting, dying, changing dimensions, or reinstalling the client does not reset the path.
- Multiplayer players can choose different paths independently.

## Mastery and XP

- Maximum mastery level: 50.
- XP is awarded by successful, meaningful actions: effective hits, blocks, traversal, utility outcomes, and objective contributions.
- Repeated no-target casts, self-target loops, AFK movement, and low-impact spam must not create unlimited XP.
- Each level can unlock a new technique, an upgrade rank, a passive, or a mastery milestone.
- Ability upgrades should change behavior as well as numerical values (range, shape, control, duration, or secondary effect).
- XP thresholds use a configurable curve. Current prototype formula: `100 + 35 * (level - 1)` XP for the next level.

## Energy

- Maximum energy: 100.
- Natural regeneration prototype: 1.5 energy per second while below maximum.
- Costs are attached to abilities and should scale with power and utility.
- Energy is server-authoritative; clients only display synchronized values.
- Cooldowns remain separate from energy so powerful abilities cannot be spammed by waiting for a tiny energy refill.

## Input and UI target

Proposed defaults (all rebindable):

| Input | Default | Action |
|---|---|---|
| Element mode | `G` | Toggle bending stance |
| Skill wheel | `X` (hold) | Open radial selector; release to confirm |
| Primary | Mouse 1 | Cast equipped primary technique |
| Guard / alternate | Mouse 2 | Contextual guard or alternate technique |
| Mobility | `C` | Use equipped movement technique |
| Skill tree | `K` | Open mastery tree |
| Profile | `J` | Open elemental profile |

UX requirements:
- Do not require memorizing long button combinations for basic abilities.
- The radial selector pauses local gameplay only in single-player; on servers it must not pause the world.
- Quick slots allow a small set of favorite abilities to be used without opening the wheel.
- Every ability communicates cost, cooldown, target/range, and whether it is currently usable.
- Controls must be configurable and must avoid conflicts with common vanilla controls where possible.

## Initial combat slice

The first playable slice should contain two original techniques per path, not the full tree. Each technique needs:
- server-side cast validation;
- energy and cooldown handling;
- target and range checks;
- effect application and visual feedback;
- XP award rules;
- multiplayer replication;
- tests for invalid casts and exploit cases.

## Balance principles

- No path should dominate every context.
- Tide: adaptable, but less burst damage.
- Stone: durable and controls space, but less mobile.
- Ember: strong pressure, but more energy-intensive and less defensive.
- Gale: high mobility and disruption, but lower sustained damage.
- PvP tuning must be separate from PvE where necessary.
