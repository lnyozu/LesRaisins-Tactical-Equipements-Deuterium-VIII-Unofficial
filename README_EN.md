# LesRaisins Tactical Equipments Unofficial

An unofficial Minecraft 1.20.1 maintenance fork of [LesRaisins Tactical Equipments](https://github.com/LesRaisins-Studios/LesRaisins-Tactical-Equipements), extending [Timeless and Classics Zero (TACZ)](https://github.com/MCModderAnchor/TACZ) with melee weapons, throwables, and consumables.

> [中文](README.md) · English

## Requirements

- Minecraft `1.20.1`
- Forge `47.2.20` or a newer 47.x release
- TACZ `1.1.5` or newer
- Java `17`

The server and all clients must use the same version of this mod. Releases with synchronized smoke configuration use a strict network protocol and cannot be mixed with older JARs.

## Highlights

### Smoke

- Authoritative smoke state is maintained by the server; clients build volumes and render particles locally.
- Two configurable presets: `sphere` and `cylinder`, with sphere as the default.
- Smoke volumes follow passable space and expand smoothly over time.
- Overlapping smoke cells are deduplicated under a global particle budget.
- Player name tags are hidden inside smoke without removing close-range visibility for players already inside it.
- LR Tactical explosions can disperse overlapping active smoke.
- Smoke can extinguish fire clouds that permit this behavior.

### Fire Clouds

- Terrain spread can climb `1` block and search `3` blocks downward by default.
- Server-side terrain regions are cached to avoid repeated path calculations.
- Client rendering discards overlapping cells and uses a global particle budget.
- Effect intervals, terrain limits, cache refresh, and smoke extinguishing are configurable.

### C4

- C4 ownership, explosion data, attachment state, and lifetime are persistent.
- Existing C4 entities, C4 items, and legacy detonator NBT remain compatible.
- Each player may deploy up to `16` C4 charges by default; existing excess charges are retained.
- Charges are silently cleaned after `30` minutes by default, with unloaded time counted using world time.
- Detonators search loaded dimensions without force-loading chunks.
- Explosion-triggered chain reactions use a rate-limited queue, processing at most `8` charges per server tick by default.
- Stationary C4 skips motion collision work and reduces support checks and synchronization.
- Detonators are synchronized as invalid after their linked charge explodes or expires.

### Additional Changes

- Fixed duplicated inspect sounds and residual sounds after switching items.
- Throwables have a forced cleanup lifetime that does not trigger their fuse or effects.
- Explosion values are clamped to safe limits, and entities exactly at the explosion center now receive damage.
- TACZ-style Lore and stat formatting with native `HideFlags` support.
- Index `tooltip` entries support translation keys and HEX colors.
- Independent switches for enchantment effects, anvil enchanting, and enchanting-table enchanting.
- Compatible with the melee content-pack implementation used by LesRaisins.

## Installation and Content Packs

### Core Installation

1. Install Forge, TACZ, and their required dependencies.
2. Place the core JAR without the `-bundled` suffix in both client and server `mods/` directories.
3. Place a compatible TACZ content pack under `.minecraft/tacz/`.

The core JAR does not contain the original author's All Rights Reserved models, textures, animations, or sounds. The mod still starts without a content pack, but the default content lists remain empty and built-in placeholder icons are used for its creative tabs.

### Local Bundled Build

The development environment can produce a local `-bundled` JAR containing an embedded pack. On first launch it extracts the pack to:

```text
.minecraft/tacz/lrtactical_official_resources/
```

Automatic extraction does not overwrite a manually installed pack or a previously extracted directory that the user has modified.

> Archives under `build/packs/` and JARs with the `-bundled` suffix may contain locally retained ARR art assets. Do not upload or publicly distribute them without permission from the original author. Public releases should use the core JAR without the `-bundled` suffix.

## Configuration

All configuration files are global to the instance and live under `config/`; none follow individual world saves.

| File | Purpose |
|---|---|
| `lrtactical-common.toml` | Shared gameplay configuration |
| `lrtactical-server.toml` | Global throwable, C4, smoke, and fire-cloud configuration |
| `lrtactical-client.toml` | Per-client visual and particle configuration |

Forge watches these files and hot-reloads saved changes. Administrators may also run `/lrtactical reload config` to explicitly reload server configuration and synchronize clients.

### Common Configuration

| Key | Default | Description |
|---|---:|---|
| `grenade.grenadeExplosionBlockDamage` | `false` | Allow supported explosions to destroy blocks |
| `melee.meleeItemConsumeDurability` | `false` | Consume durability on melee attacks |
| `melee.meleeIgnoreInvulnerableTickThreshold` | `20` | Ignore target invulnerability below this value |
| `melee.meleeEnchantmentEffectsEnabled` | `false` | Apply combat effects from existing enchantments |
| `melee.meleeAnvilEnchantingEnabled` | `false` | Allow enchanting through anvils and enchanted books |
| `melee.meleeEnchantingTableEnabled` | `false` | Allow enchanting through the enchanting table |

The three enchantment switches are independent. Enchantment combat effects do not determine whether enchantments can be acquired through an anvil or enchanting table.

### Global Server Configuration

#### Throwables and C4

| Key | Default | Description |
|---|---:|---|
| `throwable.throwable_force_cleanup_time_seconds` | `1800` | Maximum ordinary throwable lifetime |
| `throwable.remote_charge_force_cleanup_time_seconds` | `1800` | Maximum C4 lifetime; `0` disables automatic cleanup |
| `throwable.remote_charge_max_per_player` | `16` | Maximum deployed C4 charges per player |
| `throwable.remote_charge_chain_detonations_per_tick` | `8` | Maximum chain-triggered C4 explosions per tick |

#### Smoke

| Key | Default | Description |
|---|---:|---|
| `smoke.start_delay_ticks` | `40` | Delay before smoke begins |
| `smoke.shape` | `"sphere"` | `"sphere"` or `"cylinder"` |
| `smoke.radius` | `5.5` | Radius shared by both presets |
| `smoke.half_height` | `4.5` | Cylinder half-height; ignored by sphere |
| `smoke.duration_multiplier` | `1.0` | Multiplier applied to each Index `entity.life_time` |
| `smoke.max_lifetime_ticks` | `12000` | Maximum total lifetime after applying the multiplier |
| `smoke.sync_range` | `128.0` | Smoke-state synchronization range |
| `smoke.sync_interval_ticks` | `20` | Periodic synchronization interval |
| `smoke.moving_sync_interval_ticks` | `4` | Position synchronization interval while moving |
| `smoke.motion_sleep_confirm_ticks` | `5` | Low-motion ticks required before sleeping |
| `smoke.explosion_dispel_enabled` | `true` | Allow LR Tactical explosions to disperse smoke |

The Index `entity.life_time` remains the base lifetime for each smoke grenade. Lifetime modifiers mainly affect newly spawned grenades; shape and range changes are synchronized and rebuild existing client smoke volumes after a hot reload.

#### Fire Clouds

| Key | Default | Description |
|---|---:|---|
| `fire_cloud.effect_interval_ticks` | `10` | Damage and effect check interval |
| `fire_cloud.max_cells` | `192` | Maximum terrain cells per cloud |
| `fire_cloud.step_up` | `1` | Maximum upward terrain step |
| `fire_cloud.drop_down` | `3` | Maximum downward terrain search |
| `fire_cloud.cache_refresh_ticks` | `40` | Terrain-spread cache refresh interval |
| `fire_cloud.smoke_extinguish_enabled` | `true` | Allow smoke to extinguish compatible fire clouds |

### Client Configuration

| Key | Default | Description |
|---|---:|---|
| `blackFlash` | `false` | Use a black flash-blindness overlay |
| `explodeScreenShakeMultiplier` | `1.0` | Explosion screen-shake multiplier; `0` disables it |
| `smoke.render_distance` | `128.0` | Smoke particle rendering range |
| `smoke.max_particles_per_tick` | `192` | Global smoke particle budget |
| `smoke.near_particles_per_tick` | `36` | Per-smoke near-distance budget |
| `smoke.medium_particles_per_tick` | `18` | Per-smoke medium-distance budget |
| `smoke.far_particles_per_tick` | `8` | Per-smoke far-distance budget |
| `smoke.min_expansion_ticks` | `24` | Minimum expansion duration |
| `smoke.max_expansion_ticks` | `50` | Maximum expansion duration |
| `smoke.volume_rebuild_interval_ticks` | `3` | Volume rebuild interval for moving smoke |
| `smoke.max_volume_cells` | `1024` | Maximum volume cells per smoke grenade |
| `smoke.overlap_deduplication` | `true` | Deduplicate overlapping smoke cells |
| `smoke.particle_min_lifetime_ticks` | `38` | Minimum smoke-particle lifetime |
| `smoke.particle_max_lifetime_ticks` | `53` | Maximum smoke-particle lifetime |
| `smoke.particle_fade_in_ticks` | `8` | Particle fade-in duration |
| `smoke.particle_fade_out_ticks` | `10` | Particle fade-out duration |
| `fire_cloud.max_particles_per_tick` | `48` | Global fire-cloud particle budget |
| `fire_cloud.particle_density` | `0.62` | Fire-cloud particle density |
| `fire_cloud.overlap_deduplication` | `true` | Deduplicate overlapping fire-cloud cells |

## Administrative Commands

All administrative commands require Minecraft permission level `4`; the server console may also use them. Entering `/lrtactical` by itself displays the same help.

| Command | Purpose |
|---|---|
| `/lrtactical help` | Show clickable command help |
| `/lrtactical clear all` | Clear all throwables, smoke, and fire clouds |
| `/lrtactical clear throwables` | Clear throwable entities, including C4 and smoke grenades |
| `/lrtactical clear smoke` | Clear smoke grenades, authoritative smoke state, and existing client smoke particles |
| `/lrtactical clear fire_clouds` | Clear all fire clouds |
| `/lrtactical reload config` | Hot-reload global configuration, refresh caches, and synchronize clients |

Cleanup discards entities directly and does not trigger their fuse, explosion, smoke, or fire effects.

## TACZ HideFlags Compatibility

Items support TACZ's native integer `HideFlags` under the root NBT `tag`:

| Value | Effect |
|---:|---|
| `1` | Hide Lore and remove the following half-line spacing |
| `4` | Hide primary stats |
| `8` | Hide secondary stats and effects |
| `32` | Hide content-pack information |
| `63` | Hide all supported sections |

Stat groups:

- Melee: `4` hides damage and attack speed; `8` hides movement speed and range.
- Throwables: `4` hides damage or duration; `8` hides range and effects.
- Consumables: `4` hides healing and hunger; `8` hides saturation, use time, and effects.

Index files may set a default with `"hide_tooltip": 12`. Common combinations are `1`, `12`, `44`, and `63`.

## Building

```powershell
# Public core JAR without official default art content
.\gradlew.bat build

# Build a local external pack for .minecraft/tacz/
.\gradlew.bat buildOfficialPack

# Build a local JAR that embeds and extracts the local resource pack
.\gradlew.bat buildBundled

# Install the local pack into development run directories
.\gradlew.bat installOfficialPackForRuns
```

Outputs:

- Core and local bundled JARs: `build/libs/`
- External pack: `build/packs/lrtactical-official-resources-<version>.zip`

Local official resources live in the Git-ignored `src/main/resources/assets/lrtactical/` directory. Core builds still work when it is absent, while pack and bundled builds fail with a clear error.

## License

- Code: [GNU GPL 3.0](https://www.gnu.org/licenses/gpl-3.0.txt)
- Original art assets: All Rights Reserved
