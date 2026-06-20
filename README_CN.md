# LesRaisins Tactical Equipments Unofficial

[LesRaisins Tactical Equipments](https://github.com/LesRaisins-Studios/LesRaisins-Tactical-Equipements) 的 Minecraft 1.20.1 非官方维护分支，为 [Timeless and Classics Zero（TACZ）](https://github.com/MCModderAnchor/TACZ) 提供近战武器、投掷物和消耗品支持。

> 中文 · [English](README.md)

## 运行环境

- Minecraft `1.20.1`
- Forge `47.2.20` 或更高的 47.x 版本
- TACZ `1.1.5` 或更高版本
- Java `17`

服务端与客户端必须安装相同版本的本模组。

## 功能特性

- 重构并优化烟雾与燃烧云系统。
- 完善 C4 持久化、自动清扫、同步及连锁爆炸保护。
- 增加全局配置热重载和管理员清除指令。
- 修复爆炸伤害、检视音效、物品说明及兼容性问题。

## 安装

1. 安装 Forge、TACZ 及其必要依赖。
2. 将核心 JAR 放入客户端和服务端的 `mods/`。
3. 将兼容的 TACZ 内容包放入 `.minecraft/tacz/`。

公开发布的核心 JAR 不包含原作者 All Rights Reserved 的模型、贴图、动画或音效。没有安装内容包时模组仍能启动，但默认内容列表为空。

## 文档

- [配置、管理指令与 TACZ HideFlags 兼容说明](CONFIGURATION_CN.md)
- [Configuration, administrative commands, and TACZ HideFlags compatibility](CONFIGURATION.md)

## 构建

```powershell
.\gradlew.bat build
```

公开核心 JAR 输出于 `build/libs/`。`build.gradle` 中仍保留供本地使用的官方资源构建任务；未获原作者授权时，不得公开分发其产物。

## 许可证

- 代码：[GNU GPL 3.0](https://www.gnu.org/licenses/gpl-3.0.txt)
- 原作者美术资源：All Rights Reserved
