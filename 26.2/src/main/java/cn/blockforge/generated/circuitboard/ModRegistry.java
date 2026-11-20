package cn.blockforge.generated.circuitboard;

import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 所有注册项：方块、物品、方块实体、创造模式页签。 */
public final class ModRegistry {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(GeneratedMod.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GeneratedMod.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, GeneratedMod.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, GeneratedMod.MOD_ID);

    public static final DeferredBlock<CircuitBoardBlock> CIRCUIT_BOARD = BLOCKS.registerBlock(
            "circuit_board",
            CircuitBoardBlock::new,
            props -> props.strength(0.6F).sound(SoundType.WOOD)
                    .noOcclusion().pushReaction(PushReaction.DESTROY));

    public static final DeferredItem<BoardItem> CIRCUIT_BOARD_ITEM =
            ITEMS.registerItem("circuit_board", props -> new BoardItem(CIRCUIT_BOARD.get(), props));

    /** 电容：插进电路板里的元件，单向通过、断电后还能放一小会儿电。 */
    public static final DeferredItem<Item> CAPACITOR =
            ITEMS.registerItem("capacitor", props -> new CircuitPartItem(props, "item.circuit_board.capacitor.desc"));

    /** 二极管：只让电流朝一个方向走。 */
    public static final DeferredItem<Item> DIODE =
            ITEMS.registerItem("diode", props -> new CircuitPartItem(props, "item.circuit_board.diode.desc"));

    /** 三极管：用一路电控制另一路电的小开关。 */
    public static final DeferredItem<Item> TRANSISTOR =
            ITEMS.registerItem("transistor", props -> new CircuitPartItem(props, "item.circuit_board.transistor.desc"));

    /** 电阻：只有两个引脚，必须直着插在线上。 */
    public static final DeferredItem<Item> RESISTOR =
            ITEMS.registerItem("resistor", props -> new CircuitPartItem(props, "item.circuit_board.resistor.desc"));

    /** 开关：自己就是电源，右键切换开 / 关。 */
    public static final DeferredItem<Item> SWITCH =
            ITEMS.registerItem("switch", props -> new CircuitPartItem(props, "item.circuit_board.switch.desc"));

    /** 与门：两侧都有电才导通。 */
    public static final DeferredItem<Item> AND_GATE =
            ITEMS.registerItem("and_gate", props -> new CircuitPartItem(props, "item.circuit_board.and_gate.desc"));

    /** 或门：两侧任意一边有电就导通。 */
    public static final DeferredItem<Item> OR_GATE =
            ITEMS.registerItem("or_gate", props -> new CircuitPartItem(props, "item.circuit_board.or_gate.desc"));

    /** 延迟器：电流进来后等一会儿再输出。 */
    public static final DeferredItem<Item> DELAY =
            ITEMS.registerItem("delay", props -> new CircuitPartItem(props, "item.circuit_board.delay.desc"));

    /**
     * 跳线：拿在手里、在世界里点两下就能把两个红石点远距离接成一根线的小工具。
     * 板子里面负责「两条线交叉不短路」的零件是电阻，跳线不干这个。
     */
    public static final DeferredItem<Item> JUMPER =
            ITEMS.registerItem("jumper", props -> new JumperItem(props.stacksTo(64),
                    "item.circuit_board.jumper"));

    /**
     * 飞线：电路板面板里的「点两格把两格接起来」的线；拿在手里也能当跳线用，两头双向通电。
     * 和跳线是同一个用法，只是名字和用法场景（面板里）不一样。
     */
    public static final DeferredItem<Item> FLY_WIRE =
            ITEMS.registerItem("fly_wire", props -> new JumperItem(props.stacksTo(64),
                    "item.circuit_board.fly_wire"));

    /** 与非门：两个输入都通电时反而断开。 */
    public static final DeferredItem<Item> NAND_GATE =
            ITEMS.registerItem("nand_gate", props -> new CircuitPartItem(props, "item.circuit_board.nand_gate.desc"));

    /** 或非门：两个输入都没电时才输出。 */
    public static final DeferredItem<Item> NOR_GATE =
            ITEMS.registerItem("nor_gate", props -> new CircuitPartItem(props, "item.circuit_board.nor_gate.desc"));

    /** 异或门：两个输入不一样（一个通一个断）时输出。 */
    public static final DeferredItem<Item> XOR_GATE =
            ITEMS.registerItem("xor_gate", props -> new CircuitPartItem(props, "item.circuit_board.xor_gate.desc"));

    /** 同或门：两个输入一样时输出。 */
    public static final DeferredItem<Item> XNOR_GATE =
            ITEMS.registerItem("xnor_gate", props -> new CircuitPartItem(props, "item.circuit_board.xnor_gate.desc"));

    /** 非门：箭头那一面一直有电，背后一有电就断（反相）。 */
    public static final DeferredItem<Item> NOT_GATE =
            ITEMS.registerItem("not_gate", props -> new CircuitPartItem(props, "item.circuit_board.not_gate.desc"));

    /** 计时器：收到信号才开始打钟，信号一断就停。 */
    public static final DeferredItem<Item> TIMER =
            ITEMS.registerItem("timer", props -> new CircuitPartItem(props, "item.circuit_board.timer.desc"));

    /** 电线：用来把针脚和别的东西接起来。 */
    public static final DeferredItem<Item> COPPER_WIRE =
            ITEMS.registerItem("copper_wire", CopperWireItem::new, props -> props.stacksTo(64));

    /** 复制电路板：蹲下右键一块板子，复制出一份「N号电路板复制品M」。 */
    public static final DeferredItem<Item> COPY_BOARD =
            ITEMS.registerItem("copy_board", CopyBoardItem::new, props -> props.stacksTo(1));

    public static final Supplier<BlockEntityType<CircuitBoardBlockEntity>> BOARD_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("circuit_board", () -> new BlockEntityType<>(
                    CircuitBoardBlockEntity::new, Set.of(CIRCUIT_BOARD.get())));

    /**
     * 接线端子：接电线的时候自动装在目标方块旁边，负责把板子的电真正送进那个方块。
     * 没有对应的物品，玩家不用管它。
     */
    public static final DeferredBlock<WireTerminalBlock> WIRE_TERMINAL = BLOCKS.registerBlock(
            "wire_terminal",
            WireTerminalBlock::new,
            props -> props.strength(0.1F).sound(SoundType.COPPER).noOcclusion().noCollision()
                    .noLootTable().pushReaction(PushReaction.DESTROY));

    public static final Supplier<BlockEntityType<WireTerminalBlockEntity>> TERMINAL_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("wire_terminal", () -> new BlockEntityType<>(
                    WireTerminalBlockEntity::new, Set.of(WIRE_TERMINAL.get())));

    public static final Supplier<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.circuit_board.main"))
            .icon(() -> new ItemStack(CIRCUIT_BOARD_ITEM.get()))
            .displayItems((parameters, output) -> {
                output.accept(CIRCUIT_BOARD_ITEM.get());
                output.accept(COPY_BOARD.get());
                output.accept(COPPER_WIRE.get());
                output.accept(CAPACITOR.get());
                output.accept(DIODE.get());
                output.accept(TRANSISTOR.get());
                output.accept(NOT_GATE.get());
                output.accept(TIMER.get());
                output.accept(RESISTOR.get());
                output.accept(SWITCH.get());
                output.accept(AND_GATE.get());
                output.accept(OR_GATE.get());
                output.accept(DELAY.get());
                output.accept(NAND_GATE.get());
                output.accept(NOR_GATE.get());
                output.accept(XOR_GATE.get());
                output.accept(XNOR_GATE.get());
                output.accept(JUMPER.get());
                output.accept(FLY_WIRE.get());
            })
            .build());

    private ModRegistry() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        TABS.register(modBus);
        modBus.addListener(ModRegistry::addToVanillaTabs);
    }

    /**
     * 除了自建页签，再把物品塞进原版的「红石」「功能方块」「材料」页签。
     *
     * <p>自建页签在创造物品栏里排到最后，新手常常翻不到；放进原版页签就一定能看见。</p>
     */
    private static void addToVanillaTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(CIRCUIT_BOARD_ITEM.toStack());
            event.accept(COPY_BOARD.get());
            event.accept(COPPER_WIRE.get());
            event.accept(CAPACITOR.get());
            event.accept(DIODE.get());
            event.accept(TRANSISTOR.get());
            event.accept(NOT_GATE.get());
            event.accept(TIMER.get());
            event.accept(RESISTOR.get());
            event.accept(SWITCH.get());
            event.accept(AND_GATE.get());
            event.accept(OR_GATE.get());
            event.accept(DELAY.get());
            event.accept(NAND_GATE.get());
            event.accept(NOR_GATE.get());
            event.accept(XOR_GATE.get());
            event.accept(XNOR_GATE.get());
            event.accept(JUMPER.get());
            event.accept(FLY_WIRE.get());
        } else if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(CIRCUIT_BOARD_ITEM.toStack());
        } else if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(COPY_BOARD.get());
            event.accept(COPPER_WIRE.get());
            event.accept(CAPACITOR.get());
            event.accept(DIODE.get());
            event.accept(TRANSISTOR.get());
            event.accept(NOT_GATE.get());
            event.accept(TIMER.get());
            event.accept(RESISTOR.get());
            event.accept(SWITCH.get());
            event.accept(AND_GATE.get());
            event.accept(OR_GATE.get());
            event.accept(DELAY.get());
            event.accept(NAND_GATE.get());
            event.accept(NOR_GATE.get());
            event.accept(XOR_GATE.get());
            event.accept(XNOR_GATE.get());
            event.accept(JUMPER.get());
            event.accept(FLY_WIRE.get());
        }
    }
}
