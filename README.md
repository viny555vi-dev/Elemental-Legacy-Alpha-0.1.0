# Elemental Legacy — Alpha 0.1.0-alpha.3
Minecraft Java 26.3 + NeoForge 26.3.0.95, Java 25.

## Milestone 3
- First-join element selection screen (Água, Terra, Fogo, Ar).
- Z and X activate the two techniques for the locally selected path.
- R reopens the element selection screen.
- HUD displays the chosen path and hotkey legend.
- Existing server-authoritative checks for energy, cooldown, damage, knockback, particles and hit-based XP.

## Commands
- /elemental choose <tide|stone|ember|gale>
- /elemental skills
- /elemental select <ability_id>
- /elemental use [ability_id]
- /elemental status

## Hotkeys
- Z: technique one.
- X: technique two.
- R: open selection screen.

IDs: Tide water_burst / ice_bind; Stone rock_strike / seismic_wave; Ember flame_burst / fire_whip; Gale air_blast / cyclone.

The server validates and permanently locks the path after the first choice. This is still an alpha: energy/cooldown HUD synchronization, radial skill selector, full skill tree, custom projectile entities, animation polish and runtime multiplayer testing remain future work.
