# LesRaisins Tactical Equipments Unofficial

An unofficial fork of [LesRaisins Tactical Equipments](https://github.com/LesRaisins-Studios/LesRaisins-Tactical-Equipements), extending [Timeless and Classics Zero (TACZ)](https://github.com/MCModderAnchor/TACZ) with melee weapons, throwables, and consumables.

> [中文](README.md) · English

## Fork Changes

### Bug Fixes

1. Fixed inspect sound duplication: sounds no longer stack on repeated inspect key presses, and residual sounds stop when switching items.
2. Fixed missing maximum lifetime for throwable entities: throwables are force-cleaned after the configured duration, defaulting to 1800 seconds (30 minutes).

### Improvements

1. Enhanced tooltip display: Lore moved below the item name, half-line spacing between Lore and stats, stat formatting aligned with vanilla TACZ style, full TACZ HideFlags compatibility.
2. Reliable enchantment configuration: three independent toggles for enchantment combat effects, anvil enchanting, and enchanting table; anvil merge protection.
3. `IMeleeWeapon` interface compatibility with all LesRaisins officially compatible melee weapon packs.
4. Server-side smoke synchronization, explosion visual effects networking, and player name tag hiding inside smoke.
5. Custom Lore text via `tooltip` field in Index, with translation key and HEX color support.

## Resource Pack and Build Modes

> This repository and its public core JAR do not contain the original author's All Rights Reserved models, textures, animations, or sounds. Those resources remain local to the developer; the build scripts only provide optional local packaging support.

The code core is separated from the original author's All Rights Reserved art assets. Default content under `assets/lrtactical` and `data/lrtactical` is excluded from the public core JAR.

```powershell
# Public core JAR without official default content
.\gradlew.bat build

# Build the external official pack for .minecraft/tacz/
.\gradlew.bat buildOfficialPack

# Build a local bundled JAR that extracts the embedded official pack
.\gradlew.bat buildBundled

# Install the official pack into all development run directories
.\gradlew.bat installOfficialPackForRuns
```

Outputs:

- Core and bundled JARs: `build/libs/`
- External official pack: `build/packs/lrtactical-official-resources-<version>.zip`
- Bundled extraction target: `.minecraft/tacz/lrtactical_official_resources/`

The bundled build expands its embedded archive into TACZ's native folder-pack layout. It does not overwrite a manually installed pack or a user-modified extracted directory. When only the core JAR is installed with no compatible content pack, the mod still loads normally: its three creative tabs use a snowball, golden apple, and netherite sword as safe icons, while their default content lists remain empty.

Local official resources are retained in the Git-ignored `src/main/resources/assets/lrtactical/` directory. A core build still succeeds when this directory is absent; `buildOfficialPack` and `buildBundled` instead fail with a clear message.

The pack under `build/packs/` and JARs carrying the `-bundled` suffix may contain locally retained ARR art assets. Do not upload or publicly distribute those local artifacts without the original author's permission. Public releases should use the core JAR without the `-bundled` suffix.

## Configuration

Common configuration (`config/lrtactical.toml`):

```toml
[grenade]
# Whether grenade explosions destroy blocks, default false
grenadeExplosionBlockDamage = false

[melee]
# Whether melee attacks consume durability, default false
meleeItemConsumeDurability = false
# Ignore invulnerable ticks when below this threshold, default 20
meleeIgnoreInvulnerableTickThreshold = 20
# Whether enchantment combat effects apply (Sharpness, Smite, etc.), default false
meleeEnchantmentEffectsEnabled = false
# Whether anvil enchanting is available, default false
meleeAnvilEnchantingEnabled = false
# Whether enchanting table is available, default false
meleeEnchantingTableEnabled = false
```

Per-world server configuration (`<world>/serverconfig/lrtactical-server.toml`):

```toml
[throwable]
# Max throwable entity lifetime in seconds, silently removed without triggering fuse/explosion/smoke, default 1800
throwable_force_cleanup_time_seconds = 1800
```

Client configuration (`config/lrtactical-client.toml`):

```toml
# Use black overlay instead of white when blinded by flashbang, default false
blackFlash = false
# Explosion screen shake multiplier, 0.0 to disable, default 1.0
explodeScreenShakeMultiplier = 1.0
```

> The three enchantment switches are independent: enchantment effects control whether existing enchantments affect combat, while the anvil and enchanting-table switches only control their respective acquisition methods.

## TACZ HideFlags Compatibility

Items support TACZ's native integer `HideFlags` (under root `tag` in NBT, `Int` type):

| Value | Effect |
|---:|---|
| `1` | Hides Lore and removes its spacing |
| `4` | Hides primary stats |
| `8` | Hides secondary stats and effects |
| `32` | Hides pack information |
| `63` | Hides all supported sections above |

Stat groups:

- Melee: `4` hides damage and attack speed; `8` hides movement speed and range.
- Throwables: `4` hides damage or duration; `8` hides range and effects.
- Consumables: `4` hides healing and hunger; `8` hides saturation, use time, and effects.

Index files can set a default via `"hide_tooltip": 12`. Common combinations: `1` (Lore only), `12` (all numeric stats), `44` (all stats and pack info), `63` (everything).

## License

- Code: [GNU GPL 3.0](https://www.gnu.org/licenses/gpl-3.0.txt)
- Art assets: All Rights Reserved by the original author
