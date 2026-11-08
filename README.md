# Elemental Legacy

**Original elemental-action RPG mod for Minecraft Java 26.3 + NeoForge 26.3.**

Elemental Legacy is an independent project inspired by broad elemental fantasy and action-RPG progression. It does not include code, textures, models, animations, sounds, names, or other assets from third-party mods or franchises.

## Current development status

`0.1.0-alpha.2` adds the first playable server-side combat slice:

- Four original elemental paths: Tide (water), Stone (earth), Ember (fire), Gale (air).
- Permanent per-player elemental choice saved in the world.
- Two original combat techniques for each element (eight total).
- Skill listing, selection and activation commands with tab suggestions.
- Server-side target detection, damage, knockback, fire ignition, particles, energy costs and cooldowns.
- XP awarded only when a technique successfully damages a living target.
- Natural energy regeneration (1.5 energy per second).
- Persistent level, XP, energy and selected technique; 50-level progression foundation.
- Multiplayer actions are executed and validated on the server.

This is an early combat prototype, not the complete planned RPG. It does not yet include a first-run GUI, keybinds, radial selector, custom HUD, skill tree, custom projectile entities, animations, or dedicated multiplayer playtesting.

## Commands

Choose once per world:
```text
/elemental choose tide
/elemental choose stone
/elemental choose ember
/elemental choose gale
```

Combat:
```text
/elemental skills
/elemental select <ability_id>
/elemental use
/elemental use <ability_id>
/elemental status
```

Technique IDs:
- Tide: `water_burst`, `ice_bind`
- Stone: `rock_strike`, `seismic_wave`
- Ember: `flame_burst`, `fire_whip`
- Gale: `air_blast`, `cyclone`

Aim at nearby hostile or neutral living entities and activate a technique. Techniques affect living entities in a forward cone, consume energy and enter cooldown. A successful hit awards XP. A miss consumes energy and cooldown but grants no XP. Fire techniques ignite targets; air techniques apply stronger knockback.

## Build requirements

- Minecraft Java 26.3
- NeoForge 26.3.0.95
- Java Development Kit 25
- Gradle 9.2.1

GitHub Actions builds the mod and publishes the JAR as the `Elemental-Legacy-JAR` artifact.

## Project status and next milestones

The combat slice is server-authoritative and designed for multiplayer, but it still requires in-game client and dedicated-server testing. Planned work includes client controls, visual selection UI, HUD, skill progression/unlocks, richer movement and defense, custom projectiles, sound/animation polish, and balance testing.

## License

Project code is licensed under MIT. Minecraft and third-party trademarks/assets remain the property of their owners. No third-party mod assets are included.
