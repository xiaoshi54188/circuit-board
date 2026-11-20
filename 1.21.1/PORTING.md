# 1.21.1 移植记录

这个目录是一个**独立的 1.21.1 工程**（Minecraft 1.21.1 + NeoForge 21.1.252 + Java 21），
源码是从 26.2 那一份（`../src`）搬过来的，已经按 1.21.1 的真实依赖改到能编译。

编译/检查都在这个目录下跑（mc_check / mc_build 指向 `port1211`），产物在
`build/libs/circuit_board-*.jar`，交付副本放 `../dist/`。

## 两个版本之间改了什么

| 26.2 的写法 | 1.21.1 的写法 |
| --- | --- |
| `net.minecraft.resources.Identifier` | `ResourceLocation` |
| `ValueInput` / `ValueOutput` | `CompoundTag` + `HolderLookup.Provider` |
| `SavedDataType` + `Codec` | `SavedData.Factory` + `save(CompoundTag, Provider)` |
| `BlockEntityRenderState` + `extractRenderState` + `submit` | 直接 `render(be, partialTick, pose, buffer, light, overlay)` |
| `SubmitNodeCollector.submitCustomGeometry` | `buffer.getBuffer(RenderType)` 直接写顶点 |
| `renderertype.RenderType(s)`、`LightCoordsUtil` | `RenderType.entitySolid(...)`、`LightTexture` |
| `GuiGraphicsExtractor` + `text/centeredText/textWithWordWrap/item` | `GuiGraphics` + `drawString/drawCenteredString/drawWordWrap/renderItem` |
| `extractRenderState` 画界面 | `render` 画界面 |
| `MouseButtonEvent` | `mouseClicked(double, double, int)` 等 |
| `ClientPacketDistributor` | `PacketDistributor.sendToServer` |
| `useItemOn` 返回 `InteractionResult` | 返回 `ItemInteractionResult`（`net.minecraft.world` 包） |
| `neighborChanged(..., Orientation, boolean)` | `neighborChanged(..., BlockPos, boolean)` |
| `affectNeighborsAfterRemoval` | `onRemove(BlockState, Level, BlockPos, BlockState, boolean)` |
| `registerBlock/registerItem` 第三参是 `UnaryOperator` | 第三参是 `Properties` 本体；`BlockEntityType` 要三个参数 |
| `TooltipDisplay` + `Consumer<Component>` | `List<Component>` |
| `assets/<ns>/items/*.json` | 删掉，物品模型走 `assets/<ns>/models/item/*.json` |
| `data/<ns>/recipe`、`loot_table` | `data/<ns>/recipes`、`loot_tables` |
| 配方原料 `"G": "minecraft:x"` | `"G": {"item": "minecraft:x"}` |
| `pack_format` 88 | 34（资源包），数据包 48 |

## 渲染上的一个注意点

1.21.1 是「取完数据当场就画」，所以 `BoardRenderer` 里那个复用的 `BoardRenderState`
是渲染器自己的字段（渲染只在渲染线程跑），每帧重算，不能跨方块共用。
电线 / 飞线只在 `BoardPart.NW` 那一块算一次，但**四块都要画**（针脚、铜边、零件各画自己那份）。
