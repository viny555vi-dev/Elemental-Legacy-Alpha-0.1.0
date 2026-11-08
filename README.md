# Elemental Legacy — Alpha 0.1.0-alpha.3
Minecraft Java 26.3 + NeoForge 26.3.0.95, Java 25.

## Milestone 3 — first client experience
- First-time element selection screen: Água, Terra, Fogo or Ar.
- The selected path is saved on the server per player and world; the client caches the chosen path per server/world to restore the HUD after reconnecting.
- Z and X activate the two techniques of the selected path.
- R opens the elemental techniques panel, with buttons to activate each technique.
- HUD shows the current path and shortcut legend.
- Existing server-side checks apply energy cost, cooldown, damage, knockback, particles and hit-based XP.

## Commands
- /elemental choose <tide|stone|ember|gale>
- /elemental skills
- /elemental select <ability_id>
- /elemental use [ability_id]
- /elemental status

Technique IDs: Tide water_burst / ice_bind; Stone rock_strike / seismic_wave; Ember flame_burst / fire_whip; Gale air_blast / cyclone.

The server remains authoritative: a player's first elemental choice cannot be changed in that world. If an existing world already has a selected element from an earlier alpha, the first client launch after updating asks the player to confirm the same path once so the local hotkeys can be initialized.

## Milestone 4 — technique visual identity (in progress)\n- Each of the eight currently implemented techniques now has its own server-emitted particle choreography: wave, freezing spiral, rock impact, seismic rings, fire projectile, fire crescent, compressed air lanes and rising cyclone.\n- Visual effects are emitted by the server so nearby players can see the same technique effects in multiplayer.\n- These are particle-based visual sequences, not yet full skeletal bending animations or custom projectile entities.\n\nStill planned: synchronized energy/cooldown bars, radial selector, 50-level skill unlock tree, custom projectile entities with travel/collision, timed multi-stage animations, sound design, bending movement/pose animations and full client/dedicated-server multiplayer testing.
