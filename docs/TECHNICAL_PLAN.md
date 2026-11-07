# Technical plan

## Server authority

The server owns the canonical profile stored in world SavedData. Client-side screens and keybinds are presentation/input only. Every cast request must be validated server-side against the player's chosen path, unlocked ability, cooldown, energy, target, range, and game state.

## Persistence model

`ElementalWorldData` stores player UUID -> `PlayerProgress` in the Overworld's SavedData storage. This makes progression world-specific and available across dimensions. Future schema changes must include a data version and migration strategy before release.

## Planned networking

- Server -> client: initial profile sync, energy/XP/level updates, unlock updates, cast feedback.
- Client -> server: path confirmation, select ability, cast ability, request profile/tree.
- Never accept client-supplied damage, XP, energy, cooldown, or unlock values.

## Planned client implementation

- Key mappings registered on the client mod bus.
- First-run selection screen backed by server confirmation.
- Radial selector with hold/release interaction and quick-slot assignment.
- Mastery tree screen with clear prerequisites and upgrade previews.
- HUD for mastery level, XP, energy, selected technique, and cooldown.

## Compatibility and test matrix

- Minecraft 26.3 + NeoForge 26.3.
- Integrated single-player.
- Dedicated server + two or more clients.
- Player disconnect/reconnect.
- World restart and backup/restore.
- Different dimensions.
- Invalid packet, invalid ability, insufficient energy, cooldown, and no-target cast cases.
