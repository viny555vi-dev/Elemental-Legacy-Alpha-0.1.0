# Elemental Legacy

**Original elemental-action RPG mod for Minecraft Java 26.3 + NeoForge 26.3.**

Elemental Legacy takes broad inspiration from elemental fantasy and action-RPG progression. It is an independent project: it does not include code, textures, models, animations, sounds, names, or other assets from Avatar: Legend Reforged or the Avatar franchise.

## Current development status

`0.1.0-alpha.1` is the first backend prototype. It establishes:

- NeoForge 26.3 / Minecraft 26.3 build configuration (Java 25 toolchain).
- Four original paths: Tide, Stone, Ember, Gale.
- A world-scoped save for each player's permanent element and progression.
- `/elemental choose <tide|stone|ember|gale>` (one-time choice per world).
- `/elemental status` (level, XP, energy).
- A natural energy regeneration prototype.
- A 50-level XP curve foundation.

**This is not yet a complete playable release.** The first-choice screen, keybinds, radial selector, abilities, skill trees, effects, and multiplayer ability synchronization are planned next. The current environment used to prepare this source only has Java 21; Minecraft 26.3 requires Java 25, so a local Gradle compile has not yet been run here.

## Build requirements

- Minecraft Java 26.3
- NeoForge 26.3.0.100 (or a compatible 26.3 build)
- Java Development Kit 25
- Gradle wrapper from the official NeoForge 26.3 NeoGradle MDK

The official starter template is: <https://github.com/NeoForgeMDKs/MDK-26.3-NeoGradle>

Copy/overlay this project's `build.gradle`, `gradle.properties`, `settings.gradle`, and `src/` into a fresh copy of that MDK (preserving its `gradlew`, `gradlew.bat`, and `gradle/wrapper/` files), then run:

```bash
./gradlew build
```

On Windows:

```bat
gradlew.bat build
```

The mod JAR will be generated in `build/libs/`.

## Prototype commands

```text
/elemental choose tide
/elemental choose stone
/elemental choose ember
/elemental choose gale
/elemental status
```

The element choice is stored per world and cannot be overwritten by another choice. During development, a future admin-only reset utility may be added for test worlds; it will not be available to ordinary players in release builds.

## Design decisions confirmed

- 50 mastery levels per elemental path.
- Natural energy regeneration.
- Multiplayer support, with each player choosing and progressing independently.
- Element selection is permanent for that player in that world.
- XP should reward meaningful ability use, not idle or spam repetition.

## Planned milestones

1. **Foundation:** build, persistent profile, permanent path selection, server-authoritative progression.
2. **First-run UX:** custom four-path selection screen, profile sync, clear confirmation.
3. **Controls:** configurable activation key, radial skill selector, quick slots, readable cooldown/energy feedback.
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
