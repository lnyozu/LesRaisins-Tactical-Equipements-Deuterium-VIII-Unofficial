# LesRaisins Tactical Equipments Unofficial

[LesRaisins Tactical Equipments](https://github.com/LesRaisins-Studios/LesRaisins-Tactical-Equipements)的非官方分支版本，为[Timeless and Classics Zero (TACZ)](https://github.com/MCModderAnchor/TACZ)扩展近战武器、投掷物和消耗品。

> 中文 · [English](README_EN.md)

## 分支改动

### Bug修复

1. 修复检视音效重复播放：反复按下检视键时音效不再叠加，切换物品后自动停止残留音效。
2. 修复投掷物实体无最大存在寿命：超过配置时间后强制清理投掷物实体，默认1800秒（30分钟）。

### 优化调整

1. 优化Tooltip显示：Lore移至物品名称下方，Lore与属性间加入半行间距，属性排版对齐TACZ原版风格，完全兼容TACZ HideFlags。
2. 附魔效果可靠配置：附魔战斗效果、铁砧附魔、附魔台附魔三项独立开关，新增背刺附魔，铁砧合成保护。
3. 兼容所有LesRaisins官方兼容的近战武器包。
4. 新增烟雾弹服务端同步、爆炸视觉效果网络包、烟雾内玩家名牌隐藏。
5. 自带战术道具资源首次启动时自动解压至`tacz/default_melee/`目录，方便用户调整配置。
6. 支持在Index中通过`tooltip`字段自定义Lore文案，兼容翻译键和HEX颜色。

## 配置文件

通用配置(`config/lrtactical.toml`)：

```toml
[grenade]
grenadeExplosionBlockDamage = false

[melee]
meleeItemConsumeDurability = false
meleeIgnoreInvulnerableTickThreshold = 20
meleeEnchantmentEffectsEnabled = false
meleeAnvilEnchantingEnabled = false
meleeEnchantingTableEnabled = false
```

服务端世界配置(`<world>/serverconfig/lrtactical-server.toml`)：

```toml
[throwable]
throwable_force_cleanup_time_seconds = 1800
```

客户端配置(`config/lrtactical-client.toml`)：

```toml
blackFlash = false
explodeScreenShakeMultiplier = 1.0
```

三项附魔开关彼此独立：附魔效果控制已有附魔是否参与战斗；铁砧与附魔台开关只控制对应的附魔获取方式。投掷物强制清扫时间不会触发引信、爆炸或烟雾结束效果，默认1800秒（30分钟）。

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
