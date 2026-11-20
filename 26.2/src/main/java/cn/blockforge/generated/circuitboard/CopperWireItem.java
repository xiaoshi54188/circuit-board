package cn.blockforge.generated.circuitboard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 电线：把电路板的针脚接到别的地方，也能把两个红石方块远距离接起来。
 *
 * <p>三种用法：</p>
 * <ol>
 *     <li>拿着电线右键电路板 -&gt; 打开「选针脚」界面，点一根针脚，再去右键一个方块：板子那根针脚就能给那个方块送电；</li>
 *     <li>什么都没选，直接右键一个红石方块（红石灯、红石粉、中继器……）当电源那头，再右键另一个方块：
 *         电线从第一个方块单向送电到第二个（两头最远各 32 格）；</li>
 *     <li>右键已经接好的那一头，就把这根线拆掉。</li>
 * </ol>
 *
 * <p>接线的时候会在方块旁边装一个「接线端子」：端子在板子针脚 / 电源那头有电的时候朝四周发红石信号 15，
 * 所以红石灯会亮、门会开、红石粉也会被点亮。想一次连两头、两头都能当电源，用「跳线」。</p>
 *
 * <p>门 / 栅栏门 / 活板门平时右键是「开门」，会把这一下抢走。拿着电线点它们的时候由电线先接手，
 * 所以<b>六个面都能接线</b>；想开关门就空手右键，两件事互不打架。</p>
 */
public class CopperWireItem extends Item {
    /** 界面里点针脚要做的事：选/连、拆掉这根针脚上的某一根线、取消。 */
    public static final int ACTION_CHOOSE = 0;
    public static final int ACTION_REMOVE = 1;
    public static final int ACTION_CANCEL = 2;

    /**
     * 每个玩家手上「已经点好的那一端」。
     * pin &gt;= 0 表示是某块板的某根针脚；pin &lt; 0 表示记下的是一个普通方块（单向电线的电源那头）。
     */
    private record Pending(BlockPos pos, int pin) {
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    public CopperWireItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.translatable("item.circuit_board.copper_wire.desc").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatable("item.circuit_board.copper_wire.desc2")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.accept(Component.translatable("item.circuit_board.copper_wire.desc3")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * 这个玩家正在连的那块板、那根针脚。
     * 返回 -1 表示手上没有待接的针脚，返回 -2 表示待接的是「另一块板」上的针脚。
     */
    public static int pendingPin(ServerPlayer player, CircuitBoardBlockEntity board) {
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null || pending.pin() < 0) {
            return -1;
        }
        if (!pending.pos().equals(board.masterPos())) {
            return -2;
        }
        return pending.pin();
    }

    /** 忘掉这个玩家手上记着的那一头。 */
    public static void clearPending(ServerPlayer player) {
        PENDING.remove(player.getUUID());
    }

    /** 右键一个普通方块（不是电路板）。 */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        return interact(context);
    }

    /**
     * 原版会先让「方块自己」处理右键，红石粉、木门就是在这里把这一下吃掉的：红石粉被切成十字 / 圆点，
     * 门直接开 / 关，拿着电线点它们根本轮不到 {@link #useOn}。所以这两类得抢在方块前面看这一下。
     *
     * <p>门、栅栏门、活板门<b>六个面都归电线</b>：点在正面 / 侧面 / 顶面 / 底面都能拉线。
     * 想开关门就空手右键，那一下照旧是门自己的。</p>
     */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockState clickedState = level.getBlockState(clicked);
        if (clickedState.is(Blocks.REDSTONE_WIRE)) {
            return interact(context, clicked, face);
        }
        if (!LinkSupport.eatsRightClick(clickedState)) {
            return InteractionResult.PASS;
        }
        BlockPos door = LinkSupport.doorAttach(level, clicked, clickedState);
        if (door == null) {
            return InteractionResult.PASS;
        }
        // 点门沿 / 门面本来都是「开门」，第一次接线时顺手提一句怎么开门（只发在物品栏上方，不刷聊天栏）
        if (!level.isClientSide() && context.getPlayer() instanceof ServerPlayer player
                && LinkSupport.handOpened(clickedState) && LinkSupport.doorSideFace(face)
                && !PENDING.containsKey(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("msg.circuit_board.door_hand_hint"), true);
        }
        return interact(context, door, face);
    }

    private InteractionResult interact(UseOnContext context) {
        return interact(context, context.getClickedPos(), context.getClickedFace());
    }

    /**
     * 真正接线的那一段。{@code clicked} / {@code face} 是「逻辑上点的是哪一格、哪一面」：
     * 点木门时它已经被换算成整扇门最上面那一格，和 {@link UseOnContext} 里的原始数据不一样。
     */
    private InteractionResult interact(UseOnContext context, BlockPos clicked, Direction face) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            // 客户端把这一下吃掉，别让红石粉在本地被切成十字、别让门先在本地开一下；真正干活的是服务端
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null) {
            return startFromBlock(level, player, clicked, face);
        }
        if (pending.pin() < 0) {
            return linkBlockToBlock(level, player, pending, clicked, face);
        }
        return linkPinToBlock(level, player, pending, clicked, face);
    }

    /** 手上什么都没记：点的那一面已经有接头，就打开那个接头的面板；没有就把这个方块记成电源那头。 */
    private InteractionResult startFromBlock(Level level, ServerPlayer player, BlockPos clicked, Direction face) {
        // 只认点在「这一面」上的接头：别的面上的线一根都不动
        WireTerminalBlockEntity existing = LinkSupport.linkTerminalAt(level, clicked);
        if (existing == null) {
            existing = LinkSupport.linkTerminalOnFace(level, clicked, face);
        }
        if (existing != null) {
            // 一个端子上常常同时挂着一串跳线和好几根针脚接过来的线，拿电线点一下就整根挖掉太狠了。
            // 改成和空手一样打开它的面板：面板里先变红、再点一次才真的删，右键取消。
            PacketDistributor.sendToPlayer(player, new OpenTerminalPayload(existing.getBlockPos()));
            return InteractionResult.SUCCESS;
        }
        PENDING.put(player.getUUID(), new Pending(clicked.immutable(), -1));
        player.sendSystemMessage(Component.translatable("msg.circuit_board.link_pick_source",
                LinkSupport.MAX_LINK_DISTANCE));
        return InteractionResult.SUCCESS;
    }

    /**
     * 已经记了一个方块：从它单向送电到点下去的这个方块。
     *
     * <p>点的是那个小铜疙瘩（接线端子）也行：电线就挂到<b>这个端子</b>上，和它上面已经挂着的跳线
     * 共用同一个接头，电就在两者之间走。</p>
     */
    private InteractionResult linkBlockToBlock(Level level, ServerPlayer player, Pending pending,
                                               BlockPos clicked, Direction face) {
        BlockPos source = pending.pos();
        if (source.equals(clicked)) {
            clearPending(player);
            player.sendSystemMessage(Component.translatable("msg.circuit_board.link_cancel"));
            return InteractionResult.SUCCESS;
        }
        if (!LinkSupport.withinRange(source, clicked)) {
            player.sendSystemMessage(Component.translatable("msg.circuit_board.link_too_far",
                    LinkSupport.MAX_LINK_DISTANCE));
            return InteractionResult.SUCCESS;
        }
        // 点在小铜疙瘩上：直接挂到这个端子上
        WireTerminalBlockEntity existing = LinkSupport.linkTerminalAt(level, clicked);
        BlockPos terminalPos;
        Direction attachFace;
        if (existing != null) {
            terminalPos = existing.getBlockPos();
            attachFace = existing.facing();
        } else {
            if (LinkSupport.alreadyLinked(level, source, clicked)) {
                clearPending(player);
                player.sendSystemMessage(Component.translatable("msg.circuit_board.link_exists"));
                return InteractionResult.SUCCESS;
            }
            terminalPos = LinkSupport.terminalPosFor(level, clicked, face);
            attachFace = terminalPos == null ? null : LinkSupport.attachFacing(terminalPos, clicked);
            if (terminalPos == null || attachFace == null) {
                player.sendSystemMessage(Component.translatable("msg.circuit_board.link_no_room"));
                return InteractionResult.SUCCESS;
            }
        }
        if (!WireTerminalBlock.placeSourceLink(level, terminalPos, source, attachFace)) {
            player.sendSystemMessage(Component.translatable("msg.circuit_board.link_no_room"));
            return InteractionResult.SUCCESS;
        }
        clearPending(player);
        if (!player.isCreative()) {
            consumeWire(player);
        }
        player.sendSystemMessage(Component.translatable(existing != null
                        ? "msg.circuit_board.link_to_terminal"
                        : "msg.circuit_board.link_ok",
                Component.translatable("item.circuit_board.copper_wire")));
        return InteractionResult.SUCCESS;
    }

    /**
     * 板子的一根针脚已经选好了：把针脚接到这个方块上（顺便装端子，好把电送出去）。
     *
     * <p>如果点的是那个小铜疙瘩、或者那个方块这一面已经长了一个端子，就直接<b>接到那个端子上</b>：
     * 端子上挂着的跳线 / 别的针脚会跟着一起通，不用再在旁边多顶一个小铜疙瘩。</p>
     */
    private InteractionResult linkPinToBlock(Level level, ServerPlayer player, Pending pending,
                                             BlockPos clicked, Direction face) {
        CircuitBoardBlockEntity source = boardAt(level, pending.pos());
        if (source == null) {
            clearPending(player);
            player.sendSystemMessage(Component.translatable("msg.circuit_board.wire_board_gone"));
            return InteractionResult.SUCCESS;
        }
        if (!LinkSupport.withinRange(source.masterPos(), clicked)) {
            player.sendSystemMessage(Component.translatable("msg.circuit_board.wire_too_far",
                    LinkSupport.MAX_LINK_DISTANCE));
            return InteractionResult.SUCCESS;
        }
        BlockPos boardPos = source.masterPos();
        boolean readOnly = player.isShiftKeyDown();

        // 1. 点在端子本身上：这个端子就是接头
        WireTerminalBlockEntity existing = LinkSupport.linkTerminalAt(level, clicked);
        if (existing != null) {
            if (existing.hasDrive(boardPos, pending.pin())
                    || alreadyWired(source, pending.pin(), existing.getBlockPos(),
                    CircuitBoardBlockEntity.KIND_TERMINAL)) {
                clearPending(player);
                player.sendSystemMessage(Component.translatable("msg.circuit_board.wire_drive_exists",
                        pinLabel(pending.pin())));
                return InteractionResult.SUCCESS;
            }
            existing.addDrive(boardPos, pending.pin());
            source.addWire(pending.pin(), existing.getBlockPos(), -1, CircuitBoardBlockEntity.KIND_TERMINAL);
            existing.refreshPower();
            level.updateNeighborsAt(existing.getBlockPos(), ModRegistry.WIRE_TERMINAL.get());
            clearPending(player);
            if (!player.isCreative()) {
                consumeWire(player);
            }
            player.sendSystemMessage(Component.translatable("msg.circuit_board.wire_terminal_join",
                    pinLabel(pending.pin())));
            return InteractionResult.SUCCESS;
        }

        // 2. 点在方块上：这一面已经有端子就接到它上面，没有才在旁边装一个新的
        BlockPos terminalPos = LinkSupport.terminalPosFor(level, clicked, face);
        Direction attachFace = terminalPos == null ? null : LinkSupport.attachFacing(terminalPos, clicked);
        boolean existed = terminalPos != null
                && level.getBlockEntity(terminalPos) instanceof WireTerminalBlockEntity;
        // 已经有一模一样的这根线了：别再吃一根电线，也别谎报「接好了」
        boolean willPlace = !readOnly && terminalPos != null && attachFace != null;
        BlockPos planned = willPlace ? terminalPos : clicked;
        int plannedKind = willPlace ? CircuitBoardBlockEntity.KIND_TERMINAL : CircuitBoardBlockEntity.KIND_READ;
        if (alreadyWired(source, pending.pin(), planned, plannedKind)) {
            clearPending(player);
            player.sendSystemMessage(Component.translatable("msg.circuit_board.wire_duplicate",
                    pinLabel(pending.pin())));
            return InteractionResult.SUCCESS;
        }
        boolean placed = !readOnly && terminalPos != null && attachFace != null
                && WireTerminalBlock.place(level, terminalPos, boardPos, pending.pin(), attachFace);
        BlockPos target = placed ? terminalPos : clicked;
        int kind = placed ? CircuitBoardBlockEntity.KIND_TERMINAL : CircuitBoardBlockEntity.KIND_READ;
        source.addWire(pending.pin(), target, -1, kind);
        clearPending(player);
        if (!player.isCreative()) {
            consumeWire(player);
        }

        BlockState targetState = level.getBlockState(clicked);
        if (placed && existed) {
            player.sendSystemMessage(Component.translatable("msg.circuit_board.wire_terminal_join",
                    pinLabel(pending.pin())));
        } else if (placed) {
            player.sendSystemMessage(Component.translatable(targetState.is(Blocks.REDSTONE_WIRE)
                    ? "msg.circuit_board.wire_terminal_dust"
                    : "msg.circuit_board.wire_terminal", pinLabel(pending.pin())));
        } else if (readOnly) {
            player.sendSystemMessage(Component.translatable("msg.circuit_board.wire_linked_read",
                    pinLabel(pending.pin())));
        } else {
            player.sendSystemMessage(Component.translatable("msg.circuit_board.wire_no_room",
                    pinLabel(pending.pin())));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * 界面里点了某根针脚。
     *
     * @param action {@link #ACTION_CHOOSE} 选这根针脚去接新线；{@link #ACTION_REMOVE} 拆掉第 wire 根线；
     *               {@link #ACTION_CANCEL} 取消这根针脚上的选择
     * @param wire   这根针脚上的第几根线（0 开始）
     */
    public static void onWireAction(ServerPlayer player, BlockPos pos, int pin, int action, int wire) {
        CircuitBoardBlockEntity board = boardAt(player.level(), pos);
        if (board == null || pin < 0 || pin >= BoardData.PIN_COUNT) {
            return;
        }
        CircuitBoardBlockEntity master = board.master();
        if (master == null) {
            return;
        }
        if (action == ACTION_CANCEL) {
            Pending pending = PENDING.get(player.getUUID());
            if (pending != null && pending.pin() == pin && pending.pos().equals(master.masterPos())) {
                clearPending(player);
            }
            return;
        }
        if (action == ACTION_REMOVE) {
            if (master.removeWireAt(wire)) {
                player.sendSystemMessage(Component.translatable("msg.circuit_board.wire_removed_n",
                        pinLabel(pin), wire + 1));
            } else {
                player.sendSystemMessage(Component.translatable("msg.circuit_board.wire_none_n",
                        pinLabel(pin), wire + 1));
            }
            return;
        }
        if (!master.hasPinAll(pin)) {
            player.sendSystemMessage(Component.translatable("msg.circuit_board.wire_need_trace"));
            return;
        }
        Pending pending = PENDING.get(player.getUUID());
        if (pending != null && pending.pin() == pin && pending.pos().equals(master.masterPos())) {
            clearPending(player);
            player.sendSystemMessage(Component.translatable("msg.circuit_board.wire_cleared"));
            return;
        }
        if (pending == null || pending.pin() < 0 || pending.pos().equals(master.masterPos())) {
            // 同一块板上的另一根针脚：改成记住这一根（板子内部的针脚本来就通，不用接线）
            PENDING.put(player.getUUID(), new Pending(master.masterPos(), pin));
            player.sendSystemMessage(Component.translatable("msg.circuit_board.wire_pin_picked",
                    pinLabel(pin)));
            return;
        }
        CircuitBoardBlockEntity source = boardAt(player.level(), pending.pos());
        if (source == null) {
            clearPending(player);
            player.sendSystemMessage(Component.translatable("msg.circuit_board.wire_board_gone"));
            return;
        }
        if (!LinkSupport.withinRange(source.masterPos(), master.masterPos())) {
            player.sendSystemMessage(Component.translatable("msg.circuit_board.wire_too_far",
                    LinkSupport.MAX_LINK_DISTANCE));
            return;
        }
        source.addWire(pending.pin(), master.masterPos(), pin, CircuitBoardBlockEntity.KIND_BOARD);
        master.addWire(pin, source.masterPos(), pending.pin(), CircuitBoardBlockEntity.KIND_BOARD);
        clearPending(player);
        if (!player.isCreative()) {
            consumeWire(player);
        }
        player.sendSystemMessage(Component.translatable("msg.circuit_board.wire_linked",
                pinLabel(pending.pin()), pinLabel(pin)));
    }

    private static CircuitBoardBlockEntity boardAt(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CircuitBoardBlockEntity board ? board : null;
    }

    /** 这根针脚上是不是已经有一根「一样的目标、一样的接法」的线了。 */
    private static boolean alreadyWired(CircuitBoardBlockEntity board, int pin, BlockPos target, int kind) {
        for (CircuitBoardBlockEntity.WireLink wire : board.wiresOf(pin)) {
            if (wire.kind() == kind && wire.target().equals(target)) {
                return true;
            }
        }
        return false;
    }

    private static void consumeWire(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        ItemStack wire = new ItemStack(ModRegistry.COPPER_WIRE.get());
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(wire.getItem())) {
                stack.shrink(1);
                inventory.setChanged();
                return;
            }
        }
    }

    public static Component pinLabel(int pin) {
        return Component.translatable("gui.circuit_board.pin",
                Component.translatable(sideKey(BoardData.pinSide(pin))), BoardData.pinAlong(pin) + 1);
    }

    public static String sideKey(int side) {
        return switch (side & 3) {
            case 0 -> "gui.circuit_board.north";
            case 1 -> "gui.circuit_board.east";
            case 2 -> "gui.circuit_board.south";
            default -> "gui.circuit_board.west";
        };
    }
}
