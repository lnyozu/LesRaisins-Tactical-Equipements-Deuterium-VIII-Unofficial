<p align="center">
    <img width="300" src="https://s2.loli.net/2024/04/30/NJrstR1QzpoLyIT.png" alt="LesRaisins Tactical Equipments">
</p>

<h1 align="center">LesRaisins Tactical Equipments</h1>

<p align="center">
    A tactical equipment add-on for Timeless and Classics Zero (TACZ).
</p>

<p align="center">
    中文 · <a href="README_EN.md">English</a>
</p>

---

# 中文说明

LesRaisins Tactical Equipments 是 [Timeless and Classics Zero（TACZ）](https://github.com/MCModderAnchor/TACZ) 的扩展模组，为 TACZ 枪包体系增加可配置的近战武器、投掷物和消耗品。

## 环境要求

- Minecraft 1.20.1
- Forge 47+
- TACZ 1.1.5+
- 模组需要同时安装在客户端与服务端

## 主要功能

### 近战武器

- 支持枪包式 Index、Display、模型、动画、贴图、音效和第三人称动画配置。
- 支持左键、右键攻击、连击、攻击延迟、冷却、击退及自定义判定范围。
- Tooltip 自动显示伤害、攻速、移速加成和攻击范围。
- `generic.attack_damage` 直接作为武器最终基础伤害，不再额外叠加玩家实体自带的 1 点伤害。
- 默认配置下，命中目标不会消耗近战武器耐久。

### 投掷物

- 支持爆炸物、闪光弹、烟雾弹、燃烧物、效果云和遥控起爆等类型。
- 支持投掷速度、温雷、反弹、生命周期、冷却、堆叠数量及屏幕震动。
- Tooltip 根据类型自动显示伤害、持续时间、作用范围和状态效果。

### 消耗品

- 支持治疗、饥饿回复、饱和度、状态效果、负面效果清除、冷却及耐久式使用次数。
- 支持长按使用与切换式使用。
- Tooltip 自动显示生命回复、饥饿回复、饱和度、使用时间和状态效果。

## 默认资源包

首次启动时，模组会把内置资源提取到：

```text
.minecraft/tacz/default_melee/
```

如果该目录已经存在 `gunpack.meta.json`，模组不会覆盖其中的文件。更新模组后若需要重新生成最新默认资源，请先备份并删除 `default_melee` 目录，然后重新启动游戏。

## 枪包目录结构

文件夹和 ZIP 两种形式均受 TACZ 支持，服务端与客户端不要求使用相同的封装形式，但资源内容和命名空间必须一致。

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

`gunpack.meta.json` 示例：

```json
{
  "namespace": "example"
}
```

ZIP 根目录必须直接包含 `gunpack.meta.json`、`assets` 和 `data`，不能额外套一层文件夹。建议使用 UTF-8 兼容的 ZIP 工具，并避免无用的中文或特殊字符文件名。

## Tooltip 与 Lore

Index 使用与 TACZ 一致的 `tooltip` 字段：

```json
{
  "name": "item.example.weapon",
  "tooltip": "tooltip.example.weapon"
}
```

也支持数组，每个元素对应一行：

```json
{
  "tooltip": [
    "tooltip.example.weapon.line_1",
    "tooltip.example.weapon.line_2"
  ]
}
```

未配置、设为 `null`、空字符串或空数组时，不生成描述。

### 语言键

不包含 HEX 标记的内容会按语言键解析：

```json
"tooltip": "tooltip.example.weapon"
```

语言文件：

```json
{
  "tooltip.example.weapon": "A reliable melee weapon."
}
```

如果语言键不存在，Minecraft 会直接显示原字符串。

### HEX 纯色与渐变

直接文本可以使用 `&#RRGGBB`：

```json
"tooltip": "&#55ffffCyan text"
```

每次颜色标记会作用到下一个颜色标记之前的文本，因此可以逐字制作渐变：

```json
"tooltip": "&#5118f9h&#5521f8e&#592bf7l&#5e34f6l&#623df5o &#6647f4w&#6a50f3o&#6f59f2r&#7363f1l&#776cf0d"
```

语言键模式与 HEX 直接文本模式是两种独立模式：带 HEX 标记时按直接文本渲染，不再将整行作为语言键。

### 写入物品 NBT

Index 中的 `tooltip` 会在创建物品时写入：

```text
tag.display.Lore
```

因此可以使用 NBT 编辑器直接修改或删除文案。Lore 会显示在物品名称下方，并与自动参数之间保留约 4 px 的间距。

服务器重载 Index 不会覆盖已有物品中的 `display.Name`、`display.Lore` 或 `HideFlags`。Index 中的新 Tooltip 只会写入之后新生成的物品。

## TACZ HideFlags 兼容

物品支持 TACZ 原生的整数型 `HideFlags`：

```nbt
HideFlags:12
```

在 NBT 编辑器中，字段必须添加在物品根 `tag` 下，并选择 `Int` 类型，而不是字符串。

| 数值 | 当前作用 |
|---:|---|
| `1` | 隐藏 Lore 描述，并取消 Lore 后的半行间距 |
| `2` | TACZ 弹药信息位，本模组暂未使用 |
| `4` | 隐藏主要参数 |
| `8` | 隐藏次要参数及效果 |
| `16` | TACZ 改装提示位，本模组暂未使用 |
| `32` | 隐藏资源包来源 |
| `63` | 隐藏上述所有受支持内容 |

参数分组：

- 近战：`4` 隐藏伤害和攻速；`8` 隐藏移速和攻击范围。
- 投掷物：`4` 隐藏伤害或持续时间；`8` 隐藏范围和效果。
- 消耗品：`4` 隐藏生命及饥饿回复；`8` 隐藏饱和度、使用时间和效果。

创造物品栏分类行不属于 TACZ 的六个 Tooltip 区块，因此当前不会被 `HideFlags:63` 隐藏。

### 在 Index 中设置 HideFlags

Index 可使用：

```json
{
  "hide_tooltip": 12
}
```

创建物品时会自动写入：

```nbt
HideFlags:12
```

未配置 `hide_tooltip` 时不会写入该 NBT。修改 Index 只影响新生成物品，不覆盖已有物品的 HideFlags。

常用组合：

| HideFlags | 效果 |
|---:|---|
| `1` | 仅隐藏 Lore |
| `12` | 隐藏所有自动数值参数 |
| `32` | 仅隐藏资源包来源 |
| `44` | 隐藏所有自动参数和资源包来源 |
| `63` | 隐藏全部受支持的 Tooltip 区块 |

## 服务器同步

服务器会向客户端同步以下 Index 数据：

- 近战武器
- 投掷物
- 消耗品

执行 TACZ 资源重载后，创造物品栏缓存会重新构建，新生成物品会使用服务器当前 Index。

模型、贴图、Display、动画、Lua 脚本和音效不会由服务器同步，客户端必须本地安装对应资源包。若服务器存在某个 Index，而客户端没有成功加载对应显示资源，物品会显示为黑紫缺失材质方格。

已有物品通过 ID 继续读取最新战斗参数，但其已经写入 NBT 的名称、Lore 和 HideFlags 不会被重载覆盖。

## ZIP 故障排查

若文件夹形式正常、ZIP 形式显示黑紫方格，请检查客户端日志中的 `GunPackFinder`。

正常日志：

```text
DeltaForce_Melee_Pack_0.2.0.zip, Main namespace: delta_wt
```

常见错误：

```text
invalid CEN header (bad entry name)
```

该错误通常表示 ZIP 文件名编码不兼容。建议：

1. 删除未被配置引用的备份文件。
2. 避免文件名中的中文、反斜杠或特殊字符。
3. 使用 7-Zip 或 PowerShell `Compress-Archive` 重新创建 ZIP。
4. 确认 `gunpack.meta.json` 位于 ZIP 根目录。
5. 确保所有资源路径使用小写；ZIP 内路径通常区分大小写。

## 配置

通用配置：

```toml
[grenade]
grenadeExplosionBlockDamage = true

[melee]
meleeItemConsumeDurability = false
meleeIgnoreInvulnerableTickThreshold = 20
```

客户端配置：

```toml
blackFlash = false
explodeScreenShakeMultiplier = 1.0
```

旧版本已经生成的配置文件不会自动修改默认值。如果希望近战不消耗耐久，请确认：

```toml
meleeItemConsumeDurability = false
```

## 开发构建

Windows：

```powershell
.\gradlew.bat build
```

构建产物位于：

```text
build/libs/
```

## 作者

- Programmer: `xjqsh`
- Artist: `LeComte`
- LesRaisins Studio

## 致谢

- 感谢 MrCrayfish 在模组制作方面提供的经验与参考。
- 本模组的耳鸣及致盲效果实现参考了 MrCrayfish's Gun Mod。
- 感谢所有参与测试、反馈与协助的玩家。

## 许可证

- 代码：[GNU GPL 3.0](https://www.gnu.org/licenses/gpl-3.0.txt)
- 美术资源：All Rights Reserved

