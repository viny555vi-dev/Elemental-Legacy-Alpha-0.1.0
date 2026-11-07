# Elemental Legacy

**Original elemental-action RPG mod for Minecraft Java 26.3 + NeoForge 26.3.**

Elemental Legacy is an independent project inspired by broad elemental fantasy and action-RPG progression. It does not include code, textures, models, animations, sounds, names, or other assets from third-party mods or franchises.

## Current development status

`0.1.0-alpha.1` is the first backend prototype. It establishes:

- NeoForge 26.3 / Minecraft 26.3 build configuration (Java 25 toolchain).
- Four original paths: Tide, Stone, Ember, Gale.
- World-scoped storage for each player's permanent element and progression.
- `/elemental choose <tide|stone|ember|gale>` (one-time choice per world).
- `/elemental status` (level, XP, energy).
- Natural energy regeneration.
- A 50-level XP curve foundation.

**This is not yet a complete playable release.** The first-choice screen, keybinds, radial selector, abilities, skill trees, effects, and multiplayer ability synchronization remain planned work.

## Build requirements

- Minecraft Java 26.3
- NeoForge 26.3 (configured in `gradle.properties`)
- Java Development Kit 25
- Gradle 9.2.1

The GitHub Actions workflow installs Gradle 9.2.1 and Java 25 automatically, then runs `gradle build`. The compiled mod is published as the `Elemental-Legacy-JAR` workflow artifact.

For a local build, install JDK 25 and Gradle 9.2.1, then run:

```bash
gradle build
```

The mod JAR is generated in `build/libs/`.

## Prototype commands

```text
/elemental choose tide
/elemental choose stone
/elemental choose ember
/elemental choose gale
/elemental status
```

The element choice is stored per world and cannot be overwritten by another choice. Disconnecting, dying, changing dimensions, or reinstalling the client does not reset the path.

## Design decisions confirmed

- 50 mastery levels per elemental path.
- Natural energy regeneration (prototype: 1.5 energy per second).
- Multiplayer support, with each player choosing and progressing independently.
- Element selection is permanent for that player in that world.
- XP should reward meaningful ability use, not idle or spam repetition.

## Planned milestones

1. **Foundation:** build, persistent profile, permanent path selection, server-authoritative progression.
2. **First-run UX:** custom four-path selection screen, profile sync, clear confirmation.
3. **Controls:** configurable activation key, radial skill selector, quick slots, cooldown/energy feedback.
4. **Combat vertical slice:** two original abilities per path with server validation, effects, hit detection, costs, and XP.
5. **RPG progression:** 50 levels, unlock nodes, ability upgrades, mastery milestones, anti-farm XP rules.
6. **Content expansion:** full ability trees, movement, defense, utility, visual/audio polish.
7. **Multiplayer QA:** dedicated server, latency, persistence, permissions, exploit prevention, balance.

## Project structure

- `element/` — elemental identity and metadata.
- `data/` — persistent world/player progression.
- `command/` — development and admin commands.
- `progression/` — XP, energy, and tick rules.
- `docs/` — design and implementation notes.

## License

Project code is licensed under MIT. Minecraft and third-party trademarks/assets remain the property of their respective owners. No third-party mod assets are included.
