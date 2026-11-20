# 红石电路板 · Circuit Board

把一整套红石逻辑电路做进**一块 2×2 方块大的电路板**里：在板子上画线路、插零件，板边会自动长出针脚，
再用电线 / 跳线把针脚接到别的电路板、红石灯、门、活塞、红石粉上。

一个仓库里放了两个**互相独立**的工程，源码逻辑相同，只是照各自游戏版本的 API 改写过，可以单独编译。

| 目录 | 对应游戏 | 加载器 / 构建插件 | JDK |
| --- | --- | --- | --- |
| [`26.2/`](26.2) | Minecraft 26.2（快照） | NeoForge 26.2.0.76 / ModDev 2.0.146 | 25 |
| [`1.21.1/`](1.21.1) | Minecraft 1.21.1 | NeoForge 21.1.252 / ModDev 2.0.148 | 21 |

> 同一个世界里不要两个都装。

## 这个模组做什么

- **电路板**：占 2×2 个方块。点地面就平着放，点墙就竖着贴上去（板子厚度永远贴着被点的那一面）。
- **面板**：空手右键板子打开绿色面板。左键拖动画线路，右键擦掉；**线路画到板边，就会在那一面长出一根针脚**。
- **零件（14 种）**：电容、二极管、三极管、非门、电阻、开关、与门、或门、与非门、或非门、异或门、同或门、延迟器、计时器。
  面板里每种都有一句提示说清它怎么导通。
- **针脚**：既能输出也能输入，板子自己判断方向。
- **接线**：拿电线右键板子 → 打开「选针脚」面板，挑一根针脚，关掉面板后再去右键目标方块（32 格内）。
- **接线端子**：电线接到普通方块上时，会自动在旁边装一个小铜疙瘩。端子有电就朝四周发红石信号 15，
  红石灯、门、活塞、红石粉都正常响应。
- **一根针脚 / 一个端子可以挂很多根线**，每根有编号；同一端子上每根线**颜色都不一样**
  （配色故意避开红色，红色专门用来标「点了要删的这根线」），面板里编号的底色就是那根线的颜色。
- **跳线 / 飞线**：拿跳线右键第一个方块、再右键第二个，两者之间拉一根双向通电的铜线；
  飞线在板子面板里点两格，跨过中间直接连。
- **复制电路板**：蹲下右键一块画好的板子，复制一份（叫「N号电路板复制品1、2、3…」），放下去线路一模一样。
- **拆线**：空手右键端子打开面板，左键点一根线先变红，再点一次才删掉，右键取消。

配方都在 `src/main/resources/data/circuit_board/{recipe,recipes}/` 下，比如电路板 = 四块沙子围一个金粒。

## 安装

1. 装对应版本的 Minecraft 与 NeoForge。
2. 把构建出的 `circuit_board-*.jar` 放进 `.minecraft/mods/`。
3. 启动游戏。

成品也可以在仓库的 [Releases](../../releases) 里直接下载。

## 从源码构建

先进入对应版本的目录，两个工程各自构建：

```bash
cd 26.2        # 或 cd 1.21.1
./gradlew build
```

产物在 `build/libs/`。开一个带模组的测试客户端：

```bash
./gradlew runClient
```

- 首次构建**必须联网**，ModDev 插件会去官方源下载对应版本的 Minecraft / NeoForge，耗时较长属正常。
- `26.2/` 需要 JDK 25，`1.21.1/` 需要 JDK 21。`gradle.properties` 里已经写死了 toolchain 版本。
- 仓库自带 `gradlew` 和 `gradle/wrapper/`，不用另外装 Gradle。

## 目录结构

```
circuit-board/
├── 1.21.1/                  Minecraft 1.21.1 工程（JDK 21）
│   ├── PORTING.md           从 26.2 移植到 1.21.1 的逐条改动记录
│   ├── build.gradle / settings.gradle / gradle.properties
│   └── src/main/
│       ├── java/cn/blockforge/generated/circuitboard/   逻辑 + client/ 渲染与界面
│       └── resources/
│           ├── META-INF/neoforge.mods.toml
│           ├── assets/circuit_board/    贴图、模型、方块状态、语言文件
│           └── data/circuit_board/      recipes/、loot_tables/（复数）
└── 26.2/                    Minecraft 26.2 工程（JDK 25）
    └── src/main/
        ├── java/cn/blockforge/generated/circuitboard/
        └── resources/
            ├── assets/circuit_board/    items/ 下是物品模型定义，另有 models/item/
            └── data/circuit_board/      recipe/、loot_table/（单数）
```

主要类：

| 类 | 作用 |
| --- | --- |
| `GeneratedMod` | 模组入口（注册 + 网络初始化） |
| `ModRegistry` | 方块 / 物品 / 方块实体 / 创造栏注册 |
| `ModNetwork` | 网络包注册 |
| `CircuitBoardBlock` | 电路板方块（2×2 一块，平放 / 贴墙） |
| `CircuitBoardBlockEntity` | 电路板的数据与逻辑（线路、零件、针脚、传播） |
| `WireTerminalBlock(Entity)` | 接线端子方块 |
| `BoardData` / `BoardDesign` / `BoardPart` / `BoardNumbers` | 板子的存档数据、板面设计、2×2 分块、板号计数 |
| `CableColors` | 电线配色（10 种，绕开红色） |
| `*_Payload` | 客户端 ↔ 服务端的数据包 |
| `CopperWireItem` / `JumperItem` / `CircuitPartItem` / `CopyBoardItem` / `BoardItem` | 物品 |
| `client/` | 只在客户端跑的：渲染器、两个界面、高亮、几何生成 |

### 两个版本的资源格式差异

| 26.2 | 1.21.1 |
| --- | --- |
| `assets/<ns>/items/*.json` 物品模型定义 | 没有这一层，直接 `models/item/*.json` |
| `data/<ns>/recipe`、`loot_table`（单数） | `data/<ns>/recipes`、`loot_tables`（复数） |
| 配方原料写成 `"G": "minecraft:gold_nugget"` | 写成 `"G": {"item": "minecraft:gold_nugget"}` |
| `pack_format` 88 | 34（资源包）/ 48（数据包） |

## 已知限制

- `1.21.1/` 只针对 1.21.1 + NeoForge 21.1.x；1.21.2 以上的 API 又变了，不能直接装。
- `26.2/` 对应的 26.2 还是快照版本，NeoForge 与官方映射仍在变动，只保证和 **26.2.0.76** 一起能编过。
- 界面和渲染相关的问题只有客户端能测出来（自动构建只跑了服务端启动测试）。

## 反馈

- 崩溃或报错 → [Crash 报告](../../issues/new?template=crash_report.yml)，附 `crash-reports/` 里的日志。
- 功能异常 → [Bug 报告](../../issues/new?template=bug_report.yml)。
- 想法与建议 → [功能建议](../../issues/new?template=suggestion.yml)。

## 许可证

[MIT](LICENSE)。Minecraft 与 NeoForge 分别属于 Mojang Studios 和 NeoForged 团队。
