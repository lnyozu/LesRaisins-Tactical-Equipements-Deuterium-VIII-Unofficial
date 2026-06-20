# LesRaisins Tactical Equipments Unofficial

An unofficial Minecraft 1.20.1 maintenance fork of [LesRaisins Tactical Equipments](https://github.com/LesRaisins-Studios/LesRaisins-Tactical-Equipements), extending [Timeless and Classics Zero (TACZ)](https://github.com/MCModderAnchor/TACZ) with melee weapons, throwables, and consumables.

> [中文](README_CN.md) · English

## Requirements

- Minecraft `1.20.1`
- Forge `47.2.20` or a newer 47.x release
- TACZ `1.1.5` or newer
- Java `17`

The server and all clients must use the same version of this mod.

## Highlights

- Reworked and optimized smoke and fire-cloud systems.
- Improved C4 persistence, cleanup, synchronization, and chain-reaction safety.
- Added global configuration hot reload and administrative cleanup commands.
- Fixed explosion damage, inspect audio, item descriptions, and compatibility issues.

## Installation

1. Install Forge, TACZ, and their required dependencies.
2. Place the core JAR in both client and server `mods/` directories.
3. Place a compatible TACZ content pack under `.minecraft/tacz/`.

The public core JAR does not contain the original author's All Rights Reserved models, textures, animations, or sounds. It can start without a content pack, but the default content lists will be empty.

## Documentation

- [Configuration, administrative commands, and TACZ HideFlags compatibility](CONFIGURATION.md)
- [配置、管理指令与 TACZ HideFlags 兼容说明](CONFIGURATION_CN.md)

## Building

```powershell
.\gradlew.bat build
```

The public core JAR is generated under `build/libs/`. Local build tasks for retained official assets are available in `build.gradle`, but their outputs must not be publicly distributed without permission from the original author.

## License

- Code: [GNU GPL 3.0](https://www.gnu.org/licenses/gpl-3.0.txt)
- Original art assets: All Rights Reserved
