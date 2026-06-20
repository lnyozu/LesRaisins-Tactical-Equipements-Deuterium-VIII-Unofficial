# LesRaisins Tactical Equipments Unofficial

[LesRaisins Tactical Equipments](https://github.com/LesRaisins-Studios/LesRaisins-Tactical-Equipements) 的 Minecraft 1.20.1 非官方维护分支，为 [Timeless and Classics Zero（TACZ）](https://github.com/MCModderAnchor/TACZ) 提供近战武器、投掷物和消耗品支持。

> 中文 · [English](README.md)

## 运行环境

- Minecraft `1.20.1`
- Forge `47.2.20` 或更高的 47.x 版本
- TACZ `1.1.5` 或更高版本
- Java `17`

服务端与客户端必须安装相同版本的本模组。

## 本分支主要特性

- 重构烟雾系统，支持球形与圆柱体预设、动态扩散、遮蔽、爆炸驱散及重叠优化。
- 优化燃烧云的地形扩散、重叠渲染和服务端计算。
- 完善 C4 的数量限制、持久化、自动清扫、遥控器同步及连锁爆炸保护。
- 增加投掷物安全清理、全局配置热重载和管理员清除指令。
- 修复爆炸伤害、检视音效及多项边界问题。
- 改进物品说明、TACZ `HideFlags` 和原有内容包兼容性。

## 安装与资源包

### 安装核心模组

1. 安装 Forge、TACZ 及其必要依赖。
2. 将不带 `-bundled` 后缀的核心 JAR 放入客户端和服务端的 `mods/`。
3. 将兼容的 TACZ 内容包放入 `.minecraft/tacz/`。

核心 JAR 不包含原作者 All Rights Reserved 的模型、贴图、动画或音效。没有安装内容包时，模组仍能正常启动，但默认内容列表为空，并使用内置占位图标显示创造分类。

### 本地完整构建

开发环境可以生成带内嵌资源包的本地 `-bundled` JAR。首次启动时，它会将资源释放到：

```text
.minecraft/tacz/lrtactical_official_resources/
```

自动释放不会覆盖手动安装的同名资源包，也不会覆盖已经被用户修改的释放目录。

> `build/packs/` 中的资源包和 `-bundled` JAR 可能包含本地保留的 ARR 美术资源。未获原作者授权时，不得上传或公开分发。公开发布应使用不带 `-bundled` 后缀的核心 JAR。

## 配置

配置分为三份，均位于实例全局 `config/` 目录，不跟随世界存档：

| 文件 | 作用 |
|---|---|
| `lrtactical-common.toml` | 通用玩法配置 |
| `lrtactical-server.toml` | 全局服务端烟雾、燃烧云、投掷物和 C4 配置 |
| `lrtactical-client.toml` | 每名玩家本地的视觉和粒子配置 |

保存配置后 Forge 会自动热重载。管理员也可以执行 `/lrtactical reload config` 显式重载服务端配置并同步客户端。

### 通用配置

| 配置项 | 默认值 | 说明 |
|---|---:|---|
| `grenade.grenadeExplosionBlockDamage` | `false` | 允许支持方块破坏的爆炸摧毁方块 |
| `melee.meleeItemConsumeDurability` | `false` | 近战攻击消耗耐久 |
| `melee.meleeIgnoreInvulnerableTickThreshold` | `20` | 低于该值时忽略目标受伤无敌帧 |
| `melee.meleeEnchantmentEffectsEnabled` | `false` | 已有武器附魔参与战斗计算 |
| `melee.meleeAnvilEnchantingEnabled` | `false` | 允许铁砧和附魔书附魔 |
| `melee.meleeEnchantingTableEnabled` | `false` | 允许附魔台附魔 |

三项附魔开关彼此独立：战斗效果开关不会决定玩家能否通过铁砧或附魔台获取附魔。

### 全局服务端配置

#### 投掷物与 C4

| 配置项 | 默认值 | 说明 |
|---|---:|---|
| `throwable.throwable_force_cleanup_time_seconds` | `1800` | 普通投掷物最大存在时间 |
| `throwable.remote_charge_force_cleanup_time_seconds` | `1800` | C4 最大存在时间；`0` 表示禁用自动清扫 |
| `throwable.remote_charge_max_per_player` | `16` | 每名玩家最多部署的 C4 数量 |
| `throwable.remote_charge_chain_detonations_per_tick` | `8` | 每 tick 最多处理的连锁 C4 爆炸 |

#### 烟雾

| 配置项 | 默认值 | 说明 |
|---|---:|---|
| `smoke.start_delay_ticks` | `40` | 开始释放烟雾前的延迟 |
| `smoke.shape` | `"sphere"` | `"sphere"` 球形或 `"cylinder"` 圆柱体 |
| `smoke.radius` | `5.5` | 两种形状共用的半径 |
| `smoke.half_height` | `4.5` | 圆柱体半高；球形模式忽略 |
| `smoke.duration_multiplier` | `1.0` | 对每个 Index `entity.life_time` 应用的倍率 |
| `smoke.max_lifetime_ticks` | `12000` | 应用倍率后的总寿命上限 |
| `smoke.sync_range` | `128.0` | 烟雾状态同步距离 |
| `smoke.sync_interval_ticks` | `20` | 周期同步间隔 |
| `smoke.moving_sync_interval_ticks` | `4` | 烟雾弹移动时的位置同步间隔 |
| `smoke.motion_sleep_confirm_ticks` | `5` | 进入运动休眠前所需的低速 tick |
| `smoke.explosion_dispel_enabled` | `true` | 本模组爆炸可驱散重叠烟雾 |

Index 中的 `entity.life_time` 仍然决定不同烟雾弹的基础寿命。倍率和寿命上限主要影响新生成的烟雾弹；形状和范围热重载后会同步到客户端并重建现有烟雾体积。

#### 燃烧云

| 配置项 | 默认值 | 说明 |
|---|---:|---|
| `fire_cloud.effect_interval_ticks` | `10` | 伤害和效果检查间隔 |
| `fire_cloud.max_cells` | `192` | 单个燃烧云最大地形格数量 |
| `fire_cloud.step_up` | `1` | 最大向上爬坡高度 |
| `fire_cloud.drop_down` | `3` | 最大向下地形搜索距离 |
| `fire_cloud.cache_refresh_ticks` | `40` | 地形扩散缓存刷新间隔 |
| `fire_cloud.smoke_extinguish_enabled` | `true` | 烟雾可熄灭允许该行为的燃烧云 |

### 客户端配置

| 配置项 | 默认值 | 说明 |
|---|---:|---|
| `blackFlash` | `false` | 闪光致盲使用黑色遮罩 |
| `explodeScreenShakeMultiplier` | `1.0` | 爆炸屏幕震动倍率；`0` 关闭 |
| `smoke.render_distance` | `128.0` | 烟雾粒子生成距离 |
| `smoke.max_particles_per_tick` | `192` | 全局烟雾粒子预算 |
| `smoke.near_particles_per_tick` | `36` | 近距离单颗烟雾预算 |
| `smoke.medium_particles_per_tick` | `18` | 中距离单颗烟雾预算 |
| `smoke.far_particles_per_tick` | `8` | 远距离单颗烟雾预算 |
| `smoke.min_expansion_ticks` | `24` | 最短扩散时间 |
| `smoke.max_expansion_ticks` | `50` | 最长扩散时间 |
| `smoke.volume_rebuild_interval_ticks` | `3` | 移动烟雾的体积重建间隔 |
| `smoke.max_volume_cells` | `1024` | 单颗烟雾最大体积格数 |
| `smoke.overlap_deduplication` | `true` | 去除多颗烟雾重叠粒子格 |
| `smoke.particle_min_lifetime_ticks` | `38` | 烟雾粒子最短寿命 |
| `smoke.particle_max_lifetime_ticks` | `53` | 烟雾粒子最长寿命 |
| `smoke.particle_fade_in_ticks` | `8` | 淡入时间 |
| `smoke.particle_fade_out_ticks` | `10` | 淡出时间 |
| `fire_cloud.max_particles_per_tick` | `48` | 燃烧云全局粒子预算 |
| `fire_cloud.particle_density` | `0.62` | 燃烧云粒子密度 |
| `fire_cloud.overlap_deduplication` | `true` | 去除燃烧云重叠粒子格 |

## 管理指令

所有管理指令需要 Minecraft 权限等级 `4`，服务器控制台也可使用。只输入 `/lrtactical` 会显示同一份帮助。

| 指令 | 作用 |
|---|---|
| `/lrtactical help` | 显示可点击的指令帮助 |
| `/lrtactical clear all` | 清除全部投掷物、烟雾和燃烧云 |
| `/lrtactical clear throwables` | 清除投掷物实体，包括 C4 和烟雾弹 |
| `/lrtactical clear smoke` | 清除烟雾弹、服务端烟雾状态和客户端现存烟雾粒子 |
| `/lrtactical clear fire_clouds` | 清除全部燃烧云 |
| `/lrtactical reload config` | 热重载全局配置、刷新缓存并同步客户端 |

清理操作直接丢弃实体，不会触发引信、爆炸、烟雾或燃烧效果。

## TACZ HideFlags 兼容

物品支持 TACZ 原生整数型 `HideFlags`，字段位于 NBT 根 `tag` 下：

| 数值 | 作用 |
|:---|---|
| `1` | 隐藏 Lore，并取消 Lore 后的半行间距 |
| `4` | 隐藏主要参数 |
| `8` | 隐藏次要参数和效果 |
| `32` | 隐藏资源包来源 |
| `63` | 隐藏上述全部内容 |

参数分组：

- 近战：`4` 隐藏伤害和攻速；`8` 隐藏移速和攻击范围。
- 投掷物：`4` 隐藏伤害或持续时间；`8` 隐藏范围和效果。
- 消耗品：`4` 隐藏生命及饥饿回复；`8` 隐藏饱和度、使用时间和效果。

Index 可通过 `"hide_tooltip": 12` 设置默认值。常见组合为 `1`、`12`、`44` 和 `63`。

## 构建

```powershell
# 公开核心 JAR，不包含官方默认美术内容
.\gradlew.bat build

# 生成可放入 .minecraft/tacz/ 的本地官方资源包
.\gradlew.bat buildOfficialPack

# 生成内嵌本地资源包并支持首次启动自动释放的完整 JAR
.\gradlew.bat buildBundled

# 将本地资源包安装到开发运行目录
.\gradlew.bat installOfficialPackForRuns
```

产物位置：

- 核心及本地完整 JAR：`build/libs/`
- 外置资源包：`build/packs/lrtactical-official-resources-<版本>.zip`

本地官方资源位于被 Git 忽略的 `src/main/resources/assets/lrtactical/`。缺少该目录时核心构建仍可完成，但资源包和 bundled 构建会明确失败。

## 许可证

- 代码：[GNU GPL 3.0](https://www.gnu.org/licenses/gpl-3.0.txt)
- 原作者美术资源：All Rights Reserved
