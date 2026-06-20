# LesRaisins Tactical Equipments Unofficial

[LesRaisins Tactical Equipments](https://github.com/LesRaisins-Studios/LesRaisins-Tactical-Equipements)的非官方分支版本，为[Timeless and Classics Zero (TACZ)](https://github.com/MCModderAnchor/TACZ)扩展近战武器、投掷物和消耗品。

> 中文 · [English](README_EN.md)

## 分支改动

### Bug修复

1. 修复检视音效重复播放：反复按下检视键时音效不再叠加，切换物品后自动停止残留音效。
2. 修复投掷物实体无最大存在寿命：超过配置时间后强制清理投掷物实体，默认1800秒（30分钟）。

### 优化调整

1. 优化Tooltip显示：Lore移至物品名称下方，Lore与属性间加入半行间距，属性排版对齐TACZ原版风格，完全兼容TACZ HideFlags。
2. 附魔效果可靠配置：附魔战斗效果、铁砧附魔、附魔台附魔三项独立开关，铁砧合成保护。
3. 兼容所有LesRaisins官方兼容的近战武器包。
4. 新增烟雾弹服务端同步、爆炸视觉效果网络包、烟雾内玩家名牌隐藏。
5. 支持在Index中通过`tooltip`字段自定义Lore文案，兼容翻译键和HEX颜色。

## 资源包与构建方式

> 本仓库及公开核心JAR不包含原作者All Rights Reserved的模型、贴图、动画或音效。相关资源仅保留在开发者本地；构建脚本只提供可选的本地打包能力。

代码核心与原作者All Rights Reserved美术资源已分离。默认内容的`assets/lrtactical`与`data/lrtactical`不会进入公开核心JAR。

```powershell
# 公开核心JAR，不包含官方默认内容
.\gradlew.bat build

# 单独生成可放入.minecraft/tacz/的官方资源包
.\gradlew.bat buildOfficialPack

# 生成内嵌官方资源包、首次启动自动释放的本地完整JAR
.\gradlew.bat buildBundled

# 将官方资源包安装到开发运行目录
.\gradlew.bat installOfficialPackForRuns
```

产物位置：

- 核心及完整JAR：`build/libs/`
- 外置官方资源包：`build/packs/lrtactical-official-resources-<版本>.zip`
- 完整JAR自动释放位置：`.minecraft/tacz/lrtactical_official_resources/`

完整JAR会把内嵌包展开为TACZ原生文件夹资源包，不会覆盖用户手动安装的同名资源包，也不会覆盖已经修改过的自动释放目录。只有核心JAR且没有任何适配资源包时，模组仍可正常启动：三个创造分类使用雪球、金苹果和下界合金剑作为安全图标，默认内容列表为空。

本地官方资源保存在被Git忽略的`src/main/resources/assets/lrtactical/`中。缺少该目录时，核心构建仍可正常完成，但`buildOfficialPack`和`buildBundled`会给出明确错误。

`build/packs/`中的资源包与带`-bundled`后缀的JAR可能包含本地ARR美术资源；在未获得原作者授权时，请勿将这些本地产物上传或公开分发。公开发布应使用不带`-bundled`后缀的核心JAR。

## 配置文件

通用配置(`config/lrtactical.toml`)：

```toml
[grenade]
# 爆炸是否破坏方块，默认false
grenadeExplosionBlockDamage = false

[melee]
# 近战攻击是否消耗耐久，默认false
meleeItemConsumeDurability = false
# 目标受伤无敌tick低于此值时忽略无敌帧，默认20
meleeIgnoreInvulnerableTickThreshold = 20
# 附魔战斗效果是否生效（锋利、亡灵杀手等），默认false
meleeEnchantmentEffectsEnabled = false
# 铁砧附魔是否可用，默认false
meleeAnvilEnchantingEnabled = false
# 附魔台是否可用，默认false
meleeEnchantingTableEnabled = false
```

服务端世界配置(`<world>/serverconfig/lrtactical-server.toml`)：

```toml
[throwable]
# 投掷物实体最大存活时间（秒），超时强制清除，不触发爆炸/引信/烟雾效果，默认1800
throwable_force_cleanup_time_seconds = 1800
```

客户端配置(`config/lrtactical-client.toml`)：

```toml
# 闪光弹致盲时使用黑色遮罩（默认白色），默认false
blackFlash = false
# 爆炸屏幕震动倍率，0.0关闭震动，默认1.0
explodeScreenShakeMultiplier = 1.0
```

> 三项附魔开关彼此独立：附魔效果控制已有附魔是否参与战斗；铁砧与附魔台开关只控制对应的附魔获取方式。

## TACZ HideFlags兼容

物品支持TACZ原生的整数型`HideFlags`（NBT根`tag`下，`Int`类型）：

| 数值 | 作用 |
|---:|---|
| `1` | 隐藏Lore描述，并取消Lore后的半行间距 |
| `4` | 隐藏主要参数 |
| `8` | 隐藏次要参数及效果 |
| `32` | 隐藏资源包来源 |
| `63` | 隐藏上述所有受支持内容 |

参数分组：

- 近战：`4`隐藏伤害和攻速；`8`隐藏移速和攻击范围。
- 投掷物：`4`隐藏伤害或持续时间；`8`隐藏范围和效果。
- 消耗品：`4`隐藏生命及饥饿回复；`8`隐藏饱和度、使用时间和效果。

Index中可通过`"hide_tooltip": 12`设置默认HideFlags。常用组合：`1`（仅隐藏Lore）、`12`（隐藏所有数值参数）、`44`（隐藏所有参数和包来源）、`63`（隐藏全部）。

## 许可证

- 代码：[GNU GPL 3.0](https://www.gnu.org/licenses/gpl-3.0.txt)
- 美术资源：原作者All Rights Reserved
