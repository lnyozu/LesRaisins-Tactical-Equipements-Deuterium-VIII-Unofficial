<p align="center">
    <img width="300" src="https://s2.loli.net/2024/04/30/NJrstR1QzpoLyIT.png" alt="LesRaisins Tactical Equipments">
</p>

<h1 align="center">LesRaisins Tactical Equipments</h1>

<p align="center">
    A tactical equipment add-on for Timeless and Classics Zero (TACZ).
</p>

<p align="center">
    <a href="README.md">中文</a> · English
</p>

---

# English

LesRaisins Tactical Equipments is an add-on for [Timeless and Classics Zero (TACZ)](https://github.com/MCModderAnchor/TACZ). It extends the TACZ gun-pack system with configurable melee weapons, throwables, and consumables.

## Requirements

- Minecraft 1.20.1
- Forge 47+
- TACZ 1.1.5+
- The mod must be installed on both the client and the server

## Features

### Melee weapons

- Gun-pack-style Index, Display, model, animation, texture, sound, and third-person animation configuration.
- Left/right attacks, combos, delays, cooldowns, knockback, and configurable hitboxes.
- Automatic tooltip rows for damage, attack speed, movement speed, and attack range.
- `generic.attack_damage` is used directly as the final base weapon damage. The player's built-in 1 damage is not added again.
- Melee durability consumption is disabled by default.

### Throwables

- Explosives, flashbangs, smoke grenades, incendiaries, effect clouds, and remote detonation.
- Configurable throw speed, cooking, bouncing, lifetime, cooldown, stack size, and screen shake.
- Type-aware tooltip rows for damage, duration, range, and effects.

### Consumables

- Healing, hunger, saturation, effects, effect removal, cooldowns, and durability-based uses.
- Hold and toggle use modes.
- Automatic tooltip rows for healing, hunger, saturation, use time, and effects.

## Default pack

On first launch, the bundled resources are extracted to:

```text
.minecraft/tacz/default_melee/
```

If `gunpack.meta.json` already exists in that directory, its contents are not overwritten. To regenerate the latest bundled pack after updating the mod, back up and delete `default_melee`, then restart the game.

## Pack structure

TACZ supports both folders and ZIP archives. The server and client may use different container formats as long as their content and namespace match.

```text
My_Tactical_Pack/
├─ gunpack.meta.json
├─ pack.mcmeta
├─ assets/
│  └─ example/
│     ├─ display/
│     │  ├─ melee/
│     │  ├─ throwable/
│     │  └─ consumable/
│     ├─ geo_models/
│     ├─ animations/
│     ├─ scripts/
│     ├─ textures/
│     ├─ lang/
│     ├─ tacz_sounds/
│     └─ gunpack_info.json
└─ data/
   └─ example/
      └─ index/
         ├─ melee/
         ├─ throwable/
         └─ consumable/
```

Example `gunpack.meta.json`:

```json
{
  "namespace": "example"
}
```

For ZIP packs, `gunpack.meta.json`, `assets`, and `data` must be located directly at the archive root. Use a UTF-8-compatible ZIP tool and avoid unused files with non-ASCII or special-character names.

## Tooltip and Lore

Index files use the TACZ-compatible `tooltip` field:

```json
{
  "name": "item.example.weapon",
  "tooltip": "tooltip.example.weapon"
}
```

An array may be used for multiple lines:

```json
{
  "tooltip": [
    "tooltip.example.weapon.line_1",
    "tooltip.example.weapon.line_2"
  ]
}
```

No description is generated when the field is missing, `null`, blank, or an empty array.

### Translation keys

Text without a HEX marker is parsed as a translation key:

```json
"tooltip": "tooltip.example.weapon"
```

Language file:

```json
{
  "tooltip.example.weapon": "A reliable melee weapon."
}
```

If the translation key does not exist, Minecraft displays the raw string.

### Solid HEX colors and gradients

Direct text supports `&#RRGGBB` markers:

```json
"tooltip": "&#55ffffCyan text"
```

Each color remains active until the next marker, allowing per-character gradients:

```json
"tooltip": "&#5118f9h&#5521f8e&#592bf7l&#5e34f6l&#623df5o &#6647f4w&#6a50f3o&#6f59f2r&#7363f1l&#776cf0d"
```

Translation-key mode and direct HEX-text mode are separate. A line containing HEX markers is rendered as direct text instead of being treated as a translation key.

### Item NBT

The Index `tooltip` is written to:

```text
tag.display.Lore
```

This allows the text to be edited or removed with an NBT editor. Lore is placed directly below the item name, with an approximately 4 px gap before generated stat rows.

Reloading server Index data does not overwrite `display.Name`, `display.Lore`, or `HideFlags` on existing items. Updated Index tooltip text is only written to newly created items.

## TACZ HideFlags compatibility

Items support TACZ's native integer `HideFlags`:

```nbt
HideFlags:12
```

In an NBT editor, add the field directly under the item's root `tag` and use the `Int` type, not a string.

| Value | Current behavior |
|---:|---|
| `1` | Hides Lore and removes its spacing |
| `2` | TACZ ammo-info bit; currently unused by this mod |
| `4` | Hides primary stats |
| `8` | Hides secondary stats and effects |
| `16` | TACZ upgrade-tip bit; currently unused by this mod |
| `32` | Hides pack information |
| `63` | Hides every supported section above |

Stat groups:

- Melee: `4` hides damage and attack speed; `8` hides movement speed and range.
- Throwables: `4` hides damage or duration; `8` hides range and effects.
- Consumables: `4` hides healing and hunger; `8` hides saturation, use time, and effects.

The Creative inventory category line is not one of TACZ's six tooltip sections, so it is not currently hidden by `HideFlags:63`.

### Setting HideFlags from an Index

Index files may contain:

```json
{
  "hide_tooltip": 12
}
```

Newly created items will receive:

```nbt
HideFlags:12
```

When `hide_tooltip` is absent, no HideFlags value is written. Index changes do not overwrite HideFlags on existing items.

Common combinations:

| HideFlags | Result |
|---:|---|
| `1` | Hide Lore only |
| `12` | Hide all generated numeric stats |
| `32` | Hide pack information only |
| `44` | Hide all generated stats and pack information |
| `63` | Hide all supported tooltip sections |

## Server synchronization

The server synchronizes these Index types to clients:

- Melee weapons
- Throwables
- Consumables

After a TACZ resource reload, the Creative inventory cache is rebuilt and newly generated items use the current server Index.

Models, textures, Display files, animations, Lua scripts, and sounds are not transferred by the server. Clients must install the matching resource pack locally. If the server provides an Index but the client fails to load its display assets, the item appears as a black-and-purple missing-texture cube.

Existing items continue to resolve current combat data through their IDs, while NBT-stored names, Lore, and HideFlags remain unchanged.

## ZIP troubleshooting

If a folder pack works but its ZIP version produces missing-texture cubes, inspect the client log for `GunPackFinder`.

Successful loading:

```text
DeltaForce_Melee_Pack_0.2.0.zip, Main namespace: delta_wt
```

Common failure:

```text
invalid CEN header (bad entry name)
```

This usually indicates incompatible ZIP filename encoding. Recommended fixes:

1. Remove unused backup files.
2. Avoid non-ASCII characters, backslashes, and special characters in filenames.
3. Recreate the ZIP with 7-Zip or PowerShell `Compress-Archive`.
4. Ensure `gunpack.meta.json` is at the ZIP root.
5. Keep all resource paths lowercase; ZIP paths are normally case-sensitive.

## Configuration

Common configuration:

```toml
[grenade]
grenadeExplosionBlockDamage = true

[melee]
meleeItemConsumeDurability = false
meleeIgnoreInvulnerableTickThreshold = 20
```

Client configuration:

```toml
blackFlash = false
explodeScreenShakeMultiplier = 1.0
```

Configuration files created by older versions keep their existing values. To disable melee durability consumption, verify:

```toml
meleeItemConsumeDurability = false
```

## Building

Windows:

```powershell
.\gradlew.bat build
```

Build artifacts are written to:

```text
build/libs/
```

## Authors

- Programmer: `xjqsh`
- Artist: `LeComte`
- LesRaisins Studio

## Credits

- Special thanks to MrCrayfish for valuable modding experience and references.
- The deafened and blind effect implementations reference MrCrayfish's Gun Mod.
- Thanks to every player who tested, reported issues, or otherwise helped the project.

## License

- Code: [GNU GPL 3.0](https://www.gnu.org/licenses/gpl-3.0.txt)
- Art assets: All Rights Reserved
