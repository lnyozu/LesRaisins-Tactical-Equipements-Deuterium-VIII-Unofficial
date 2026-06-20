# Configuration and TACZ Compatibility

> [中文](CONFIGURATION_CN.md) · English · [Back to README](README.md)

## Configuration Files

All configuration files are global to the instance and live under `config/`; none follow individual world saves.

| File | Purpose |
|---|---|
| `lrtactical-common.toml` | Shared gameplay configuration |
| `lrtactical-server.toml` | Global throwable, C4, smoke, and fire-cloud configuration |
| `lrtactical-client.toml` | Per-client visual and particle configuration |

Forge watches these files and hot-reloads saved changes. Administrators may also run `/lrtactical reload config` to explicitly reload server configuration and synchronize clients.

## Common Configuration

| Key | Default | Description |
|---|---:|---|
| `grenade.grenadeExplosionBlockDamage` | `false` | Allow supported explosions to destroy blocks |
| `melee.meleeItemConsumeDurability` | `false` | Consume durability on melee attacks |
| `melee.meleeIgnoreInvulnerableTickThreshold` | `20` | Ignore target invulnerability below this value |
| `melee.meleeEnchantmentEffectsEnabled` | `false` | Apply combat effects from existing enchantments |
| `melee.meleeAnvilEnchantingEnabled` | `false` | Allow enchanting through anvils and enchanted books |
| `melee.meleeEnchantingTableEnabled` | `false` | Allow enchanting through the enchanting table |

The three enchantment switches are independent. Enchantment combat effects do not determine whether enchantments can be acquired through an anvil or enchanting table.

## Global Server Configuration

### Throwables and C4

| Key | Default | Description |
|---|---:|---|
| `throwable.throwable_force_cleanup_time_seconds` | `1800` | Maximum ordinary throwable lifetime |
| `throwable.remote_charge_force_cleanup_time_seconds` | `1800` | Maximum C4 lifetime; `0` disables automatic cleanup |
| `throwable.remote_charge_max_per_player` | `16` | Maximum deployed C4 charges per player |
| `throwable.remote_charge_chain_detonations_per_tick` | `8` | Maximum chain-triggered C4 explosions per tick |

### Smoke

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

### Fire Clouds

| Key | Default | Description |
|---|---:|---|
| `fire_cloud.effect_interval_ticks` | `10` | Damage and effect check interval |
| `fire_cloud.max_cells` | `192` | Maximum terrain cells per cloud |
| `fire_cloud.step_up` | `1` | Maximum upward terrain step |
| `fire_cloud.drop_down` | `3` | Maximum downward terrain search |
| `fire_cloud.cache_refresh_ticks` | `40` | Terrain-spread cache refresh interval |
| `fire_cloud.smoke_extinguish_enabled` | `true` | Allow smoke to extinguish compatible fire clouds |

## Client Configuration

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
